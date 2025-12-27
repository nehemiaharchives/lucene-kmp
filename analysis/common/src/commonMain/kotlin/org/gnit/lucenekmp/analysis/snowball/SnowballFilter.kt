package org.gnit.lucenekmp.analysis.snowball

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.tartarus.snowball.SnowballStemmer
import org.tartarus.snowball.ext.RussianStemmer

/**
 * A filter that stems words using a Snowball-generated stemmer.
 */
class SnowballFilter : TokenFilter {
    private val stemmer: SnowballStemmer
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

    constructor(input: TokenStream, stemmer: SnowballStemmer) : super(input) {
        this.stemmer = stemmer
    }

    constructor(input: TokenStream, name: String) : super(input) {
        var resolvedName = name
        if (resolvedName == "German2") {
            resolvedName = "German"
        }
        stemmer = when (resolvedName) {
            "Russian" -> RussianStemmer()
            else -> throw IllegalArgumentException("Invalid stemmer class specified: $resolvedName")
        }
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttr.isKeyword) {
                val termBuffer = termAtt.buffer()
                val length = termAtt.length
                stemmer.setCurrent(termBuffer, length)
                stemmer.stem()
                val finalTerm = stemmer.getCurrentBuffer()
                val newLength = stemmer.getCurrentBufferLength()
                if (finalTerm !== termBuffer) {
                    termAtt.copyBuffer(finalTerm, 0, newLength)
                } else {
                    termAtt.setLength(newLength)
                }
            }
            return true
        }
        return false
    }
}
