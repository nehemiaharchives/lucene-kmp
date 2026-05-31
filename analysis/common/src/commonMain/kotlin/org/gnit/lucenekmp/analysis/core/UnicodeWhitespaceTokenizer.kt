package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.util.CharTokenizer
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * A UnicodeWhitespaceTokenizer is a tokenizer that divides text at whitespace. Adjacent sequences
 * of non-Whitespace characters form tokens (according to Unicode's WHITESPACE property).
 */
class UnicodeWhitespaceTokenizer : CharTokenizer {
    /** Construct a new UnicodeWhitespaceTokenizer. */
    constructor()

    /**
     * Construct a new UnicodeWhitespaceTokenizer using a given [AttributeFactory].
     *
     * @param factory the attribute factory to use for this [Tokenizer]
     */
    constructor(factory: AttributeFactory) : super(factory)

    /**
     * Construct a new UnicodeWhitespaceTokenizer using a given [AttributeFactory].
     *
     * @param factory the attribute factory to use for this [Tokenizer]
     * @param maxTokenLen maximum token length the tokenizer will emit. Must be greater than 0 and
     *     less than MAX_TOKEN_LENGTH_LIMIT (1024*1024)
     * @throws IllegalArgumentException if maxTokenLen is invalid.
     */
    constructor(factory: AttributeFactory, maxTokenLen: Int) : super(factory, maxTokenLen)

    /** Collects only characters which do not satisfy Unicode's WHITESPACE property. */
    override fun isTokenChar(c: Int): Boolean {
        return !isUnicodeWhitespace(c)
    }

    companion object {
        private fun isUnicodeWhitespace(c: Int): Boolean {
            // Java's Character.isWhitespace excludes NBSP; include space separators to match Unicode whitespace tokenization.
            return c.toChar().isWhitespace() || Character.getType(c) == Character.SPACE_SEPARATOR.toInt()
        }
    }
}
