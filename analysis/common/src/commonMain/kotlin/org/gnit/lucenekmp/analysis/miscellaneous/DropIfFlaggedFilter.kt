package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttribute

/**
 * Allows Tokens with a given combination of flags to be dropped. If all flags specified are present
 * the token is dropped, otherwise it is retained.
 *
 * @see DropIfFlaggedFilterFactory
 * @since 8.8.0
 */
class DropIfFlaggedFilter(input: TokenStream, private val dropFlags: Int) : FilteringTokenFilter(input) {
    private val flagsAtt = addAttribute(FlagsAttribute::class)

    /**
     * Construct a token stream filtering the given input.
     *
     * @param input the source stream
     * @param dropFlags a combination of flags that indicates that the token should be dropped.
     */
    override fun accept(): Boolean {
        return (flagsAtt.flags and dropFlags) != dropFlags
    }
}
