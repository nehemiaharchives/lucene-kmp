package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

/**
 * This TokenFilter limits its emitted tokens to those with positions that are not greater than the
 * configured limit.
 *
 * <p>By default, this filter ignores any tokens in the wrapped `TokenStream` once the limit
 * has been exceeded, which can result in `reset()` being called prior to
 * `incrementToken()` returning `false`. For most `TokenStream` implementations this
 * should be acceptable, and faster then consuming the full stream. If you are wrapping a
 * `TokenStream` which requires that the full stream of tokens be exhausted in order to function
 * properly, use the [LimitTokenPositionFilter] `consumeAllTokens`
 * option.
 */
class LimitTokenPositionFilter : TokenFilter {
    private val maxTokenPosition: Int
    private val consumeAllTokens: Boolean
    private var tokenPosition = 0
    private var exhausted = false
    private val posIncAtt = addAttribute(PositionIncrementAttribute::class)

    /**
     * Build a filter that only accepts tokens up to and including the given maximum position. This
     * filter will not consume any tokens with position greater than the maxTokenPosition limit.
     *
     * @param in the stream to wrap
     * @param maxTokenPosition max position of tokens to produce (1st token always has position 1)
     * @see LimitTokenPositionFilter
     */
    constructor(`in`: TokenStream, maxTokenPosition: Int) : this(`in`, maxTokenPosition, false)

    /**
     * Build a filter that limits the maximum position of tokens to emit.
     *
     * @param in the stream to wrap
     * @param maxTokenPosition max position of tokens to produce (1st token always has position 1)
     * @param consumeAllTokens whether all tokens from the wrapped input stream must be consumed even
     * if maxTokenPosition is exceeded.
     */
    constructor(`in`: TokenStream, maxTokenPosition: Int, consumeAllTokens: Boolean) : super(`in`) {
        if (maxTokenPosition < 1) {
            throw IllegalArgumentException("maxTokenPosition must be greater than zero")
        }
        this.maxTokenPosition = maxTokenPosition
        this.consumeAllTokens = consumeAllTokens
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (exhausted) {
            return false
        }
        if (input.incrementToken()) {
            tokenPosition += posIncAtt.getPositionIncrement()
            if (tokenPosition <= maxTokenPosition) {
                return true
            } else {
                while (consumeAllTokens && input.incrementToken()) {
                    /* NOOP */
                }
                exhausted = true
                return false
            }
        } else {
            exhausted = true
            return false
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        tokenPosition = 0
        exhausted = false
    }
}
