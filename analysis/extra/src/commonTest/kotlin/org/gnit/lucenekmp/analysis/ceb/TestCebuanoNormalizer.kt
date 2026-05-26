package org.gnit.lucenekmp.analysis.ceb

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [CebuanoNormalizer]. */
class TestCebuanoNormalizer : BaseTokenStreamTestCase() {
    /** Test some basic normalization. */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("Cebuano", "Cebuano")
        check("Bisaya", "Bisaya")
    }

    @Test
    @Throws(IOException::class)
    fun testLatinVariants() {
        check("to’o", "to'o")
        check("lig‑on", "lig-on")
        check("básâ", "basa")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = CebuanoNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, CebuanoNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
