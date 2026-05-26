package org.gnit.lucenekmp.analysis.km

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

class KhmerNumberFilter(input: TokenStream) : TokenFilter(input) {
    private val termAttr: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val posIncrAttr: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val offsetAttr: OffsetAttribute = addAttribute(OffsetAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }

        if (!keywordAttr.isKeyword) {
            val term = termAttr.toString()
            val numeralTerm = isNumeral(term)
            if (numeralTerm) {
                val normalizedNumber = normalizeNumber(term)

                termAttr.setEmpty()
                termAttr.append(normalizedNumber)
            }
        }

        return true
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
    }

    fun normalizeNumber(number: String): String {
        return number
            .replace("១", "1")
            .replace("២", "2")
            .replace("៣", "3")
            .replace("៤", "4")
            .replace("៥", "5")
            .replace("៦", "6")
            .replace("៧", "7")
            .replace("៨", "8")
            .replace("៩", "9")
            .replace("០", "0")
    }

    fun isNumeral(input: String): Boolean {
        for (i in input.indices) {
            if (!isNumeral(input[i])) {
                return false
            }
        }
        return true
    }

    fun isNumeral(c: Char): Boolean {
        return KH_NUM.indexOf(c) >= 0
    }

    companion object {
        private const val KH_NUM: String = "០១២៣៤៥៦៧៨៩,."
    }
}
