package org.gnit.lucenekmp.analysis.util

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * Removes elisions from a [TokenStream]. For example, "l'avion" (the plane) will be tokenized
 * as "avion" (plane).
 */
class ElisionFilter(
    input: TokenStream,
    private val articles: CharArraySet
) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    init {
        requireNotNull(articles) { "articles" }
    }

    /** Increments the [TokenStream] with a [CharTermAttribute] without elisioned start. */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val termBuffer = termAtt.buffer()
            val termLength = termAtt.length

            var index = -1
            for (i in 0 until termLength) {
                val ch = termBuffer[i]
                if (ch == '\'' || ch == '\u2019') {
                    index = i
                    break
                }
            }

            // An apostrophe has been found. If the prefix is an article strip it off.
            if (index >= 0 && articles.contains(termBuffer, 0, index)) {
                termAtt.copyBuffer(termBuffer, index + 1, termLength - (index + 1))
            }

            return true
        }
        return false
    }
}
