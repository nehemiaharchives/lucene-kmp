package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.Attribute

/**
 * Determines how many positions this token spans. Very few analyzer components actually produce
 * this attribute, and indexing ignores it, but it's useful to express the graph structure naturally
 * produced by decompounding, word splitting/joining, synonym filtering, etc.
 *
 *
 * NOTE: this is optional, and most analyzers don't change the default value (1).
 */
interface PositionLengthAttribute : Attribute {
    /**
     * Returns the position length of this Token.
     *
     * @see .setPositionLength
     */
    /**
     * Set the position length of this Token.
     *
     *
     * The default value is one.
     *
     * @param positionLength how many positions this token spans.
     * @throws IllegalArgumentException if `positionLength` is zero or negative.
     * @see .getPositionLength
     */
    var positionLength: Int
}
