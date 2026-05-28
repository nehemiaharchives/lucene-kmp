package org.gnit.lucenekmp.analysis.no

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A [TokenFilter] that applies [NorwegianLightStemmer] to stem Norwegian words.
 *
 * To prevent terms from being stemmed use an instance of [SetKeywordMarkerFilter] or a
 * custom [TokenFilter] that sets the [KeywordAttribute] before this [TokenStream].
 */
class NorwegianLightStemFilter : TokenFilter {
    private val stemmer: NorwegianLightStemmer
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

    /**
     * Calls [NorwegianLightStemFilter(input, NorwegianLightStemmer.BOKMAAL)].
     */
    constructor(input: TokenStream) : this(input, NorwegianLightStemmer.BOKMAAL)

    /**
     * Creates a new NorwegianLightStemFilter
     *
     * @param flags set to [NorwegianLightStemmer.BOKMAAL], [NorwegianLightStemmer.NYNORSK], or both.
     */
    constructor(input: TokenStream, flags: Int) : super(input) {
        stemmer = NorwegianLightStemmer(flags)
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttr.isKeyword) {
                val newlen = stemmer.stem(termAtt.buffer(), termAtt.length)
                termAtt.setLength(newlen)
            }
            return true
        } else {
            return false
        }
    }
}

