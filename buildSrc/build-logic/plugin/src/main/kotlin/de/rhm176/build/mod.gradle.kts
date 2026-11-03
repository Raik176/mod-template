package de.rhm176.build

import de.rhm176.build.config.findMinecraftConfig
import de.rhm176.build.model.LoaderType
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType

plugins {
    java
    idea
}

pluginManager.apply("dev.kikugie.stonecutter")

val stonecutter = extensions.getByType<StonecutterBuildExtension>()

val modMetadata = extensions.create<ModMetadataExtension>("modMetadata")
val mixins = extensions.create<MixinRegistryExtension>("mixins")
val classTweakers = extensions.create<ClassTweakerRegistryExtension>("classTweakers")
val resourceRelocations = extensions.create<ResourceRelocationExtension>("resourceRelocations")
val modPublishing = extensions.create<ModPublishingExtension>("modPublishing")
val datagen = extensions.create<DatagenExtension>("datagen")
val mixinExtras = extensions.create<MixinExtrasExtension>("mixinExtras")

val branch = parent?.takeIf { it != rootProject }
    ?: error("$path must be a version project inside a loader branch (:<loader>:<minecraft>)")

configureModNode(
    loader = LoaderType.fromBranchName(branch.name),
    minecraftText = stonecutter.current.version,
    config = findMinecraftConfig(rootProject.file("gradle/minecraft"), stonecutter.current.version),
    metadata = modMetadata,
    mixins = mixins,
    classTweakers = classTweakers,
    resourceRelocations = resourceRelocations,
    datagen = datagen,
    mixinExtras = mixinExtras,
)

pluginManager.apply("de.rhm176.build.mod-publishing")
