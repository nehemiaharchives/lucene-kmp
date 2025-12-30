package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.util.CSVUtil
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.PositiveIntOutputs
import okio.IOException
import okio.Path

internal class TokenInfoDictionaryBuilder(
    private val encoding: String,
    normalizeEntries: Boolean
) {
    /**
     * Internal word id - incrementally assigned as entries are read and added. This will be byte
     * offset of dictionary file
     */
    private var offset = 0

    private val normalize = normalizeEntries

    @Throws(IOException::class)
    fun build(dir: Path): TokenInfoDictionaryWriter {
        val files = Files.newDirectoryStream(dir)
        val csvFiles = files.filter { it.name.endsWith(".csv") }.sortedBy { it.name }.toList()
        return buildDictionary(csvFiles)
    }

    @Throws(IOException::class)
    private fun buildDictionary(csvFiles: List<Path>): TokenInfoDictionaryWriter {
        val dictionary = TokenInfoDictionaryWriter(10 * 1024 * 1024)
        val lines: MutableList<Array<String>> = ArrayList(400000)
        for (path in csvFiles) {
            Files.newBufferedReader(path, Charset.forName(encoding)).use { br ->
                var line: String?
                while (true) {
                    line = br.readLine() ?: break
                    val entry = CSVUtil.parse(line)
                    if (entry.size < 12) {
                        throw IllegalArgumentException(
                            "Entry in CSV is not valid (12 field values expected): $line"
                        )
                    }
                    if (normalize) {
                        val normalizedEntry = Array(entry.size) { idx -> entry[idx] }
                        lines.add(normalizedEntry)
                    } else {
                        lines.add(entry)
                    }
                }
            }
        }

        lines.sortBy { it[0] }

        val fstOutput = PositiveIntOutputs.singleton
        val fstCompiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE2, fstOutput).build()
        val scratch = IntsRefBuilder()
        var ord = -1L
        var lastValue: String? = null

        for (entry in lines) {
            val surfaceForm = entry[0].trim()
            if (surfaceForm.isEmpty()) continue
            val next = dictionary.put(entry)

            if (next == offset) {
                throw IllegalStateException("Failed to process line: ${entry.contentToString()}")
            }

            if (surfaceForm != lastValue) {
                ord++
                lastValue = surfaceForm
                scratch.growNoCopy(surfaceForm.length)
                scratch.setLength(surfaceForm.length)
                for (i in surfaceForm.indices) {
                    scratch.setIntAt(i, surfaceForm[i].code)
                }
                fstCompiler.add(scratch.get(), ord)
            }
            dictionary.addMapping(ord.toInt(), offset)
            offset = next
        }

        val fstMeta = fstCompiler.compile()
        val fst = FST.fromFSTReader(fstMeta, fstCompiler.getFSTReader())
            ?: throw IllegalStateException("FST compilation produced null")
        dictionary.setFST(fst)
        return dictionary
    }
}
