package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

/**
 * A TokenFilter which filters out Tokens at the same position and Term text as the previous token
 * in the stream.
 */
class RemoveDuplicatesTokenFilter(`in`: TokenStream) : TokenFilter(`in`) {
    private val termAttribute = addAttribute(CharTermAttribute::class)
    private val posIncAttribute = addAttribute(PositionIncrementAttribute::class)

    private val previous = CharArraySet(8, false)

    /**
     * Creates a new RemoveDuplicatesTokenFilter
     *
     * @param in TokenStream that will be filtered
     */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        while (input.incrementToken()) {
            val term = termAttribute.buffer()
            val length = termAttribute.length
            val posIncrement = posIncAttribute.getPositionIncrement()

            if (posIncrement > 0) {
                previous.clear()
            }

            val duplicate = posIncrement == 0 && previous.contains(term, 0, length)

            // clone the term, and add to the set of seen terms.
            val saved = term.copyOf(length)
            previous.add(saved)

            if (!duplicate) {
                return true
            }
        }
        return false
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        previous.clear()
    }
}
