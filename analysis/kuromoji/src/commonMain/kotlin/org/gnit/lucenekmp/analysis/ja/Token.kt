package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.ja.dict.JaMorphData
import org.gnit.lucenekmp.analysis.morph.TokenType
import org.gnit.lucenekmp.jdkport.fromCharArray

/** Analyzed token with morphological data from its dictionary. */
class Token(
    surfaceForm: CharArray,
    offset: Int,
    length: Int,
    startOffset: Int,
    endOffset: Int,
    private val morphId: Int,
    type: TokenType,
    private val morphData: JaMorphData
) : org.gnit.lucenekmp.analysis.morph.Token(surfaceForm, offset, length, startOffset, endOffset, type) {

    override fun toString(): String {
        return "Token(\"" +
            String.fromCharArray(surfaceForm, offset, length) +
            "\" offset=" +
            startOffset +
            " length=" +
            length +
            " posLen=" +
            positionLength +
            " type=" +
            type +
            " morphId=" +
            morphId +
            " leftID=" +
            morphData.getLeftId(morphId) +
            ")"
    }

    /** @return reading. null if token doesn't have reading. */
    fun getReading(): String? = morphData.getReading(morphId, surfaceForm, offset, length)

    /** @return pronunciation. null if token doesn't have pronunciation. */
    fun getPronunciation(): String? = morphData.getPronunciation(morphId, surfaceForm, offset, length)

    /** @return part of speech. */
    fun getPartOfSpeech(): String? = morphData.getPartOfSpeech(morphId)

    /** @return inflection type or null */
    fun getInflectionType(): String? = morphData.getInflectionType(morphId)

    /** @return inflection form or null */
    fun getInflectionForm(): String? = morphData.getInflectionForm(morphId)

    /** @return base form or null if token is not inflected */
    fun getBaseForm(): String? = morphData.getBaseForm(morphId, surfaceForm, offset, length)

    /** Returns true if this token is known word */
    fun isKnown(): Boolean = type == TokenType.KNOWN

    /** Returns true if this token is unknown word */
    fun isUnknown(): Boolean = type == TokenType.UNKNOWN

    /** Returns true if this token is defined in user dictionary */
    fun isUser(): Boolean = type == TokenType.USER
}
