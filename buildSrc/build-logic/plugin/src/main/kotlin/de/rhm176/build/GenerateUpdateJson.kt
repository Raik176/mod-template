package de.rhm176.build

import de.rhm176.build.model.UpdateJson
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

@UntrackedTask(because = "Merges the release into a file of the repository, which is the input as well as the output")
abstract class GenerateUpdateJson : DefaultTask() {
    @get:Input
    abstract val modVersion: Property<String>

    @get:Input
    abstract val minecraftVersions: ListProperty<String>

    @get:Input
    abstract val changelog: Property<String>

    @get:Input
    @get:Optional
    abstract val homepage: Property<String>

    @get:Input
    abstract val recommended: Property<Boolean>

    @get:Internal
    abstract val updateFile: RegularFileProperty

    @TaskAction
    fun write() {
        val file = updateFile.get().asFile
        val existing = if (file.isFile) file.readText() else null
        val merged = try {
            UpdateJson.merge(
                existing,
                homepage.orNull,
                modVersion.get(),
                minecraftVersions.get(),
                changelog.get(),
                recommended.get(),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException("Could not update ${file.path}: ${e.message}", e)
        }
        if (merged == existing) {
            logger.lifecycle("${file.name} is already up to date")
            return
        }
        file.parentFile.mkdirs()
        file.writeText(merged)
        logger.lifecycle(
            "${file.name}: ${modVersion.get()} is the latest" +
                (if (recommended.get()) " and recommended" else "") +
                " for Minecraft ${minecraftVersions.get().joinToString()}"
        )
        logger.lifecycle("Written to $file. Commit and push it so Forge and NeoForge see the update.")
    }
}
