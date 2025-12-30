package org.gnit.lucenekmp.analysis.ko.dict

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
            HANGUL,
            HANJA,
            HANJANUMERIC
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
        val HANGUL: Byte = CharacterClass.HANGUL.ordinal.toByte()
        val HANJA: Byte = CharacterClass.HANJA.ordinal.toByte()
        val HANJANUMERIC: Byte = CharacterClass.HANJANUMERIC.ordinal.toByte()

        fun getInstance(): CharacterDefinition = SingletonHolder.INSTANCE

        fun lookupCharacterClass(characterClassName: String): Byte {
            return CharacterClass.valueOf(characterClassName).ordinal.toByte()
        }

        private fun getClassResource(): InputStream {
            return ByteArrayInputStream(KoreanDictionaryData.characterDefinition)
        }

        private object SingletonHolder {
            val INSTANCE: CharacterDefinition = try {
                CharacterDefinition()
            } catch (ioe: IOException) {
                throw RuntimeException("Cannot load CharacterDefinition.", ioe)
            }
        }
    }

    fun isHanja(c: Char): Boolean {
        val characterClass = getCharacterClass(c)
        return characterClass == HANJA || characterClass == HANJANUMERIC
    }

    fun isHangul(c: Char): Boolean = getCharacterClass(c) == HANGUL

    fun hasCoda(ch: Char): Boolean {
        return ((ch.code - 0xAC00) % 0x001C) != 0
    }
}
