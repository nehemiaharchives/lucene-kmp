package org.gnit.lucenekmp.analysis.en

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/** A high-performance kstem filter for english. */
class KStemFilter(input: TokenStream) : TokenFilter(input) {
    private val stemmer = KStemmer()
    private val termAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAtt = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false

        val term = termAttribute.buffer()
        val len = termAttribute.length
        if (!keywordAtt.isKeyword && stemmer.stem(term, len)) {
            termAttribute.setEmpty()
            termAttribute.append(stemmer.asCharSequence())
        }

        return true
    }
}
