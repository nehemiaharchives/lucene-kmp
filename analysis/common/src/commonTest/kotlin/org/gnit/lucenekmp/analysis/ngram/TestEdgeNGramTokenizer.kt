package org.gnit.lucenekmp.analysis.ngram

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomStrings
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Tests [EdgeNGramTokenizer] for correctness. */
class TestEdgeNGramTokenizer : BaseTokenStreamTestCase() {
    private lateinit var input: StringReader

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        input = StringReader("abcde")
    }

    @Test
    fun testInvalidInput() {
        expectThrows(IllegalArgumentException::class) {
            EdgeNGramTokenizer(0, 0).setReader(input)
        }
    }

    @Test
    fun testInvalidInput2() {
        expectThrows(IllegalArgumentException::class) {
            EdgeNGramTokenizer(2, 1).setReader(input)
        }
    }

    @Test
    fun testInvalidInput3() {
        expectThrows(IllegalArgumentException::class) {
            EdgeNGramTokenizer(-1, 2).setReader(input)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFrontUnigram() {
        val tokenizer = EdgeNGramTokenizer(1, 1)
        tokenizer.setReader(input)
        assertTokenStreamContents(tokenizer, arrayOf("a"), intArrayOf(0), intArrayOf(1), 5)
    }

    @Test
    @Throws(Exception::class)
    fun testOversizedNgrams() {
        val tokenizer = EdgeNGramTokenizer(6, 6)
        tokenizer.setReader(input)
        assertTokenStreamContents(tokenizer, emptyArray(), intArrayOf(), intArrayOf(), 5)
    }

    @Test
    @Throws(Exception::class)
    fun testFrontRangeOfNgrams() {
        val tokenizer = EdgeNGramTokenizer(1, 3)
        tokenizer.setReader(input)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("a", "ab", "abc"),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 3),
            5,
        )
    }

    @Test
    @Throws(Exception::class)
    fun testReset() {
        val tokenizer = EdgeNGramTokenizer(1, 3)
        tokenizer.setReader(input)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("a", "ab", "abc"),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 3),
            5,
        )
        tokenizer.setReader(StringReader("abcde"))
        assertTokenStreamContents(
            tokenizer,
            arrayOf("a", "ab", "abc"),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 3),
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
                        val tokenizer: Tokenizer = EdgeNGramTokenizer(min, max)
                        return TokenStreamComponents(tokenizer, tokenizer)
                    }
                }
            checkRandomData(random(), a, 100 * RANDOM_MULTIPLIER, 20)
            checkRandomData(random(), a, 10 * RANDOM_MULTIPLIER, 8192)
            a.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testTokenizerPositions() {
        val tokenizer = EdgeNGramTokenizer(1, 3)
        tokenizer.setReader(StringReader("abcde"))
        assertTokenStreamContents(
            tokenizer,
            arrayOf("a", "ab", "abc"),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 3),
            null,
            intArrayOf(1, 1, 1),
            null,
            null,
            false,
        )
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
            TestNGramTokenizer.testNGrams(minGram, maxGram, s, nonTokenChars, true)
        }
    }
}
