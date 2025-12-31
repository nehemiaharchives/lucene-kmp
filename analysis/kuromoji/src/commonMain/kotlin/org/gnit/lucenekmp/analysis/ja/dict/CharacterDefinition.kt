package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.jdkport.InputStream
import okio.IOException

/** Character category data. */
class CharacterDefinition private constructor() : org.gnit.lucenekmp.analysis.morph.CharacterDefinition(
    { getClassResource() },
    DictionaryConstants.CHARDEF_HEADER,
    DictionaryConstants.VERSION,
    CLASS_COUNT
) {
    companion object {
        val CLASS_COUNT: Int = CharacterClass.entries.size

        // only used internally for lookup:
        private enum class CharacterClass {
            NGRAM,
            DEFAULT,
            SPACE,
            SYMBOL,
            NUMERIC,
            ALPHA,
            CYRILLIC,
            GREEK,
            HIRAGANA,
            KATAKANA,
            KANJI,
            KANJINUMERIC
        }

        // the classes:
        val NGRAM: Byte = CharacterClass.NGRAM.ordinal.toByte()
        val DEFAULT: Byte = CharacterClass.DEFAULT.ordinal.toByte()
        val SPACE: Byte = CharacterClass.SPACE.ordinal.toByte()
        val SYMBOL: Byte = CharacterClass.SYMBOL.ordinal.toByte()
        val NUMERIC: Byte = CharacterClass.NUMERIC.ordinal.toByte()
        val ALPHA: Byte = CharacterClass.ALPHA.ordinal.toByte()
        val CYRILLIC: Byte = CharacterClass.CYRILLIC.ordinal.toByte()
        val GREEK: Byte = CharacterClass.GREEK.ordinal.toByte()
        val HIRAGANA: Byte = CharacterClass.HIRAGANA.ordinal.toByte()
        val KATAKANA: Byte = CharacterClass.KATAKANA.ordinal.toByte()
        val KANJI: Byte = CharacterClass.KANJI.ordinal.toByte()
        val KANJINUMERIC: Byte = CharacterClass.KANJINUMERIC.ordinal.toByte()

        fun lookupCharacterClass(characterClassName: String): Byte {
            return CharacterClass.valueOf(characterClassName).ordinal.toByte()
        }

        fun getInstance(): CharacterDefinition = SingletonHolder.INSTANCE

        private fun getClassResource(): InputStream {
            return ByteArrayInputStream(JapaneseDictionaryData.characterDefinition)
        }

        private object SingletonHolder {
            val INSTANCE: CharacterDefinition = try {
                CharacterDefinition()
            } catch (ioe: IOException) {
                throw RuntimeException("Cannot load CharacterDefinition.", ioe)
            }
        }
    }

    fun isKanji(c: Char): Boolean {
        val characterClass = getCharacterClass(c)
        return characterClass == KANJI || characterClass == KANJINUMERIC
    }
}
