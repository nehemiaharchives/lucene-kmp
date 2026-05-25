package org.gnit.lucenekmp.analysis.ha

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/** A [TokenFilter] that applies [HausaStemmer] to stem Hausa words. */
class HausaStemFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAtt: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val stemmer = HausaStemmer()

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAtt.isKeyword) {
                termAtt.setLength(stemmer.stem(termAtt.buffer(), termAtt.length))
            }
            return true
        }
        return false
    }
}
