package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Character

/**
 * Removes words that are too long or too short from the stream.
 *
 * <p>Note: Length is calculated as the number of Unicode codepoints.
 */
class CodepointCountFilter(`in`: TokenStream, private val min: Int, private val max: Int) :
    FilteringTokenFilter(`in`) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    /**
     * Create a new [CodepointCountFilter]. This will filter out tokens whose [CharTermAttribute]
     * is either too short ([Character.codePointCount] &lt;
     * min) or too long ([Character.codePointCount] &gt; max).
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
        val max32 = termAtt.length
        val min32 = max32 shr 1
        return if (min32 >= min && max32 <= max) {
            // definitely within range
            true
        } else if (min32 > max || max32 < min) {
            // definitely not
            false
        } else {
            // we must count to be sure
            val len = Character.codePointCount(termAtt.buffer(), 0, termAtt.length)
            len in min..max
        }
    }
}
