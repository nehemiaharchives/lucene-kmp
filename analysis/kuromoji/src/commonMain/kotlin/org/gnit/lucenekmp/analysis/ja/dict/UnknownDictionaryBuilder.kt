package org.gnit.lucenekmp.analysis.ja.dict

import okio.Path
import org.gnit.lucenekmp.analysis.util.CSVUtil
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.Files

internal class UnknownDictionaryBuilder(private val encoding: String) {
    companion object {
        private const val NGRAM_DICTIONARY_ENTRY = "NGRAM,5,5,-32768,記号,一般,*,*,*,*,*,*,*"
    }

    fun build(dir: Path): UnknownDictionaryWriter {
        val unkDictionary = readDictionaryFile(dir.resolve("unk.def"))
        readCharacterDefinition(dir.resolve("char.def"), unkDictionary)
        return unkDictionary
    }

    private fun readDictionaryFile(path: Path): UnknownDictionaryWriter {
        return readDictionaryFile(path, encoding)
    }

    private fun readDictionaryFile(path: Path, encoding: String): UnknownDictionaryWriter {
        val dictionary = UnknownDictionaryWriter(5 * 1024 * 1024)
        val lines: MutableList<Array<String>> = ArrayList()
        Files.newBufferedReader(path, Charset.forName(encoding)).use { reader ->
            dictionary.put(CSVUtil.parse(NGRAM_DICTIONARY_ENTRY))
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                val parsed = CSVUtil.parse("$line,*,*")
                lines.add(parsed)
            }
        }

        lines.sortWith(compareBy { CharacterDefinition.lookupCharacterClass(it[0]) })
        for (entry in lines) {
            dictionary.put(entry)
        }
        return dictionary
    }

    private fun readCharacterDefinition(path: Path, dictionary: UnknownDictionaryWriter) {
        Files.newBufferedReader(path, Charset.forName(encoding)).use { reader ->
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                line = line.replace(Regex("^\\s"), "")
                line = line.replace(Regex("\\s*#.*"), "")
                line = line.replace(Regex("\\s+"), " ")

                if (line.isEmpty()) {
                    continue
                }

                if (line.startsWith("0x")) {
                    val values = line.split(" ", limit = 2)
                    if (!values[0].contains("..")) {
                        val cp = values[0].toInt(16)
                        dictionary.putCharacterCategory(cp, values[1])
                    } else {
                        val codePoints = values[0].split("..")
                        val cpFrom = codePoints[0].toInt(16)
                        val cpTo = codePoints[1].toInt(16)
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
}
