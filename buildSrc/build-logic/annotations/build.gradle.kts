plugins {
    `java-library`
}

group = "de.rhm176.build"
version = "1.0.0"

base {
    archivesName = "rhm176-mod-build-annotations"
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)

    options.compilerArgs.add("-Xlint:-options")
}
