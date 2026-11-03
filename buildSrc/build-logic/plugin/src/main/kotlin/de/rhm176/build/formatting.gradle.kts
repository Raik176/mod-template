package de.rhm176.build

plugins {
    base
    id("com.diffplug.spotless")
}

if (project != rootProject) error("de.rhm176.build.formatting belongs in the root project (stonecutter.gradle.kts)")

spotless {
    encoding("UTF-8")

    java {
        target("src/**/*.java")
        targetExclude("src/**/generated/**")
        toggleOffOn()
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        replaceRegex("collapse blank lines", "\n{3,}", "\n\n")
        endWithNewline()
    }

    format("kotlin") {
        target("src/**/*.kt", "src/**/*.kts")
        targetExclude("src/**/generated/**")
        toggleOffOn()
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        replaceRegex("collapse blank lines", "\n{3,}", "\n\n")
        endWithNewline()
    }

    format("gradle") {
        target("*.gradle", "*.gradle.kts")
        toggleOffOn()
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        replaceRegex("collapse blank lines", "\n{3,}", "\n\n")
        endWithNewline()
    }

    format("json") {
        target("src/**/*.json", "src/**/*.json5", "src/**/*.mcmeta")
        targetExclude("src/**/generated/**")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("config") {
        target(
            ".github/**/*.yml", ".github/**/*.yaml",
            "gradle.properties", "gradle/*.properties", "gradle/minecraft/*.toml",
            "src/**/*.toml", "src/**/*.properties", "src/**/*.cfg", "src/**/*.ct", "src/**/*.accesswidener",
            ".gitignore", ".gitattributes", ".editorconfig",
        )
        targetExclude("src/**/generated/**")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("markdown") {
        target("*.md", ".github/**/*.md")
        endWithNewline()
    }
}
