package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [KeepWordFilter] */
class TestKeepWordFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testStopAndGo() {
        val words: MutableSet<Any> = hashSetOf()
        words.add("aaa")
        words.add("bbb")

        val input = "xxx yyy aaa zzz BBB ccc ddd EEE"

        // Test Stopwords
        var stream: TokenStream = whitespaceMockTokenizer(input)
        stream = KeepWordFilter(stream, CharArraySet(words, true))
        assertTokenStreamContents(stream, arrayOf("aaa", "BBB"), intArrayOf(3, 2))

        // Now force case
        stream = whitespaceMockTokenizer(input)
        stream = KeepWordFilter(stream, CharArraySet(words, false))
        assertTokenStreamContents(stream, arrayOf("aaa"), intArrayOf(3))
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val words: MutableSet<Any> = hashSetOf()
        words.add("a")
        words.add("b")

        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val stream: TokenStream = KeepWordFilter(tokenizer, CharArraySet(words, true))
                return TokenStreamComponents(tokenizer, stream)
            }
        }

        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
