package de.rhm176.build

import de.rhm176.build.model.Json
import de.rhm176.build.model.ProjectDescription
import de.rhm176.build.model.changelog.RepoLinks
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@UntrackedTask(because = "Talks to the Modrinth API")
abstract class SyncModrinthDescription : DefaultTask() {
    @get:Internal
    abstract val readme: RegularFileProperty

    @get:Internal
    abstract val readmeDirectory: Property<String>

    @get:Internal
    abstract val repositoryUrl: Property<String>

    @get:Internal
    abstract val branch: Property<String>

    @get:Internal
    abstract val projectId: Property<String>

    @get:Internal
    abstract val token: Property<String>

    @get:Internal
    abstract val userAgent: Property<String>

    @get:Internal
    abstract val live: Property<Boolean>

    @get:Internal
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun sync() {
        val file = readme.get().asFile
        if (!file.isFile) {
            throw GradleException("${file.path} does not exist. Add the README, or remove projectDescription { } from modPublishing.")
        }
        val prepared = try {
            ProjectDescription.prepare(
                file.readText(),
                readmeDirectory.get(),
                RepoLinks.parse(repositoryUrl.orNull).orElse(null),
                branch.get(),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException("Cannot use ${file.name} as the project description: ${e.message}", e)
        }
        if (prepared.unresolved().isNotEmpty()) {
            logger.warn(
                "${file.name}: these relative links stay as written and will not work on Modrinth (no repository URL to " +
                    "point them at, or outside it): ${prepared.unresolved().joinToString()}"
            )
        }
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(prepared.text())

        val id = projectId.get()
        if (!live.get()) {
            logger.lifecycle("Dry run: the Modrinth description of $id would become ${file.name} (${prepared.text().length} characters, see $out)")
            return
        }
        val accessToken = token.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("MODRINTH_TOKEN is not set, so the description of $id cannot be updated")

        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
        fun request(method: String, body: String? = null): HttpResponse<String> {
            val builder = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/$id"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", accessToken)
                .header("User-Agent", userAgent.get())
            if (body != null) builder.header("Content-Type", "application/json")
            builder.method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }

        val current = request("GET")
        when (current.statusCode()) {
            200 -> {
                val existing = runCatching { Json.parseObject(current.body())["body"] as? String }.getOrNull()
                if (existing != null && existing.trim() == prepared.text().trim()) {
                    logger.lifecycle("The Modrinth description of $id is already up to date")
                    return
                }
            }
            401, 404 -> throw GradleException(
                "Modrinth answered ${current.statusCode()} for project '$id': check the project id and that MODRINTH_TOKEN may access it"
            )
            else -> logger.warn("Could not read the current description of $id (HTTP ${current.statusCode()}); updating it anyway")
        }

        val response = request("PATCH", Json.write(linkedMapOf<String, Any?>("body" to prepared.text())))
        if (response.statusCode() !in 200..299) {
            val hint = if (response.statusCode() == 401) " The token needs the PROJECT_WRITE scope to edit the project." else ""
            throw GradleException(
                "Modrinth refused the description of $id (HTTP ${response.statusCode()}).$hint ${response.body().take(300)}".trim()
            )
        }
        logger.lifecycle("The Modrinth description of $id now is ${file.name} (${prepared.text().length} characters)")
    }
}
