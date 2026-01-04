package org.gnit.lucenekmp.analysis.vi

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [VietnameseNormalizer]. */
class TestVietnameseNormalizer : BaseTokenStreamTestCase() {
    /** Test basic diacritic folding. */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("điện thoại", arrayOf("dien", "thoai"))
        check("Việt Nam", arrayOf("Viet", "Nam"))
        check("Cộng hòa", arrayOf("Cong", "hoa"))
    }

    private fun check(input: String, output: Array<String>) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = VietnameseNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, output)
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, VietnameseNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
