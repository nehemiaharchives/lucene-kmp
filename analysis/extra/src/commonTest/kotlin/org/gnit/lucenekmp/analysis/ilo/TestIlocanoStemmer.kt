package org.gnit.lucenekmp.analysis.ilo

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [IlocanoStemmer]. */
class TestIlocanoStemmer : BaseTokenStreamTestCase() {
    /** Test some common prefixes. */
    @Test
    @Throws(IOException::class)
    fun testPrefixes() {
        check("agbasa", "basa")
        check("nagadal", "adal")
        check("mangala", "ala")
        check("makapagadal", "adal")
    }

    /** Test infixes and suffixes. */
    @Test
    @Throws(IOException::class)
    fun testAffixes() {
        check("kumita", "kita")
        check("sinurat", "surat")
        check("adalen", "adal")
        check("basaan", "basa")
    }

    /** Test reduplication. */
    @Test
    @Throws(IOException::class)
    fun testReduplication() {
        check("basbasa", "basa")
        check("basa-basa", "basa")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = IlocanoStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, IlocanoStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
