package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.util.CSVUtil
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.Files
import okio.IOException
import okio.Path

internal class UnknownDictionaryBuilder(private val encoding: String) {
    companion object {
        private const val NGRAM_DICTIONARY_ENTRY = "NGRAM,1801,3559,3677,SY,*,*,*,*,*,*,*"
    }

    @Throws(IOException::class)
    fun build(dir: Path): UnknownDictionaryWriter {
        val unkDictionary = readDictionaryFile(dir.resolve("unk.def"))
        readCharacterDefinition(dir.resolve("char.def"), unkDictionary)
        return unkDictionary
    }

    @Throws(IOException::class)
    private fun readDictionaryFile(path: Path): UnknownDictionaryWriter {
        val dictionary = UnknownDictionaryWriter(5 * 1024 * 1024)
        val lines: MutableList<Array<String>> = ArrayList()
        Files.newBufferedReader(path, Charset.forName(encoding)).use { br ->
            dictionary.put(CSVUtil.parse(NGRAM_DICTIONARY_ENTRY))
            var line: String?
            while (true) {
                line = br.readLine() ?: break
                val parsed = CSVUtil.parse("$line,*,*")
                lines.add(parsed)
            }
        }

        lines.sortBy { CharacterDefinition.lookupCharacterClass(it[0]).toInt() }
        for (entry in lines) {
            dictionary.put(entry)
        }
        return dictionary
    }

    @Throws(IOException::class)
    private fun readCharacterDefinition(path: Path, dictionary: UnknownDictionaryWriter) {
        Files.newBufferedReader(path, Charset.forName(encoding)).use { br ->
            var line: String?
            while (true) {
                line = br.readLine() ?: break
                line = line.replace(Regex("^\\s"), "")
                line = line.replace(Regex("\\s*#.*"), "")
                line = line.replace(Regex("\\s+"), " ")
                if (line.isEmpty()) {
                    continue
                }
                if (line.startsWith("0x")) {
                    val values = line.split(" ", limit = 2)
                    if (!values[0].contains("..")) {
                        val cp = parseCodePoint(values[0])
                        dictionary.putCharacterCategory(cp, values[1])
                    } else {
                        val codePoints = values[0].split("..")
                        val cpFrom = parseCodePoint(codePoints[0])
                        val cpTo = parseCodePoint(codePoints[1])
                        for (i in cpFrom..cpTo) {
                            dictionary.putCharacterCategory(i, values[1])
                        }
                    }
                } else {
                    val values = line.split(" ")
                    val characterClassName = values[0]
                    val invoke = values[1].toInt()
                    val group = values[2].toInt()
                    val length = values[3].toInt()
                    dictionary.putInvokeDefinition(characterClassName, invoke, group, length)
                }
            }
        }
    }

    private fun parseCodePoint(value: String): Int {
        return if (value.startsWith("0x") || value.startsWith("0X")) {
            value.substring(2).toInt(16)
        } else {
            value.toInt()
        }
    }
}
