package org.gnit.lucenekmp.analysis.pa

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [PunjabiNormalizer]. */
class TestPunjabiNormalizer : BaseTokenStreamTestCase() {
    /** Test some basic normalization. */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("ਪੰਜਾਬੀ", "ਪੰਜਾਬੀ")
        check("ਪੰਜਾਬੀਆਂ", "ਪੰਜਾਬੀਆਂ")
    }

    @Test
    @Throws(IOException::class)
    fun testPunctuationNormalization() {
        check("ਪੰਜਾਬੀ੤", "ਪੰਜਾਬੀ।")
        check("ਪੰਜਾਬੀ੥", "ਪੰਜਾਬੀ॥")
    }

    @Test
    @Throws(IOException::class)
    fun testMarkNormalization() {
        check("ਚਁਦ", "ਚਂਦ")
        check("ਕ੍", "ਕ")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = PunjabiNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, PunjabiNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
