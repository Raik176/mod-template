package de.rhm176.build

import de.rhm176.build.config.loadMinecraftConfigs
import de.rhm176.build.model.LoaderMatrix
import de.rhm176.build.model.LoaderType
import de.rhm176.build.model.McVersion
import groovy.lang.MissingMethodException
import groovy.lang.MissingPropertyException
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.api.initialization.ProjectDescriptor
import java.io.File

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases")
    }
}

pluginManager.apply("dev.kikugie.stonecutter")

val minecraftConfigs = loadMinecraftConfigs(file("gradle/minecraft"))

val branchesDirectory = "versions"

val centralScript = "build.gradle.kts"

val problems = mutableListOf<String>()

val branches = linkedMapOf<String, MutableList<String>>()

minecraftConfigs.forEach { config ->
    val minecraft = config.minecraft.version
    val ids = config.enabledLoaderIds()
    LoaderMatrix.validate(minecraft, ids.toSet())
        .forEach { problems += "${config.sourceFile.name}: $it" }
    ids.forEach { id ->
        val loader = LoaderType.fromId(id)
        if (loader.buildable() && loader.supports(McVersion.parse(minecraft))) {
            val versions = branches.getOrPut(loader.branchName()) { mutableListOf() }
            if (minecraft !in versions) versions += minecraft
        }
    }
}

require(problems.isEmpty()) {
    "Invalid Minecraft version catalogs:\n" + problems.joinToString("\n") { " - $it" }
}

val vcsFile = file("gradle/minecraft/vcs.txt")
require(vcsFile.isFile) { "Missing ${vcsFile.path} (the Minecraft version whose sources the working tree shows, e.g. 1.21.1)." }
val vcsMinecraft = LoaderType.minecraftOfVcsEntry(vcsFile.readText().trim())
require(branches.values.any { vcsMinecraft in it }) {
    "gradle/minecraft/vcs.txt names '$vcsMinecraft', which no loader builds. " +
        "Available: ${branches.values.flatten().distinct().joinToString()}"
}

extensions.getByName("stonecutter").withGroovyBuilder {
    setProperty("kotlinController", true)
    setProperty("centralScript", centralScript)

    "create"(rootProject) {
        branches.forEach { (loaderId, versions) ->
            "branch"(loaderId) {
                versions.forEach { minecraft ->
                    try {
                        "version"(minecraft, minecraft)
                    } catch (e: MissingMethodException) {
                        "vers"(minecraft, minecraft)
                    }
                }
                val active = if (vcsMinecraft in versions) vcsMinecraft
                else versions.maxBy { McVersion.parse(it) }
                try {
                    setProperty("vcsVersion", active)
                } catch (e: MissingPropertyException) {
                }
            }
        }
    }
}

val defaultDirectories = mutableListOf<File>()

fun pruneEmpty(directory: File) {
    if (directory.isDirectory && directory.walkBottomUp().none { it.isFile }) directory.deleteRecursively()
}

fun ProjectDescriptor.useBuildScript(script: File) {
    buildFileName = projectDir.toPath().toAbsolutePath().normalize()
        .relativize(script.toPath().toAbsolutePath().normalize())
        .toString()
}

gradle.settingsEvaluated {
    branches.keys.forEach { loaderId ->
        val branch = rootProject.children.firstOrNull { it.name == loaderId } ?: return@forEach
        val projects = listOf(branch) + branch.children
        val buildScripts = projects.associateWith { it.buildFile }
        defaultDirectories += branch.projectDir
        branch.children.forEach { defaultDirectories += it.projectDir }
        branch.projectDir = file("$branchesDirectory/$loaderId").also { it.mkdirs() }
        branch.children.forEach { it.projectDir = branch.projectDir.resolve(it.name).also { dir -> dir.mkdirs() } }
        projects.forEach { project ->
            val script = buildScripts.getValue(project).takeIf { it.isFile }
                ?: rootProject.projectDir.resolve(centralScript).takeIf { project != branch }
            if (script != null) project.useBuildScript(script)
        }
    }
    defaultDirectories.forEach(::pruneEmpty)
}

gradle.projectsLoaded { defaultDirectories.forEach(::pruneEmpty) }
