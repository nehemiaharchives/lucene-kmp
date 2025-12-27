package org.gnit.lucenekmp.analysis.id

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/** A TokenFilter that applies IndonesianStemmer to stem Indonesian words. */
class IndonesianStemFilter : TokenFilter {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAtt: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val stemmer = IndonesianStemmer()
    private val stemDerivational: Boolean

    /** Calls IndonesianStemFilter(input, true). */
    constructor(input: TokenStream) : this(input, true)

    /**
     * Create a new IndonesianStemFilter.
     *
     * If stemDerivational is false, only inflectional suffixes (particles and
     * possessive pronouns) are stemmed.
     */
    constructor(input: TokenStream, stemDerivational: Boolean) : super(input) {
        this.stemDerivational = stemDerivational
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAtt.isKeyword) {
                val newLen = stemmer.stem(termAtt.buffer(), termAtt.length, stemDerivational)
                termAtt.setLength(newLen)
            }
            return true
        }
        return false
    }
}
