package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.analysis.morph.MorphData

/** Represents Japanese morphological information. */
interface JaMorphData : MorphData {
    /** Get Part-Of-Speech of tokens */
    fun getPartOfSpeech(morphId: Int): String?

    /** Get reading of tokens */
    fun getReading(morphId: Int, surface: CharArray, off: Int, len: Int): String?

    /** Get base form of word (only different for inflected words, otherwise null) */
    fun getBaseForm(morphId: Int, surface: CharArray, off: Int, len: Int): String?

    /** Get pronunciation of tokens */
    fun getPronunciation(morphId: Int, surface: CharArray, off: Int, len: Int): String?

    /** Get inflection type of tokens, or null */
    fun getInflectionType(morphId: Int): String?

    /** Get inflection form of tokens, or null */
    fun getInflectionForm(wordId: Int): String?
}
