package org.gnit.lucenekmp.analysis.ngram

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.codePointAt
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomStrings
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests [NGramTokenizer] for correctness. */
class TestNGramTokenizer : BaseTokenStreamTestCase() {
    private lateinit var input: StringReader

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        input = StringReader("abcde")
    }

    @Test
    fun testInvalidInput() {
        expectThrows(IllegalArgumentException::class) {
            NGramTokenizer(2, 1)
        }
    }

    @Test
    fun testInvalidInput2() {
        expectThrows(IllegalArgumentException::class) {
            val tok = NGramTokenizer(0, 1)
            tok.setReader(input)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testUnigrams() {
        val tokenizer = NGramTokenizer(1, 1)
        tokenizer.setReader(input)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("a", "b", "c", "d", "e"),
            intArrayOf(0, 1, 2, 3, 4),
            intArrayOf(1, 2, 3, 4, 5),
            5,
        )
    }

    @Test
    @Throws(Exception::class)
    fun testBigrams() {
        val tokenizer = NGramTokenizer(2, 2)
        tokenizer.setReader(input)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("ab", "bc", "cd", "de"),
            intArrayOf(0, 1, 2, 3),
            intArrayOf(2, 3, 4, 5),
            5,
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNgrams() {
        val tokenizer = NGramTokenizer(1, 3)
        tokenizer.setReader(input)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("a", "ab", "abc", "b", "bc", "bcd", "c", "cd", "cde", "d", "de", "e"),
            intArrayOf(0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 4),
            intArrayOf(1, 2, 3, 2, 3, 4, 3, 4, 5, 4, 5, 5),
            null,
            null,
            null,
            5,
            false,
        )
    }

    @Test
    @Throws(Exception::class)
    fun testOversizedNgrams() {
        val tokenizer = NGramTokenizer(6, 7)
        tokenizer.setReader(input)
        assertTokenStreamContents(tokenizer, emptyArray(), intArrayOf(), intArrayOf(), 5)
    }

    @Test
    @Throws(Exception::class)
    fun testReset() {
        val tokenizer = NGramTokenizer(1, 1)
        tokenizer.setReader(input)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("a", "b", "c", "d", "e"),
            intArrayOf(0, 1, 2, 3, 4),
            intArrayOf(1, 2, 3, 4, 5),
            5,
        )
        tokenizer.setReader(StringReader("abcde"))
        assertTokenStreamContents(
            tokenizer,
            arrayOf("a", "b", "c", "d", "e"),
            intArrayOf(0, 1, 2, 3, 4),
            intArrayOf(1, 2, 3, 4, 5),
            5,
        )
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val numIters = if (TEST_NIGHTLY) 10 else 1
        for (i in 0 until numIters) {
            val min = TestUtil.nextInt(random(), 2, 10)
            val max = TestUtil.nextInt(random(), min, 20)
            val a =
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer: Tokenizer = NGramTokenizer(min, max)
                        return TokenStreamComponents(tokenizer, tokenizer)
                    }
                }
            checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER, 20)
            checkRandomData(random(), a, 10 * RANDOM_MULTIPLIER, 1027)
            a.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testLargeInput() {
        val minGram = TestUtil.nextInt(random(), 1, 100)
        val maxGram = TestUtil.nextInt(random(), minGram, 100)
        testNGrams(minGram, maxGram, TestUtil.nextInt(random(), 3 * 1024, 4 * 1024), "")
    }

    @Test
    @Throws(IOException::class)
    fun testLargeMaxGram() {
        val minGram = TestUtil.nextInt(random(), 1290, 1300)
        val maxGram = TestUtil.nextInt(random(), minGram, 1300)
        testNGrams(minGram, maxGram, TestUtil.nextInt(random(), 3 * 1024, 4 * 1024), "")
    }

    @Test
    @Throws(IOException::class)
    fun testPreTokenization() {
        val minGram = TestUtil.nextInt(random(), 1, 100)
        val maxGram = TestUtil.nextInt(random(), minGram, 100)
        testNGrams(minGram, maxGram, TestUtil.nextInt(random(), 0, 4 * 1024), "a")
    }

    @Test
    @Throws(IOException::class)
    fun testHeavyPreTokenization() {
        val minGram = TestUtil.nextInt(random(), 1, 100)
        val maxGram = TestUtil.nextInt(random(), minGram, 100)
        testNGrams(minGram, maxGram, TestUtil.nextInt(random(), 0, 4 * 1024), "abcdef")
    }

    @Test
    @Throws(IOException::class)
    fun testFewTokenChars() {
        val chrs = CharArray(TestUtil.nextInt(random(), 4000, 5000))
        chrs.fill(' ')
        for (i in chrs.indices) {
            if (random().nextFloat() < 0.1f) {
                chrs[i] = 'a'
            }
        }
        val minGram = TestUtil.nextInt(random(), 1, 2)
        val maxGram = TestUtil.nextInt(random(), minGram, 2)
        testNGrams(minGram, maxGram, String.fromCharArray(chrs), " ")
    }

    @Test
    @Throws(IOException::class)
    fun testFullUTF8Range() {
        val minGram = TestUtil.nextInt(random(), 1, 100)
        val maxGram = TestUtil.nextInt(random(), minGram, 100)
        val s = TestUtil.randomUnicodeString(random(), 4 * 1024)
        testNGrams(minGram, maxGram, s, "")
        testNGrams(minGram, maxGram, s, "abcdef")
    }

    companion object {
        @Throws(IOException::class)
        private fun testNGrams(minGram: Int, maxGram: Int, length: Int, nonTokenChars: String) {
            val s = RandomStrings.randomAsciiLettersOfLengthBetween(LuceneTestCase.random(), length, length)
            testNGrams(minGram, maxGram, s, nonTokenChars)
        }

        @Throws(IOException::class)
        private fun testNGrams(minGram: Int, maxGram: Int, s: String, nonTokenChars: String) {
            testNGrams(minGram, maxGram, s, nonTokenChars, false)
        }

        internal fun toCodePoints(s: CharSequence): IntArray {
            val chars = s.toString().toCharArray()
            val codePoints = IntArray(Character.codePointCount(chars, 0, chars.size))
            var i = 0
            var j = 0
            while (i < s.length) {
                codePoints[j] = Character.codePointAt(s, i)
                i += Character.charCount(codePoints[j])
                ++j
            }
            return codePoints
        }

        internal fun isTokenChar(nonTokenChars: String, codePoint: Int): Boolean {
            var i = 0
            while (i < nonTokenChars.length) {
                val cp = nonTokenChars.codePointAt(i)
                if (cp == codePoint) {
                    return false
                }
                i += Character.charCount(cp)
            }
            return true
        }

        @Throws(IOException::class)
        internal fun testNGrams(
            minGram: Int,
            maxGram: Int,
            s: String,
            nonTokenChars: String,
            edgesOnly: Boolean,
        ) {
            val codePoints = toCodePoints(s)
            val offsets = IntArray(codePoints.size + 1)
            for (i in codePoints.indices) {
                offsets[i + 1] = offsets[i] + Character.charCount(codePoints[i])
            }
            val grams =
                object : NGramTokenizer(minGram, maxGram, edgesOnly) {
                    override fun isTokenChar(chr: Int): Boolean {
                        return isTokenChar(nonTokenChars, chr)
                    }
                }
            grams.setReader(StringReader(s))
            val termAtt = grams.addAttribute(CharTermAttribute::class)
            val posIncAtt = grams.addAttribute(PositionIncrementAttribute::class)
            val posLenAtt = grams.addAttribute(PositionLengthAttribute::class)
            val offsetAtt = grams.addAttribute(OffsetAttribute::class)
            grams.reset()
            for (start in codePoints.indices) {
                nextGram@ for (end in (start + minGram)..minOf(start + maxGram, codePoints.size)) {
                    if (edgesOnly && start > 0 && isTokenChar(nonTokenChars, codePoints[start - 1])) {
                        continue@nextGram
                    }
                    for (j in start until end) {
                        if (!isTokenChar(nonTokenChars, codePoints[j])) {
                            continue@nextGram
                        }
                    }
                    assertTrue(grams.incrementToken())
                    assertContentEquals(ArrayUtil.copyOfSubArray(codePoints, start, end), toCodePoints(termAtt))
                    assertEquals(1, posIncAtt.getPositionIncrement())
                    assertEquals(1, posLenAtt.positionLength)
                    assertEquals(offsets[start], offsetAtt.startOffset())
                    assertEquals(offsets[end], offsetAtt.endOffset())
                }
            }
            assertFalse(grams.incrementToken())
            grams.end()
            assertEquals(s.length, offsetAtt.startOffset())
            assertEquals(s.length, offsetAtt.endOffset())
        }
    }
}
