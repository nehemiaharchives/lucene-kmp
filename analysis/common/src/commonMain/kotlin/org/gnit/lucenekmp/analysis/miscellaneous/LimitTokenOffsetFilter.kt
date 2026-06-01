package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute

/**
 * Lets all tokens pass through until it sees one with a start offset &lt;= a configured limit,
 * which won't pass and ends the stream. This can be useful to limit highlighting, for example.
 *
 * <p>By default, this filter ignores any tokens in the wrapped `TokenStream` once the limit
 * has been exceeded, which can result in `reset()` being called prior to
 * `incrementToken()` returning `false`. For most `TokenStream` implementations this
 * should be acceptable, and faster then consuming the full stream. If you are wrapping a
 * `TokenStream` which requires that the full stream of tokens be exhausted in order to function
 * properly, use the [LimitTokenOffsetFilter] option.
 */
class LimitTokenOffsetFilter : TokenFilter {
    private val offsetAttrib = addAttribute(OffsetAttribute::class)
    private var maxStartOffset: Int
    private val consumeAllTokens: Boolean

    // some day we may limit by end offset too but no need right now

    /**
     * Lets all tokens pass through until it sees one with a start offset &lt;= `maxStartOffset`
     * which won't pass and ends the stream. It won't consume any tokens afterwards.
     *
     * @param maxStartOffset the maximum start offset allowed
     */
    constructor(input: TokenStream, maxStartOffset: Int) : this(input, maxStartOffset, false)

    constructor(input: TokenStream, maxStartOffset: Int, consumeAllTokens: Boolean) : super(input) {
        if (maxStartOffset < 0) {
            throw IllegalArgumentException("maxStartOffset must be >= zero")
        }
        this.maxStartOffset = maxStartOffset
        this.consumeAllTokens = consumeAllTokens
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }
        if (offsetAttrib.startOffset() <= maxStartOffset) {
            return true
        }
        if (consumeAllTokens) {
            while (input.incrementToken()) {
                // no-op
            }
        }
        return false
    }
}
