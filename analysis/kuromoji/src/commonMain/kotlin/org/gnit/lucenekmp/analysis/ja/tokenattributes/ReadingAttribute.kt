package org.gnit.lucenekmp.analysis.ja.tokenattributes

import org.gnit.lucenekmp.analysis.ja.Token
import org.gnit.lucenekmp.util.Attribute

/**
 * Attribute for Kuromoji reading data.
 */
interface ReadingAttribute : Attribute {
    fun getReading(): String?

    fun getPronunciation(): String?

    fun setToken(token: Token?)
}
