package org.gnit.lucenekmp.analysis.ko.tokenattributes

import org.gnit.lucenekmp.analysis.ko.Token
import org.gnit.lucenekmp.util.Attribute

/**
 * Attribute for Korean reading data.
 */
interface ReadingAttribute : Attribute {
    /** Get the reading of the token. */
    fun getReading(): String?

    /** Set the current token. */
    fun setToken(token: Token?)
}
