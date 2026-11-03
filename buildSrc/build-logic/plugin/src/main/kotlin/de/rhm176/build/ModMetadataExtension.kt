package de.rhm176.build

import de.rhm176.build.model.LoaderType
import de.rhm176.build.model.ModMetadata
import de.rhm176.build.model.ModMetadata.DependencyType
import de.rhm176.build.model.ModMetadata.Ordering
import de.rhm176.build.model.ModMetadata.Side
import de.rhm176.build.model.VersionRange
import org.gradle.api.Action

open class ModMetadataExtension {
    var modId: String? = null
    var version: String? = null
    var name: String? = null
    var description: String? = null
    var license: String? = null

    var icon: String? = null

    var url: String? = null
    var sources: String? = null
    var issues: String? = null

    var updateUrl: String? = null
    var credits: String? = null

    var environment: ModMetadata.Environment = ModMetadata.Environment.BOTH

    var packFormat: Int? = null

    var quilt: Boolean = false

    private val authors = mutableListOf<String>()
    private val contributors = mutableListOf<String>()
    private val dependencies = mutableListOf<ModMetadata.Dependency>()
    private val custom = linkedMapOf<String, Any?>()
    private val loaderRanges = linkedMapOf<LoaderType, String>()

    internal var modMenuSettings: ModMenuExtension? = null
        private set

    fun author(name: String) {
        authors += name
    }

    fun contributor(name: String) {
        contributors += name
    }

    fun requires(modId: String, range: String = "*") = dependency(modId, DependencyType.REQUIRED, range)

    fun recommends(modId: String, range: String = "*") = dependency(modId, DependencyType.RECOMMENDED, range)

    fun suggest(modId: String, range: String = "*") = dependency(modId, DependencyType.SUGGESTED, range)

    fun markConflict(modId: String, range: String = "*") = dependency(modId, DependencyType.DISCOURAGED, range)

    fun markIncompatible(modId: String, range: String = "*") = dependency(modId, DependencyType.INCOMPATIBLE, range)

    fun dependency(
        modId: String,
        type: DependencyType,
        range: String = "*",
        side: Side = Side.BOTH,
        ordering: Ordering = Ordering.NONE,
        loaders: List<String> = emptyList(),
    ) {
        dependencies += ModMetadata.Dependency(
            modId,
            type,
            VersionRange.parse(range),
            side,
            ordering,
            loaders.map(LoaderType::fromId).toSet(),
        )
    }

    fun custom(vararg entries: Pair<String, Any?>) {
        entries.forEach { (key, value) -> custom[key] = value }
    }

    fun loaderVersionRange(loader: String, range: String) {
        loaderRanges[LoaderType.fromId(loader)] = range
    }

    fun modMenu(action: Action<ModMenuExtension>) {
        action.execute(modMenuSettings ?: ModMenuExtension().also { modMenuSettings = it })
    }

    internal fun toModel(
        projectVersion: String,
        modrinth: String? = null,
        curseforge: String? = null,
        github: String? = null,
        updateJsonUrl: String? = null,
    ): ModMetadata {
        val builder = ModMetadata.builder()
            .modId(modId ?: error("modMetadata.modId is not set"))
            .version(version ?: projectVersion)
            .name(name ?: error("modMetadata.name is not set"))
            .description(description)
            .license(license)
            .icon(icon)
            .homepage(url)
            .sources(sources)
            .issues(issues)
            .updateUrl(updateUrl ?: updateJsonUrl)
            .credits(credits)
            .environment(environment)
            .packFormat(packFormat)
            .quilt(quilt)
            .custom(custom)
        authors.forEach(builder::author)
        contributors.forEach(builder::contributor)
        dependencies.forEach(builder::dependency)
        loaderRanges.forEach { (loader, range) -> builder.loaderVersionRange(loader, range) }
        modMenuSettings?.takeIf { it.enabled }?.let { builder.modMenu(it.toModel().resolve(modrinth, curseforge, github)) }
        return builder.build()
    }
}
