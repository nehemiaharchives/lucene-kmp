package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.ja.dict.ToStringUtil
import org.gnit.lucenekmp.analysis.ja.tokenattributes.ReadingAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * A [TokenFilter] that replaces the term attribute with the reading of a token in either
 * katakana or romaji form. The default reading form is katakana.
 */
class JapaneseReadingFormFilter(
    input: TokenStream,
    private val useRomaji: Boolean = false
) : TokenFilter(input) {

    private val termAttr: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val readingAttr: ReadingAttribute = addAttribute(ReadingAttribute::class)

    private val buffer = StringBuilder()

    private fun isHiragana(ch: Char): Boolean {
        return ch in HIRAGANA_START..HIRAGANA_END
    }

    private fun containsHiragana(s: CharSequence): Boolean {
        for (i in 0 until s.length) {
            if (isHiragana(s[i])) {
                return true
            }
        }
        return false
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }

        var reading = readingAttr.getReading()

        if (reading == null && containsHiragana(termAttr)) {
            // When a term is OOV and contains hiragana, convert the term to katakana and treat it as reading.
            val len = termAttr.length
            val readingBuffer = CharArray(len)
            for (i in 0 until len) {
                var ch = termAttr[i]
                if (isHiragana(ch)) {
                    ch = (ch.code + 0x60).toChar()
                }
                readingBuffer[i] = ch
            }
            reading = readingBuffer.concatToString()
        }

        if (useRomaji) {
            if (reading == null) {
                // If it's an OOV term, just try the term text.
                buffer.setLength(0)
                ToStringUtil.getRomanization(buffer, termAttr)
                termAttr.setEmpty()!!.append(buffer)
            } else {
                ToStringUtil.getRomanization(termAttr.setEmpty()!!, reading)
            }
        } else {
            if (reading != null) {
                termAttr.setEmpty()!!.append(reading)
            }
        }

        return true
    }

    companion object {
        private const val HIRAGANA_START: Char = 0x3041.toChar()
        private const val HIRAGANA_END: Char = 0x3096.toChar()
    }
}
