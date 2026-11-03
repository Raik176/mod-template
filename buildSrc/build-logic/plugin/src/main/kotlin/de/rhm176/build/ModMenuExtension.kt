package de.rhm176.build

import de.rhm176.build.model.ModMenuMetadata
import org.gradle.api.Action

open class ModMenuExtension {
    var enabled: Boolean = true

    var updateChecker: Boolean? = null

    var autoLinks: Boolean = true

    var optionalDependency: Boolean = true

    var runInDevelopment: Boolean = true

    private val links = linkedMapOf<String, String>()
    private val badges = linkedSetOf<ModMenuMetadata.Badge>()
    private var parent: ModMenuMetadata.Parent? = null

    fun link(type: String, url: String) {
        links[ModMenuMetadata.linkKey(type)] = url
    }

    fun discord(url: String) = link("discord", url)

    fun donate(url: String) = link("donate", url)

    fun wiki(url: String) = link("wiki", url)

    fun youtube(url: String) = link("youtube", url)

    fun twitter(url: String) = link("twitter", url)

    fun mastodon(url: String) = link("mastodon", url)

    fun reddit(url: String) = link("reddit", url)

    fun twitch(url: String) = link("twitch", url)

    fun patreon(url: String) = link("patreon", url)

    fun kofi(url: String) = link("kofi", url)

    fun paypal(url: String) = link("paypal", url)

    fun githubSponsors(url: String) = link("github_sponsors", url)

    fun library() {
        badges += ModMenuMetadata.Badge.LIBRARY
    }

    fun deprecated() {
        badges += ModMenuMetadata.Badge.DEPRECATED
    }

    fun parent(id: String) {
        parent = ModMenuMetadata.Parent(id)
    }

    fun parent(id: String, action: Action<ModMenuParent>) {
        val spec = ModMenuParent().also { action.execute(it) }
        parent = ModMenuMetadata.Parent(id, spec.name, spec.description, spec.icon, spec.badges)
    }

    internal fun toModel(): ModMenuMetadata {
        val builder = ModMenuMetadata.builder()
            .updateChecker(updateChecker)
            .autoLinks(autoLinks)
            .parent(parent)
        links.forEach { (key, url) -> builder.link(key, url) }
        badges.forEach { builder.badge(it) }
        return builder.build()
    }
}

open class ModMenuParent {
    var name: String? = null
    var description: String? = null

    var icon: String? = null

    internal val badges = linkedSetOf<ModMenuMetadata.Badge>()

    fun library() {
        badges += ModMenuMetadata.Badge.LIBRARY
    }

    fun deprecated() {
        badges += ModMenuMetadata.Badge.DEPRECATED
    }
}
