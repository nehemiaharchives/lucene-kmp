import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.ObjectInputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.Base64

abstract class GenerateChineseDictionaryKotlinTask : DefaultTask() {
    @get:InputFile
    abstract val bigramMemFile: RegularFileProperty

    @get:InputFile
    abstract val coreMemFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generateKotlin() {
        val bigramFile = bigramMemFile.get().asFile
        val coreFile = coreMemFile.get().asFile
        if (!bigramFile.isFile) {
            throw GradleException("Chinese bigram dictionary not found at ${bigramFile.absolutePath}")
        }
        if (!coreFile.isFile) {
            throw GradleException("Chinese core dictionary not found at ${coreFile.absolutePath}")
        }

        val (bigramHashTable, frequencyTable) = ObjectInputStream(bigramFile.inputStream()).use { input ->
            @Suppress("UNCHECKED_CAST")
            val hashes = input.readObject() as LongArray
            val freqs = input.readObject() as IntArray
            hashes to freqs
        }

        val (wordIndexTable, charIndexTable, wordItemCharArrayTable, wordItemFrequencyTable) =
            ObjectInputStream(coreFile.inputStream()).use { input ->
                val wordIndex = input.readObject() as ShortArray
                val charIndex = input.readObject() as CharArray
                @Suppress("UNCHECKED_CAST")
                val wordItems = input.readObject() as Array<Array<CharArray?>?>
                @Suppress("UNCHECKED_CAST")
                val wordFreqs = input.readObject() as Array<IntArray?>
                Quad(wordIndex, charIndex, wordItems, wordFreqs)
            }

        val bigramData = encodeBigram(bigramHashTable, frequencyTable)
        val coreData = encodeCore(wordIndexTable, charIndexTable, wordItemCharArrayTable, wordItemFrequencyTable)

        val packageName = "org.gnit.lucenekmp.analysis.cn.smart.hhmm"
        val outputRoot = outputDir.get()
            .dir(packageName.replace('.', '/'))
            .asFile
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

        fun writePartsStreaming(baseName: String, bytes: ByteArray, maxFileSize: Int = 1_000_000): List<String> {
            val chunkSize = 4096
            val partNames = mutableListOf<String>()
            var partIndex = 0
            var chunkIndex = 0

            BufferedInputStream(bytes.inputStream()).use { input ->
                var partWriter: CountingWriter? = null
                var chunkNames = mutableListOf<String>()
                var partName: String? = null

                fun openPart() {
                    partName = baseName + "Part" + partIndex
                    val partFile = outputRoot.resolve("ChineseDictionaryData_${baseName}_${partIndex}.kt")
                    val writer = CountingWriter(BufferedWriter(OutputStreamWriter(partFile.outputStream(), Charsets.UTF_8)))
                    writer.write("// Auto-generated by generateChineseDictionaryKotlin. Do not edit.\n")
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
                    if (before == after) {
                        break
                    }
                }
                closePart()
            }
            return partNames
        }

        run {
            val helperFile = outputRoot.resolve("ChineseDictionaryData_Base64.kt")
            BufferedWriter(OutputStreamWriter(helperFile.outputStream(), Charsets.UTF_8)).use { w ->
                w.write("// Auto-generated by generateChineseDictionaryKotlin. Do not edit.\n")
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

        val bigramParts = writePartsStreaming("bigramDictData", bigramData)
        val coreParts = writePartsStreaming("coreDictData", coreData)

        val main = buildString {
            append("// Auto-generated by generateChineseDictionaryKotlin. Do not edit.\n")
            append("package ").append(packageName).append("\n\n")
            fun renderAggregate(name: String, parts: List<String>) {
                val builderName = name.replaceFirstChar { it.uppercase() }
                append("private fun build").append(builderName).append("(): ByteArray {\n")
                append("    val parts = arrayOf(\n")
                for (part in parts) {
                    append("        ").append(part).append(",\n")
                }
                append("    )\n")
                append("    var total = 0\n")
                append("    for (p in parts) total += p.size\n")
                append("    val out = ByteArray(total)\n")
                append("    var pos = 0\n")
                append("    for (p in parts) {\n")
                append("        p.copyInto(out, pos)\n")
                append("        pos += p.size\n")
                append("    }\n")
                append("    return out\n")
                append("}\n")
                append("internal val ").append(name).append(": ByteArray by lazy { build")
                    .append(builderName).append("() }\n")
            }
            renderAggregate("bigramDictData", bigramParts)
            append("\n")
            renderAggregate("coreDictData", coreParts)
        }

        outputRoot.resolve("ChineseDictionaryData.kt").writeText(main)
    }

    private fun encodeBigram(bigramHashTable: LongArray, frequencyTable: IntArray): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(bigramHashTable.size)
            for (value in bigramHashTable) {
                out.writeLong(value)
            }
            out.writeInt(frequencyTable.size)
            for (value in frequencyTable) {
                out.writeInt(value)
            }
        }
        return baos.toByteArray()
    }

    private fun encodeCore(
        wordIndexTable: ShortArray,
        charIndexTable: CharArray,
        wordItemCharArrayTable: Array<Array<CharArray?>?>,
        wordItemFrequencyTable: Array<IntArray?>
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(wordIndexTable.size)
            for (value in wordIndexTable) {
                out.writeShort(value.toInt())
            }
            out.writeInt(charIndexTable.size)
            for (value in charIndexTable) {
                out.writeChar(value.code)
            }
            out.writeInt(wordItemCharArrayTable.size)
            for (entry in wordItemCharArrayTable) {
                if (entry == null) {
                    out.writeInt(-1)
                } else {
                    out.writeInt(entry.size)
                    for (word in entry) {
                        if (word == null) {
                            out.writeInt(-1)
                        } else {
                            out.writeInt(word.size)
                            for (ch in word) {
                                out.writeChar(ch.code)
                            }
                        }
                    }
                }
            }
            out.writeInt(wordItemFrequencyTable.size)
            for (entry in wordItemFrequencyTable) {
                if (entry == null) {
                    out.writeInt(-1)
                } else {
                    out.writeInt(entry.size)
                    for (value in entry) {
                        out.writeInt(value)
                    }
                }
            }
        }
        return baos.toByteArray()
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

val bigramMemFilePath = rootProject.projectDir
    .resolve("gradle/smartcn/bigramdict.mem")
    .normalize()
val coreMemFilePath = rootProject.projectDir
    .resolve("gradle/smartcn/coredict.mem")
    .normalize()
val generatedKotlinDir = layout.buildDirectory.dir("generated/cn/smart/kotlin")

val generateChineseDictionaryKotlin = tasks.register<GenerateChineseDictionaryKotlinTask>("generateChineseDictionaryKotlin") {
    bigramMemFile.set(bigramMemFilePath)
    coreMemFile.set(coreMemFilePath)
    outputDir.set(generatedKotlinDir)
}

afterEvaluate {
    val kotlinExtension = extensions.findByName("kotlin") as? ExtensionAware ?: return@afterEvaluate
    val sourceSets = kotlinExtension.extensions.getByName("sourceSets") as NamedDomainObjectContainer<*>
    val commonMain = sourceSets.getByName("commonMain") as ExtensionAware

    tasks.matching {
        it.name.startsWith("compile") && it.name.contains("Kotlin")
    }.configureEach {
        dependsOn(generateChineseDictionaryKotlin)
    }
}
