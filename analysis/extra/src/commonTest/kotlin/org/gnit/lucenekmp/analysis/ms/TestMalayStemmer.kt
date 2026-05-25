package org.gnit.lucenekmp.analysis.ms

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [MalayStemmer]. */
class TestMalayStemmer : BaseTokenStreamTestCase() {
    /** Test first-order Malay prefixes. */
    @Test
    @Throws(IOException::class)
    fun testFirstOrderPrefixes() {
        check("membaca", "baca")
        check("menulis", "tulis")
        check("menyapu", "sapu")
        check("mengambil", "ambil")
        check("ditulis", "tulis")
        check("terbuka", "buka")
    }

    /** Test second-order Malay prefixes. */
    @Test
    @Throws(IOException::class)
    fun testSecondOrderPrefixes() {
        check("berjalan", "jalan")
        check("pelajar", "ajar")
    }

    /** Test common Malay suffixes and particles. */
    @Test
    @Throws(IOException::class)
    fun testSuffixes() {
        check("tuliskan", "tulis")
        check("tulisan", "tulis")
        check("tulisnya", "tulis")
        check("bacalah", "baca")
    }

    /** Test prefix-suffix pairs. */
    @Test
    @Throws(IOException::class)
    fun testPrefixSuffixPairs() {
        check("dituliskan", "tulis")
        check("menggunakan", "guna")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = MalayStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, MalayStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
