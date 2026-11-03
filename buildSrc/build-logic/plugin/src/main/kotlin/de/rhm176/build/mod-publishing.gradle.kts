package de.rhm176.build

import de.rhm176.build.config.findMinecraftConfig
import de.rhm176.build.model.LoaderType
import de.rhm176.build.model.McVersion
import de.rhm176.build.model.ModMetadata
import de.rhm176.build.model.UpdateJson
import de.rhm176.build.model.changelog.ReleaseChannel
import de.rhm176.build.model.changelog.RepoLinks
import me.modmuss50.mpp.ReleaseType
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withGroovyBuilder

plugins {
    id("me.modmuss50.mod-publish-plugin")
}

val publishingSettings = extensions.getByType<ModPublishingExtension>()
val modMetadataSettings = extensions.getByType<ModMetadataExtension>()

fun secret(name: String): Provider<String> =
    providers.environmentVariable(name).orElse(providers.gradleProperty(name))

afterEvaluate {
    val node = extensions.extraProperties.get("modNode") as Map<*, *>
    val loader = node["loader"] as String
    val minecraft = node["mc"] as String
    val usesFabricApi = node["fabricApi"] != null
    val listsModMenu = node["modMenuOptionalDependency"] == true

    val modName = modMetadataSettings.name ?: error("modMetadata.name is not set")
    val modVersion = modMetadataSettings.version ?: project.version.toString()
    val changelogSettings = publishingSettings.changelogSettings
    val tagName = changelogSettings.tagPrefix + modVersion

    val live: Provider<Boolean> = providers.gradleProperty("publish")
        .orElse(providers.environmentVariable("PUBLISH"))
        .map { it.equals("true", ignoreCase = true) }
        .orElse(false)

    val registry = releaseRegistry()
    val ownsRelease = registry.changelogTaskPath == null
    if (ownsRelease) {
        val generate = tasks.register<GenerateChangelog>("generateChangelog") {
            group = "publishing"
            description = "Generates the changelog of the release from the conventional commits since the previous release."
            repositoryDirectory.set(rootProject.layout.projectDirectory)
            releaseVersion.set(modVersion)
            tagPrefix.set(changelogSettings.tagPrefix)
            repositoryUrl.set(modMetadataSettings.sources)
            includeUnconventional.set(changelogSettings.includeUnconventional)
            sections.set(changelogSettings.encodedSections())
            failOnReusedVersion.set(live)
            outputFile.set(layout.buildDirectory.file("changelog/changelog.md"))
        }
        registry.changelog.set(generate.flatMap { it.outputFile }.map { it.asFile.readText() })
        registry.changelogTaskPath = "$path:generateChangelog"
        registry.ownerProjectPath = path
    }
    val changelogTaskPath = registry.changelogTaskPath!!

    val finalJar: Provider<RegularFile> = tasks.named<Jar>("jar").flatMap { jar ->
        jar.destinationDirectory.file(jar.archiveBaseName.zip(jar.archiveVersion) { base, version -> "$base-$version.jar" })
    }

    val modrinthId = publishingSettings.modrinthProject
    val curseforgeId = publishingSettings.curseforgeProject
    val publishToGithub = publishingSettings.githubEnabled
    val githubTaskPath = "${registry.ownerProjectPath}:publishGithub"
    val updateJson = publishingSettings.updateJsonSettings?.takeIf { it.enabled }
    if (updateJson != null) {
        if (loader == "forge" || loader == "neoforge") {
            val range = findMinecraftConfig(rootProject.file("gradle/minecraft"), minecraft).minecraft
            registry.updateJsonMinecraft.addAll(range.version, range.compatibilityBound)
        }
        if (ownsRelease) {
            val githubSlug = if (publishToGithub) {
                publishingSettings.githubRepository ?: RepoLinks.parse(modMetadataSettings.sources).map { it.slug() }.orElse(null)
            } else {
                null
            }
            val extraMinecraft = updateJson.extraMinecraft.toList()
            val generateUpdateJson = tasks.register<GenerateUpdateJson>("generateUpdateJson") {
                group = "publishing"
                description = "Adds this release to ${updateJson.path}, which Forge and NeoForge read to tell players about updates."
                this.modVersion.set(modVersion)
                minecraftVersions.set(registry.updateJsonMinecraft.map { found ->
                    (found + extraMinecraft).distinct().sortedWith { a, b -> McVersion.compare(a, b) }
                })
                changelog.set(registry.changelog)
                homepage.set(UpdateJson.homepage(updateJson.homepage, modrinthId, curseforgeId, githubSlug, modMetadataSettings.url))
                recommended.set(updateJson.recommended ?: (ReleaseChannel.of(modVersion) == ReleaseChannel.STABLE))
                updateFile.set(rootProject.layout.projectDirectory.file(updateJson.path))
                dependsOn(changelogTaskPath)
            }
            tasks.named("publishMods") { dependsOn(generateUpdateJson) }
        }
    }

    val projectDescription = publishingSettings.projectDescriptionSettings?.takeIf { it.enabled }
    if (projectDescription != null && ownsRelease) {
        if (modrinthId == null) {
            logger.warn("modPublishing.projectDescription needs modrinth(\"...\"): there is no other page it can be uploaded to")
        } else {
            val readmeFile = rootProject.layout.projectDirectory.file(projectDescription.file)
            val readmeDir = projectDescription.file.replace('\\', '/').substringBeforeLast('/', "")
            val githubSlug = if (publishToGithub) {
                publishingSettings.githubRepository ?: RepoLinks.parse(modMetadataSettings.sources).map { it.slug() }.orElse(null)
            } else {
                null
            }
            val descriptionRepository = githubSlug?.let { "https://github.com/$it" } ?: modMetadataSettings.sources
            val agent = "${modMetadataSettings.modId ?: "mod"}/$modVersion" + (descriptionRepository?.let { " ($it)" } ?: "")
            val liveProvider = live
            val readmeProvider = readmeFile
            val secretToken = secret("MODRINTH_TOKEN")
            val projectName = modrinthId
            val syncDescription = tasks.register<SyncModrinthDescription>("syncModrinthDescription") {
                group = "publishing"
                description = "Uploads ${projectDescription.file} as the description of the Modrinth project (CurseForge has no API for that)."
                readme.set(readmeProvider)
                readmeDirectory.set(readmeDir)
                repositoryUrl.set(providers.provider { descriptionRepository })
                branch.set(projectDescription.branch)
                projectId.set(projectName)
                token.set(secretToken)
                userAgent.set(agent)
                this.live.set(liveProvider)
                outputFile.set(layout.buildDirectory.file("publishMods/description.md"))
            }
            tasks.named("publishMods") { finalizedBy(syncDescription) }
        }
    }

    val publishTask = Regex("publish(Mods|Modrinth|Curseforge|Github).*")
    tasks.configureEach {
        if (name.matches(publishTask)) dependsOn("assemble", changelogTaskPath)
        if (publishToGithub && name.matches(Regex("publish(Modrinth|Curseforge).*"))) mustRunAfter(githubTaskPath)
    }

    val configured = modrinthId != null || curseforgeId != null || publishToGithub
    if (!configured) {
        if (ownsRelease) {
            tasks.named("publishMods") {
                doFirst {
                    logger.lifecycle("Nothing to publish: add modrinth(...), curseforge(...) or github() to modPublishing { } in build.gradle.kts.")
                }
            }
        }
        return@afterEvaluate
    }

    val channel = publishingSettings.releaseChannel?.let { ReleaseChannel.parse(it) } ?: ReleaseChannel.of(modVersion)
    val releaseType = when (channel) {
        ReleaseChannel.ALPHA -> ReleaseType.ALPHA
        ReleaseChannel.BETA -> ReleaseType.BETA
        ReleaseChannel.STABLE -> ReleaseType.STABLE
    }

    val spec = findMinecraftConfig(rootProject.file("gradle/minecraft"), minecraft).minecraft
    val first = McVersion.parse(spec.version)
    val second = McVersion.parse(spec.compatibilityBound)
    val lowestMinecraft = minOf(first, second).toString()
    val highestMinecraft = maxOf(first, second).toString()

    val uploadVersion = "$modVersion+$minecraft-$loader"
    val uploadTitle = "$modName $modVersion (${LoaderType.fromId(loader).displayName()} $minecraft)"
    val relations = publishingSettings.relationsFor(loader)
    val environment = modMetadataSettings.environment

    publishMods {
        modLoaders.add(loader)
        if (loader == "fabric" && modMetadataSettings.quilt) modLoaders.add("quilt")
        type.set(releaseType)
        changelog.set(registry.changelog)
        version.set(uploadVersion)
        dryRun.set(live.map { !it })

        if (modrinthId != null) {
            modrinth {
                accessToken.set(secret("MODRINTH_TOKEN"))
                projectId.set(modrinthId)
                displayName.set(uploadTitle)
                file.set(finalJar)
                if (lowestMinecraft == highestMinecraft) {
                    minecraftVersions.add(lowestMinecraft)
                } else {
                    minecraftVersionRange {
                        start = lowestMinecraft
                        end = highestMinecraft
                    }
                }
                if (loader == "fabric" && usesFabricApi) requires("fabric-api")
                if (listsModMenu) optional("modmenu")
                relations.forEach { relation ->
                    when (relation.kind) {
                        PublishRelation.REQUIRES -> requires(relation.slug)
                        PublishRelation.OPTIONAL -> optional(relation.slug)
                        PublishRelation.INCOMPATIBLE -> incompatible(relation.slug)
                        PublishRelation.EMBEDS -> embeds(relation.slug)
                    }
                }
            }
        }

        if (curseforgeId != null) {
            curseforge {
                accessToken.set(secret("CURSEFORGE_TOKEN"))
                projectId.set(curseforgeId)
                displayName.set(uploadTitle)
                file.set(finalJar)
                client.set(environment != ModMetadata.Environment.SERVER)
                server.set(environment != ModMetadata.Environment.CLIENT)
                if (lowestMinecraft == highestMinecraft) {
                    minecraftVersions.add(lowestMinecraft)
                } else {
                    minecraftVersionRange {
                        start = lowestMinecraft
                        end = highestMinecraft
                    }
                }
                if (loader == "fabric" && usesFabricApi) requires("fabric-api")
                if (listsModMenu) optional("modmenu")
                relations.forEach { relation ->
                    when (relation.kind) {
                        PublishRelation.REQUIRES -> requires(relation.slug)
                        PublishRelation.OPTIONAL -> optional(relation.slug)
                        PublishRelation.INCOMPATIBLE -> incompatible(relation.slug)
                        PublishRelation.EMBEDS -> embeds(relation.slug)
                    }
                }
            }
        }

        if (publishToGithub && ownsRelease) {
            val repository = publishingSettings.githubRepository
                ?: RepoLinks.parse(modMetadataSettings.sources).map { it.slug() }.orElse(null)
                ?: error("modPublishing.github() needs a repository: set modMetadata.sources to the GitHub URL or call github(\"owner/repo\")")
            val head = providers.environmentVariable("GITHUB_SHA").orElse(
                providers.exec {
                    commandLine("git", "rev-parse", "HEAD")
                    workingDir = rootProject.projectDir
                    isIgnoreExitValue = true
                }.standardOutput.asText.map { it.trim() }
            )
            github {
                accessToken.set(secret("GITHUB_TOKEN"))
                this.repository.set(repository)
                commitish.set(head)
                this.tagName.set(tagName)
                displayName.set("$modName $modVersion")
                file.set(finalJar)
            }
        } else if (publishToGithub) {
            val release = rootProject.project(registry.ownerProjectPath!!).tasks.named("publishGithub")
            github {
                accessToken.set(secret("GITHUB_TOKEN"))
                file.set(finalJar)
                withGroovyBuilder { "parent"(release) }
            }
        }
    }
}
