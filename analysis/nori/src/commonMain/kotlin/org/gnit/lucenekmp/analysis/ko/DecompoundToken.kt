package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.ko.dict.KoMorphData
import org.gnit.lucenekmp.analysis.morph.TokenType

/** A token that was generated from a compound. */
class DecompoundToken(
    private val posTag: POS.Tag,
    surfaceForm: String,
    startOffset: Int,
    endOffset: Int,
    type: TokenType
) : Token(surfaceForm.toCharArray(), 0, surfaceForm.length, startOffset, endOffset, type) {

    override fun toString(): String {
        return "DecompoundToken(\"$surfaceFormString\" pos=${startOffset} length=${length} startOffset=${startOffset} endOffset=${endOffset})"
    }

    override val posType: POS.Type = POS.Type.MORPHEME

    override val leftPOS: POS.Tag = posTag

    override val rightPOS: POS.Tag = posTag

    override val readingValue: String? = null

    override val morphemeArray: Array<KoMorphData.Morpheme>? = null
}
