package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.ko.tokenattributes.ReadingAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import okio.IOException

/**
 * Replaces term text with the [ReadingAttribute] which is the Hangul transcription of Hanja
 * characters.
 */
class KoreanReadingFormFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    init {
        addAttributeImpl(org.gnit.lucenekmp.analysis.ko.tokenattributes.ReadingAttributeImpl())
    }
    private val readingAtt: ReadingAttribute = addAttribute(ReadingAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        return if (input.incrementToken()) {
            val reading = readingAtt.getReading()
            if (reading != null) {
                termAtt.setEmpty()!!.append(reading)
            }
            true
        } else {
            false
        }
    }
}
