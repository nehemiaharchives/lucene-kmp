package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.test.Test

/** */
class TestTrimFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testTrim() {
        var ts: TokenStream = CannedTokenStream(
            Token(" a ", 1, 5),
            Token("b   ", 6, 10),
            Token("cCc", 11, 15),
            Token("   ", 16, 20),
            Token("", 21, 21)
        )
        ts = TrimFilter(ts)

        assertTokenStreamContents(ts, arrayOf("a", "b", "cCc", "", ""))
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.KEYWORD, false)
                return TokenStreamComponents(tokenizer, TrimFilter(tokenizer))
            }
        }
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, TrimFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
