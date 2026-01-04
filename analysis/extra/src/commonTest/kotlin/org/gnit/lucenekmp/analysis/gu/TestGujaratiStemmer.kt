package org.gnit.lucenekmp.analysis.gu

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [GujaratiStemmer]. */
class TestGujaratiStemmer : BaseTokenStreamTestCase() {
    /** Test noun inflections */
    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("ગુજરાતી", "ગુજરાતી")
        check("ગુજરાતીઓ", "ગુજરાતી")
        check("ગુજરાતનું", "ગુજરાત")
        check("ઘરમાં", "ઘર")
        check("ઘરમાંથી", "ઘર")
        check("છોકરીઓ", "છોકરી")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = GujaratiStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, GujaratiStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
