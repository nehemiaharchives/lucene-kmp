package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.util.CharTokenizer
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * A tokenizer that divides text at whitespace characters as defined by [ ][Character.isWhitespace]. Note: That definition explicitly excludes the non-breaking space.
 * Adjacent sequences of non-Whitespace characters form tokens.
 *
 * @see UnicodeWhitespaceTokenizer
 */
class WhitespaceTokenizer : CharTokenizer {
    /** Construct a new WhitespaceTokenizer.  */
    constructor()

    /**
     * Construct a new WhitespaceTokenizer using a given [ ].
     *
     * @param factory the attribute factory to use for this [Tokenizer]
     */
    constructor(factory: AttributeFactory) : super(factory)

    /**
     * Construct a new WhitespaceTokenizer using a given max token length
     *
     * @param maxTokenLen maximum token length the tokenizer will emit. Must be greater than 0 and
     * less than MAX_TOKEN_LENGTH_LIMIT (1024*1024)
     * @throws IllegalArgumentException if maxTokenLen is invalid.
     */
    constructor(maxTokenLen: Int) : super(
        DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
        maxTokenLen
    )

    /**
     * Construct a new WhitespaceTokenizer using a given [ ].
     *
     * @param factory the attribute factory to use for this [Tokenizer]
     * @param maxTokenLen maximum token length the tokenizer will emit. Must be greater than 0 and
     * less than MAX_TOKEN_LENGTH_LIMIT (1024*1024)
     * @throws IllegalArgumentException if maxTokenLen is invalid.
     */
    constructor(factory: AttributeFactory, maxTokenLen: Int) : super(factory, maxTokenLen)

    /** Collects only characters which do not satisfy [Character.isWhitespace].  */
    override fun isTokenChar(c: Int): Boolean {
        return !c.toChar().isWhitespace()
    }
}
