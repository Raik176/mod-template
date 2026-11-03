package de.rhm176.build

import de.rhm176.build.model.ClassTweakerConverter
import de.rhm176.build.model.Json
import de.rhm176.build.model.Json5
import de.rhm176.build.model.LoaderType
import de.rhm176.build.model.McVersion
import de.rhm176.build.model.MetadataGenerators
import de.rhm176.build.model.MetadataTarget
import de.rhm176.build.model.MixinConfigMerger
import de.rhm176.build.model.ModMetadata
import de.rhm176.build.model.VersionRange
import de.rhm176.build.model.mapping.AccessTransformerTranslator
import de.rhm176.build.model.mapping.LegacyForgeMappings
import de.rhm176.build.model.mapping.McpConfig
import de.rhm176.build.model.mapping.MojangApi
import de.rhm176.build.model.mapping.PackFormatResolver
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Cheap; reads compiled classes")
abstract class ScanModClasses : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirectories: ConfigurableFileCollection

    @get:Input
    abstract val packageDefaults: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun scan() {
        val defaults = packageDefaults.get().map {
            val (pkg, side) = it.split('=', limit = 2)
            MixinPackageDefault(pkg, MixinSide.valueOf(side))
        }
        val result = ModClassScanner.scan(classDirectories.files, defaults)

        val json = linkedMapOf<String, Any?>(
            "entrypoints" to result.entrypoints.mapValuesTo(linkedMapOf<String, Any?>()) { it.value.toList() },
            "mixins" to result.mixins.map {
                linkedMapOf<String, Any?>(
                    "className" to it.className,
                    "alias" to it.alias,
                    "bucket" to it.side.bucket,
                )
            },
        )
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(Json.write(json))
        }
    }
}

@DisableCachingByDefault(because = "Inputs include absolute paths")
abstract class GenerateModMetadata : DefaultTask() {
    @get:Input
    abstract val metadataJson: Property<String>

    @get:Input
    abstract val loaderId: Property<String>

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Input
    abstract val minecraftRange: Property<String>

    @get:Input
    abstract val loaderVersion: Property<String>

    @get:Input
    abstract val javaVersion: Property<Int>

    @get:Input
    abstract val fabricApi: Property<Boolean>

    @get:Input
    abstract val modMenu: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val accessWidener: Property<String>

    @get:Input
    @get:Optional
    abstract val refmapName: Property<String>

    @get:Input
    abstract val mixinSpecs: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mixinSources: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val scanResult: RegularFileProperty

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val defaultDir: DirectoryProperty

    @get:OutputDirectory
    abstract val overrideDir: DirectoryProperty

    @TaskAction
    @Suppress("UNCHECKED_CAST")
    fun generate() {
        val loader = LoaderType.fromId(loaderId.get())
        val metadata = ModMetadata.fromJson(metadataJson.get())
        val scan = Json.parseObject(scanResult.get().asFile.readText())
        val discoveredEntrypoints = scan["entrypoints"] as Map<String, List<String>>
        val discoveredMixins = scan["mixins"] as List<Map<String, Any?>>

        val specs = mixinSpecs.get().map {
            val (alias, path, source) = it.split('|', limit = 3)
            Triple(alias, path, source)
        }
        val unknownAliases = discoveredMixins.map { it["alias"] as String }.toSet() - specs.map { it.first }.toSet()
        if (unknownAliases.isNotEmpty()) {
            throw GradleException(
                "Mixin classes reference unconfigured mixin aliases: ${unknownAliases.sorted().joinToString()}. " +
                    "Register them with mixins.configure(sourceSets.main) { mixin(path, alias) }."
            )
        }
        if (specs.isEmpty() && discoveredMixins.isNotEmpty()) {
            logger.warn(
                "Found ${discoveredMixins.size} @Mixin class(es) but no mixin config is registered; " +
                    "they will not be loaded."
            )
        }

        val overrides = overrideDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        specs.forEach { (alias, path, source) ->
            val buckets = MixinConfigMerger.emptyBuckets()
            discoveredMixins.filter { it["alias"] == alias }.forEach {
                buckets.getValue(it["bucket"] as String).add(it["className"] as String)
            }
            val merged = try {
                MixinConfigMerger.merge(File(source).readText(), buckets, javaVersion.get(), refmapName.orNull)
            } catch (e: IllegalArgumentException) {
                throw GradleException("Mixin config '$path': ${e.message}", e)
            }
            overrides.resolve(path).apply { parentFile.mkdirs(); writeText(merged) }
        }

        var packFormat: Int? = null
        if ((loader == LoaderType.FORGE || loader == LoaderType.NEOFORGE) && metadata.packFormat() == null) {
            packFormat = try {
                PackFormatResolver.resolve(minecraftVersion.get(), cacheDir.get().asFile.toPath(), MojangApi())
            } catch (e: java.io.IOException) {
                logger.warn("modMetadata: could not read the pack format of Minecraft ${minecraftVersion.get()}: ${e.message}")
                null
            }
        }

        val target = MetadataTarget(
            loader,
            McVersion.parse(minecraftVersion.get()),
            VersionRange.parse(minecraftRange.get()),
            loaderVersion.get(),
            javaVersion.get(),
            fabricApi.get(),
            if (loader == LoaderType.FABRIC) discoveredEntrypoints else emptyMap(),
            specs.map { it.second },
            accessWidener.orNull,
            packFormat,
            modMenu.get(),
        )
        val output = MetadataGenerators.generate(metadata, target)
        output.warnings().forEach { logger.warn("modMetadata: $it") }

        val defaults = defaultDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        output.files().forEach { file ->
            defaults.resolve(file.path()).apply { parentFile.mkdirs(); writeText(file.content()) }
        }
    }
}

@DisableCachingByDefault(because = "Downloads mapping data")
abstract class GenerateSrgMappings : DefaultTask() {
    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputFile
    abstract val namedToSrg: RegularFileProperty

    @get:OutputFile
    abstract val srgToNamed: RegularFileProperty

    @TaskAction
    fun generate() {
        val mappings = LegacyForgeMappings.load(
            minecraftVersion.get(),
            cacheDir.get().asFile.toPath(),
            MojangApi(),
            McpConfig.DEFAULT_MAVEN,
        )
        namedToSrg.get().asFile.apply { parentFile.mkdirs(); writeText(mappings.toNamedToSrg()) }
        srgToNamed.get().asFile.apply { parentFile.mkdirs(); writeText(mappings.toSrgToNamed()) }
    }
}

@CacheableTask
abstract class GenerateAccessTransformer : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tweakers: ConfigurableFileCollection

    @get:Input
    abstract val tweakerPaths: ListProperty<String>

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Input
    abstract val srgNames: Property<Boolean>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val paths = tweakerPaths.get()
        val files = tweakers.files.toList()
        check(paths.size == files.size) { "Class tweaker paths and files differ: $paths vs $files" }

        val lines = linkedSetOf<String>()
        paths.zip(files).forEach { (path, file) ->
            val converted = try {
                ClassTweakerConverter.toAccessTransformer(file.readText())
            } catch (e: IllegalArgumentException) {
                throw GradleException("Class tweaker '$path': ${e.message}", e)
            }
            converted.warnings().forEach { logger.warn("$path: $it") }
            converted.accessTransformer().lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach(lines::add)
        }

        var atLines: List<String> = lines.toList()
        if (srgNames.get()) {
            val mappings = try {
                LegacyForgeMappings.load(
                    minecraftVersion.get(),
                    cacheDir.get().asFile.toPath(),
                    MojangApi(),
                    McpConfig.DEFAULT_MAVEN,
                )
            } catch (e: java.io.IOException) {
                throw GradleException(
                    "Cannot load the mappings of Minecraft ${minecraftVersion.get()} needed to turn the class tweaker " +
                        "into a Forge access transformer: ${e.message}",
                    e,
                )
            }
            val translated = AccessTransformerTranslator.toSrg(lines.joinToString("\n"), mappings)
            if (translated.errors().isNotEmpty()) {
                throw GradleException(
                    "The class tweaker has targets that do not exist in Minecraft ${minecraftVersion.get()}:\n" +
                        translated.errors().joinToString("\n") { " - $it" }
                )
            }
            atLines = translated.text().lines().filter { it.isNotBlank() }
        }

        val root = outputDirectory.get().asFile.apply { deleteRecursively(); mkdirs() }
        File(root, "META-INF/accesstransformer.cfg").apply {
            parentFile.mkdirs()
            writeText("# Generated from ${paths.joinToString()}. Do not edit.\n" + atLines.joinToString("") { "$it\n" })
        }
    }
}

private class ResourceSource(val file: File, val path: String, val moved: Boolean) {
    val converted: Boolean get() = Json5.isCommented(path)
}

@CacheableTask
abstract class RelocateResources : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectories: ConfigurableFileCollection

    @get:Input
    abstract val rules: ListProperty<RelocationRule>

    @get:Input
    abstract val jsonIndent: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun relocate() {
        val relocator = ResourceRelocator(rules.get())
        val winners = linkedMapOf<String, ResourceSource>()
        sourceDirectories.files.filter(File::isDirectory).forEach { directory ->
            val inDirectory = hashMapOf<String, ResourceSource>()
            directory.walkTopDown().filter(File::isFile).forEach { file ->
                val path = file.relativeTo(directory).invariantSeparatorsPath
                val relocated = relocator.relocate(path)
                val source = ResourceSource(file, path, relocated != path)
                val clash = inDirectory.put(relocated, source)
                if (clash != null && (clash.converted || source.converted)) {
                    throw GradleException(
                        "'${clash.path}' and '$path' (in ${directory.path}) both produce '$relocated'; keep only one of them"
                    )
                }
                winners[relocated] = source
            }
        }

        val indent = jsonIndent.get()
        val root = outputDirectory.get().asFile.apply { deleteRecursively(); mkdirs() }
        winners.forEach { (path, source) ->
            if (!source.moved) return@forEach
            val target = File(root, path)
            if (!source.converted) {
                source.file.copyTo(target, overwrite = true)
                return@forEach
            }
            val json = try {
                Json5.toJson(source.file.readText(Charsets.UTF_8), indent)
            } catch (e: IllegalArgumentException) {
                throw GradleException("${source.path}: ${e.message}", e)
            }
            if (json == null) {
                logger.info("${source.path} has no content (blank or commented out); it is left out of the jar")
                return@forEach
            }
            target.parentFile.mkdirs()
            target.writeText(json, Charsets.UTF_8)
        }
    }
}

abstract class MixinApArguments : CommandLineArgumentProvider {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val srgFile: RegularFileProperty

    @get:Input
    abstract val refmapPath: Property<String>

    override fun asArguments(): Iterable<String> = listOf(
        "-AreobfSrgFile=${srgFile.get().asFile.absolutePath}",
        "-AoutRefMapFile=${refmapPath.get()}",
        "-AdefaultObfuscationEnv=searge",
    )
}
