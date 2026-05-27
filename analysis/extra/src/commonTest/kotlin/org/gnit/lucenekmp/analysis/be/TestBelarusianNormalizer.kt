package org.gnit.lucenekmp.analysis.be

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [BelarusianNormalizer]. */
class TestBelarusianNormalizer : BaseTokenStreamTestCase() {
    /** Test some basic normalization. */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("Беларусь", "Беларусь")
        check("мова", "мова")
    }

    @Test
    @Throws(IOException::class)
    fun testPunctuation() {
        check("пʼе", "п'е")
        check("па-беларуску–добра", "па-беларуску-добра")
    }

    @Test
    @Throws(IOException::class)
    fun testCombiningMarks() {
        check("у\u0306", "ў")
        check("е\u0308н", "ён")
    }

    @Test
    @Throws(IOException::class)
    fun testRussianI() {
        check("мир", "мір")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = BelarusianNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, BelarusianNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
