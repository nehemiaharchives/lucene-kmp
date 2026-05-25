package org.gnit.lucenekmp.analysis.sw

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [SwahiliNormalizer]. */
class TestSwahiliNormalizer : BaseTokenStreamTestCase() {
    /** Test some basic normalization. */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("Swahili", "Swahili")
        check("Kiswahili", "Kiswahili")
    }

    @Test
    @Throws(IOException::class)
    fun testPunctuation() {
        check("ng’ombe", "ng'ombe")
        check("Dar–es–Salaam", "Dar-es-Salaam")
    }

    @Test
    @Throws(IOException::class)
    fun testDiacritics() {
        check("Kiswahíli", "Kiswahili")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = SwahiliNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, SwahiliNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
