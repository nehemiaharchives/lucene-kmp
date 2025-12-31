package org.gnit.lucenekmp.analysis.ja.tokenattributes

import org.gnit.lucenekmp.analysis.ja.Token
import org.gnit.lucenekmp.util.Attribute

/** Attribute for [Token.getPartOfSpeech]. */
interface PartOfSpeechAttribute : Attribute {
    fun getPartOfSpeech(): String?

    fun setToken(token: Token?)
}
