package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * A ConditionalTokenFilter that only applies its wrapped filters to tokens that are not contained
 * in a protected set.
 */
class ProtectedTermFilter(
    private val protectedTerms: CharArraySet,
    input: TokenStream,
    inputFactory: (TokenStream) -> TokenStream,
) : ConditionalTokenFilter(input, inputFactory) {
    private val termAtt = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun shouldFilter(): Boolean {
        val b = protectedTerms.contains(termAtt.buffer(), 0, termAtt.length)
        return b == false
    }
}
