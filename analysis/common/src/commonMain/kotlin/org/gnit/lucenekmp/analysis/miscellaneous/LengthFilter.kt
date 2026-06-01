package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * Removes words that are too long or too short from the stream.
 *
 * <p>Note: Length is calculated as the number of UTF-16 code units.
 */
class LengthFilter(`in`: TokenStream, private val min: Int, private val max: Int) :
    FilteringTokenFilter(`in`) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    /**
     * Create a new [LengthFilter]. This will filter out tokens whose [CharTermAttribute]
     * is either too short ([CharTermAttribute.length] &lt; min) or too long ([CharTermAttribute.length]
     * &gt; max).
     *
     * @param in the [TokenStream] to consume
     * @param min the minimum length
     * @param max the maximum length
     */
    init {
        if (min < 0) {
            throw IllegalArgumentException("minimum length must be greater than or equal to zero")
        }
        if (min > max) {
            throw IllegalArgumentException("maximum length must not be greater than minimum length")
        }
    }

    override fun accept(): Boolean {
        val len = termAtt.length
        return len in min..max
    }
}
