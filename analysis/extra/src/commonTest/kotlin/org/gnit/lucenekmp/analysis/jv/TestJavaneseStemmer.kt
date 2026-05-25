package org.gnit.lucenekmp.analysis.jv

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [JavaneseStemmer]. */
class TestJavaneseStemmer : BaseTokenStreamTestCase() {
    /** Test some passive and pronominal prefixes */
    @Test
    @Throws(IOException::class)
    fun testPrefixes() {
        check("ditulis", "tulis")
        check("ketulis", "tulis")
        check("taktulis", "tulis")
        check("koktulis", "tulis")
    }

    /** Test nasal active prefixes */
    @Test
    @Throws(IOException::class)
    fun testNasalPrefixes() {
        check("nulis", "tulis")
        check("nyapu", "sapu")
        check("ngombe", "ombe")
        check("macul", "pacul")
    }

    /** Test common suffixes */
    @Test
    @Throws(IOException::class)
    fun testSuffixes() {
        check("tulisake", "tulis")
        check("tulisaken", "tulis")
        check("tulisan", "tulis")
        check("bukune", "buku")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = JavaneseStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, JavaneseStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
