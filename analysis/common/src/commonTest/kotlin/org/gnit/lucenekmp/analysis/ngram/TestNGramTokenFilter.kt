package org.gnit.lucenekmp.analysis.ngram

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.ASCIIFoldingFilter
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

/** Tests [NGramTokenFilter] for correctness. */
class TestNGramTokenFilter : BaseTokenStreamTestCase() {
    private lateinit var input: TokenStream

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        input = whitespaceMockTokenizer("abcde")
    }

    @Test
    fun testInvalidInput() {
        expectThrows(IllegalArgumentException::class) {
            NGramTokenFilter(input, 2, 1, false)
        }
    }

    @Test
    fun testInvalidInput2() {
        expectThrows(IllegalArgumentException::class) {
            NGramTokenFilter(input, 0, 1, false)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testUnigrams() {
        val filter = NGramTokenFilter(input, 1, 1, false)
        assertTokenStreamContents(
            filter,
            arrayOf("a", "b", "c", "d", "e"),
            intArrayOf(0, 0, 0, 0, 0),
            intArrayOf(5, 5, 5, 5, 5),
            intArrayOf(1, 0, 0, 0, 0),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testBigrams() {
        val filter = NGramTokenFilter(input, 2, 2, false)
        assertTokenStreamContents(
            filter,
            arrayOf("ab", "bc", "cd", "de"),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(5, 5, 5, 5),
            intArrayOf(1, 0, 0, 0),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNgrams() {
        val filter = NGramTokenFilter(input, 1, 3, false)
        assertTokenStreamContents(
            filter,
            arrayOf("a", "ab", "abc", "b", "bc", "bcd", "c", "cd", "cde", "d", "de", "e"),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5),
            null,
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            null,
            null,
            false,
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNgramsNoIncrement() {
        val filter = NGramTokenFilter(input, 1, 3, false)
        assertTokenStreamContents(
            filter,
            arrayOf("a", "ab", "abc", "b", "bc", "bcd", "c", "cd", "cde", "d", "de", "e"),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5),
            null,
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            null,
            null,
            false,
        )
    }

    @Test
    @Throws(Exception::class)
    fun testOversizedNgrams() {
        val filter = NGramTokenFilter(input, 6, 7, false)
        assertTokenStreamContents(filter, emptyArray(), intArrayOf(), intArrayOf())
    }

    @Test
    @Throws(Exception::class)
    fun testOversizedNgramsPreserveOriginal() {
        val tokenizer = NGramTokenFilter(input, 6, 6, true)
        assertTokenStreamContents(tokenizer, arrayOf("abcde"), intArrayOf(0), intArrayOf(5))
    }

    @Test
    @Throws(Exception::class)
    fun testSmallTokenInStream() {
        input = whitespaceMockTokenizer("abc de fgh")
        val tokenizer = NGramTokenFilter(input, 3, 3, false)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("abc", "fgh"),
            intArrayOf(0, 7),
            intArrayOf(3, 10),
            intArrayOf(1, 2),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testSmallTokenInStreamPreserveOriginal() {
        input = whitespaceMockTokenizer("abc de fgh")
        val tokenizer = NGramTokenFilter(input, 3, 3, true)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("abc", "de", "fgh"),
            intArrayOf(0, 4, 7),
            intArrayOf(3, 6, 10),
            intArrayOf(1, 1, 1),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testReset() {
        val tokenizer = WhitespaceTokenizer()
        tokenizer.setReader(StringReader("abcde"))
        val filter = NGramTokenFilter(tokenizer, 1, 1, false)
        assertTokenStreamContents(
            filter,
            arrayOf("a", "b", "c", "d", "e"),
            intArrayOf(0, 0, 0, 0, 0),
            intArrayOf(5, 5, 5, 5, 5),
            intArrayOf(1, 0, 0, 0, 0),
        )
        tokenizer.setReader(StringReader("abcde"))
        assertTokenStreamContents(
            filter,
            arrayOf("a", "b", "c", "d", "e"),
            intArrayOf(0, 0, 0, 0, 0),
            intArrayOf(5, 5, 5, 5, 5),
            intArrayOf(1, 0, 0, 0, 0),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testKeepShortTermKeepLongTerm() {
        val inputString = "a bcd efghi jk"

        run {
            val ts = whitespaceMockTokenizer(inputString)
            val filter = NGramTokenFilter(ts, 2, 3, false)
            assertTokenStreamContents(
                filter,
                arrayOf("bc", "bcd", "cd", "ef", "efg", "fg", "fgh", "gh", "ghi", "hi", "jk"),
                intArrayOf(2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 12),
                intArrayOf(5, 5, 5, 11, 11, 11, 11, 11, 11, 11, 14),
                intArrayOf(2, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1),
            )
        }

        run {
            val ts = whitespaceMockTokenizer(inputString)
            val filter = NGramTokenFilter(ts, 2, 3, true)
            assertTokenStreamContents(
                filter,
                arrayOf("a", "bc", "bcd", "cd", "ef", "efg", "fg", "fgh", "gh", "ghi", "hi", "efghi", "jk"),
                intArrayOf(0, 2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 6, 12),
                intArrayOf(1, 5, 5, 5, 11, 11, 11, 11, 11, 11, 11, 11, 14),
                intArrayOf(1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1),
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidOffsets() {
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    var filters: TokenFilter = ASCIIFoldingFilter(tokenizer)
                    filters = NGramTokenFilter(filters, 2, 2, false)
                    return TokenStreamComponents(tokenizer, filters)
                }
            }
        assertAnalyzesTo(
            analyzer,
            "mosfellsbær",
            arrayOf("mo", "os", "sf", "fe", "el", "ll", "ls", "sb", "ba", "ae", "er"),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEndPositionIncrement() {
        val source = whitespaceMockTokenizer("seventeen one two three four")
        val input = NGramTokenFilter(source, 8, 8, false)
        val posIncAtt = input.addAttribute(PositionIncrementAttribute::class)
        input.reset()
        while (input.incrementToken()) {
        }
        input.end()
        assertEquals(4, posIncAtt.getPositionIncrement())
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
                        return TokenStreamComponents(tokenizer, NGramTokenFilter(tokenizer, min, max, preserveOriginal))
                    }
                }
            checkRandomData(random(), a, 10 * RANDOM_MULTIPLIER, 20)
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
                    return TokenStreamComponents(tokenizer, NGramTokenFilter(tokenizer, 2, 15, false))
                }
            }
        checkAnalysisConsistency(random, a, random.nextBoolean(), "")
        a.close()
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
            tk = NGramTokenFilter(tk, minGram, maxGram, preserveOriginal)
            val termAtt = tk.addAttribute(CharTermAttribute::class)
            val offsetAtt = tk.addAttribute(OffsetAttribute::class)
            tk.reset()

            if (codePointCount < minGram && preserveOriginal) {
                assertTrue(tk.incrementToken())
                assertEquals(0, offsetAtt.startOffset())
                assertEquals(s.length, offsetAtt.endOffset())
                assertEquals(s, termAtt.toString())
            }

            for (start in 0 until codePointCount) {
                for (end in (start + minGram)..minOf(codePointCount, start + maxGram)) {
                    assertTrue(tk.incrementToken())
                    assertEquals(0, offsetAtt.startOffset())
                    assertEquals(s.length, offsetAtt.endOffset())
                    val startIndex = Character.offsetByCodePoints(chars, 0, chars.size, 0, start)
                    val endIndex = Character.offsetByCodePoints(chars, 0, chars.size, 0, end)
                    assertEquals(s.substring(startIndex, endIndex), termAtt.toString())
                }
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
}
