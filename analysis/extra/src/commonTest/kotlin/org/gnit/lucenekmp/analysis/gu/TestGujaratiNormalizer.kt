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

/** Test [GujaratiNormalizer]. */
class TestGujaratiNormalizer : BaseTokenStreamTestCase() {
    /** Test some basic normalization. */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("ગુજરાતી", "ગુજરાતી")
        check("ગુજરાતીઓ", "ગુજરાતીઓ")
    }

    @Test
    @Throws(IOException::class)
    fun testPunctuationNormalization() {
        check("ભગવાન:", "ભગવાનઃ")
        check("અહીં૤", "અહીં।")
        check("અહીં૥", "અહીં॥")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = GujaratiNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, GujaratiNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
