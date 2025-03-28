package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.Attribute

/** A Token's lexical type. The Default value is "word".  */
interface TypeAttribute : Attribute {

    /**
     * Returns this Token's lexical type. Defaults to "word".
     *
     * @see .setType
     */
    fun type(): String

    /**
     * Set the lexical type.
     *
     * @see .type
     */
    fun setType(type: String)

    companion object {
        /** the default type  */
        const val DEFAULT_TYPE: String = "word"
    }
}
