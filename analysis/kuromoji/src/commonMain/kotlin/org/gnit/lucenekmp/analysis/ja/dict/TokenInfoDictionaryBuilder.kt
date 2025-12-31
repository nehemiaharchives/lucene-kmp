package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.analysis.util.CSVUtil
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.PositiveIntOutputs
import okio.Path

internal class TokenInfoDictionaryBuilder(
    private val format: DictionaryBuilder.DictionaryFormat,
    private val encoding: String,
    normalizeEntries: Boolean
) {
    // Normalization is not available in common code; keep flag for API parity.
    private val normalize = normalizeEntries

    private var offset: Int = 0

    fun build(dir: Path): TokenInfoDictionaryWriter {
        val csvFiles = Files.newDirectoryStream(dir)
            .filter { it.name.endsWith(".csv") }
            .sortedBy { it.name }
            .toList()
        return buildDictionary(csvFiles)
    }

    private fun buildDictionary(csvFiles: List<Path>): TokenInfoDictionaryWriter {
        val dictionary = TokenInfoDictionaryWriter(10 * 1024 * 1024)
        val cs = Charset.forName(encoding)
        val lines: MutableList<Array<String>> = ArrayList(400000)
        for (path in csvFiles) {
            Files.newBufferedReader(path, cs).use { reader ->
                var line: String?
                while (true) {
                    line = reader.readLine() ?: break
                    val entry = CSVUtil.parse(line)
                    if (entry.size < 13) {
                        throw IllegalArgumentException(
                            "Entry in CSV is not valid (13 field values expected): $line"
                        )
                    }
                    lines.add(formatEntry(entry))
                    if (normalize) {
                        // Normalization is unavailable in common code; skip alternate normalized entries.
                    }
                }
            }
        }

        lines.sortWith(compareBy { it[0] })

        val fstOutput = PositiveIntOutputs.singleton
        val fstCompiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE2, fstOutput).build()
        val scratch = IntsRefBuilder()
        var ord = -1L
        var lastValue: String? = null

        for (entry in lines) {
            val next = dictionary.put(entry)
            if (next == offset) {
                throw IllegalStateException("Failed to process line: ${entry.contentToString()}")
            }
            val token = entry[0]
            if (token != lastValue) {
                ord++
                lastValue = token
                scratch.growNoCopy(token.length)
                scratch.setLength(token.length)
                for (i in token.indices) {
                    scratch.setIntAt(i, token[i].code)
                }
                fstCompiler.add(scratch.get(), ord)
            }
            dictionary.addMapping(ord.toInt(), offset)
            offset = next
        }
        val fst = FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader())
            ?: throw IllegalStateException("FST compilation produced null")
        dictionary.setFST(fst)
        return dictionary
    }

    private fun formatEntry(features: Array<String>): Array<String> {
        if (format == DictionaryBuilder.DictionaryFormat.IPADIC) {
            return features
        }
        val features2 = Array(13) { "" }
        for (i in 0..9) {
            features2[i] = features[i]
        }
        features2[10] = features[11]
        if (features[13].isEmpty()) {
            features2[11] = features[0]
            features2[12] = features[0]
        } else {
            features2[11] = features[13]
            features2[12] = features[13]
        }
        return features2
    }
}
