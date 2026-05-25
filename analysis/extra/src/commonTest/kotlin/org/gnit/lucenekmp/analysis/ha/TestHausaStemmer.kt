package org.gnit.lucenekmp.analysis.ha

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [HausaStemmer]. */
class TestHausaStemmer : BaseTokenStreamTestCase() {
    /** Test some common prefixes. */
    @Test
    @Throws(IOException::class)
    fun testPrefixes() {
        check("nakaranta", "karanta")
        check("yakaranta", "karanta")
        check("takaranta", "karanta")
    }

    /** Test common suffixes. */
    @Test
    @Throws(IOException::class)
    fun testSuffixes() {
        check("karantawa", "karanta")
        check("gidan", "gida")
        check("makarantar", "makaranta")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = HausaStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, HausaStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
