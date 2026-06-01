package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** HyphenatedWordsFilter test */
class TestHyphenatedWordsFilter : BaseTokenStreamTestCase() {
    @Test
    fun testHyphenatedWords() {
        val input = "ecologi-\r\ncal devel-\r\n\r\nop compre-\u0009hensive-hands-on and ecologi-\ncal"
        var ts: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (ts as Tokenizer).setReader(StringReader(input))
        ts = HyphenatedWordsFilter(ts)
        assertTokenStreamContents(
            ts,
            arrayOf("ecological", "develop", "comprehensive-hands-on", "and", "ecological")
        )
    }

    /** Test that HyphenatedWordsFilter behaves correctly with a final hyphen */
    @Test
    fun testHyphenAtEnd() {
        val input = "ecologi-\r\ncal devel-\r\n\r\nop compre-\u0009hensive-hands-on and ecology-"
        var ts: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (ts as Tokenizer).setReader(StringReader(input))
        ts = HyphenatedWordsFilter(ts)
        assertTokenStreamContents(
            ts,
            arrayOf("ecological", "develop", "comprehensive-hands-on", "and", "ecology-")
        )
    }

    @Test
    fun testOffsets() {
        val input = "abc- def geh 1234- 5678-"
        var ts: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (ts as Tokenizer).setReader(StringReader(input))
        ts = HyphenatedWordsFilter(ts)
        assertTokenStreamContents(
            ts,
            arrayOf("abcdef", "geh", "12345678-"),
            intArrayOf(0, 9, 13),
            intArrayOf(8, 12, 24)
        )
    }

    /** blast some random strings through the analyzer */
    @Test
    fun testRandomString() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, HyphenatedWordsFilter(tokenizer))
                }
            }

        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }

    @Test
    fun testEmptyTerm() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, HyphenatedWordsFilter(tokenizer))
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }
}
