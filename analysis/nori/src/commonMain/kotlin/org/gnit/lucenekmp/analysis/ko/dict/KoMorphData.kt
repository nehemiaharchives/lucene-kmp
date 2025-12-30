package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.ko.POS
import org.gnit.lucenekmp.analysis.morph.MorphData

/** Represents Korean morphological information. */
interface KoMorphData : MorphData {
    /** A morpheme extracted from a compound token. */
    data class Morpheme(val posTag: POS.Tag, val surfaceForm: String)

    /**
     * Get the [POS.Type] of specified word (morpheme, compound, inflect or pre-analysis)
     */
    fun getPOSType(morphId: Int): POS.Type

    /**
     * Get the left [POS.Tag] of specified word.
     *
     * For [POS.Type.MORPHEME] and [POS.Type.COMPOUND] the left and right POS are the same.
     */
    fun getLeftPOS(morphId: Int): POS.Tag

    /**
     * Get the right [POS.Tag] of specified word.
     *
     * For [POS.Type.MORPHEME] and [POS.Type.COMPOUND] the left and right POS are the same.
     */
    fun getRightPOS(morphId: Int): POS.Tag

    /** Get the reading of specified word (mainly used for Hanja to Hangul conversion). */
    fun getReading(morphId: Int): String?

    /** Get the morphemes of specified word (e.g. 가깝으나: 가깝 + 으나). */
    fun getMorphemes(morphId: Int, surfaceForm: CharArray, off: Int, len: Int): Array<Morpheme>?
}
