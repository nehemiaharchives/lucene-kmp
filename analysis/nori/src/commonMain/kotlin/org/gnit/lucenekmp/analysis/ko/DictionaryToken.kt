package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.ko.dict.KoMorphData
import org.gnit.lucenekmp.analysis.morph.TokenType

/** A token stored in a [KoMorphData]. */
class DictionaryToken(
    type: TokenType,
    private val morphAtts: KoMorphData,
    private val wordId: Int,
    surfaceForm: CharArray,
    offset: Int,
    length: Int,
    startOffset: Int,
    endOffset: Int
) : Token(surfaceForm, offset, length, startOffset, endOffset, type) {

    override fun toString(): String {
        return "DictionaryToken(\"${surfaceFormString}\" pos=${startOffset} length=${length} posLen=${positionLength} type=${type} wordId=${wordId} leftID=${morphAtts.getLeftId(wordId)})"
    }

    /** Returns true if this token is known word */
    fun isKnown(): Boolean = type == TokenType.KNOWN

    /** Returns true if this token is unknown word */
    fun isUnknown(): Boolean = type == TokenType.UNKNOWN

    /** Returns true if this token is defined in user dictionary */
    fun isUser(): Boolean = type == TokenType.USER

    override val posType: POS.Type
        get() = morphAtts.getPOSType(wordId)

    override val leftPOS: POS.Tag
        get() = morphAtts.getLeftPOS(wordId)

    override val rightPOS: POS.Tag
        get() = morphAtts.getRightPOS(wordId)

    override val readingValue: String?
        get() = morphAtts.getReading(wordId)

    override val morphemeArray: Array<KoMorphData.Morpheme>?
        get() = morphAtts.getMorphemes(wordId, surfaceForm, offset, length)
}
