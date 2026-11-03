package de.rhm176.build

import de.rhm176.build.model.changelog.ChangelogException
import de.rhm176.build.model.changelog.ChangelogGenerator
import de.rhm176.build.model.changelog.ChangelogOptions
import de.rhm176.build.model.changelog.Section
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option

@UntrackedTask(because = "The result depends on the git history, which Gradle does not track")
abstract class GenerateChangelog : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val releaseVersion: Property<String>

    @get:Input
    abstract val tagPrefix: Property<String>

    @get:Input
    @get:Optional
    abstract val repositoryUrl: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "since", description = "Describe the changes since this tag or commit instead of since the previous release.")
    abstract val since: Property<String>

    @get:Input
    abstract val includeUnconventional: Property<Boolean>

    @get:Input
    abstract val sections: ListProperty<String>

    @get:Input
    abstract val failOnReusedVersion: Property<Boolean>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val options = ChangelogOptions()
        options.repository = repositoryDirectory.get().asFile.toPath()
        options.version = releaseVersion.get()
        options.tagPrefix = tagPrefix.get()
        options.repositoryUrl = repositoryUrl.orNull
        options.since = since.orNull
        options.includeUnconventional = includeUnconventional.get()
        options.sections = sections.get().map { Section.decode(it) }

        val result = try {
            ChangelogGenerator.generate(options)
        } catch (e: ChangelogException) {
            throw GradleException(e.message ?: "Could not generate the changelog", e)
        }

        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(result.markdown())

        result.warnings().forEach { logger.warn("Changelog: $it") }
        if (result.tagElsewhere() && failOnReusedVersion.get()) {
            throw GradleException(
                "Version ${releaseVersion.get()} is already released as tag ${result.tag()} on a different commit. " +
                    "Bump the version (or tag a new one) before publishing."
            )
        }

        val comparedTo = when {
            result.firstRelease() -> "the start of the history (first release)"
            else -> result.previousTag()
        }
        logger.lifecycle("")
        logger.lifecycle("Changelog for ${result.tag()}, ${result.entries()} change(s) since $comparedTo:")
        logger.lifecycle("")
        result.markdown().lines().forEach { logger.lifecycle("    $it") }
        logger.lifecycle("")
        result.suggestedVersion()?.let { logger.lifecycle("Version suggested by the commits: $it") }
        logger.lifecycle("Written to $file")
    }
}
