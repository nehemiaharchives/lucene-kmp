package org.gnit.lucenekmp.analysis.fr

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A TokenFilter that applies FrenchLightStemmer to stem French words.
 */
class FrenchLightStemFilter(input: TokenStream) : TokenFilter(input) {
    private val stemmer = FrenchLightStemmer()
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttr.isKeyword) {
                val newLen = stemmer.stem(termAtt.buffer(), termAtt.length)
                termAtt.setLength(newLen)
            }
            return true
        }
        return false
    }
}
