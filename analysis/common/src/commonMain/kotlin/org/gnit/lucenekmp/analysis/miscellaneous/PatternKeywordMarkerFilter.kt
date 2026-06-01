package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * Marks terms as keywords via the [org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute].
 * Each token that matches the provided pattern is marked as a keyword by setting
 * [org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute.isKeyword] to `true`.
 */
class PatternKeywordMarkerFilter(
    `in`: TokenStream,
    private val pattern: Regex
) : KeywordMarkerFilter(`in`) {
    private val termAtt = addAttribute(CharTermAttribute::class)

    /**
     * Create a new [PatternKeywordMarkerFilter], that marks the current token as a keyword if
     * the tokens term buffer matches the provided pattern via the KeywordAttribute.
     *
     * @param in TokenStream to filter
     * @param pattern the pattern to apply to the incoming term buffer
     */
    override fun isKeyword(): Boolean {
        return pattern.matches(termAtt.toString())
    }
}
