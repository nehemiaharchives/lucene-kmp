package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TestLengthFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testFilterWithPosIncr() {
        val stream: TokenStream =
            whitespaceMockTokenizer("short toolong evenmuchlongertext a ab toolong foo")
        val filter = LengthFilter(stream, 2, 6)
        assertTokenStreamContents(filter, arrayOf("short", "ab", "foo"), intArrayOf(1, 4, 2))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, LengthFilter(tokenizer, 0, 5))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }

    /** checking the validity of constructor arguments */
    @Test
    @Throws(Exception::class)
    fun testIllegalArguments() {
        assertFailsWith<IllegalArgumentException> {
            LengthFilter(whitespaceMockTokenizer("accept only valid arguments"), -4, -1)
        }
    }
}
