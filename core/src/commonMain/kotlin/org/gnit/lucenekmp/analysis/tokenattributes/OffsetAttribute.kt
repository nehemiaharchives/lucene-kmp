package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.Attribute

/** The start and end character offset of a Token.  */
interface OffsetAttribute : Attribute {
    /**
     * Returns this Token's starting offset, the position of the first character corresponding to this
     * token in the source text.
     *
     *
     * Note that the difference between [.endOffset] and `startOffset()` may not
     * be equal to termText.length(), as the term text may have been altered by a stemmer or some
     * other filter.
     *
     * @see .setOffset
     */
    fun startOffset(): Int

    /**
     * Set the starting and ending offset.
     *
     * @throws IllegalArgumentException If `startOffset` or `endOffset` are
     * negative, or if `startOffset` is greater than `endOffset`
     * @see .startOffset
     * @see .endOffset
     */
    fun setOffset(startOffset: Int, endOffset: Int)

    /**
     * Returns this Token's ending offset, one greater than the position of the last character
     * corresponding to this token in the source text. The length of the token in the source text is (
     * `endOffset()` - [.startOffset]).
     *
     * @see .setOffset
     */
    fun endOffset(): Int
}
