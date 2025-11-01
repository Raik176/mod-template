pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7.1"
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.1.4"
}

gitHooks {
    preCommit {
        tasks("\"Reset active project\"")
    }

    createHooks()
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    create(rootProject) {
        versions("1.20.6")
        vcsVersion = "1.20.6"
        branch("fabric")
        branch("forge")
        branch("neoforge") { versions("1.20.6") }
    }
}

rootProject.name = "Template Mod"