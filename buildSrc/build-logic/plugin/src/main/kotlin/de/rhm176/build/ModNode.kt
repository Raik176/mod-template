package de.rhm176.build

import de.rhm176.build.config.MinecraftConfig
import de.rhm176.build.fabric.FabricEntrypoint
import de.rhm176.build.model.DatagenRules
import de.rhm176.build.model.ForgeMixinRules
import de.rhm176.build.model.Json5
import de.rhm176.build.model.LoaderType
import de.rhm176.build.model.McVersion
import de.rhm176.build.model.MixinExtrasRules
import de.rhm176.build.model.NeoForgeRules
import de.rhm176.build.model.changelog.RepoLinks
import de.rhm176.build.model.UpdateJson
import de.rhm176.build.model.VersionRange
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

private fun toolPluginId(loader: LoaderType, minecraft: McVersion): String = when (loader) {
    LoaderType.FABRIC ->
        if (minecraft.isAtLeast(McVersion.FIRST_UNOBFUSCATED)) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap"
    LoaderType.FORGE ->
        if (ForgeMixinRules.usesLegacyPlugin(minecraft)) "net.neoforged.moddev.legacyforge" else "net.minecraftforge.gradle"
    LoaderType.NEOFORGE ->
        if (NeoForgeRules.usesNeoGradle(minecraft)) "net.neoforged.gradle.userdev" else "net.neoforged.moddev"
    LoaderType.QUILT -> error("Quilt has no build target")
}

private fun Project.warnOnce(key: String, message: String) {
    val extra = rootProject.extensions.extraProperties
    val flag = "de.rhm176.build.warned.$key"
    if (extra.has(flag)) return
    extra.set(flag, true)
    logger.warn(message)
}

private fun Project.sourceRoots(kind: String, minecraft: String, loader: LoaderType): List<File> {
    val targets = listOf("common", loader.id())
    return targets.map { rootProject.file("src/$it/main/$kind") } +
        targets.map { rootProject.file("src/versions/$minecraft/$it/main/$kind") }
}

private fun resolve(rootsHighToLow: List<File>, path: String): File? =
    rootsHighToLow.flatMap { root -> Json5.sourcePaths(path).map { File(root, it) } }.firstOrNull { it.isFile }

internal fun Project.configureModNode(
    loader: LoaderType,
    minecraftText: String,
    config: MinecraftConfig,
    metadata: ModMetadataExtension,
    mixins: MixinRegistryExtension,
    classTweakers: ClassTweakerRegistryExtension,
    resourceRelocations: ResourceRelocationExtension,
    datagen: DatagenExtension,
    mixinExtras: MixinExtrasExtension,
) {
    val minecraft = McVersion.parse(minecraftText)
    val nodeName = loader.nodeName(minecraftText)
    if (!loader.supports(minecraft)) {
        throw GradleException("${loader.displayName()} is supported from Minecraft ${loader.minimum()}, not $minecraft ($nodeName)")
    }

    val loaderVersion = config.loaderVersion(loader.id())
        ?: throw GradleException("${config.sourceFile.name} has no '${loader.id()}-loader' entry")
    val fabricApi = if (loader == LoaderType.FABRIC) config.versionOrNull("fabric-api") else null
    val javaVersion = config.requiredVersion("java").toInt()
    if (javaVersion != minecraft.javaVersion()) {
        logger.warn("$nodeName: catalog says Java $javaVersion but Minecraft $minecraft normally uses Java ${minecraft.javaVersion()}")
    }
    val spec = config.minecraft
    val lowest = minOf(McVersion.parse(spec.version), McVersion.parse(spec.compatibilityBound))
    val highest = maxOf(McVersion.parse(spec.version), McVersion.parse(spec.compatibilityBound))
    val minecraftRange = VersionRange.between(lowest.toString(), true, highest.toString(), true)

    val forgeLegacy = loader == LoaderType.FORGE && ForgeMixinRules.usesLegacyPlugin(minecraft)
    val srgEraForge = loader == LoaderType.FORGE && ForgeMixinRules.usesSrgNames(minecraft)
    val neoGradle = loader == LoaderType.NEOFORGE && NeoForgeRules.usesNeoGradle(minecraft)
    val cacheDirectory = gradle.gradleUserHomeDir.resolve("caches/rhm176-mod-build")

    extensions.getByType<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

    repositories.mavenCentral()
    repositories.maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
    if (loader == LoaderType.FABRIC) repositories.maven { url = uri("https://maven.fabricmc.net/") }
    val annotationsJar = FabricEntrypoint::class.java.protectionDomain.codeSource.location.toURI()
    dependencies.add("compileOnly", files(annotationsJar))
    dependencies.add("compileOnly", "org.spongepowered:mixin:${if (srgEraForge) "0.8.5" else "0.8.7"}")

    val sourceSets = extensions.getByType<SourceSetContainer>()
    val main = sourceSets.getByName("main")
    val javaRoots = sourceRoots("java", minecraftText, loader)
    val resourceRoots = sourceRoots("resources", minecraftText, loader)
    main.java.setSrcDirs(javaRoots)
    main.resources.setSrcDirs(resourceRoots)
    tasks.named<ProcessResources>("processResources") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }

    val runDir = layout.projectDirectory.dir("run")
    val prepareRun = tasks.register<PrepareRunDirectory>("prepareModRunDirectory") {
        runDirectory.set(runDir)
    }
    tasks.matching { it.name.startsWith("run") && !it.name.startsWith("runtime") && it.name != prepareRun.name }
        .configureEach { dependsOn(prepareRun) }

    afterEvaluate {
        val publishing = extensions.getByType<ModPublishingExtension>()
        val githubRepository = if (publishing.githubEnabled) {
            publishing.githubRepository ?: RepoLinks.parse(metadata.sources).map { it.slug() }.orElse(null)
        } else {
            null
        }
        val updateJson = publishing.updateJsonSettings?.takeIf { it.enabled }
        val updateJsonUrl = updateJson?.let {
            val repositoryUrl = (publishing.githubRepository ?: RepoLinks.parse(metadata.sources).map { r -> r.slug() }.orElse(null))
                ?.let { slug -> "https://github.com/$slug" }
                ?: metadata.sources
            try {
                UpdateJson.url(it.url, repositoryUrl, it.branch, it.path)
            } catch (e: IllegalArgumentException) {
                throw GradleException("Invalid modPublishing.updateJson: ${e.message}", e)
            }
        }
        if (updateJson != null && updateJsonUrl == null && metadata.updateUrl == null &&
            (loader == LoaderType.FORGE || loader == LoaderType.NEOFORGE)
        ) {
            logger.warn(
                "$nodeName: modPublishing.updateJson cannot tell where the file will be served from. Set modMetadata.sources " +
                    "to the GitHub URL of the repository, or updateJson { url = \"...\" }; no updateJSONURL is written"
            )
        }
        val model = try {
            metadata.toModel(
                version.toString(),
                publishing.modrinthProject,
                publishing.curseforgeProject,
                githubRepository,
                updateJsonUrl,
            )
        } catch (e: RuntimeException) {
            throw GradleException(e.message ?: "Invalid modMetadata", e)
        }
        mixins.validate()

        val vcsMinecraft = rootProject.file("gradle/minecraft/vcs.txt")
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.let { McVersion.parse(LoaderType.minecraftOfVcsEntry(it)) }

        val datagenSettings = datagen.takeIf { it.enabled }
        val sharedDatagen = datagenSettings?.minecraft != null
        val generatedRoot = datagenSettings?.let { settings ->
            val default = if (sharedDatagen) "src/common/generated/resources" else "src/versions/<mc>/generated/resources"
            rootProject.file((settings.outputDirectory ?: default).replace("<mc>", minecraftText))
        }
        val allResourceRoots = listOfNotNull(generatedRoot) + resourceRoots
        val relocationRoots = if (sharedDatagen) allResourceRoots else resourceRoots
        main.resources.setSrcDirs(allResourceRoots)
        val fabricMinecraft = rootProject.childProjects.values
            .filter { branch -> runCatching { LoaderType.fromBranchName(branch.name) }.getOrNull() == LoaderType.FABRIC }
            .flatMap { it.childProjects.keys }
        val datagenSupported = DatagenRules.supports(minecraft)
        if (loader == LoaderType.FABRIC && !datagenSupported) main.java.exclude("**/fabric/datagen/**")
        if (datagenSettings != null && loader == LoaderType.FABRIC && !datagenSupported) {
            warnOnce(
                "datagen-unsupported-$minecraftText",
                "datagen: Fabric API has data generation from Minecraft ${DatagenRules.FIRST_MINECRAFT}, so nothing is generated " +
                    "for $minecraftText and the classes in fabric/datagen are left out of its build"
            )
        }
        val hasGenerator = datagenSettings != null && loader == LoaderType.FABRIC && datagenSupported && javaRoots.any { root ->
            root.walkTopDown().any { it.isFile && (it.extension == "java" || it.extension == "kt") && "DataGeneratorEntrypoint" in it.readText() }
        }
        if (datagenSettings != null && sharedDatagen && datagenSettings.minecraft !in fabricMinecraft) {
            warnOnce(
                "datagen-no-fabric",
                "datagen { minecraft = \"${datagenSettings.minecraft}\" }: there is no Fabric build for that Minecraft version " +
                    "to run the generators on, so nothing is generated"
            )
        }
        if (datagenSettings != null && !sharedDatagen && minecraftText !in fabricMinecraft) {
            warnOnce(
                "datagen-no-fabric-$minecraftText",
                "datagen: there is no Fabric build for Minecraft $minecraftText, so nothing is generated for it " +
                    "(its resources are the ones in src/common and src/versions/$minecraftText)"
            )
        }
        if (datagenSettings != null && loader == LoaderType.FABRIC && datagenSupported && !hasGenerator &&
            (!sharedDatagen || minecraftText == datagenSettings.minecraft)
        ) {
            warnOnce(
                "datagen-no-generator-$minecraftText",
                "datagen: no class implementing DataGeneratorEntrypoint for Fabric $minecraftText, so nothing is generated " +
                    "for it. Add one in src/fabric (or src/versions/$minecraftText/fabric for this version only)."
            )
        }
        val runsDatagen = hasGenerator && (!sharedDatagen || minecraftText == datagenSettings!!.minecraft)
        if (runsDatagen && fabricApi == null) {
            throw GradleException("$nodeName: data generation needs Fabric API, but ${config.sourceFile.name} has no 'fabric-api' version")
        }
        if (runsDatagen) {
            val activeFabric = datagenSettings!!.minecraft
                ?: vcsMinecraft?.toString()?.takeIf { it in fabricMinecraft }
                ?: fabricMinecraft.maxWithOrNull { a, b -> McVersion.compare(a, b) }
            if (minecraftText == activeFabric) {
                val nodePath = path
                rootProject.tasks.register("runDatagenFabric") {
                    group = "mod runs"
                    description = "Runs the data generators on Fabric $minecraftText; each loader of that version uses the output."
                    dependsOn("$nodePath:runDatagen")
                }
            }
        }

        val highToLow = allResourceRoots.asReversed()

        val modMenuSettings = metadata.modMenuSettings?.takeIf { it.enabled && loader == LoaderType.FABRIC }
        val modMenuVersion = modMenuSettings?.let { config.versionOrNull("modmenu") }
        if (modMenuSettings != null && modMenuVersion == null) {
            logger.warn(
                "$nodeName: modMetadata has a modMenu block, but ${config.sourceFile.name} has no 'modmenu' version, " +
                    "so Mod Menu is left out of this build"
            )
        }
        if (loader == LoaderType.FABRIC && modMenuVersion == null) main.java.exclude("**/fabric/modmenu/**")

        val relocationRules = try {
            resourceRelocations.rules(minecraft, vcsMinecraft)
        } catch (e: IllegalArgumentException) {
            throw GradleException("Invalid resourceRelocations: ${e.message}", e)
        }
        val relocate = tasks.register<RelocateResources>("relocateResources") {
            sourceDirectories.from(relocationRoots)
            rules.set(relocationRules)
            jsonIndent.set(resourceRelocations.jsonIndent)
            outputDirectory.set(layout.buildDirectory.dir("generated/resourceRelocation"))
        }
        val relocator = ResourceRelocator(relocationRules)
        tasks.named<ProcessResources>("processResources") {
            exclude("**/.cache/**")
            from(relocate.flatMap { it.outputDirectory })
            exclude { element ->
                val root = relocationRoots.firstOrNull { element.file.startsWith(it) } ?: return@exclude false
                val path = element.file.relativeTo(root).invariantSeparatorsPath
                relocator.relocate(path) != path
            }
        }

        extensions.getByType<BasePluginExtension>().archivesName.set("${model.modId()}-${loader.id()}-$minecraftText")

        val mixinSources = mixins.registrations.map { registration ->
            registration to (
                resolve(highToLow, registration.path)
                    ?: throw GradleException(
                        "Mixin config '${registration.path}' not found in: " +
                            highToLow.joinToString { it.relativeTo(rootProject.projectDir).path }
                    )
                )
        }
        val mixinPaths = mixinSources.map { it.first.path }.distinct()


        val extrasSettings = mixinExtras.takeIf { it.enabled }
        val extrasDelivery = extrasSettings?.let {
            if (mixinPaths.isEmpty()) {
                warnOnce("mixinextras-no-mixins", "mixinExtras is enabled, but no mixin config is registered (mixins.configure { mixin(...) }), so it is left out")
                null
            } else {
                MixinExtrasRules.delivery(loader, minecraft, loaderVersion).also { delivery ->
                    if (delivery == MixinExtrasRules.Delivery.UNSUPPORTED) {
                        warnOnce(
                            "mixinextras-unsupported-$nodeName",
                            "$nodeName: mixinExtras is left out: ${MixinExtrasRules.unsupportedReason(loader, minecraft)}. " +
                                "Mixins using its annotations would not load here; wrap them in a Stonecutter condition."
                        )
                    }
                }
            }
        }?.takeIf { it != MixinExtrasRules.Delivery.UNSUPPORTED }
        val extrasVersion = extrasSettings?.let { config.versionOrNull("mixinextras") ?: it.version ?: MixinExtrasRules.DEFAULT_VERSION }

        var accessWidener: String? = null
        var accessTransformer: Any? = null
        var accessTransformerPath: String? = null
        val tweakerFiles = classTweakers.paths.map { path ->
            path to (resolve(highToLow, path) ?: throw GradleException("Class tweaker '$path' not found in the resource roots"))
        }
        if (loader == LoaderType.FABRIC) {
            if (tweakerFiles.size > 1) {
                throw GradleException("Fabric supports one class tweaker; merge ${tweakerFiles.joinToString { it.first }}")
            }
            tweakerFiles.singleOrNull()?.let { accessWidener = it.first }
        } else {
            val handWritten = resolve(highToLow, "META-INF/accesstransformer.cfg")
            if (handWritten != null) {
                accessTransformer = handWritten
                accessTransformerPath = handWritten.path
            } else if (tweakerFiles.isNotEmpty()) {
                val generatedRoot = layout.buildDirectory.dir("generated/accessTransformer")
                val generateAt = tasks.register<GenerateAccessTransformer>("generateAccessTransformer") {
                    tweakers.from(tweakerFiles.map { it.second })
                    tweakerPaths.set(tweakerFiles.map { it.first })
                    minecraftVersion.set(minecraftText)
                    srgNames.set(srgEraForge)
                    cacheDir.set(cacheDirectory)
                    outputDirectory.set(generatedRoot)
                }
                main.resources.srcDir(generateAt.flatMap { it.outputDirectory })
                tasks.named<ProcessResources>("processResources") { dependsOn(generateAt) }
                accessTransformer = generateAt.flatMap { it.outputDirectory.file("META-INF/accesstransformer.cfg") }
                accessTransformerPath = generatedRoot.get().file("META-INF/accesstransformer.cfg").asFile.path
            }
        }

        val usesMixins = loader == LoaderType.FORGE && mixinPaths.isNotEmpty()
        val refmapName = if (usesMixins && srgEraForge) ForgeMixinRules.refmapName(model.modId()) else null
        var srgToNamedFile: File? = null
        if (usesMixins && ForgeMixinRules.generatesOwnRefmap(minecraft)) {
            val srgTask = tasks.register<GenerateSrgMappings>("generateSrgMappings") {
                minecraftVersion.set(minecraftText)
                cacheDir.set(cacheDirectory)
                namedToSrg.set(layout.buildDirectory.file("generated/srg/named-to-srg.srg"))
                srgToNamed.set(layout.buildDirectory.file("generated/srg/srg-to-named.srg"))
            }
            srgToNamedFile = layout.buildDirectory.file("generated/srg/srg-to-named.srg").get().asFile
            val refmapFile = layout.buildDirectory.file("generated/mixinRefmap/$refmapName").get().asFile
            dependencies.add("annotationProcessor", "org.spongepowered:mixin:0.8.5:processor")
            val apArguments = objects.newInstance(MixinApArguments::class.java).apply {
                srgFile.set(srgTask.flatMap { it.namedToSrg })
                refmapPath.set(refmapFile.absolutePath)
            }
            val compile = tasks.named<JavaCompile>("compileJava")
            compile.configure { options.compilerArgumentProviders.add(apArguments) }
            tasks.named<ProcessResources>("processResources") { from(files(refmapFile) { builtBy(compile) }) }
            tasks.matching { it.name.startsWith("run") && !it.name.startsWith("runtime") }
                .configureEach { dependsOn(srgTask) }
        }

        if (loader == LoaderType.FORGE && mixinPaths.isNotEmpty()) {
            tasks.named<Jar>("jar") { manifest.attributes(mapOf("MixinConfigs" to mixinPaths.joinToString(","))) }
        }

        val compileJava = tasks.named<JavaCompile>("compileJava")
        val scan = tasks.register<ScanModClasses>("scanModClasses") {
            classDirectories.from(compileJava.flatMap { it.destinationDirectory })
            packageDefaults.set(mixins.packageDefaults.map { "${it.packageName}=${it.side.name}" })
            outputFile.set(layout.buildDirectory.file("generated/modScan/scan.json"))
        }
        val generate = tasks.register<GenerateModMetadata>("generateModMetadata") {
            metadataJson.set(model.toJson())
            cacheDir.set(cacheDirectory)
            loaderId.set(loader.id())
            minecraftVersion.set(minecraftText)
            this.minecraftRange.set(minecraftRange.toMaven())
            this.loaderVersion.set(loaderVersion)
            this.javaVersion.set(javaVersion)
            this.fabricApi.set(fabricApi != null)
            this.modMenu.set(modMenuVersion != null)
            accessWidener?.let { this.accessWidener.set(it) }
            refmapName?.let { this.refmapName.set(it) }
            mixinSpecs.set(mixinSources.map { (reg, file) -> "${reg.alias}|${reg.path}|${file.absolutePath}" })
            this.mixinSources.from(mixinSources.map { it.second })
            scanResult.set(scan.flatMap { it.outputFile })
            defaultDir.set(layout.buildDirectory.dir("generated/modMetadata/defaults"))
            overrideDir.set(layout.buildDirectory.dir("generated/modMetadata/overrides"))
        }
        tasks.named<ProcessResources>("processResources") {
            from(generate.flatMap { it.defaultDir }) { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
            from(generate.flatMap { it.overrideDir }) { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
        }

        extensions.extraProperties.set("modNode", mapOf(
            "nodeName" to nodeName,
            "loader" to loader.id(),
            "mc" to minecraftText,
            "loaderVersion" to loaderVersion,
            "fabricApi" to fabricApi,
            "modMenu" to modMenuVersion,
            "modMenuRuntime" to (modMenuVersion != null && modMenuSettings!!.runInDevelopment),
            "modMenuOptionalDependency" to (modMenuVersion != null && modMenuSettings!!.optionalDependency),
            "javaVersion" to javaVersion,
            "unobfuscated" to minecraft.isAtLeast(McVersion.FIRST_UNOBFUSCATED),
            "modId" to model.modId(),
            "runDir" to runDir.asFile,
            "accessTransformer" to accessTransformer,
            "accessTransformerPath" to accessTransformerPath,
            "classTweaker" to accessWidener?.let { resolve(highToLow, it) },
            "mixinConfigs" to mixinPaths,
            "forgeLegacy" to forgeLegacy,
            "neoGradle" to neoGradle,
            "refmapName" to refmapName,
            "srgToNamed" to srgToNamedFile,
            "mixinBootstrap" to (usesMixins && ForgeMixinRules.needsMixinBootstrap(minecraft)),
            "mixinExtras" to if (extrasDelivery != null) mapOf(
                "version" to extrasVersion,
                "artifact" to MixinExtrasRules.bundledArtifact(loader),
                "bundle" to (extrasDelivery == MixinExtrasRules.Delivery.BUNDLED),
            ) else null,
            "datagen" to if (runsDatagen) mapOf(
                "output" to generatedRoot,
                "client" to datagenSettings!!.client,
                "strictValidation" to datagenSettings.strictValidation,
            ) else null,
        ))
        val scriptName = when {
            forgeLegacy -> "forge-legacy"
            neoGradle -> "neoforge-neogradle"
            else -> loader.id()
        }
        val script = rootProject.file("buildSrc/build-logic/loaders/$scriptName.gradle")
        if (!script.isFile) throw GradleException("Missing loader script ${script.path}")
        project.apply(mapOf("from" to script))
        if (extrasDelivery == MixinExtrasRules.Delivery.BUNDLED) {
            val extrasScript = rootProject.file("buildSrc/build-logic/loaders/mixinextras.gradle")
            if (!extrasScript.isFile) throw GradleException("Missing loader script ${extrasScript.path}")
            project.apply(mapOf("from" to extrasScript))
        }
    }

    val toolPlugin = toolPluginId(loader, minecraft)
    try {
        pluginManager.apply(toolPlugin)
    } catch (e: UnknownPluginException) {
        if (!neoGradle) throw e
        throw GradleException(
            "$nodeName is built with NeoGradle ($toolPlugin), which is not on the build-logic classpath. " +
                "Add 'neogradle=<version>' to gradle/tools.properties.",
            e
        )
    }
}
