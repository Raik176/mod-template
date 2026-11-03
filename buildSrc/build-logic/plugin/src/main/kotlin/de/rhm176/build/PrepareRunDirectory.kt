package de.rhm176.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Creates the loader run directory when it is missing")
abstract class PrepareRunDirectory : DefaultTask() {
    @get:OutputDirectory
    abstract val runDirectory: DirectoryProperty

    @TaskAction
    fun createRunDirectory() {
        runDirectory.get().asFile.mkdirs()
    }
}
