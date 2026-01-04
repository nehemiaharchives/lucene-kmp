package org.gnit.lucenekmp.analysis.mr

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [MarathiStemmer]. */
class TestMarathiStemmer : BaseTokenStreamTestCase() {
    /** Test noun inflections */
    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("मुलगा", "मुलग")
        check("मुलगे", "मुलग")
        check("मुलगां", "मुलग")

        check("मुलगी", "मुलग")
        check("मुलगीं", "मुलग")

        check("पुस्तक", "पुस्तक")
        check("पुस्तके", "पुस्तक")
        check("पुस्तकां", "पुस्तक")
    }

    /** Test some verb forms */
    @Test
    @Throws(IOException::class)
    fun testVerbs() {
        check("खाता", "खा")
        check("खाती", "खा")
        check("खा", "खा")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = MarathiStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, MarathiStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
