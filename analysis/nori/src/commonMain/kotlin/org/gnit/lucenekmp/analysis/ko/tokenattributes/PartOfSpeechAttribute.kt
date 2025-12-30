package org.gnit.lucenekmp.analysis.ko.tokenattributes

import org.gnit.lucenekmp.analysis.ko.POS
import org.gnit.lucenekmp.analysis.ko.Token
import org.gnit.lucenekmp.analysis.ko.dict.KoMorphData
import org.gnit.lucenekmp.util.Attribute

/**
 * Part of Speech attributes for Korean.
 */
interface PartOfSpeechAttribute : Attribute {
    /** Get the [POS.Type] of the token. */
    fun getPOSType(): POS.Type?

    /** Get the left part of speech of the token. */
    fun getLeftPOS(): POS.Tag?

    /** Get the right part of speech of the token. */
    fun getRightPOS(): POS.Tag?

    /** Get the [KoMorphData.Morpheme] decomposition of the token. */
    fun getMorphemes(): Array<KoMorphData.Morpheme>?

    /** Set the current token. */
    fun setToken(token: Token?)
}
