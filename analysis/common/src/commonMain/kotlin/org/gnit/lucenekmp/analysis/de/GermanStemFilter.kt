package org.gnit.lucenekmp.analysis.de

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A TokenFilter that stems German words.
 */
class GermanStemFilter(input: TokenStream) : TokenFilter(input) {
    private var stemmer: GermanStemmer = GermanStemmer()

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val term = termAtt.toString()
            if (!keywordAttr.isKeyword) {
                val s = stemmer.stem(term)
                if (s != term) {
                    termAtt.setEmpty()
                    termAtt.append(s)
                }
            }
            return true
        }
        return false
    }

    /** Set an alternative/custom GermanStemmer for this filter. */
    fun setStemmer(stemmer: GermanStemmer?) {
        if (stemmer != null) {
            this.stemmer = stemmer
        }
    }
}
