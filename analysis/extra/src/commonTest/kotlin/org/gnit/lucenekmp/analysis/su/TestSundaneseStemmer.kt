package org.gnit.lucenekmp.analysis.su

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [SundaneseStemmer]. */
class TestSundaneseStemmer : BaseTokenStreamTestCase() {
    /** Test some common prefixes. */
    @Test
    @Throws(IOException::class)
    fun testPrefixes() {
        check("dibacakeun", "baca")
        check("kadaharan", "dahar")
        check("pangajaran", "ajar")
    }

    /** Test nasal active prefixes. */
    @Test
    @Throws(IOException::class)
    fun testNasalPrefixes() {
        check("nyapu", "sapu")
        check("ngalakukeun", "laku")
        check("nulis", "tulis")
    }

    /** Test common suffixes. */
    @Test
    @Throws(IOException::class)
    fun testSuffixes() {
        check("bukuna", "buku")
        check("dahareun", "dahar")
        check("jalanan", "jalan")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = SundaneseStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, SundaneseStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
