import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.task.RemapSourcesJarTask
import org.gradle.kotlin.dsl.withType
import java.net.HttpURLConnection
import java.net.URI


plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.11-SNAPSHOT" apply false
    id("architectury-plugin") version "3.4-SNAPSHOT" apply false
    id("me.modmuss50.mod-publish-plugin") version "1.0.0"
    id("se.bjurr.gitchangelog.git-changelog-gradle-plugin") version "3.1.1"

    kotlin("jvm") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
    id("dev.kikugie.fletching-table") version "0.1.0-alpha.22" apply false
    id("dev.kikugie.fletching-table.fabric") version "0.1.0-alpha.22" apply false
    id("dev.kikugie.fletching-table.lexforge") version "0.1.0-alpha.22" apply false
    id("dev.kikugie.fletching-table.neoforge") version "0.1.0-alpha.22" apply false
}
stonecutter active "1.20.6" /* [SC] DO NOT EDIT */

val changelogProvider = layout.buildDirectory.file("CHANGELOG.md")
val changelogContentsProvider = providers.fileContents(changelogProvider).asText

@OptIn(ExperimentalSerializationApi::class)
fun generatePayload(): JsonObject {
    val modrinthJson = try {
        val url = URI("https://api.modrinth.com/v2/project/${mod.prop("modrinthSlug")}").toURL()
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"
            inputStream.bufferedReader().readText()
        }
    } catch (_: Exception) {
        null
    }

    val iconUrl = modrinthJson?.let {
        Json.parseToJsonElement(it).jsonObject["icon_url"]?.jsonPrimitive?.contentOrNull
    }

    return buildJsonObject {
        putJsonArray("embeds") {
            add(buildJsonObject {
                put("title", "${mod.name} ${mod.version} has been released!")
                put("description", changelogContentsProvider.get())
                put("color", 7506394)
                iconUrl?.let { putJsonObject("thumbnail") { put("url", it) } }
            })
        }
        putJsonArray("components") {
            add(buildJsonObject {
                put("type", 1)
                putJsonArray("components") {
                    listOf(
                        "Modrinth" to "https://modrinth.com/mod/${mod.prop("modrinthSlug")}",
                        "CurseForge" to "https://www.curseforge.com/minecraft/mc-mods/${mod.prop("curseforgeSlug")}",
                        "GitHub" to "https://github.com/${mod.prop("github")}"
                    ).forEach { (label, url) ->
                        add(buildJsonObject {
                            put("type", 2)
                            put("style", 5)
                            put("label", label)
                            put("url", url)
                        })
                    }
                }
            })
        }
    }
}

tasks.register("build") {
    group = "build"
    description = "Builds all subprojects"

    subprojects.forEach { sub ->
        sub.tasks.findByName("build")?.let { buildTask ->
            dependsOn(buildTask)
        }
    }
}

for (node in stonecutter.tree.nodes) {
    val minecraft = node.metadata.version

    node.project.repositories {
        fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
            forRepository { maven(url) { name = alias } }
            filter { groups.forEach(::includeGroup) }
        }
        strictMaven("https://www.cursemaven.com", "Curseforge", "curse.maven")
        strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    }

    node.project.plugins.apply("org.jetbrains.kotlin.jvm")
    node.project.plugins.apply("com.google.devtools.ksp")

    node.project.afterEvaluate {
        val projectStonecutter = node.project.extensions.getByType<dev.kikugie.stonecutter.build.StonecutterBuildExtension>()
        val loader = node.project.prop("loom.platform") ?: "common"
        val common: Project = requireNotNull(projectStonecutter.node.sibling("")) {
            "No common project for $project"
        }.project

        node.project.dependencies {
            "io.github.llamalad7:mixinextras-common:${mod.dep("mixin_extras")}".let {
                add("annotationProcessor", it)
                add("implementation", it)
            }

            add("minecraft", "com.mojang:minecraft:$minecraft")
            add(
                "mappings",
                node.project.extensions.getByType<net.fabricmc.loom.api.LoomGradleExtensionAPI>().officialMojangMappings()
            )

            if (loader != "common") {
                project(common.path, "namedElements").let {
                    add("implementation", it)
                    add("include", it)
                }
            }
        }

        if (loader != "common") {
            node.project.tasks.withType<RemapJarTask> {
                destinationDirectory = rootProject.layout.buildDirectory.dir("libs/${mod.version}/$loader")
            }
            node.project.tasks.withType<RemapSourcesJarTask> {
                destinationDirectory = rootProject.layout.buildDirectory.dir("libs/${mod.version}/$loader")
            }

            node.project.extensions.configure<me.modmuss50.mpp.ModPublishExtension> {
                file.set(node.project.tasks.named("remapJar", RemapJarTask::class.java).flatMap { it.archiveFile })
                changelog = rootProject.publishMods.changelog
                type = STABLE

                modrinth {
                    accessToken = providers.environmentVariable("MODRINTH_API_KEY")
                    projectId = mod.prop("modrinthId")
                    minecraftVersions = common.mod.prop("mc_targets").split(" ")
                    projectDescription = providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText
                }
                curseforge {
                    accessToken = providers.environmentVariable("CF_API_KEY")
                    projectId = mod.prop("curseforgeId")
                    minecraftVersions = common.mod.prop("mc_targets").split(" ")
                }
                github {
                    accessToken = providers.environmentVariable("GITHUB_TOKEN")

                    parent(rootProject.tasks.named("publishGithub"))
                }

                dryRun = providers.environmentVariable("PUBLISH_DRY_RUN").isPresent
            }
        }

        node.project.version = "${mod.version}+$minecraft"
        node.project.group = "${mod.group}.$loader"
        node.project.extensions.configure<BasePluginExtension> {
            archivesName.set("${mod.id}-$loader")
        }

        node.project.extensions.configure<JavaPluginExtension> {
            withSourcesJar()
            val java = when {
                projectStonecutter.eval(minecraft, ">=1.20.5") -> JavaVersion.VERSION_21
                projectStonecutter.eval(minecraft, ">=1.17") -> JavaVersion.VERSION_17
                else -> JavaVersion.VERSION_1_8
            }
            targetCompatibility = java
            sourceCompatibility = java
        }

        node.project.extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
            if (node.branch.id == "forge") {
                forge {
                    convertAccessWideners = true
                    forge.mixinConfigs("${mod.id}-common.mixins.json")
                }
            }

            decompilers {
                get("vineflower").apply { // Adds names to lambdas - useful for mixins
                    options.put("mark-corresponding-synthetics", "1")
                }
            }

            runConfigs.all {
                isIdeConfigGenerated = false
                runDir = project.layout.projectDirectory.asFile.toPath().toAbsolutePath()
                    .relativize(rootProject.layout.projectDirectory.file("run").asFile.toPath())
                    .toString()
                vmArgs("-Dmixin.debug.export=true")
            }
        }
    }

    if (!node.branch.id.isEmpty() && node.metadata.version == stonecutter.current?.version) {
        for (type in listOf("Client", "Server")) tasks.register("runActive$type${node.branch.id.upperCaseFirst()}") {
            group = "project"
            dependsOn("${node.hierarchy}:run$type")
        }
    }
}

tasks.named("publishMods") {
    enabled = false
}

tasks.register("publishMod") {
    group = "publishing"

    dependsOn(tasks.named("gitChangelog"))

    stonecutter.tree.nodes.forEach {
        it.project.tasks.findByName("publishMods")?.let { publishTask ->
            dependsOn(publishTask)
        }
    }

    dependsOn(tasks.named("publishGithub"))

    doLast {
        with(URI(providers.environmentVariable("DISCORD_WEBHOOK").get()).toURL().openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            outputStream.write(Json.encodeToString(JsonObject.serializer(), generatePayload()).toByteArray())
            if (responseCode != 204) {
                println("Failed to send webhook: $responseCode")
            }
        }
    }
}

tasks.named("publishMods") {
    enabled = false
}

publishMods {
    version = mod.version
    changelog = changelogContentsProvider
    type = STABLE
    displayName = mod.version

    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = mod.prop("github")
        commitish = "main"
        tagName = "v${mod.version}"

        allowEmptyFiles = true
    }

    dryRun = providers.environmentVariable("PUBLISH_DRY_RUN").isPresent
}

fun getLatestTag(): String {
    val url = URI("https://api.github.com/repos/${mod.prop("github")}/tags").toURL()
    val connection = url.openConnection() as HttpURLConnection

    val response = connection.inputStream.bufferedReader().readText()
    val json = Json.parseToJsonElement(response).jsonArray

    return json[0].jsonObject["name"]!!.jsonPrimitive.content
}

tasks.gitChangelog {
    file.set(changelogProvider.map { it.asFile })
    fromRevision.set(getLatestTag())
    toRevision.set("HEAD")

    templateContent.set("""
{{#ifContainsType commits type='feat'}}
### ✨ Features
{{#commits}}
  {{#ifCommitType . type='feat'}}
- {{#eachCommitScope .}} **{{.}}** {{/eachCommitScope}}{{commitDescription .}} ([{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}))
  {{/ifCommitType}}
{{/commits}}
{{/ifContainsType}}
{{#ifContainsType commits type='fix'}}
### 🐛 Bug Fixes
{{#commits}}
  {{#ifCommitType . type='fix'}}
- {{#eachCommitScope .}} **{{.}}** {{/eachCommitScope}}{{commitDescription .}} ([{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}))
  {{/ifCommitType}}
{{/commits}}
{{/ifContainsType}}
{{#ifContainsType commits type='chore'}}
### 🧹 Chores
{{#commits}}
  {{#ifCommitType . type='chore'}}
- {{#eachCommitScope .}} **{{.}}** {{/eachCommitScope}}{{commitDescription .}} ([{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}))
  {{/ifCommitType}}
{{/commits}}
{{/ifContainsType}}
{{#ifContainsType commits type='docs'}}
### 📚 Documentation
{{#commits}}
  {{#ifCommitType . type='docs'}}
- {{#eachCommitScope .}} **{{.}}** {{/eachCommitScope}}{{commitDescription .}} ([{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}))
  {{/ifCommitType}}
{{/commits}}
{{/ifContainsType}}
{{#ifContainsType commits type='refactor'}}
### ♻️ Refactors
{{#commits}}
  {{#ifCommitType . type='refactor'}}
- {{#eachCommitScope .}} **{{.}}** {{/eachCommitScope}}{{commitDescription .}} ([{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}))
  {{/ifCommitType}}
{{/commits}}
{{/ifContainsType}}
{{#ifContainsType commits type='perf'}}
### ⚡ Performance
{{#commits}}
  {{#ifCommitType . type='perf'}}
- {{#eachCommitScope .}} **{{.}}** {{/eachCommitScope}}{{commitDescription .}} ([{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}))
  {{/ifCommitType}}
{{/commits}}
{{/ifContainsType}}
{{#ifContainsType commits type='test'}}
### 🧪 Tests
{{#commits}}
  {{#ifCommitType . type='test'}}
- {{#eachCommitScope .}} **{{.}}** {{/eachCommitScope}}{{commitDescription .}} ([{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}))
  {{/ifCommitType}}
{{/commits}}
{{/ifContainsType}}
{{#ifContainsType commits type='ci'}}
### 🤖 CI
{{#commits}}
  {{#ifCommitType . type='ci'}}
- {{#eachCommitScope .}} **{{.}}** {{/eachCommitScope}}{{commitDescription .}} ([{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}))
  {{/ifCommitType}}
{{/commits}}
{{/ifContainsType}}
{{#ifContainsType commits type='revert'}}
### ⏪ Reverts
{{#commits}}
  {{#ifCommitType . type='revert'}}
- {{#eachCommitScope .}} **{{.}}** {{/eachCommitScope}}{{commitDescription .}} ([{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}))
  {{/ifCommitType}}
{{/commits}}
{{/ifContainsType}}
""")
    file.set(rootProject.layout.projectDirectory.file("CHANGELOG.md").asFile)
}