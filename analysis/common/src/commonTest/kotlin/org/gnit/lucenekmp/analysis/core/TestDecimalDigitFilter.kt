package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.SparseFixedBitSet
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Tests for [DecimalDigitFilter] */
class TestDecimalDigitFilter : BaseTokenStreamTestCase() {
    private lateinit var tokenized: Analyzer
    private lateinit var keyword: Analyzer

    @BeforeTest
    fun setUp() {
        tokenized =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, DecimalDigitFilter(tokenizer))
                }
            }
        keyword =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, DecimalDigitFilter(tokenizer))
                }
            }
    }

    @AfterTest
    fun tearDown() {
        tokenized.close()
        keyword.close()
    }

    /** test that digits are normalized */
    @Test
    @Throws(Exception::class)
    fun testSimple() {
        checkOneTerm(tokenized, "١٢٣٤", "1234")
    }

    /** test that double struck digits are normalized */
    @Test
    @Throws(Exception::class)
    fun testDoubleStruck() {
        // MATHEMATICAL DOUBLE-STRUCK DIGIT ... 1, 9, 8, 4
        val input = "𝟙 𝟡 𝟠 𝟜"
        val expected = "1 9 8 4"
        checkOneTerm(keyword, input, expected)
        checkOneTerm(keyword, input.replace("\\s".toRegex(), ""), expected.replace("\\s".toRegex(), ""))
    }

    /** test sequences of digits mixed with other random simple string data */
    @Test
    @Throws(Exception::class)
    fun testRandomSequences() {

        // test numIters random strings containing a sequence of numDigits codepoints
        val numIters = atLeast(5)
        for (iter in 0..<numIters) {
            val numDigits = atLeast(20)
            val expected = StringBuilder()
            val actual = StringBuilder()
            for (digitCounter in 0..<numDigits) {

                // increased odds of 0 length random string prefix
                val prefix = if (random().nextBoolean()) "" else TestUtil.randomSimpleString(random())
                expected.append(prefix)
                actual.append(prefix)

                val codepoint = getRandomDecimalDigit(random())

                val value = Character.getNumericValue(codepoint)
                assert(value in 0..9)
                expected.append(value.toString())
                appendCodePoint(actual, codepoint)
            }
            // occasional suffix, increased odds of 0 length random string
            val suffix = if (random().nextBoolean()) "" else TestUtil.randomSimpleString(random())
            expected.append(suffix)
            actual.append(suffix)

            checkOneTerm(keyword, actual.toString(), expected.toString())
        }
    }

    /** test each individual digit in different locations of strings. */
    @Test
    @Throws(Exception::class)
    fun testRandom() {
        var numCodePointsChecked = 0 // sanity check
        var codepoint = DECIMAL_DIGIT_CODEPOINTS.nextSetBit(0)
        while (codepoint != DocIdSetIterator.NO_MORE_DOCS) {
            assert(Character.isDigit(codepoint))

            // add some a-z before/after the string
            val prefix = TestUtil.randomSimpleString(random())
            val suffix = TestUtil.randomSimpleString(random())

            val expected = StringBuilder()
            expected.append(prefix)
            val value = Character.getNumericValue(codepoint)
            assert(value in 0..9)
            expected.append(value.toString())
            expected.append(suffix)

            val actual = StringBuilder()
            actual.append(prefix)
            appendCodePoint(actual, codepoint)
            actual.append(suffix)

            checkOneTerm(keyword, actual.toString(), expected.toString())

            numCodePointsChecked++
            codepoint = DECIMAL_DIGIT_CODEPOINTS.nextSetBit(codepoint + 1)
        }
        assert(DECIMAL_DIGIT_CODEPOINTS.cardinality() == numCodePointsChecked)
    }

    /** check the filter is a no-op for the empty string term */
    @Test
    @Throws(Exception::class)
    fun testEmptyTerm() {
        checkOneTerm(keyword, "", "")
    }

    /** blast some random strings through the filter */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), tokenized, 200 * RANDOM_MULTIPLIER)
    }

    companion object {
        private val DECIMAL_DIGIT_CODEPOINTS: SparseFixedBitSet = init_DECIMAL_DIGIT_CODEPOINTS()

        private fun init_DECIMAL_DIGIT_CODEPOINTS(): SparseFixedBitSet {
            val decimalDigitCodepoints = SparseFixedBitSet(Character.MAX_CODE_POINT)
            for (codepoint in Character.MIN_CODE_POINT..<Character.MAX_CODE_POINT) {
                if (Character.isDigit(codepoint)) {
                    decimalDigitCodepoints.set(codepoint)
                }
            }
            assert(0 < decimalDigitCodepoints.cardinality())
            return decimalDigitCodepoints
        }

        /** returns a psuedo-random codepoint which is a Decimal Digit */
        fun getRandomDecimalDigit(r: Random): Int {
            val aprox = TestUtil.nextInt(r, 0, DECIMAL_DIGIT_CODEPOINTS.length - 1)

            if (DECIMAL_DIGIT_CODEPOINTS.get(aprox)) { // lucky guess
                assert(Character.isDigit(aprox))
                return aprox
            }

            // seek up and down for closest set bit
            val lower = DECIMAL_DIGIT_CODEPOINTS.prevSetBit(aprox)
            val higher = DECIMAL_DIGIT_CODEPOINTS.nextSetBit(aprox)

            // sanity check edge cases
            if (lower < 0) {
                assert(higher != DocIdSetIterator.NO_MORE_DOCS)
                assert(Character.isDigit(higher))
                return higher
            }
            if (higher == DocIdSetIterator.NO_MORE_DOCS) {
                assert(0 <= lower)
                assert(Character.isDigit(lower))
                return lower
            }

            // which is closer?
            val cmp = (aprox - lower).compareTo(higher - aprox)

            if (0 == cmp) {
                // dead even, flip a coin
                val result = if (r.nextBoolean()) lower else higher
                assert(Character.isDigit(result))
                return result
            }

            val result = if (cmp < 0) lower else higher
            assert(Character.isDigit(result))
            return result
        }

        private fun appendCodePoint(builder: StringBuilder, codePoint: Int) {
            val chars = CharArray(2)
            val charCount = Character.toChars(codePoint, chars, 0)
            builder.appendRange(chars, 0, charCount)
        }
    }
}

