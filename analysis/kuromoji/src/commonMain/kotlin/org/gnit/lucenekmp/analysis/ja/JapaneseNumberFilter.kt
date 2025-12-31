package org.gnit.lucenekmp.analysis.ja

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute

/**
 * A [TokenFilter] that normalizes Japanese numbers (kansūji) to regular Arabic decimal numbers.
 */
class JapaneseNumberFilter(input: TokenStream) : TokenFilter(input) {
    private val termAttr: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAttr: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val posIncrAttr: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLengthAttr: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)

    private var state: State? = null
    private var numeral: StringBuilder = StringBuilder()
    private var fallThroughTokens: Int = 0
    private var exhausted: Boolean = false

    override fun incrementToken(): Boolean {
        if (state != null) {
            restoreState(state!!)
            state = null
            return true
        }
        if (exhausted) {
            return false
        }
        if (!input.incrementToken()) {
            exhausted = true
            return false
        }
        if (keywordAttr.isKeyword) {
            return true
        }
        if (fallThroughTokens > 0) {
            fallThroughTokens--
            return true
        }
        if (posIncrAttr.getPositionIncrement() == 0) {
            fallThroughTokens = posLengthAttr.positionLength - 1
            return true
        }

        var moreTokens = true
        var composedNumberToken = false
        var startOffset = 0
        var endOffset = 0
        val preCompositionState = captureState()
        var term = termAttr.toString()
        var numeralTerm = isNumeral(term)

        while (moreTokens && numeralTerm) {
            if (!composedNumberToken) {
                startOffset = offsetAttr.startOffset()
                composedNumberToken = true
            }

            endOffset = offsetAttr.endOffset()
            moreTokens = input.incrementToken()
            if (!moreTokens) {
                exhausted = true
            }

            if (posIncrAttr.getPositionIncrement() == 0) {
                fallThroughTokens = posLengthAttr.positionLength - 1
                state = captureState()
                restoreState(preCompositionState)
                return moreTokens
            }

            numeral.append(term)

            if (moreTokens) {
                term = termAttr.toString()
                numeralTerm = isNumeral(term) || isNumeralPunctuation(term)
            }
        }

        if (composedNumberToken) {
            if (moreTokens) {
                state = captureState()
            }
            val normalizedNumber = normalizeNumber(numeral.toString())
            termAttr.setEmpty()
            termAttr.append(normalizedNumber)
            offsetAttr.setOffset(startOffset, endOffset)
            numeral = StringBuilder()
            return true
        }
        return moreTokens
    }

    override fun reset() {
        super.reset()
        fallThroughTokens = 0
        numeral = StringBuilder()
        state = null
        exhausted = false
    }

    private fun BigDecimal.removeTrailingZeroesPublic(): BigDecimal {
        // Mirrors the internal IonSpin BigDecimal implementation used to squeeze significands
        // into minimal precision (e.g. 12340000 -> 1234 while representing the same value).
        if (this.isZero()) return this

        var significand = this.significand
        var divisionResult = BigInteger.QuotientAndRemainder(significand, BigInteger.ZERO)

        do {
            divisionResult = divisionResult.quotient.divrem(BigInteger.TEN)
            if (divisionResult.remainder == BigInteger.ZERO) {
                significand = divisionResult.quotient
            }
        } while (divisionResult.remainder == BigInteger.ZERO)

        return BigDecimal.fromBigIntegerWithExponent(significand, this.exponent)
    }

    /** Normalizes a Japanese number */
    fun normalizeNumber(number: String): String {
        return try {
            val normalizedNumber = parseNumber(NumberBuffer(number)) ?: return number
            normalizedNumber.removeTrailingZeroesPublic().toPlainString()
        } catch (_: Exception) {
            number
        }
    }

    private fun parseNumber(buffer: NumberBuffer): BigDecimal? {
        var sum = BigDecimal.ZERO
        var result = parseLargePair(buffer)
        if (result == null) {
            return null
        }
        while (result != null) {
            sum = sum + result
            result = parseLargePair(buffer)
        }
        return sum
    }

    private fun parseLargePair(buffer: NumberBuffer): BigDecimal? {
        val first = parseMediumNumber(buffer)
        val second = parseLargeKanjiNumeral(buffer)
        if (first == null && second == null) {
            return null
        }
        if (second == null) {
            return first
        }
        if (first == null) {
            return second
        }
        return first * second
    }

    private fun parseMediumNumber(buffer: NumberBuffer): BigDecimal? {
        var sum = BigDecimal.ZERO
        var result = parseMediumPair(buffer)
        if (result == null) {
            return null
        }
        while (result != null) {
            sum = sum + result
            result = parseMediumPair(buffer)
        }
        return sum
    }

    private fun parseMediumPair(buffer: NumberBuffer): BigDecimal? {
        val first = parseBasicNumber(buffer)
        val second = parseMediumKanjiNumeral(buffer)
        if (first == null && second == null) {
            return null
        }
        if (second == null) {
            return first
        }
        if (first == null) {
            return second
        }
        return first * second
    }

    private fun parseBasicNumber(buffer: NumberBuffer): BigDecimal? {
        val builder = StringBuilder()
        var i = buffer.position()
        while (i < buffer.length()) {
            val c = buffer.charAt(i)
            when {
                isArabicNumeral(c) -> builder.append(arabicNumeralValue(c))
                isKanjiNumeral(c) -> builder.append(kanjiNumeralValue(c))
                isDecimalPoint(c) -> builder.append(".")
                isThousandSeparator(c) -> { /* skip */ }
                else -> break
            }
            i++
            buffer.advance()
        }
        if (builder.isEmpty()) {
            return null
        }
        return BigDecimal.parseString(builder.toString())
    }

    fun parseLargeKanjiNumeral(buffer: NumberBuffer): BigDecimal? {
        val i = buffer.position()
        if (i >= buffer.length()) {
            return null
        }
        val c = buffer.charAt(i)
        val power = exponents[c.code]
        if (power > 3) {
            buffer.advance()
            return BigDecimal.fromIntWithExponent(1, power.toLong())
        }
        return null
    }

    fun parseMediumKanjiNumeral(buffer: NumberBuffer): BigDecimal? {
        val i = buffer.position()
        if (i >= buffer.length()) {
            return null
        }
        val c = buffer.charAt(i)
        val power = exponents[c.code]
        if (power in 1..3) {
            buffer.advance()
            return BigDecimal.fromIntWithExponent(1, power.toLong())
        }
        return null
    }

    fun isNumeral(input: String): Boolean {
        for (i in input.indices) {
            if (!isNumeral(input[i])) {
                return false
            }
        }
        return true
    }

    fun isNumeral(c: Char): Boolean = isArabicNumeral(c) || isKanjiNumeral(c) || exponents[c.code] > 0

    fun isNumeralPunctuation(input: String): Boolean {
        for (i in input.indices) {
            if (!isNumeralPunctuation(input[i])) {
                return false
            }
        }
        return true
    }

    fun isNumeralPunctuation(c: Char): Boolean = isDecimalPoint(c) || isThousandSeparator(c)

    fun isArabicNumeral(c: Char): Boolean = isHalfWidthArabicNumeral(c) || isFullWidthArabicNumeral(c)

    private fun isHalfWidthArabicNumeral(c: Char): Boolean = c in '0'..'9'

    private fun isFullWidthArabicNumeral(c: Char): Boolean = c in '０'..'９'

    private fun arabicNumeralValue(c: Char): Int {
        val offset = if (isHalfWidthArabicNumeral(c)) '0'.code else '０'.code
        return c.code - offset
    }

    private fun isKanjiNumeral(c: Char): Boolean = numerals[c.code] != NO_NUMERAL

    private fun kanjiNumeralValue(c: Char): Int = numerals[c.code].code

    private fun isDecimalPoint(c: Char): Boolean = c == '.' || c == '．'

    private fun isThousandSeparator(c: Char): Boolean = c == ',' || c == '，'

    class NumberBuffer(val string: String) {
        private var position: Int = 0

        fun charAt(index: Int): Char = string[index]

        fun length(): Int = string.length

        fun advance() { position++ }

        fun position(): Int = position
    }

    companion object {
        private const val NO_NUMERAL: Char = Char.MAX_VALUE

        private val numerals: CharArray = CharArray(0x10000) { NO_NUMERAL }
        private val exponents: IntArray = IntArray(0x10000)

        init {
            numerals['〇'.code] = 0.toChar()
            numerals['一'.code] = 1.toChar()
            numerals['二'.code] = 2.toChar()
            numerals['三'.code] = 3.toChar()
            numerals['四'.code] = 4.toChar()
            numerals['五'.code] = 5.toChar()
            numerals['六'.code] = 6.toChar()
            numerals['七'.code] = 7.toChar()
            numerals['八'.code] = 8.toChar()
            numerals['九'.code] = 9.toChar()

            exponents['十'.code] = 1
            exponents['百'.code] = 2
            exponents['千'.code] = 3
            exponents['万'.code] = 4
            exponents['億'.code] = 8
            exponents['兆'.code] = 12
            exponents['京'.code] = 16
            exponents['垓'.code] = 20
        }
    }
}
