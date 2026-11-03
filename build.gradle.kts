import de.rhm176.build.mixin.MixinEnvironment.Type.CLIENT
import de.rhm176.build.mixin.MixinEnvironment.Type.SERVER

plugins {
    id("de.rhm176.build.mod")
}

modMetadata {
    modId = "example_mod"
    version = project.version.toString()
    name = "Example Mod"
    description = "Example Mod"
    license = "MIT"
    icon = "assets/example_mod/icon.png"

    url = "https://rhm176.de"
    sources = "https://github.com/rhm176/ExampleMod"
    issues = "$sources/issues"

    author("rhm176")

    modMenu {
    }

    custom(
        "example_mod" to mapOf(
            "example" to "example",
        )
    )
}

modPublishing {
    updateJson {
    }

    projectDescription {
    }

    changelog {
    }
}

mixins.configure(sourceSets.main) {
    mixin("example_mod.mixins.json")

    defaultType("de.rhm176.example_mod.mixin.client", CLIENT)
    defaultType("de.rhm176.example_mod.mixin.server", SERVER)
}

mixinExtras {
    enabled = true
}

classTweakers.configure(sourceSets.main) {
    classTweaker("example_mod.ct")
}

resourceRelocations.configure(sourceSets.main) {
    vanilla()
}

datagen {
    enabled = true
}
