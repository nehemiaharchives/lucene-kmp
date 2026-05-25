package org.gnit.lucenekmp.analysis.ig

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [IgboStemmer]. */
class TestIgboStemmer : BaseTokenStreamTestCase() {
    /** Test some common prefixes. */
    @Test
    @Throws(IOException::class)
    fun testPrefixes() {
        check("igbaso", "gbaso")
        check("ikwu", "kwu")
        check("ikpe", "kpe")
    }

    /** Test common suffixes. */
    @Test
    @Throws(IOException::class)
    fun testSuffixes() {
        check("ekwughi", "ekwu")
        check("amarala", "amara")
        check("agakwa", "aga")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = IgboStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, IgboStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
