package de.rhm176.build

import de.rhm176.build.model.LoaderType

plugins {
    idea
}

pluginManager.apply("dev.kikugie.stonecutter")

if (project == rootProject) {
    val runNodes: List<Triple<String, LoaderType, String>> = rootProject.childProjects.values.flatMap { branch ->
        val loader = try {
            LoaderType.fromBranchName(branch.name)
        } catch (e: IllegalArgumentException) {
            return@flatMap emptyList()
        }
        if (!loader.buildable()) emptyList()
        else branch.childProjects.keys.map { minecraft -> Triple(branch.path, loader, minecraft) }
    }

    val activeEntry: String? = rootProject.file("gradle/minecraft/vcs.txt")
        .takeIf { it.isFile }?.readText()?.trim()?.let(LoaderType::minecraftOfVcsEntry)

    runNodes.map { it.second }.distinct().forEach { loader ->
        val ofLoader = runNodes.filter { it.second == loader }.sortedBy { it.third }
        val preferred = ofLoader.firstOrNull { it.third == activeEntry } ?: ofLoader.last()

        listOf("Client", "Server").forEach { side ->
            val base = "run$side${loader.displayName()}"
            tasks.register(base) {
                group = "mod runs"
                description = "Runs the ${side.lowercase()} of ${loader.displayName()} on Minecraft ${preferred.third}."
                dependsOn("${preferred.first}:${preferred.third}:run$side")
            }
            if (ofLoader.size > 1) {
                ofLoader.forEach { (branchPath, _, minecraft) ->
                    tasks.register("$base-$minecraft") {
                        group = "mod runs"
                        description = "Runs the ${side.lowercase()} of ${loader.displayName()} on Minecraft $minecraft."
                        dependsOn("$branchPath:$minecraft:run$side")
                    }
                }
            }
        }
    }
}
