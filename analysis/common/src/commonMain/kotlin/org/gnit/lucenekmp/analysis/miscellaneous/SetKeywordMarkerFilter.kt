package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * Marks terms as keywords via the KeywordAttribute when they exist in the provided set.
 */
class SetKeywordMarkerFilter(
    input: TokenStream,
    private val keywordSet: CharArraySet
) : KeywordMarkerFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    init {
        requireNotNull(keywordSet) { "keywordSet" }
    }

    override fun isKeyword(): Boolean {
        return keywordSet.contains(termAtt.buffer(), 0, termAtt.length)
    }
}
