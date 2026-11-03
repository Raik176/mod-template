package de.rhm176.build

import de.rhm176.build.mixin.MixinEnvironment
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.tasks.SourceSet

internal enum class MixinSide(val bucket: String) {
    COMMON("mixins"),
    CLIENT("client"),
    SERVER("server"),
}

internal data class MixinRegistration(val path: String, val alias: String)

internal data class MixinPackageDefault(val packageName: String, val side: MixinSide)

open class MixinRegistryExtension {
    internal val registrations = mutableListOf<MixinRegistration>()
    internal val packageDefaults = mutableListOf<MixinPackageDefault>()

    fun configure(sourceSet: SourceSet, action: Action<MixinSourceSetSpec>) {
        if (sourceSet.name != "main") {
            throw GradleException("Mixins can only be registered for the 'main' source set (got '${sourceSet.name}')")
        }
        action.execute(MixinSourceSetSpec(registrations, packageDefaults))
    }

    fun configure(sourceSet: NamedDomainObjectProvider<SourceSet>, action: Action<MixinSourceSetSpec>) {
        configure(sourceSet.get(), action)
    }

    internal fun validate() {
        registrations.groupBy { it.alias }.forEach { (alias, entries) ->
            if (entries.size > 1) {
                throw GradleException(
                    "Mixin alias '$alias' is configured more than once: ${entries.joinToString { it.path }}"
                )
            }
        }
        packageDefaults.groupBy { it.packageName }.forEach { (packageName, entries) ->
            if (entries.size > 1) {
                throw GradleException("Default Mixin type for package '$packageName' is configured more than once")
            }
        }
    }
}

class MixinSourceSetSpec internal constructor(
    private val registrations: MutableList<MixinRegistration>,
    private val packageDefaults: MutableList<MixinPackageDefault>,
) {
    fun mixin(path: String) = mixin(path, "default")

    fun mixin(path: String, alias: String) {
        require(path.isNotBlank()) { "Mixin config path cannot be blank" }
        require(alias.isNotBlank()) { "Mixin alias cannot be blank" }
        registrations += MixinRegistration(normalizeResourcePath(path), alias)
    }

    fun defaultType(packageName: String, type: MixinEnvironment.Type) {
        val normalized = packageName.trim().trimEnd('.')
        require(normalized.isNotBlank()) { "Mixin package cannot be blank" }
        packageDefaults += MixinPackageDefault(normalized, MixinSide.valueOf(type.name))
    }
}

open class ClassTweakerRegistryExtension {
    internal val paths = mutableListOf<String>()

    fun configure(sourceSet: SourceSet, action: Action<ClassTweakerSourceSetSpec>) {
        if (sourceSet.name != "main") {
            throw GradleException("Class tweakers can only be registered for the 'main' source set (got '${sourceSet.name}')")
        }
        action.execute(ClassTweakerSourceSetSpec(paths))
    }

    fun configure(sourceSet: NamedDomainObjectProvider<SourceSet>, action: Action<ClassTweakerSourceSetSpec>) {
        configure(sourceSet.get(), action)
    }
}

class ClassTweakerSourceSetSpec internal constructor(private val paths: MutableList<String>) {
    fun classTweaker(path: String) {
        require(path.isNotBlank()) { "Class tweaker path cannot be blank" }
        val normalized = normalizeResourcePath(path)
        if (normalized in paths) throw GradleException("Class tweaker '$normalized' is configured more than once")
        paths += normalized
    }
}

internal fun normalizeResourcePath(path: String): String = path.replace('\\', '/').removePrefix("./")
