import java.util.Properties

plugins {
    `kotlin-dsl`
}

group = "de.rhm176.build"
version = "1.0.0"

val stonecutterVersion = Properties().apply {
    rootProject.projectDir.parentFile.parentFile.resolve("gradle/tools.properties").inputStream().use { load(it) }
}.getProperty("stonecutter") ?: error("gradle/tools.properties has no 'stonecutter'")

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(project(":config"))
    implementation(project(":model"))

    implementation("dev.kikugie.stonecutter:dev.kikugie.stonecutter.gradle.plugin:$stonecutterVersion")
}
