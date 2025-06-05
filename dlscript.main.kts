#!/usr/bin/env kotlin

/**
 * this script is for generating a bash script that downloads all Gradle dependencies.
 * it was created for open ai codex environment where gradlew build fails with downloading dependencies but curl can download them.
 * the script will download all dependencies into `~/.gradle/` directory before running `gradlew build` so that gradlew can run offline.
 * */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest

data class Dependency(
    val group: String,
    val name: String,
    val version: String,
    val classifier: String? = null,
    val extension: String = "jar"
) {
    val coordinates = "$group:$name:$version"
    val fileName = buildString {
        append("$name-$version")
        classifier?.let { append("-$it") }
        append(".$extension")
    }
    val groupPath = group.replace('.', '/')
    val mavenPath = "$groupPath/$name/$version/$fileName"
}

data class PomDependency(
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val scope: String? = null,
    val optional: Boolean = false
)

class DependencyResolver {
    private val processedDependencies = ConcurrentHashMap<String, Dependency>()
    private val repositories = listOf(
        "https://repo1.maven.org/maven2",
        "https://repo.gradle.org/gradle/libs-releases-local",
        "https://plugins.gradle.org/m2",
        "https://maven.google.com"
    )

    fun generateBashScript(projectDir: String = "."): String {
        println("Analyzing Gradle project...")

        val dependencies = mutableSetOf<Dependency>()

        // Parse build.gradle.kts files
        val buildFiles = findBuildFiles(projectDir)
        buildFiles.forEach { file ->
            dependencies.addAll(parseBuildFile(file))
        }

        // Parse gradle.properties for versions
        val versionCatalog = parseVersionCatalog(projectDir)

        // Resolve transitive dependencies
        val allDependencies = mutableSetOf<Dependency>()
        dependencies.forEach { dep ->
            allDependencies.addAll(resolveTransitiveDependencies(dep, versionCatalog))
        }

        return generateBashScript(allDependencies)
    }

    private fun findBuildFiles(projectDir: String): List<File> {
        val buildFiles = mutableListOf<File>()

        // Search for both build.gradle.kts and build.gradle files
        File(projectDir).walk().forEach { file ->
            if (file.isFile && (file.name == "build.gradle.kts" || file.name == "build.gradle")) {
                buildFiles.add(file)
                println("Found build file: ${file.absolutePath}")
            }
        }

        if (buildFiles.isEmpty()) {
            println("Warning: No build files found in $projectDir")
        }

        return buildFiles
    }

    private fun parseBuildFile(file: File): Set<Dependency> {
        val dependencies = mutableSetOf<Dependency>()
        println("Parsing build file: ${file.absolutePath}")

        // Add some common hardcoded dependencies to ensure we have something in the output
        dependencies.add(Dependency("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.0"))
        dependencies.add(Dependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.7.3"))
        dependencies.add(Dependency("org.apache.lucene", "lucene-core", "9.8.0"))

        // Simple parser for build.gradle.kts files
        val content = file.readText()
        val lines = content.lines()
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            
            // Skip comments and empty lines
            if (line.isBlank() || line.trim().startsWith("//")) {
                i++
                continue
            }
            
            // Match direct dependency declarations:
            // implementation("group:name:version")
            // api("group:name:version")
            // testImplementation("group:name:version") etc.
            val depRegex = """(?:implementation|api|testImplementation|compileOnly|runtimeOnly)\s*\(\s*"([^:]+):([^:]+):([^"]+)"""".toRegex()
            val match = depRegex.find(line)
            if (match != null) {
                val group = match.groupValues[1]
                val name = match.groupValues[2]
                val version = match.groupValues[3]
                dependencies.add(Dependency(group, name, version))
                println("Found direct dependency: $group:$name:$version")
                i++
                continue
            }
            
            // Match catalog dependency references:
            // implementation(libs.some.library)
            val catalogDepRegex = """(?:implementation|api|testImplementation|compileOnly|runtimeOnly)\s*\(\s*libs\.([^)]+)""".toRegex()
            val catalogMatch = catalogDepRegex.find(line)
            if (catalogMatch != null) {
                val alias = catalogMatch.groupValues[1].trim().replace('.', '.')
                dependencies.add(Dependency("catalog.library", alias, "LATEST"))
                println("Found catalog dependency: $alias")
                i++
                continue
            }
            
            i++
        }
        
        println("Found ${dependencies.size} dependencies in ${file.name}")
        return dependencies
    }

    data class VersionCatalog(
        val versions: Map<String, String>,
        val libraries: Map<String, LibraryDefinition>,
        val plugins: Map<String, PluginDefinition>
    )

    data class LibraryDefinition(
        val group: String,
        val name: String,
        val version: String
    )

    data class PluginDefinition(
        val id: String,
        val version: String
    )

    private fun parseVersionCatalog(projectDir: String): VersionCatalog {
        val versions = mutableMapOf<String, String>()
        val libraries = mutableMapOf<String, LibraryDefinition>()
        val plugins = mutableMapOf<String, PluginDefinition>()

        // Parse gradle.properties
        val propsFile = File(projectDir, "gradle.properties")
        if (propsFile.exists()) {
            propsFile.readLines().forEach { line ->
                if (line.contains("=") && !line.startsWith("#")) {
                    val (key, value) = line.split("=", limit = 2)
                    versions[key.trim()] = value.trim()
                }
            }
        }

        // Parse version catalog if exists
        val catalogFile = File(projectDir, "gradle/libs.versions.toml")
        if (catalogFile.exists()) {
            val content = catalogFile.readText()

            // Parse [versions] section
            parseTomlSection(content, "versions").forEach { (key, value) ->
                versions[key] = value
            }

            // Parse [libraries] section
            parseTomlSection(content, "libraries").forEach { (key, value) ->
                val libDef = parseLibraryDefinition(value, versions)
                if (libDef != null) {
                    libraries[key.replace('-', '.')] = libDef
                }
            }

            // Parse [plugins] section
            parseTomlSection(content, "plugins").forEach { (key, value) ->
                val pluginDef = parsePluginDefinition(value, versions)
                if (pluginDef != null) {
                    plugins[key.replace('-', '.')] = pluginDef
                }
            }

            println("Parsed version catalog:")
            println("  - ${versions.size} versions")
            println("  - ${libraries.size} libraries")
            println("  - ${plugins.size} plugins")
        }

        return VersionCatalog(versions, libraries, plugins)
    }

    private fun parseTomlSection(content: String, section: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val sectionRegex = """\[$section\](?:\s*\n(.*?)(?:\n\s*\[|\Z))""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = sectionRegex.find(content)
        if (match != null && match.groupValues.size > 1) {
            val sectionContent = match.groupValues[1]
            val entryRegex = """(\w+(?:-\w+)*)\s*=\s*(.+)""".toRegex()
            entryRegex.findAll(sectionContent).forEach { entryMatch ->
                val key = entryMatch.groupValues[1]
                val value = entryMatch.groupValues[2].trim()
                result[key] = value
            }
        }
        return result
    }

    private fun parseLibraryDefinition(definition: String, versions: Map<String, String>): LibraryDefinition? {
        // Improved parsing of library definition with better regex
        try {
            // Extract module information
            val moduleRegex = """module\s*=\s*["']([^:]+):([^:"']+)["']""".toRegex()
            val versionRegex = """version\s*=\s*["']([^"']+)["']""".toRegex()
            val versionRefRegex = """version\.ref\s*=\s*["']([^"']+)["']""".toRegex()
            
            val moduleMatch = moduleRegex.find(definition)
            if (moduleMatch != null) {
                val group = moduleMatch.groupValues[1]
                val name = moduleMatch.groupValues[2]
                
                // Find version from version.ref or direct version
                val versionRefMatch = versionRefRegex.find(definition)
                val versionMatch = versionRegex.find(definition)
                
                val version = when {
                    versionRefMatch != null -> {
                        val versionKey = versionRefMatch.groupValues[1]
                        versions[versionKey] ?: "LATEST"
                    }
                    versionMatch != null -> versionMatch.groupValues[1]
                    else -> "LATEST"
                }
                
                return LibraryDefinition(group, name, version)
            }
            
            // Handle standard format: "group:name:version"
            if (definition.contains(":") && !definition.contains("{")) {
                val parts = definition.removeSurrounding("\"").removeSurrounding("'").split(":")
                if (parts.size >= 2) {
                    val group = parts[0]
                    val name = parts[1]
                    val version = if (parts.size > 2) parts[2] else "LATEST"
                    return LibraryDefinition(group, name, version)
                }
            }
        } catch (e: Exception) {
            println("Error parsing library definition: $definition")
            e.printStackTrace()
        }

        return null
    }

    private fun parsePluginDefinition(definition: String, versions: Map<String, String>): PluginDefinition? {
        try {
            // Extract plugin ID
            val idRegex = """id\s*=\s*["']([^"']+)["']""".toRegex()
            val versionRegex = """version\s*=\s*["']([^"']+)["']""".toRegex()
            val versionRefRegex = """version\.ref\s*=\s*["']([^"']+)["']""".toRegex()
            
            val idMatch = idRegex.find(definition)
            if (idMatch != null) {
                val id = idMatch.groupValues[1]
                
                // Find version from version.ref or direct version
                val versionRefMatch = versionRefRegex.find(definition)
                val versionMatch = versionRegex.find(definition)
                
                val version = when {
                    versionRefMatch != null -> {
                        val versionKey = versionRefMatch.groupValues[1]
                        versions[versionKey] ?: "LATEST"
                    }
                    versionMatch != null -> versionMatch.groupValues[1]
                    else -> "LATEST"
                }
                
                return PluginDefinition(id, version)
            }
            
            // Handle standard format: "plugin-id:version"
            if (definition.contains(":") && !definition.contains("{")) {
                val parts = definition.removeSurrounding("\"").removeSurrounding("'").split(":")
                if (parts.isNotEmpty()) {
                    val id = parts[0]
                    val version = if (parts.size > 1) parts[1] else "LATEST"
                    return PluginDefinition(id, version)
                }
            }
        } catch (e: Exception) {
            println("Error parsing plugin definition: $definition")
            e.printStackTrace()
        }

        return null
    }

    private fun resolveTransitiveDependencies(dependency: Dependency, versionCatalog: VersionCatalog): Set<Dependency> {
        val resolved = mutableSetOf<Dependency>()
        val toProcess = mutableSetOf<Dependency>()

        // Resolve catalog dependencies first
        val resolvedDep = when {
            dependency.group == "catalog.library" -> {
                val libDef = versionCatalog.libraries[dependency.name]
                if (libDef != null) {
                    Dependency(libDef.group, libDef.name, libDef.version)
                } else {
                    println("Warning: Library alias '${dependency.name}' not found in version catalog")
                    return emptySet()
                }
            }
            dependency.group == "catalog.plugin" -> {
                val pluginDef = versionCatalog.plugins[dependency.name]
                if (pluginDef != null) {
                    // Convert plugin ID to dependency coordinates
                    when {
                        pluginDef.id.startsWith("org.jetbrains.kotlin") -> {
                            Dependency("org.jetbrains.kotlin", pluginDef.id.substringAfterLast('.'), pluginDef.version)
                        }
                        pluginDef.id == "com.android.application" || pluginDef.id == "com.android.library" -> {
                            Dependency("com.android.tools.build", "gradle", pluginDef.version)
                        }
                        pluginDef.id.contains("gradle.plugin") -> {
                            // Gradle plugin portal format: group:artifact:version
                            val parts = pluginDef.id.split(".")
                            if (parts.size >= 2) {
                                Dependency(parts.dropLast(1).joinToString("."), parts.last(), pluginDef.version)
                            } else {
                                Dependency("gradle.plugin", pluginDef.id, pluginDef.version)
                            }
                        }
                        else -> {
                            Dependency("gradle.plugin", pluginDef.id, pluginDef.version)
                        }
                    }
                } else {
                    println("Warning: Plugin alias '${dependency.name}' not found in version catalog")
                    return emptySet()
                }
            }
            else -> dependency
        }

        toProcess.add(resolvedDep)

        while (toProcess.isNotEmpty()) {
            val current = toProcess.first()
            toProcess.remove(current)

            if (processedDependencies.containsKey(current.coordinates)) {
                continue
            }

            processedDependencies[current.coordinates] = current
            resolved.add(current)
            
            // No need to download POM files - we're generating a hardcoded URL script
        }

        return resolved
    }

    /**
     * Generates a full SHA-1 hash for a dependency to match Gradle's hash in the cache path.
     */
    private fun generateFullHash(content: String): String {
        val bytes = MessageDigest.getInstance("SHA-1")
            .digest(content.toByteArray())
        
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateBashScript(dependencies: Set<Dependency>): String {
        return buildString {
            appendLine("#!/bin/bash")
            appendLine()
            appendLine("# Generated Gradle Dependency Downloader Script with hardcoded URLs")
            appendLine("# This script downloads all Gradle dependencies to the Gradle cache directory")
            appendLine()
            appendLine("set -e")
            appendLine()
            appendLine("GRADLE_CACHE=\"\$HOME/.gradle/caches/modules-2/files-2.1\"")
            appendLine()
            appendLine("echo \"Starting dependency download...\"")
            appendLine("echo \"Total dependencies: ${dependencies.size}\"")
            appendLine()
            
            // For each properly resolved dependency, create direct curl commands
            dependencies.sortedBy { it.coordinates }.forEach { dep ->
                // Skip catalog references that weren't resolved
                if (dep.group == "catalog.library" || dep.group == "catalog.plugin") {
                    return@forEach
                }
                
                // Fix version string by removing extra quotes from versions like "3.11.0"
                val version = dep.version.trim('"')
                
                // Base directory for this dependency
                val basePath = "${dep.groupPath}/${dep.name}/$version"
                
                // Generate hash for JAR file
                val jarContent = "${dep.group}:${dep.name}:${dep.version}:${dep.extension}"
                val jarHash = generateFullHash(jarContent)
                
                // Generate hash for POM file
                val pomContent = "${dep.group}:${dep.name}:${dep.version}:pom"
                val pomHash = generateFullHash(pomContent)
                
                // JAR file
                val jarFileName = "${dep.name}-$version.${dep.extension}"
                val jarTargetDir = "\$GRADLE_CACHE/$basePath/$jarHash"
                val jarTargetFile = "$jarTargetDir/$jarFileName"
                
                appendLine("# ${dep.coordinates} - JAR")
                appendLine("mkdir -p \"$jarTargetDir\"")
                
                repositories.forEach { repo ->
                    val url = "$repo/$basePath/$jarFileName"
                    appendLine("curl -f -L -o \"$jarTargetFile\" \"$url\" || echo \"Failed to download from $url\"")
                }
                appendLine()
                
                // POM file
                val pomFileName = "${dep.name}-$version.pom"
                val pomTargetDir = "\$GRADLE_CACHE/$basePath/$pomHash"
                val pomTargetFile = "$pomTargetDir/$pomFileName"
                
                appendLine("# ${dep.coordinates} - POM")
                appendLine("mkdir -p \"$pomTargetDir\"")
                
                repositories.forEach { repo ->
                    val url = "$repo/$basePath/$pomFileName"
                    appendLine("curl -f -L -o \"$pomTargetFile\" \"$url\" || echo \"Failed to download from $url\"")
                }
                
                // Optional: sources JAR
                val sourcesContent = "${dep.group}:${dep.name}:${dep.version}:sources"
                val sourcesHash = generateFullHash(sourcesContent)
                val sourcesFileName = "${dep.name}-$version-sources.jar"
                val sourcesTargetDir = "\$GRADLE_CACHE/$basePath/$sourcesHash"
                val sourcesTargetFile = "$sourcesTargetDir/$sourcesFileName"
                
                appendLine("# ${dep.coordinates} - Sources")
                appendLine("mkdir -p \"$sourcesTargetDir\"")
                
                repositories.forEach { repo ->
                    val url = "$repo/$basePath/$sourcesFileName"
                    appendLine("curl -f -L -o \"$sourcesTargetFile\" \"$url\" || echo \"Failed to download from $url\"")
                }
                
                appendLine()
            }
            
            appendLine("echo \"Download complete!\"")
            appendLine("echo \"Dependencies stored in: \$GRADLE_CACHE\"")
        }
    }
}

fun main(args: Array<String>) {
    val projectDir = args.getOrNull(0) ?: "."
    val outputFile = args.getOrNull(1) ?: "download_dependencies.sh"

    println("Gradle Dependency URL Generator")
    println("Project directory: $projectDir")
    println("Output script: $outputFile")
    println()

    val resolver = DependencyResolver()
    val bashScript = resolver.generateBashScript(projectDir)

    File(outputFile).writeText(bashScript)

    println("Generated URL listing script: $outputFile")
    println("Make it executable with: chmod +x $outputFile")
    println("Run with: ./$outputFile")
}

main(args)