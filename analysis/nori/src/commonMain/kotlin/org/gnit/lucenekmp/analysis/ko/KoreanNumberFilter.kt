package org.gnit.lucenekmp.analysis.ko

import com.ionspin.kotlin.bignum.integer.BigInteger
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.jdkport.valueOf
import okio.IOException

/**
 * A [TokenFilter] that normalizes Korean numbers to regular Arabic decimal numbers in
 * half-width characters.
 */
class KoreanNumberFilter(input: TokenStream) : TokenFilter(input) {
    private val termAttr: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAttr: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val posIncrAttr: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLengthAttr: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)

    private var state: State? = null
    private var numeral = StringBuilder()
    private var fallThroughTokens = 0
    private var exhausted = false

    override fun incrementToken(): Boolean {
        if (state != null) {
            restoreState(state)
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

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        fallThroughTokens = 0
        numeral = StringBuilder()
        state = null
        exhausted = false
    }

    /**
     * Normalizes a Korean number.
     */
    fun normalizeNumber(number: String): String {
        return try {
            val normalizedNumber = parseNumber(NumberBuffer(number)) ?: return number
            normalizedNumber.stripTrailingZeros().toPlainString()
        } catch (_: NumberFormatException) {
            number
        } catch (_: ArithmeticException) {
            number
        }
    }

    private fun parseNumber(buffer: NumberBuffer): DecimalNumber? {
        var sum = DecimalNumber.ZERO
        var result: DecimalNumber? = parseLargePair(buffer) ?: return null
        while (result != null) {
            sum = sum.add(result)
            result = parseLargePair(buffer)
        }
        return sum
    }

    private fun parseLargePair(buffer: NumberBuffer): DecimalNumber? {
        val first = parseMediumNumber(buffer)
        val second = parseLargeHangulNumeral(buffer)
        if (first == null && second == null) return null
        if (second == null) return first
        if (first == null) return second
        return first.multiply(second)
    }

    private fun parseMediumNumber(buffer: NumberBuffer): DecimalNumber? {
        var sum = DecimalNumber.ZERO
        var result: DecimalNumber? = parseMediumPair(buffer) ?: return null
        while (result != null) {
            sum = sum.add(result)
            result = parseMediumPair(buffer)
        }
        return sum
    }

    private fun parseMediumPair(buffer: NumberBuffer): DecimalNumber? {
        val first = parseBasicNumber(buffer)
        val second = parseMediumHangulNumeral(buffer)
        if (first == null && second == null) return null
        if (second == null) return first
        if (first == null) return second
        return first.multiply(second)
    }

    private fun parseBasicNumber(buffer: NumberBuffer): DecimalNumber? {
        val builder = StringBuilder()
        var i = buffer.position()
        while (i < buffer.length()) {
            val c = buffer.charAt(i)
            when {
                isArabicNumeral(c) -> builder.append(arabicNumeralValue(c))
                isHangulNumeral(c) -> builder.append(hangulNumeralValue(c))
                isDecimalPoint(c) -> builder.append(".")
                isThousandSeparator(c) -> {
                    // skip
                }
                else -> break
            }
            i++
            buffer.advance()
        }
        if (builder.isEmpty()) return null
        return DecimalNumber.parse(builder.toString())
    }

    fun parseLargeHangulNumeral(buffer: NumberBuffer): DecimalNumber? {
        val i = buffer.position()
        if (i >= buffer.length()) return null
        val c = buffer.charAt(i)
        val power = exponents[c.code]
        return if (power > 3) {
            buffer.advance()
            DecimalNumber.tenPow(power)
        } else {
            null
        }
    }

    fun parseMediumHangulNumeral(buffer: NumberBuffer): DecimalNumber? {
        val i = buffer.position()
        if (i >= buffer.length()) return null
        val c = buffer.charAt(i)
        val power = exponents[c.code]
        return if (power in 1..3) {
            buffer.advance()
            DecimalNumber.tenPow(power)
        } else {
            null
        }
    }

    fun isNumeral(input: String): Boolean = input.all { isNumeral(it) }

    fun isNumeral(c: Char): Boolean = isArabicNumeral(c) || isHangulNumeral(c) || exponents[c.code] > 0

    fun isNumeralPunctuation(input: String): Boolean = input.all { isNumeralPunctuation(it) }

    fun isNumeralPunctuation(c: Char): Boolean = isDecimalPoint(c) || isThousandSeparator(c)

    fun isArabicNumeral(c: Char): Boolean = isHalfWidthArabicNumeral(c) || isFullWidthArabicNumeral(c)

    private fun isHalfWidthArabicNumeral(c: Char): Boolean = c in '0'..'9'

    private fun isFullWidthArabicNumeral(c: Char): Boolean = c in '０'..'９'

    private fun isHangulNumeral(c: Char): Boolean = numerals[c.code] != NO_NUMERAL

    private fun arabicNumeralValue(c: Char): Int {
        return if (isHalfWidthArabicNumeral(c)) {
            c.code - '0'.code
        } else {
            c.code - '０'.code
        }
    }

    private fun hangulNumeralValue(c: Char): Int = numerals[c.code]

    private fun isDecimalPoint(c: Char): Boolean = c == '.' || c == '．'

    private fun isThousandSeparator(c: Char): Boolean = c == ',' || c == '，'

    /** Buffer that holds a Korean number string and a position index used as a parsed-to marker */
    class NumberBuffer(private val string: String) {
        private var position: Int = 0

        fun charAt(index: Int): Char = string[index]

        fun length(): Int = string.length

        fun advance() {
            position++
        }

        fun position(): Int = position
    }

    class DecimalNumber(private val intVal: BigInteger, private val scale: Int) {
        companion object {
            val ZERO = DecimalNumber(BigInteger.ZERO, 0)

            fun parse(value: String): DecimalNumber {
                val dot = value.indexOf('.')
                return if (dot < 0) {
                    DecimalNumber(parseDigits(value), 0)
                } else {
                    val digits = value.replace(".", "")
                    val scale = value.length - dot - 1
                    DecimalNumber(parseDigits(digits), scale)
                }
            }

            private fun parseDigits(value: String): BigInteger {
                var result = BigInteger.ZERO
                val ten = BigInteger.valueOf(10)
                for (ch in value) {
                    val digit = ch.code - '0'.code
                    result = result * ten + BigInteger.valueOf(digit.toLong())
                }
                return result
            }

            fun tenPow(power: Int): DecimalNumber {
                var result = BigInteger.ONE
                repeat(power) { result *= BigInteger.valueOf(10) }
                return DecimalNumber(result, 0)
            }
        }

        fun add(other: DecimalNumber): DecimalNumber {
            val maxScale = maxOf(scale, other.scale)
            val thisScaled = intVal * tenPowInt(maxScale - scale)
            val otherScaled = other.intVal * tenPowInt(maxScale - other.scale)
            return DecimalNumber(thisScaled + otherScaled, maxScale)
        }

        fun multiply(other: DecimalNumber): DecimalNumber {
            return DecimalNumber(intVal * other.intVal, scale + other.scale)
        }

        fun stripTrailingZeros(): DecimalNumber {
            if (intVal == BigInteger.ZERO) return ZERO
            var value = intVal
            var newScale = scale
            val ten = BigInteger.valueOf(10)
            while (newScale > 0 && value.mod(ten) == BigInteger.ZERO) {
                value /= ten
                newScale--
            }
            return DecimalNumber(value, newScale)
        }

        fun toPlainString(): String {
            if (scale == 0) return intVal.toString()
            val digits = intVal.toString()
            val len = digits.length
            return if (scale >= len) {
                val zeros = "0".repeat(scale - len)
                "0.$zeros$digits"
            } else {
                val split = len - scale
                digits.take(split) + "." + digits.substring(split)
            }
        }

        private fun tenPowInt(power: Int): BigInteger {
            var result = BigInteger.ONE
            repeat(power) { result *= BigInteger.valueOf(10) }
            return result
        }

    }

    companion object {
        private const val NO_NUMERAL = Int.MAX_VALUE

        private val numerals = IntArray(0x10000) { NO_NUMERAL }
        private val exponents = IntArray(0x10000)

        init {
            numerals['영'.code] = 0
            numerals['일'.code] = 1
            numerals['이'.code] = 2
            numerals['삼'.code] = 3
            numerals['사'.code] = 4
            numerals['오'.code] = 5
            numerals['육'.code] = 6
            numerals['칠'.code] = 7
            numerals['팔'.code] = 8
            numerals['구'.code] = 9

            exponents['십'.code] = 1
            exponents['백'.code] = 2
            exponents['천'.code] = 3
            exponents['만'.code] = 4
            exponents['억'.code] = 8
            exponents['조'.code] = 12
            exponents['경'.code] = 16
            exponents['해'.code] = 20
        }
    }
}
