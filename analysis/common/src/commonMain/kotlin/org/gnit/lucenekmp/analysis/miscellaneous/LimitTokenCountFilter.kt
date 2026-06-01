package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * This TokenFilter limits the number of tokens while indexing. It is a replacement for the maximum
 * field length setting inside [org.gnit.lucenekmp.index.IndexWriter].
 *
 * <p>By default, this filter ignores any tokens in the wrapped `TokenStream` once the limit
 * has been reached, which can result in `reset()` being called prior to
 * `incrementToken()` returning `false`. For most `TokenStream` implementations this
 * should be acceptable, and faster then consuming the full stream. If you are wrapping a
 * `TokenStream` which requires that the full stream of tokens be exhausted in order to function
 * properly, use the [LimitTokenCountFilter] `consumeAllTokens`
 * option.
 */
class LimitTokenCountFilter : TokenFilter {
    private val maxTokenCount: Int
    private val consumeAllTokens: Boolean
    private var tokenCount = 0
    private var exhausted = false

    /**
     * Build a filter that only accepts tokens up to a maximum number. This filter will not consume
     * any tokens beyond the maxTokenCount limit
     *
     * @see LimitTokenCountFilter
     */
    constructor(`in`: TokenStream, maxTokenCount: Int) : this(`in`, maxTokenCount, false)

    /**
     * Build an filter that limits the maximum number of tokens per field.
     *
     * @param in the stream to wrap
     * @param maxTokenCount max number of tokens to produce
     * @param consumeAllTokens whether all tokens from the input must be consumed even if
     * maxTokenCount is reached.
     */
    constructor(`in`: TokenStream, maxTokenCount: Int, consumeAllTokens: Boolean) : super(`in`) {
        if (maxTokenCount < 1) {
            throw IllegalArgumentException("maxTokenCount must be greater than zero")
        }
        this.maxTokenCount = maxTokenCount
        this.consumeAllTokens = consumeAllTokens
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        return if (exhausted) {
            false
        } else if (tokenCount < maxTokenCount) {
            if (input.incrementToken()) {
                tokenCount++
                true
            } else {
                exhausted = true
                false
            }
        } else {
            while (consumeAllTokens && input.incrementToken()) {
                /* NOOP */
            }
            false
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        tokenCount = 0
        exhausted = false
    }
}
