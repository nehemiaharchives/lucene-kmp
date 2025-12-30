package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.ko.dict.KoMorphData
import org.gnit.lucenekmp.analysis.morph.TokenType

/** Analyzed token with morphological data. */
abstract class Token(
    surfaceForm: CharArray,
    offset: Int,
    length: Int,
    startOffset: Int,
    endOffset: Int,
    type: TokenType
) : org.gnit.lucenekmp.analysis.morph.Token(surfaceForm, offset, length, startOffset, endOffset, type) {
    /** Get the [POS.Type] of the token. */
    abstract val posType: POS.Type

    /** Get the left part of speech of the token. */
    abstract val leftPOS: POS.Tag

    /** Get the right part of speech of the token. */
    abstract val rightPOS: POS.Tag

    /** Get the reading of the token. */
    abstract val readingValue: String?

    /**
     * Get the [KoMorphData.Morpheme] decomposition of the token.
     */
    abstract val morphemeArray: Array<KoMorphData.Morpheme>?
}
