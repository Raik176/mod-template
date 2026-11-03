package de.rhm176.build

import org.gradle.api.GradleException
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

private const val MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;"
private const val MIXIN_ENVIRONMENT_ANNOTATION = "Lde/rhm176/build/mixin/MixinEnvironment;"
private const val MIXIN_ENVIRONMENT_TYPE_DESCRIPTOR = $$"Lde/rhm176/build/mixin/MixinEnvironment$Type;"
private const val ENTRYPOINT_ANNOTATION = "Lde/rhm176/build/fabric/FabricEntrypoint;"

private val STANDARD_CLASS_ENTRYPOINTS = mapOf(
    "net/fabricmc/api/ModInitializer" to "main",
    "net/fabricmc/api/ClientModInitializer" to "client",
    "net/fabricmc/api/DedicatedServerModInitializer" to "server",
    "com/terraformersmc/modmenu/api/ModMenuApi" to "modmenu",
    "net/fabricmc/fabric/api/datagen/v1/DataGeneratorEntrypoint" to "fabric-datagen",
)

internal data class DiscoveredMixin(val className: String, val alias: String, val side: MixinSide)

internal data class ScanResult(
    val entrypoints: Map<String, Set<String>>,
    val mixins: List<DiscoveredMixin>,
)

internal object ModClassScanner {
    fun scan(roots: Iterable<File>, packageDefaults: List<MixinPackageDefault>): ScanResult {
        val entrypoints = linkedMapOf<String, MutableSet<String>>()
        val mixins = mutableListOf<DiscoveredMixin>()
        val flags = ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES

        roots.filter(File::exists).forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { classFile ->
                val entrypointVisitor = EntrypointClassVisitor { key, value ->
                    if (key.isBlank()) throw GradleException("@FabricEntrypoint key cannot be blank")
                    entrypoints.getOrPut(key) { linkedSetOf() }.add(value)
                }
                ClassReader(classFile.readBytes()).accept(MixinClassVisitor(packageDefaults, mixins::add, entrypointVisitor), flags)
            }
        }

        return ScanResult(
            entrypoints.mapValues { (_, values) -> values.toSortedSet() as Set<String> }.toSortedMap(),
            mixins.distinct().sortedWith(compareBy({ it.alias }, { it.side }, { it.className })),
        )
    }
}

private class MixinClassVisitor(
    private val packageDefaults: List<MixinPackageDefault>,
    private val add: (DiscoveredMixin) -> Unit,
    next: ClassVisitor,
) : ClassVisitor(Opcodes.ASM9, next) {
    private lateinit var className: String
    private var isMixin = false
    private var alias = "default"
    private var explicitSide: MixinSide? = null

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        className = name.replace('/', '.')
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? = when (descriptor) {
        MIXIN_ANNOTATION -> {
            isMixin = true
            super.visitAnnotation(descriptor, visible)
        }

        MIXIN_ENVIRONMENT_ANNOTATION -> object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visit(name: String?, value: Any?) {
                if (name == "alias" && value is String) alias = value
                super.visit(name, value)
            }

            override fun visitEnum(name: String?, descriptor: String?, value: String?) {
                if (name == "type" && descriptor == MIXIN_ENVIRONMENT_TYPE_DESCRIPTOR && value != null) {
                    explicitSide = try {
                        MixinSide.valueOf(value)
                    } catch (_: IllegalArgumentException) {
                        throw GradleException("Unknown @MixinEnvironment.Type '$value' on $className")
                    }
                }
                super.visitEnum(name, descriptor, value)
            }
        }

        else -> super.visitAnnotation(descriptor, visible)
    }

    override fun visitEnd() {
        if (isMixin) {
            if (alias.isBlank()) throw GradleException("@MixinEnvironment alias on $className cannot be blank")
            add(DiscoveredMixin(className, alias, explicitSide ?: defaultSideForPackage() ?: MixinSide.COMMON))
        }
        super.visitEnd()
    }

    private fun defaultSideForPackage(): MixinSide? {
        val packageName = className.substringBeforeLast('.', missingDelimiterValue = "")
        return packageDefaults
            .asSequence()
            .filter { packageName == it.packageName || packageName.startsWith("${it.packageName}.") }
            .maxByOrNull { it.packageName.length }
            ?.side
    }
}

private class EntrypointClassVisitor(
    private val add: (String, String) -> Unit,
) : ClassVisitor(Opcodes.ASM9) {
    private lateinit var className: String
    private var implementedInterfaces: Set<String> = emptySet()

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        className = name.replace('/', '.')
        implementedInterfaces = interfaces?.toSet().orEmpty()
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        if (descriptor != ENTRYPOINT_ANNOTATION) return null
        return entrypointAnnotation { configuredKey ->
            val key = configuredKey.ifBlank {
                STANDARD_CLASS_ENTRYPOINTS.entries
                    .firstOrNull { (interfaceName, _) -> interfaceName in implementedInterfaces }
                    ?.value
                    ?: throw GradleException("Cannot infer Fabric entrypoint key for $className.")
            }
            add(key, className)
        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
        override fun visitAnnotation(annotationDescriptor: String, visible: Boolean): AnnotationVisitor? {
            if (annotationDescriptor != ENTRYPOINT_ANNOTATION) return null
            if (name == "<init>" || name == "<clinit>") {
                throw GradleException("@FabricEntrypoint cannot be used on a constructor in $className")
            }
            if ((access and Opcodes.ACC_STATIC) == 0) {
                throw GradleException("Fabric member entrypoint must be static: $className::$name")
            }
            return entrypointAnnotation { key -> add(key.ifBlank { "main" }, "$className::$name") }
        }
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?,
    ): FieldVisitor = object : FieldVisitor(Opcodes.ASM9) {
        override fun visitAnnotation(annotationDescriptor: String, visible: Boolean): AnnotationVisitor? {
            if (annotationDescriptor != ENTRYPOINT_ANNOTATION) return null
            if ((access and Opcodes.ACC_STATIC) == 0) {
                throw GradleException("Fabric member entrypoint must be static: $className::$name")
            }
            return entrypointAnnotation { key -> add(key.ifBlank { "main" }, "$className::$name") }
        }
    }
}

private fun entrypointAnnotation(onComplete: (String) -> Unit): AnnotationVisitor =
    object : AnnotationVisitor(Opcodes.ASM9) {
        private var key = ""

        override fun visit(name: String?, value: Any?) {
            if (name == "value" && value is String) key = value
            super.visit(name, value)
        }

        override fun visitEnd() {
            onComplete(key)
            super.visitEnd()
        }
    }
