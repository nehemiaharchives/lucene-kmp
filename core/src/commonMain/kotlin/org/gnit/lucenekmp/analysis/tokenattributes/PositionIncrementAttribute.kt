package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.Attribute

/**
 * Determines the position of this token relative to the previous Token in a TokenStream, used in
 * phrase searching.
 *
 *
 * The default value is one.
 *
 *
 * Some common uses for this are:
 *
 *
 *  * Set it to zero to put multiple terms in the same position. This is useful if, e.g., a word
 * has multiple stems. Searches for phrases including either stem will match. In this case,
 * all but the first stem's increment should be set to zero: the increment of the first
 * instance should be one. Repeating a token with an increment of zero can also be used to
 * boost the scores of matches on that token.
 *  * Set it to values greater than one to inhibit exact phrase matches. If, for example, one
 * does not want phrases to match across removed stop words, then one could build a stop word
 * filter that removes stop words and also sets the increment to the number of stop words
 * removed before each non-stop word. Then exact phrase queries will only match when the terms
 * occur with no intervening stop words.
 *
 *
 * @see org.apache.lucene.index.PostingsEnum
 */
interface PositionIncrementAttribute : Attribute {
    /**
     * Returns the position increment of this Token.
     *
     * @see .setPositionIncrement
     */
    fun getPositionIncrement(): Int

    /**
     * Set the position increment. The default value is one.
     *
     * @param positionIncrement the distance from the prior term
     * @throws IllegalArgumentException if `positionIncrement` is negative.
     * @see .getPositionIncrement
     */
    fun setPositionIncrement(positionIncrement: Int)

}
