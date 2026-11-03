package de.rhm176.build.config

import java.io.File

data class MinecraftVersionSpec(
    val version: String,
    val compatibilityBound: String = version,
) {
    init {
        require(version.isNotBlank()) { "Minecraft version must not be blank" }
        require(compatibilityBound.isNotBlank()) { "Minecraft compatibility bound must not be blank" }
    }
}

data class MinecraftConfig(
    val sourceFile: File,
    val minecraft: MinecraftVersionSpec,
    val versions: Map<String, String>,
) {
    fun versionOrNull(name: String): String? = versions[name]

    fun requiredVersion(name: String): String =
        versionOrNull(name)
            ?: error("Missing [versions].$name in ${sourceFile.path}")

    fun enabledLoaderIds(): List<String> =
        LOADER_IDS.filter { versionOrNull("$it-loader") != null }

    fun loaderVersion(loaderId: String): String? = versionOrNull("$loaderId-loader")

    companion object {
        val LOADER_IDS = listOf("forge", "fabric", "neoforge", "quilt")
    }
}

private data class FileStamp(val name: String, val lastModified: Long, val length: Long)

private class ParsedConfigs(val stamps: List<FileStamp>, val configs: List<MinecraftConfig>)

private val parsedConfigs = HashMap<String, ParsedConfigs>()

fun loadMinecraftConfigs(directory: File): List<MinecraftConfig> {
    require(directory.isDirectory) {
        "Missing Minecraft configuration directory: ${directory.path}"
    }

    val files = directory
        .listFiles()
        ?.filter { it.isFile && it.name.endsWith(".versions.toml") }
        ?.sortedBy { it.name }
        ?: emptyList()

    require(files.isNotEmpty()) {
        "No *.versions.toml Minecraft configurations found in ${directory.path}"
    }

    val stamps = files.map { FileStamp(it.name, it.lastModified(), it.length()) }
    val key = directory.absoluteFile.normalize().path
    return synchronized(parsedConfigs) {
        val known = parsedConfigs[key]
        if (known != null && known.stamps == stamps) {
            known.configs
        } else {
            val configs = parseAndValidate(files)
            parsedConfigs[key] = ParsedConfigs(stamps, configs)
            configs
        }
    }
}

private fun parseAndValidate(files: List<File>): List<MinecraftConfig> {
    val configs = files.map(::parseMinecraftConfig)
    val duplicateVersions = configs
        .groupBy { it.minecraft.version }
        .filterValues { it.size > 1 }

    require(duplicateVersions.isEmpty()) {
        duplicateVersions.entries.joinToString(
            prefix = "Multiple Minecraft TOML configurations declare the same actual version: ",
            separator = "; ",
        ) { (version, duplicates) ->
            "$version -> ${duplicates.joinToString { it.sourceFile.name }}"
        }
    }

    return configs
}

fun findMinecraftConfig(directory: File, minecraftVersion: String): MinecraftConfig =
    loadMinecraftConfigs(directory).singleOrNull { it.minecraft.version == minecraftVersion }
        ?: error(
            "No *.versions.toml configuration in ${directory.path} declares " +
                "[versions].minecraft = '$minecraftVersion' (or starts its array with it)"
        )

fun parseMinecraftConfig(file: File): MinecraftConfig {
    require(file.isFile) { "Missing Minecraft configuration: ${file.path}" }

    var section: String? = null
    var minecraftVersion: MinecraftVersionSpec? = null
    val versions = linkedMapOf<String, String>()

    file.useLines { lines ->
        lines.forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = stripTomlComment(rawLine).trim()
            if (line.isEmpty()) return@forEachIndexed

            if (line.startsWith('[')) {
                require(line.endsWith(']') && !line.startsWith("[[")) {
                    "${file.path}:$lineNumber: unsupported TOML table syntax: $line"
                }
                section = line.substring(1, line.length - 1).trim()
                require(section == "versions") {
                    "${file.path}:$lineNumber: unsupported table [$section]. " +
                        "Minecraft config files use a single [versions] table."
                }
                return@forEachIndexed
            }

            require(section == "versions") {
                "${file.path}:$lineNumber: keys must be inside [versions]"
            }

            val equalsIndex = findUnquoted(line, '=')
            require(equalsIndex > 0) {
                "${file.path}:$lineNumber: expected key = value"
            }

            val key = line.substring(0, equalsIndex).trim()
            val rawValue = line.substring(equalsIndex + 1).trim()
            require(key.matches(Regex("[A-Za-z0-9_-]+"))) {
                "${file.path}:$lineNumber: unsupported key '$key'"
            }

            if (key == "minecraft") {
                require(minecraftVersion == null) {
                    "${file.path}:$lineNumber: duplicate [versions].minecraft"
                }
                minecraftVersion = parseMinecraftVersion(rawValue, file, lineNumber)
            } else {
                require(key !in versions) {
                    "${file.path}:$lineNumber: duplicate [versions].$key"
                }
                versions[key] = parseTomlString(rawValue, file, lineNumber)
            }
        }
    }

    return MinecraftConfig(
        sourceFile = file,
        minecraft = minecraftVersion
            ?: error("Missing [versions].minecraft in ${file.path}"),
        versions = versions.toMap(),
    )
}

private fun parseMinecraftVersion(value: String, file: File, lineNumber: Int): MinecraftVersionSpec {
    if (!value.startsWith('[')) {
        val version = parseTomlString(value, file, lineNumber)
        return MinecraftVersionSpec(version)
    }

    require(value.endsWith(']')) {
        "${file.path}:$lineNumber: malformed [versions].minecraft array"
    }

    val elements = splitTomlArray(value.substring(1, value.length - 1), file, lineNumber)
    require(elements.size == 2) {
        "${file.path}:$lineNumber: [versions].minecraft array must contain exactly two strings: " +
            "[actualVersion, compatibilityBound]"
    }

    return MinecraftVersionSpec(
        version = parseTomlString(elements[0], file, lineNumber),
        compatibilityBound = parseTomlString(elements[1], file, lineNumber),
    )
}

private fun splitTomlArray(body: String, file: File, lineNumber: Int): List<String> {
    val elements = mutableListOf<String>()
    var start = 0
    var inString = false
    var escaped = false

    body.forEachIndexed { index, char ->
        when {
            escaped -> escaped = false
            inString && char == '\\' -> escaped = true
            char == '"' -> inString = !inString
            char == ',' && !inString -> {
                elements += body.substring(start, index).trim()
                start = index + 1
            }
        }
    }

    require(!inString && !escaped) {
        "${file.path}:$lineNumber: unterminated string in array"
    }

    elements += body.substring(start).trim()
    require(elements.none { it.isEmpty() }) {
        "${file.path}:$lineNumber: empty value in [versions].minecraft array"
    }
    return elements
}

private fun parseTomlString(value: String, file: File, lineNumber: Int): String {
    require(value.length >= 2 && value.first() == '"' && value.last() == '"') {
        "${file.path}:$lineNumber: expected a TOML basic string, got: $value"
    }

    val body = value.substring(1, value.length - 1)
    val result = StringBuilder(body.length)
    var escaped = false

    body.forEach { char ->
        if (escaped) {
            result.append(
                when (char) {
                    '"' -> '"'
                    '\\' -> '\\'
                    'b' -> '\b'
                    't' -> '\t'
                    'n' -> '\n'
                    'f' -> '\u000C'
                    'r' -> '\r'
                    else -> error(
                        "${file.path}:$lineNumber: unsupported escape sequence \\$char"
                    )
                }
            )
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else {
            result.append(char)
        }
    }

    require(!escaped) {
        "${file.path}:$lineNumber: unfinished escape sequence"
    }

    return result.toString()
}

private fun stripTomlComment(line: String): String {
    var inString = false
    var escaped = false

    line.forEachIndexed { index, char ->
        when {
            escaped -> escaped = false
            inString && char == '\\' -> escaped = true
            char == '"' -> inString = !inString
            char == '#' && !inString -> return line.substring(0, index)
        }
    }

    return line
}

private fun findUnquoted(value: String, target: Char): Int {
    var inString = false
    var escaped = false

    value.forEachIndexed { index, char ->
        when {
            escaped -> escaped = false
            inString && char == '\\' -> escaped = true
            char == '"' -> inString = !inString
            char == target && !inString -> return index
        }
    }

    return -1
}
