package org.gnit.lucenekmp.analysis.ngram

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.core.LetterTokenizer
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizer
import org.gnit.lucenekmp.analysis.shingle.ShingleFilter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.codePointCount
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests [EdgeNGramTokenFilter] for correctness. */
class TestEdgeNGramTokenFilter : BaseTokenStreamTestCase() {
    private lateinit var input: TokenStream

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        input = whitespaceMockTokenizer("abcde")
    }

    @Test
    fun testInvalidInput() {
        expectThrows(IllegalArgumentException::class) {
            EdgeNGramTokenFilter(input, 0, 0, false)
        }
    }

    @Test
    fun testInvalidInput2() {
        expectThrows(IllegalArgumentException::class) {
            EdgeNGramTokenFilter(input, 2, 1, false)
        }
    }

    @Test
    fun testInvalidInput3() {
        expectThrows(IllegalArgumentException::class) {
            EdgeNGramTokenFilter(input, -1, 2, false)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFrontUnigram() {
        val tokenizer = EdgeNGramTokenFilter(input, 1, 1, false)
        assertTokenStreamContents(tokenizer, arrayOf("a"), intArrayOf(0), intArrayOf(5))
    }

    @Test
    @Throws(Exception::class)
    fun testOversizedNgrams() {
        val tokenizer = EdgeNGramTokenFilter(input, 6, 6, false)
        assertTokenStreamContents(tokenizer, emptyArray(), intArrayOf(), intArrayOf())
    }

    @Test
    @Throws(Exception::class)
    fun testOversizedNgramsPreserveOriginal() {
        val tokenizer = EdgeNGramTokenFilter(input, 6, 6, true)
        assertTokenStreamContents(tokenizer, arrayOf("abcde"), intArrayOf(0), intArrayOf(5))
    }

    @Test
    @Throws(Exception::class)
    fun testPreserveOriginal() {
        val inputString = "a bcd efghi jk"

        run {
            val ts = whitespaceMockTokenizer(inputString)
            val filter = EdgeNGramTokenFilter(ts, 2, 3, false)
            assertTokenStreamContents(
                filter,
                arrayOf("bc", "bcd", "ef", "efg", "jk"),
                intArrayOf(2, 2, 6, 6, 12),
                intArrayOf(5, 5, 11, 11, 14),
                intArrayOf(2, 0, 1, 0, 1),
            )
        }

        run {
            val ts = whitespaceMockTokenizer(inputString)
            val filter = EdgeNGramTokenFilter(ts, 2, 3, true)
            assertTokenStreamContents(
                filter,
                arrayOf("a", "bc", "bcd", "ef", "efg", "efghi", "jk"),
                intArrayOf(0, 2, 2, 6, 6, 6, 12),
                intArrayOf(1, 5, 5, 11, 11, 11, 14),
                intArrayOf(1, 1, 0, 1, 0, 0, 1),
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFrontRangeOfNgrams() {
        val tokenizer = EdgeNGramTokenFilter(input, 1, 3, false)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("a", "ab", "abc"),
            intArrayOf(0, 0, 0),
            intArrayOf(5, 5, 5),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testFilterPositions() {
        val ts = whitespaceMockTokenizer("abcde vwxyz")
        val tokenizer = EdgeNGramTokenFilter(ts, 1, 3, false)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("a", "ab", "abc", "v", "vw", "vwx"),
            intArrayOf(0, 0, 0, 6, 6, 6),
            intArrayOf(5, 5, 5, 11, 11, 11),
        )
    }

    private class PositionFilter(input: TokenStream) : TokenFilter(input) {
        private val posIncrAtt = addAttribute(PositionIncrementAttribute::class)
        private var started = false

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (input.incrementToken()) {
                if (started) {
                    posIncrAtt.setPositionIncrement(0)
                } else {
                    started = true
                }
                return true
            } else {
                return false
            }
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            started = false
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFirstTokenPositionIncrement() {
        var ts: TokenStream = whitespaceMockTokenizer("a abc")
        ts = PositionFilter(ts)
        val filter = EdgeNGramTokenFilter(ts, 2, 3, false)
        assertTokenStreamContents(
            filter,
            arrayOf("ab", "abc"),
            intArrayOf(2, 2),
            intArrayOf(5, 5),
            intArrayOf(1, 0),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testSmallTokenInStream() {
        input = whitespaceMockTokenizer("abc de fgh")
        val tokenizer = EdgeNGramTokenFilter(input, 3, 3, false)
        assertTokenStreamContents(tokenizer, arrayOf("abc", "fgh"), intArrayOf(0, 7), intArrayOf(3, 10))
    }

    @Test
    @Throws(Exception::class)
    fun testReset() {
        val tokenizer = WhitespaceTokenizer()
        tokenizer.setReader(StringReader("abcde"))
        val filter = EdgeNGramTokenFilter(tokenizer, 1, 3, false)
        assertTokenStreamContents(filter, arrayOf("a", "ab", "abc"), intArrayOf(0, 0, 0), intArrayOf(5, 5, 5))
        tokenizer.setReader(StringReader("abcde"))
        assertTokenStreamContents(filter, arrayOf("a", "ab", "abc"), intArrayOf(0, 0, 0), intArrayOf(5, 5, 5))
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        for (i in 0 until 10) {
            val min = TestUtil.nextInt(random(), 2, 10)
            val max = TestUtil.nextInt(random(), min, 20)
            val preserveOriginal = TestUtil.nextInt(random(), 0, 1) % 2 == 0
            val a =
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                        return TokenStreamComponents(tokenizer, EdgeNGramTokenFilter(tokenizer, min, max, preserveOriginal))
                    }
                }
            checkRandomData(random(), a, 10 * RANDOM_MULTIPLIER)
            a.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyTerm() {
        val random: Random = random()
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, EdgeNGramTokenFilter(tokenizer, 2, 15, false))
                }
            }
        checkAnalysisConsistency(random, a, random.nextBoolean(), "")
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testGraphs() {
        var tk: TokenStream = LetterTokenizer()
        (tk as Tokenizer).setReader(StringReader("abc d efgh ij klmno p q"))
        tk = ShingleFilter(tk)
        tk = EdgeNGramTokenFilter(tk, 7, 10, false)
        assertTokenStreamContents(
            tk,
            arrayOf("efgh ij", "ij klmn", "ij klmno", "klmno p"),
            intArrayOf(6, 11, 11, 14),
            intArrayOf(13, 19, 19, 21),
            intArrayOf(3, 1, 0, 1),
            intArrayOf(2, 2, 2, 2),
            23,
        )
    }

    @Test
    @Throws(IOException::class)
    fun testSupplementaryCharacters() {
        for (i in 0 until 20) {
            val s = TestUtil.randomUnicodeString(random(), 10)
            val codePointCount = s.codePointCount(0, s.length)
            val chars = s.toCharArray()
            val minGram = TestUtil.nextInt(random(), 1, 3)
            val maxGram = TestUtil.nextInt(random(), minGram, 10)
            val preserveOriginal = TestUtil.nextInt(random(), 0, 1) % 2 == 0

            var tk: TokenStream = KeywordTokenizer()
            (tk as Tokenizer).setReader(StringReader(s))
            tk = EdgeNGramTokenFilter(tk, minGram, maxGram, preserveOriginal)
            val termAtt = tk.addAttribute(CharTermAttribute::class)
            val offsetAtt = tk.addAttribute(OffsetAttribute::class)
            tk.reset()

            if (codePointCount < minGram && preserveOriginal) {
                assertTrue(tk.incrementToken())
                assertEquals(0, offsetAtt.startOffset())
                assertEquals(s.length, offsetAtt.endOffset())
                assertEquals(s, termAtt.toString())
            }

            for (j in minGram..minOf(codePointCount, maxGram)) {
                assertTrue(tk.incrementToken())
                assertEquals(0, offsetAtt.startOffset())
                assertEquals(s.length, offsetAtt.endOffset())
                val end = Character.offsetByCodePoints(chars, 0, chars.size, 0, j)
                assertEquals(s.substring(0, end), termAtt.toString())
            }

            if (codePointCount > maxGram && preserveOriginal) {
                assertTrue(tk.incrementToken())
                assertEquals(0, offsetAtt.startOffset())
                assertEquals(s.length, offsetAtt.endOffset())
                assertEquals(s, termAtt.toString())
            }

            assertFalse(tk.incrementToken())
            tk.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testEndPositionIncrement() {
        val source = whitespaceMockTokenizer("seventeen one two three four")
        val input = EdgeNGramTokenFilter(source, 8, 8, false)
        val posIncAtt = input.addAttribute(PositionIncrementAttribute::class)
        input.reset()
        while (input.incrementToken()) {
        }
        input.end()
        assertEquals(4, posIncAtt.getPositionIncrement())
    }
}
