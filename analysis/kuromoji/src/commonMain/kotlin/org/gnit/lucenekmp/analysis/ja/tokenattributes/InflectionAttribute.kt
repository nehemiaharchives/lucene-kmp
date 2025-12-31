package org.gnit.lucenekmp.analysis.ja.tokenattributes

import org.gnit.lucenekmp.analysis.ja.Token
import org.gnit.lucenekmp.util.Attribute

/** Attribute for Kuromoji inflection data. */
interface InflectionAttribute : Attribute {
    fun getInflectionType(): String?

    fun getInflectionForm(): String?

    fun setToken(token: Token?)
}
