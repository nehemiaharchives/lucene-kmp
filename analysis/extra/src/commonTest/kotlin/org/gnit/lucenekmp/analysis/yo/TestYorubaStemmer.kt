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

/** Test [YorubaStemmer]. */
class TestYorubaStemmer : BaseTokenStreamTestCase() {
    /** Test guarded nominalizing prefixes on longer words. */
    @Test
    @Throws(IOException::class)
    fun testPrefixes() {
        check("ikowe", "kowe")
        check("akowe", "kowe")
        check("ile", "ile")
        check("ife", "ife")
    }

    /** Test conservative reduplication and contraction handling. */
    @Test
    @Throws(IOException::class)
    fun testReduplicationAndContractions() {
        check("pupo-pupo", "pupo")
        check("rere-rere", "rere")
        check("mo'n", "mo")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = YorubaStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, YorubaStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
