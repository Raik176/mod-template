import kotlinx.serialization.ExperimentalSerializationApi
import me.modmuss50.mpp.platforms.curseforge.CurseforgeOptions
import me.modmuss50.mpp.platforms.modrinth.ModrinthOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URI

plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.11-SNAPSHOT" apply false
    id("architectury-plugin") version "3.4-SNAPSHOT" apply false
    id("me.modmuss50.mod-publish-plugin") version "1.0.0"
}
stonecutter active "1.20.6" /* [SC] DO NOT EDIT */
stonecutter parameters {
    val loader = node.metadata.project.substringAfter('-')
    constants.match(loader, "fabric", "forge", "neoforge")
}

val changelogProvider = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText

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
                put("description", changelogProvider.get())
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
    if (node.branch.id.isEmpty() || node.metadata.version != stonecutter.current?.version) continue
    for (type in listOf("Client", "Server")) tasks.register("runActive$type${node.branch.id.upperCaseFirst()}") {
        group = "project"
        dependsOn("${node.hierarchy}run$type")
    }
}

tasks.named("publishMods") {
    enabled = false
}

tasks.register("publishMod") {
    group = "publishing"

    stonecutter.tree.nodes.forEach {
        it.project.tasks.findByName("publishMods")?.let { publishTask ->
            dependsOn(publishTask)
        }
    }

    dependsOn(
        tasks.named("publishGithub")
    )

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
    changelog = changelogProvider
    type = STABLE
    displayName = mod.version

    extra["configureCurseforge"] = { opts: CurseforgeOptions ->
        opts.accessToken = providers.environmentVariable("CF_API_KEY")
        opts.projectId = mod.prop("curseforgeId")
        opts.minecraftVersions.addAll(mod.prop("mc_targets").split(" "))
    }

    extra["configureModrinth"] = { opts: ModrinthOptions ->
        opts.accessToken = providers.environmentVariable("MODRINTH_API_KEY")
        opts.projectId = mod.prop("modrinthId")
        opts.minecraftVersions.addAll(mod.prop("mc_targets").split(" "))
        opts.projectDescription = providers.fileContents(layout.projectDirectory.file("../../README.md")).asText
    }

    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = mod.prop("github")
        commitish = "main"
        tagName = "v${mod.version}"

        allowEmptyFiles = true
    }

    dryRun = providers.environmentVariable("PUBLISH_DRY_RUN").isPresent
}

afterEvaluate {
    extra["changelog"] = changelogProvider.get()
}