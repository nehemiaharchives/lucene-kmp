package org.gnit.lucenekmp.analysis.yo

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [YorubaNormalizer]. */
class TestYorubaNormalizer : BaseTokenStreamTestCase() {
    /** Test precomposed Yoruba orthography normalization. */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("Yorùbá", "Yoruba")
        check("fẹ́", "fe")
        check("ọmọ", "omo")
        check("ṣé", "se")
    }

    @Test
    @Throws(IOException::class)
    fun testCombiningMarks() {
        check("s\u0323e\u0301", "se")
        check("o\u0323mo\u0300", "omo")
        check("n\u0301kan", "nkan")
    }

    @Test
    @Throws(IOException::class)
    fun testPunctuation() {
        check("Yorùbá–ìkọwé", "Yoruba-ikowe")
        check("moʼn", "mo'n")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = YorubaNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, YorubaNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
