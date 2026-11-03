import java.util.Properties

plugins {
    `kotlin-dsl`
}

group = "de.rhm176.build"
version = "1.0.0"

val tools = Properties().apply {
    rootProject.projectDir.parentFile.parentFile.resolve("gradle/tools.properties").inputStream().use { load(it) }
}
fun tool(name: String): String = tools.getProperty(name) ?: error("gradle/tools.properties has no '$name'")


repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.kikugie.dev/releases")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.neoforged.net/releases")
}

dependencies {
    implementation(project(":config"))
    implementation(project(":annotations"))
    implementation(project(":model"))

    implementation("dev.kikugie.stonecutter:dev.kikugie.stonecutter.gradle.plugin:${tool("stonecutter")}")

    implementation("net.fabricmc:fabric-loom:${tool("loom")}")
    implementation("net.minecraftforge.gradle:net.minecraftforge.gradle.gradle.plugin:${tool("forgegradle")}")
    implementation("net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:${tool("moddevgradle")}")
    implementation("net.neoforged.moddev.legacyforge:net.neoforged.moddev.legacyforge.gradle.plugin:${tool("moddevgradle")}")

    implementation("me.modmuss50.mod-publish-plugin:me.modmuss50.mod-publish-plugin.gradle.plugin:${tool("modpublish")}")

    implementation("com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:${tool("spotless")}")

    tools.getProperty("neogradle")?.let {
        implementation("net.neoforged.gradle.userdev:net.neoforged.gradle.userdev.gradle.plugin:$it")
    }

    implementation("org.ow2.asm:asm:9.8")
}
