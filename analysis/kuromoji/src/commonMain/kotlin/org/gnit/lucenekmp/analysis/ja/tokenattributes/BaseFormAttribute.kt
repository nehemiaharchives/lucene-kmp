package org.gnit.lucenekmp.analysis.ja.tokenattributes

import org.gnit.lucenekmp.analysis.ja.Token
import org.gnit.lucenekmp.util.Attribute

/**
 * Attribute for [Token.getBaseForm].
 *
 *
 * Note: depending on part of speech, this value may not be applicable, and will be null.
 */
interface BaseFormAttribute : Attribute {
    fun getBaseForm(): String?

    fun setToken(token: Token?)
}
