package org.gnit.lucenekmp.analysis.`in`

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test IndicNormalizer. */
class TestIndicNormalizer : BaseTokenStreamTestCase() {
    /** Test some basic normalization */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("अाॅअाॅ", "ऑऑ")
        check("अाॆअाॆ", "ऒऒ")
        check("अाेअाे", "ओओ")
        check("अाैअाै", "औऔ")
        check("अाअा", "आआ")
        check("अाैर", "और")
        // khanda-ta
        check("ত্‍", "ৎ")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = IndicNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, IndicNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
