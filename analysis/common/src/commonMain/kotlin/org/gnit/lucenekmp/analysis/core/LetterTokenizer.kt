package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.util.CharTokenizer
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * A LetterTokenizer is a tokenizer that divides text at non-letters. That's to say, it defines
 * tokens as maximal strings of adjacent letters, as defined by Character.isLetter()
 * predicate.
 *
 *
 * Note: this does a decent job for most European languages, but does a terrible job for some
 * Asian languages, where words are not separated by spaces.
 */
class LetterTokenizer : CharTokenizer {
    /** Construct a new LetterTokenizer.  */
    constructor()

    /**
     * Construct a new LetterTokenizer using a given [org.gnit.lucenekmp.util.AttributeFactory].
     *
     * @param factory the attribute factory to use for this [org.gnit.lucenekmp.analysis.Tokenizer]
     */
    constructor(factory: AttributeFactory) : super(factory)

    /**
     * Construct a new LetterTokenizer using a given [AttributeFactory].
     *
     * @param factory the attribute factory to use for this [org.gnit.lucenekmp.analysis.Tokenizer]
     * @param maxTokenLen maximum token length the tokenizer will emit. Must be greater than 0 and
     * less than MAX_TOKEN_LENGTH_LIMIT (1024*1024)
     * @throws IllegalArgumentException if maxTokenLen is invalid.
     */
    constructor(factory: AttributeFactory, maxTokenLen: Int) : super(factory, maxTokenLen)

    /** Collects only characters which satisfy [org.gnit.lucenekmp.jdkport.Character.isLetter].  */
    override fun isTokenChar(c: Int): Boolean {
        return c.toChar().isLetter()
    }
}