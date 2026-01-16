import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.Base64

abstract class GenerateKoreanDictionaryKotlinTask : DefaultTask() {
    @get:InputFile
    abstract val charDefFile: RegularFileProperty

    @get:InputFile
    abstract val connCostsFile: RegularFileProperty

    @get:InputFile
    abstract val tokenInfoTargetMapFile: RegularFileProperty

    @get:InputFile
    abstract val tokenInfoPosDictFile: RegularFileProperty

    @get:InputFile
    abstract val tokenInfoDictFile: RegularFileProperty

    @get:InputFile
    abstract val tokenInfoFstFile: RegularFileProperty

    @get:InputFile
    abstract val unknownTargetMapFile: RegularFileProperty

    @get:InputFile
    abstract val unknownPosDictFile: RegularFileProperty

    @get:InputFile
    abstract val unknownDictFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generateKotlin() {
        val files = mapOf(
            "characterDefinition" to charDefFile.get().asFile,
            "connectionCosts" to connCostsFile.get().asFile,
            "tokenInfoTargetMap" to tokenInfoTargetMapFile.get().asFile,
            "tokenInfoPosDict" to tokenInfoPosDictFile.get().asFile,
            "tokenInfoDict" to tokenInfoDictFile.get().asFile,
            "tokenInfoFst" to tokenInfoFstFile.get().asFile,
            "unknownTargetMap" to unknownTargetMapFile.get().asFile,
            "unknownPosDict" to unknownPosDictFile.get().asFile,
            "unknownDict" to unknownDictFile.get().asFile,
        )
        for ((name, file) in files) {
            if (!file.isFile) {
                throw GradleException("Korean dictionary data not found for $name at ${file.absolutePath}")
            }
        }

        val packageName = "org.gnit.lucenekmp.analysis.ko.dict"
        val outputRoot = outputDir.get().dir(packageName.replace('.', '/')).asFile
        outputRoot.mkdirs()

        class CountingWriter(private val delegate: Writer) : Writer() {
            var count: Int = 0
                private set

            override fun write(cbuf: CharArray, off: Int, len: Int) {
                delegate.write(cbuf, off, len)
                count += len
            }

            override fun write(str: String) {
                delegate.write(str)
                count += str.length
            }

            override fun write(c: Int) {
                delegate.write(c)
                count += 1
            }

            override fun flush() = delegate.flush()
            override fun close() = delegate.close()
        }

        fun writeChunk(
            writer: Writer,
            namePrefix: String,
            chunkIndex: Int,
            buffer: ByteArray,
            length: Int
        ) {
            val encoded = Base64.getEncoder().encodeToString(buffer.copyOf(length))
            writer.write("private const val ")
            writer.write(namePrefix)
            writer.write("Chunk")
            writer.write(chunkIndex.toString())
            writer.write(" = \"")
            writer.write(encoded)
            writer.write("\"\n\n")
        }

        fun writePartFooter(writer: Writer, partName: String, chunkNames: List<String>) {
            val buildName = partName.replaceFirstChar { it.uppercase() }
            writer.write("private fun build")
            writer.write(buildName)
            writer.write("(): ByteArray {\n")
            writer.write("    val parts = arrayOf(\n")
            for (name in chunkNames) {
                writer.write("        decodeBase64ToBytes(")
                writer.write(name)
                writer.write("),\n")
            }
            writer.write("    )\n")
            writer.write("    var total = 0\n")
            writer.write("    for (p in parts) total += p.size\n")
            writer.write("    val out = ByteArray(total)\n")
            writer.write("    var pos = 0\n")
            writer.write("    for (p in parts) {\n")
            writer.write("        p.copyInto(out, pos)\n")
            writer.write("        pos += p.size\n")
            writer.write("    }\n")
            writer.write("    return out\n")
            writer.write("}\n")
            writer.write("internal val ")
            writer.write(partName)
            writer.write(": ByteArray by lazy { build")
            writer.write(buildName)
            writer.write("() }\n")
        }

        fun writePartsStreaming(baseName: String, file: File, maxFileSize: Int = 1_000_000): List<String> {
            val chunkSize = 4096
            val partNames = mutableListOf<String>()
            var partIndex = 0
            var chunkIndex = 0

            BufferedInputStream(file.inputStream()).use { input ->
                var partWriter: CountingWriter? = null
                var chunkNames = mutableListOf<String>()
                var partName: String? = null

                fun openPart() {
                    partName = baseName + "Part" + partIndex
                    val partFile = outputRoot.resolve("KoreanDictionaryData_${baseName}_${partIndex}.kt")
                    val writer = CountingWriter(BufferedWriter(OutputStreamWriter(partFile.outputStream(), Charsets.UTF_8)))
                    writer.write("// Auto-generated by generateKoreanDictionaryKotlin. Do not edit.\n")
                    writer.write("package ")
                    writer.write(packageName)
                    writer.write("\n\n")
                    partWriter = writer
                    chunkNames = mutableListOf()
                    partNames.add(partName!!)
                }

                fun closePart() {
                    val writer = partWriter ?: return
                    if (chunkNames.isNotEmpty()) {
                        writePartFooter(writer, partName!!, chunkNames)
                    }
                    writer.flush()
                    writer.close()
                    partWriter = null
                }

                val buffer = ByteArray(chunkSize)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    if (partWriter == null) {
                        openPart()
                    }
                    val writer = partWriter ?: break
                    val before = writer.count
                    writeChunk(writer, baseName, chunkIndex, buffer, read)
                    val after = writer.count
                    chunkNames.add(baseName + "Chunk" + chunkIndex)
                    chunkIndex++
                    if (after >= maxFileSize && chunkNames.isNotEmpty()) {
                        closePart()
                        partIndex++
                    }
                    // In case a single chunk blows the file size, continue anyway.
                    if (before == after) {
                        break
                    }
                }
                closePart()
            }
            return partNames
        }

        val partsMap = mutableMapOf<String, List<String>>()
        // Write a shared Base64 decoder helper once per generation.
        run {
            val helperFile = outputRoot.resolve("KoreanDictionaryData_Base64.kt")
            BufferedWriter(OutputStreamWriter(helperFile.outputStream(), Charsets.UTF_8)).use { w ->
                w.write("// Auto-generated by generateKoreanDictionaryKotlin. Do not edit.\n")
                w.write("package ")
                w.write(packageName)
                w.write("\n\n")
                w.write("private val BASE64_INV: IntArray = IntArray(256) { -1 }.also { table ->\n")
                w.write("    val alphabet = \"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\"\n")
                w.write("    for (i in alphabet.indices) table[alphabet[i].code] = i\n")
                w.write("    table['='.code] = -2\n")
                w.write("}\n\n")
                w.write("internal fun decodeBase64ToBytes(data: String): ByteArray {\n")
                w.write("    var validCount = 0\n")
                w.write("    for (c in data) {\n")
                w.write("        if (c == '\\n' || c == '\\r' || c == ' ' || c == '\\t') continue\n")
                w.write("        validCount++\n")
                w.write("    }\n")
                w.write("    val out = ByteArray((validCount * 3) / 4)\n")
                w.write("    var outPos = 0\n")
                w.write("    var acc = 0\n")
                w.write("    var accBits = 0\n")
                w.write("    for (c in data) {\n")
                w.write("        val v = if (c.code < 256) BASE64_INV[c.code] else -1\n")
                w.write("        if (v == -1) continue\n")
                w.write("        if (v == -2) break\n")
                w.write("        acc = (acc shl 6) or v\n")
                w.write("        accBits += 6\n")
                w.write("        if (accBits >= 8) {\n")
                w.write("            accBits -= 8\n")
                w.write("            out[outPos++] = (acc shr accBits).toByte()\n")
                w.write("        }\n")
                w.write("    }\n")
                w.write("    return if (outPos == out.size) out else out.copyOf(outPos)\n")
                w.write("}\n")
            }
        }
        for ((name, file) in files) {
            partsMap[name] = writePartsStreaming(name, file)
        }

        val main = buildString {
            append("// Auto-generated by generateKoreanDictionaryKotlin. Do not edit.\n")
            append("package ").append(packageName).append("\n\n")
            append("internal object KoreanDictionaryData {\n")
            fun renderAggregate(name: String, parts: List<String>) {
                val builderName = name.replaceFirstChar { it.uppercase() }
                append("    private fun build").append(builderName).append("(): ByteArray {\n")
                append("        val parts = arrayOf(\n")
                for (part in parts) {
                    append("            ").append(part).append(",\n")
                }
                append("        )\n")
                append("        var total = 0\n")
                append("        for (p in parts) total += p.size\n")
                append("        val out = ByteArray(total)\n")
                append("        var pos = 0\n")
                append("        for (p in parts) {\n")
                append("            p.copyInto(out, pos)\n")
                append("            pos += p.size\n")
                append("        }\n")
                append("        return out\n")
                append("    }\n")
                append("    internal val ").append(name)
                    .append(": ByteArray by lazy { build")
                    .append(builderName).append("() }\n")
            }
            for ((name, parts) in partsMap) {
                renderAggregate(name, parts)
                append("\n")
            }
            append("}\n")
        }

        outputRoot.resolve("KoreanDictionaryData.kt").writeText(main)
    }
}

val generateKoreanDictionaryData = tasks.register("generateKoreanDictionaryData", GenerateKoreanDictionaryKotlinTask::class.java) {
    val baseDir = rootProject.file("gradle/nori")
    charDefFile.set(baseDir.resolve("CharacterDefinition.dat"))
    connCostsFile.set(baseDir.resolve("ConnectionCosts.dat"))
    tokenInfoTargetMapFile.set(baseDir.resolve("TokenInfoDictionary\$targetMap.dat"))
    tokenInfoPosDictFile.set(baseDir.resolve("TokenInfoDictionary\$posDict.dat"))
    tokenInfoDictFile.set(baseDir.resolve("TokenInfoDictionary\$buffer.dat"))
    tokenInfoFstFile.set(baseDir.resolve("TokenInfoDictionary\$fst.dat"))
    unknownTargetMapFile.set(baseDir.resolve("UnknownDictionary\$targetMap.dat"))
    unknownPosDictFile.set(baseDir.resolve("UnknownDictionary\$posDict.dat"))
    unknownDictFile.set(baseDir.resolve("UnknownDictionary\$buffer.dat"))
    outputDir.set(layout.buildDirectory.dir("generated/ko/nori/kotlin"))
}

tasks.matching {
    it.name.startsWith("compile") && it.name.contains("Kotlin")
}.configureEach {
    dependsOn(generateKoreanDictionaryData)
}

tasks.matching { it.name == "compileAndroidMain" }.configureEach {
    dependsOn(generateKoreanDictionaryData)
}
