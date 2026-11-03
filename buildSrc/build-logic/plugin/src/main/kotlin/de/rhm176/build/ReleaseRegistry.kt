package de.rhm176.build

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

class ReleaseRegistry(project: Project) {
    val changelog: Property<String> = project.objects.property(String::class.java)

    var changelogTaskPath: String? = null

    var ownerProjectPath: String? = null

    val updateJsonMinecraft: ListProperty<String> = project.objects.listProperty(String::class.java)
}

private const val REGISTRY_KEY = "de.rhm176.build.releaseRegistry"

fun Project.releaseRegistry(): ReleaseRegistry {
    val extra = rootProject.extensions.extraProperties
    if (!extra.has(REGISTRY_KEY)) extra.set(REGISTRY_KEY, ReleaseRegistry(rootProject))
    return extra.get(REGISTRY_KEY) as ReleaseRegistry
}
