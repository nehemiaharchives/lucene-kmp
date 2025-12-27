package org.gnit.lucenekmp.analysis.te

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [TeluguStemmer]. */
class TestTeluguStemmer : BaseTokenStreamTestCase() {
    /** Test plural forms */
    @Test
    @Throws(IOException::class)
    fun testPlurals() {
        check("వస్తువులు", "వస్తువు")
        check("పన్నులు", "పన్ను")
    }

    /** Test some verb forms */
    @Test
    @Throws(IOException::class)
    fun testVerbs() {
        check("చేపిస్తున్నది", "చేపిస్తున్న")
        check("చేపిస్తున్నడు", "చేపిస్తున్న")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = TeluguStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, TeluguStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
