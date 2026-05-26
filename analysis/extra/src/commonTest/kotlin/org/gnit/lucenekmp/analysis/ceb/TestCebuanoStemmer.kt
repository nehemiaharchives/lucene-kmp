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

/** Test [CebuanoStemmer]. */
class TestCebuanoStemmer : BaseTokenStreamTestCase() {
    /** Test actor-focus and patient-focus prefixes. */
    @Test
    @Throws(IOException::class)
    fun testPrefixes() {
        check("mopalit", "palit")
        check("nipalit", "palit")
        check("gipalit", "palit")
        check("nagbasa", "basa")
        check("magluto", "luto")
    }

    /** Test common infixes. */
    @Test
    @Throws(IOException::class)
    fun testInfixes() {
        check("sumulat", "sulat")
        check("linuto", "luto")
        check("umabot", "abot")
    }

    /** Test common suffixes. */
    @Test
    @Throws(IOException::class)
    fun testSuffixes() {
        check("paliton", "palit")
        check("palitan", "palit")
        check("basahon", "basa")
        check("sulatanan", "sulat")
    }

    @Test
    @Throws(IOException::class)
    fun testAffixesAndReduplication() {
        check("gipalitan", "palit")
        check("mopalitay", "palitay")
        check("basa-basa", "basa")
        check("basa'g", "basa")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = CebuanoStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, CebuanoStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
