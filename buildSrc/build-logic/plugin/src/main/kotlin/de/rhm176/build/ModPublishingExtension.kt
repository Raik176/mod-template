package de.rhm176.build

import de.rhm176.build.model.changelog.ChangelogOptions
import de.rhm176.build.model.changelog.Section
import org.gradle.api.Action

enum class PublishRelation { REQUIRES, OPTIONAL, INCOMPATIBLE, EMBEDS }

open class ChangelogSettings {
    var tagPrefix: String = "v"

    var includeUnconventional: Boolean = false

    private val sections = ChangelogOptions.defaultSections().toMutableList()

    fun section(type: String, title: String) {
        val section = Section(type, title)
        val index = sections.indexOfFirst { it.type() == section.type() }
        if (index >= 0) sections[index] = section else sections.add(section)
    }

    fun hide(vararg types: String) {
        val hidden = types.map { it.trim().lowercase() }.toSet()
        sections.removeAll { it.type() in hidden }
    }

    internal fun encodedSections(): List<String> = sections.map { it.encode() }
}

open class UpdateJsonSettings {
    var enabled: Boolean = true

    var url: String? = null

    var branch: String = "main"

    var path: String = "update.json"

    var homepage: String? = null

    var recommended: Boolean? = null

    internal val extraMinecraft = mutableListOf<String>()

    fun minecraft(vararg versions: String) {
        extraMinecraft += versions.map { it.trim() }
    }
}

open class ProjectDescriptionSettings {
    var enabled: Boolean = true

    var file: String = "README.md"

    var branch: String = "main"
}

open class ModPublishingExtension {
    internal var modrinthProject: String? = null
    internal var curseforgeProject: String? = null
    internal var githubEnabled: Boolean = false
    internal var githubRepository: String? = null
    internal val changelogSettings = ChangelogSettings()

    internal var updateJsonSettings: UpdateJsonSettings? = null
        private set

    internal var projectDescriptionSettings: ProjectDescriptionSettings? = null
        private set

    var releaseChannel: String? = null

    class Relation(val kind: PublishRelation, val slug: String, val loaders: Set<String>)

    private val relations = mutableListOf<Relation>()

    fun modrinth(projectId: String) {
        modrinthProject = projectId
    }

    fun curseforge(projectId: String) {
        curseforgeProject = projectId
    }

    fun github(repository: String? = null) {
        githubEnabled = true
        githubRepository = repository
    }

    fun requires(slug: String, vararg loaders: String) = relate(PublishRelation.REQUIRES, slug, loaders)

    fun optional(slug: String, vararg loaders: String) = relate(PublishRelation.OPTIONAL, slug, loaders)

    fun incompatible(slug: String, vararg loaders: String) = relate(PublishRelation.INCOMPATIBLE, slug, loaders)

    fun embeds(slug: String, vararg loaders: String) = relate(PublishRelation.EMBEDS, slug, loaders)

    fun updateJson(action: Action<UpdateJsonSettings>) {
        action.execute(updateJsonSettings ?: UpdateJsonSettings().also { updateJsonSettings = it })
    }

    fun projectDescription(action: Action<ProjectDescriptionSettings>) {
        action.execute(projectDescriptionSettings ?: ProjectDescriptionSettings().also { projectDescriptionSettings = it })
    }

    fun changelog(action: Action<ChangelogSettings>) {
        action.execute(changelogSettings)
    }

    private fun relate(kind: PublishRelation, slug: String, loaders: Array<out String>) {
        relations += Relation(kind, slug, loaders.map { it.trim().lowercase() }.toSet())
    }

    internal fun relationsFor(loader: String): List<Relation> =
        relations.filter { it.loaders.isEmpty() || loader in it.loaders }
}
