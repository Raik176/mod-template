plugins {
    `java-library`
}

group = "de.rhm176.build"
version = "1.0.0"

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}
