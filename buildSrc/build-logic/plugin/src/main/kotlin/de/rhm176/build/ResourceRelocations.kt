package de.rhm176.build

import de.rhm176.build.model.Json5
import de.rhm176.build.model.McVersion
import de.rhm176.build.model.ResourceRelocation
import de.rhm176.build.model.VanillaResourceRelocations
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.tasks.SourceSet
import java.io.Serializable

data class RelocationRule(val source: String, val target: String) : Serializable

internal sealed interface RelocationEntry {
    data class Custom(val source: String, val target: String) : RelocationEntry

    data class Vanilla(val from: String?) : RelocationEntry
}

internal class ResourceRelocator(rules: List<RelocationRule>) {
    private val relocations = rules.map { ResourceRelocation.of(it.source, it.target) }

    fun relocate(path: String): String = ResourceRelocation.applyAll(relocations, Json5.outputPath(path))
}

open class ResourceRelocationExtension {
    internal val entries = mutableListOf<RelocationEntry>()

    var jsonIndent: String = "  "

    fun configure(sourceSet: SourceSet, action: Action<ResourceRelocationSourceSetSpec>) {
        if (sourceSet.name != "main") {
            throw GradleException("Resource relocations can only be registered for the 'main' source set (got '${sourceSet.name}')")
        }
        action.execute(ResourceRelocationSourceSetSpec(entries))
    }

    fun configure(sourceSet: NamedDomainObjectProvider<SourceSet>, action: Action<ResourceRelocationSourceSetSpec>) {
        configure(sourceSet.get(), action)
    }

    internal fun rules(target: McVersion, vcs: McVersion?): List<RelocationRule> = entries.flatMap { entry ->
        when (entry) {
            is RelocationEntry.Custom -> listOf(RelocationRule(entry.source, entry.target))
            is RelocationEntry.Vanilla -> {
                val from = entry.from?.let(McVersion::parse)
                    ?: vcs
                    ?: throw GradleException("resourceRelocations: vanilla() needs gradle/minecraft/vcs.txt or an explicit source version")
                VanillaResourceRelocations.between(from, target).map { RelocationRule(it.source(), it.target()) }
            }
        }
    }
}

class ResourceRelocationSourceSetSpec internal constructor(private val entries: MutableList<RelocationEntry>) {
    fun relocate(source: String, target: String) {
        ResourceRelocation.of(source, target)
        entries += RelocationEntry.Custom(source, target)
    }

    fun vanilla() {
        entries += RelocationEntry.Vanilla(null)
    }

    fun vanilla(from: String) {
        McVersion.parse(from)
        entries += RelocationEntry.Vanilla(from)
    }
}
