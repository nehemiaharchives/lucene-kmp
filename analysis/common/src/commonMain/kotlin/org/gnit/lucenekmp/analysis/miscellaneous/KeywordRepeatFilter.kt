package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

/**
 * This TokenFilter emits each incoming token twice once as keyword and once non-keyword, in other
 * words once with [KeywordAttribute.isKeyword] set to `true` and once
 * set to `false`. This is useful if used with a stem filter that respects the [KeywordAttribute]
 * to index the stemmed and the un-stemmed version of a term into the same field.
 */
class KeywordRepeatFilter(input: TokenStream) : TokenFilter(input) {
    private val keywordAttribute = addAttribute(KeywordAttribute::class)
    private val posIncAttr = addAttribute(PositionIncrementAttribute::class)
    private var state: State? = null

    /** Construct a token stream filtering the given input. */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (state != null) {
            restoreState(state)
            posIncAttr.setPositionIncrement(0)
            keywordAttribute.isKeyword = false
            state = null
            return true
        }
        if (input.incrementToken()) {
            state = captureState()
            keywordAttribute.isKeyword = true
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        state = null
    }
}
