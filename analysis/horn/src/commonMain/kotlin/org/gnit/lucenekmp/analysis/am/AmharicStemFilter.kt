package org.gnit.lucenekmp.analysis.am

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/** Applies compact Horn-inspired Amharic stemming. */
class AmharicStemFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAtt: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val stemmer = AmharicStemmer()

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false
        if (!keywordAtt.isKeyword) {
            termAtt.setLength(stemmer.stem(termAtt.buffer(), termAtt.length))
        }
        return true
    }
}
