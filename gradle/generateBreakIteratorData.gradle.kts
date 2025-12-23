import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import java.io.File
import java.io.OutputStream

abstract class BaseJavaToolTask : DefaultTask() {
    protected fun findJavaExecutable(name: String): File {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val exeName = if (isWindows) "$name.exe" else name
        val javaHome = File(System.getProperty("java.home"))
        val direct = javaHome.resolve("bin").resolve(exeName)
        if (direct.isFile) return direct
        val parent = javaHome.parentFile?.resolve("bin")?.resolve(exeName)
        if (parent != null && parent.isFile) return parent
        throw GradleException("Unable to locate $exeName under JAVA_HOME: $javaHome")
    }

    protected fun runProcess(command: List<String>, stdout: OutputStream? = null, stderr: OutputStream? = null) {
        val builder = ProcessBuilder(command)
        val process = builder.start()
        stdout?.let { process.inputStream.copyTo(it) }
        stderr?.let { process.errorStream.copyTo(it) }
        val exit = process.waitFor()
        if (exit != 0) {
            throw GradleException("Process failed with exit code $exit: ${command.firstOrNull().orEmpty()}")
        }
    }
}

abstract class CompileBreakIteratorGenerator : BaseJavaToolTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compile() {
        val output = outputDir.get().asFile
        output.mkdirs()
        val sourceFiles = sources.files.map { it.absolutePath }
        if (sourceFiles.isEmpty()) {
            throw GradleException("No generator sources found.")
        }
        val javac = findJavaExecutable("javac").absolutePath
        val command = listOf(
            javac,
            "-d", output.absolutePath,
            "--add-exports", "java.base/sun.text=ALL-UNNAMED",
            "--add-exports", "java.base/sun.text.resources=ALL-UNNAMED"
        ) + sourceFiles
        runProcess(command, System.out, System.err)
    }
}

abstract class GenerateBreakIteratorBinaryData : BaseJavaToolTask() {
    @get:InputFile
    abstract val unicodeDataFile: RegularFileProperty

    @get:InputDirectory
    abstract val toolClassesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputFiles
    abstract val resourceJavaSources: ConfigurableFileCollection

    @get:InputFiles
    abstract val dictionaryFiles: ConfigurableFileCollection

    @get:org.gradle.api.tasks.Input
    abstract val languages: ListProperty<String>

    @TaskAction
    fun generate() {
        val unicodeFile = unicodeDataFile.get().asFile
        if (!unicodeFile.isFile) {
            throw GradleException("UnicodeData.txt not found at $unicodeFile")
        }
        val output = outputDir.get().asFile
        output.mkdirs()
        val javaBin = findJavaExecutable("java").absolutePath
        val classpathDir = toolClassesDir.get().asFile.absolutePath
        val moduleArgs = listOf(
            "--add-exports", "java.base/sun.text=ALL-UNNAMED",
            "--add-exports", "java.base/sun.text.resources=ALL-UNNAMED"
        )
        for (language in languages.get()) {
            val langArgs = if (language.isBlank()) emptyList() else listOf("-language", language)
            val command = listOf(javaBin) + moduleArgs + listOf(
                "-cp", classpathDir,
                "build.tools.generatebreakiteratordata.GenerateBreakIteratorData",
                "-o", output.absolutePath,
                "-spec", unicodeFile.absolutePath
            ) + langArgs
            runProcess(command, System.out, System.err)
        }
    }
}

abstract class GenerateBreakIteratorKotlinTask : DefaultTask() {
    @get:InputDirectory
    abstract val dataDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputFiles
    abstract val dictionaryFiles: ConfigurableFileCollection

    @TaskAction
    fun generateKotlin() {
        val dataRoot = dataDir.get().asFile
        val dataFiles = mapOf(
            "WordBreakIteratorData" to "wordBreakIteratorData",
            "LineBreakIteratorData" to "lineBreakIteratorData",
            "SentenceBreakIteratorData" to "sentenceBreakIteratorData",
            "WordBreakIteratorData_th" to "wordBreakIteratorDataTh",
            "LineBreakIteratorData_th" to "lineBreakIteratorDataTh"
        )
        val packageName = "org.gnit.lucenekmp.jdkport"
        val outputFile = outputDir.get()
            .dir(packageName.replace('.', '/'))
            .file("BreakIteratorData.kt")
            .asFile
        outputFile.parentFile.mkdirs()

        fun renderByteArrayChunks(name: String, bytes: ByteArray): String {
            val sb = StringBuilder()
            val chunkSize = 4096
            val chunks = (bytes.size + chunkSize - 1) / chunkSize
            for (chunkIndex in 0 until chunks) {
                val start = chunkIndex * chunkSize
                val endExclusive = minOf(bytes.size, start + chunkSize)
                sb.append("private fun ").append(name).append("Chunk").append(chunkIndex)
                    .append("(): ByteArray = byteArrayOf(\n")
                val bytesPerLine = 16
                for (i in start until endExclusive) {
                    val localIndex = i - start
                    if (localIndex % bytesPerLine == 0) {
                        sb.append("    ")
                    }
                    sb.append(bytes[i].toInt())
                    if (i != endExclusive - 1) {
                        sb.append(", ")
                    }
                    if (localIndex % bytesPerLine == bytesPerLine - 1 || i == endExclusive - 1) {
                        sb.append("\n")
                    }
                }
                sb.append(")\n\n")
            }

            val builderName = name.replaceFirstChar { it.uppercase() }
            sb.append("private fun build").append(builderName).append("(): ByteArray {\n")
            sb.append("    val parts = arrayOf(\n")
            for (chunkIndex in 0 until chunks) {
                sb.append("        ").append(name).append("Chunk").append(chunkIndex).append("()")
                if (chunkIndex != chunks - 1) {
                    sb.append(",")
                }
                sb.append("\n")
            }
            sb.append("    )\n")
            sb.append("    var total = 0\n")
            sb.append("    for (p in parts) total += p.size\n")
            sb.append("    val out = ByteArray(total)\n")
            sb.append("    var pos = 0\n")
            sb.append("    for (p in parts) {\n")
            sb.append("        p.copyInto(out, pos)\n")
            sb.append("        pos += p.size\n")
            sb.append("    }\n")
            sb.append("    return out\n")
            sb.append("}\n")
            sb.append("val ").append(name).append(": ByteArray by lazy { build").append(builderName).append("() }\n")
            return sb.toString()
        }

        val content = buildString {
            append("// Auto-generated by generateBreakIteratorKotlin. Do not edit.\n")
            append("package ").append(packageName).append("\n\n")
            dataFiles.forEach { (fileName, propertyName) ->
                val file = dataRoot.resolve(fileName)
                if (file.isFile) {
                    append(renderByteArrayChunks(propertyName, file.readBytes()))
                    append("\n")
                }
            }

            dictionaryFiles.files.forEach { dictFile ->
                if (dictFile.isFile) {
                    val propName = when (dictFile.name) {
                        "thai_dict" -> "thaiDictionaryData"
                        else -> dictFile.name.replace(Regex("[^A-Za-z0-9_]"), "_") + "Data"
                    }
                    append(renderByteArrayChunks(propName, dictFile.readBytes()))
                    append("\n")
                }
            }
            append("val ruleBasedBreakIteratorData: ByteArray = wordBreakIteratorData\n")
        }

        outputFile.writeText(content)
    }
}

val localJdk24uDir = rootProject.projectDir.resolve("gradle/jdk24u").normalize()
val generatorSourcesDir = localJdk24uDir
val unicodeDataFilePath = localJdk24uDir.resolve("UnicodeData.txt")
val dictionaryFilesTree = fileTree(localJdk24uDir) {
    include("**/thai_dict")
}

val breakIteratorToolClassesDir = layout.buildDirectory.dir("generated/breakiterator/tool-classes")
val breakIteratorDataDir = layout.buildDirectory.dir("generated/breakiterator/data")
val generatedKotlinDir = layout.buildDirectory.dir("generated/breakiterator/kotlin")

val generatorSources = fileTree(generatorSourcesDir) {
    include("**/*.java")
}

val compileBreakIteratorTool = tasks.register<CompileBreakIteratorGenerator>("compileBreakIteratorGenerator") {
    sources.from(generatorSources)
    outputDir.set(breakIteratorToolClassesDir)
}

val generateBreakIteratorBinaryData = tasks.register<GenerateBreakIteratorBinaryData>("generateBreakIteratorBinaryData") {
    dependsOn(compileBreakIteratorTool)
    unicodeDataFile.set(unicodeDataFilePath)
    toolClassesDir.set(breakIteratorToolClassesDir)
    outputDir.set(breakIteratorDataDir)
    resourceJavaSources.from(generatorSources)
    dictionaryFiles.from(dictionaryFilesTree)
    languages.set(listOf("", "th"))
}

val generateBreakIteratorKotlin = tasks.register<GenerateBreakIteratorKotlinTask>("generateBreakIteratorKotlin") {
    dependsOn(generateBreakIteratorBinaryData)
    dataDir.set(breakIteratorDataDir)
    outputDir.set(generatedKotlinDir)
    dictionaryFiles.from(dictionaryFilesTree)
}

tasks.register("generateBreakIteratorData") {
    dependsOn(generateBreakIteratorKotlin)
}

afterEvaluate {
    val kotlinExtension = extensions.findByName("kotlin") as? ExtensionAware ?: return@afterEvaluate
    val sourceSets = kotlinExtension.extensions.getByName("sourceSets") as NamedDomainObjectContainer<*>
    val commonMain = sourceSets.getByName("commonMain") as ExtensionAware
    val kotlinSource = commonMain.extensions.findByName("kotlin") as? SourceDirectorySet
        ?: return@afterEvaluate
    kotlinSource.srcDir(generatedKotlinDir)

    tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
        dependsOn(generateBreakIteratorKotlin)
    }
}
