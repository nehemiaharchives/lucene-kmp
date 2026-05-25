package org.gnit.lucenekmp.analysis.sw

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [SwahiliStemmer]. */
class TestSwahiliStemmer : BaseTokenStreamTestCase() {
    /** Test noun inflections */
    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("kitabu", "tabu")
        check("vitabu", "tabu")
        check("mtoto", "toto")
        check("watoto", "toto")
        check("mchezo", "chezo")
        check("michezo", "chezo")
    }

    /** Test some verb forms */
    @Test
    @Throws(IOException::class)
    fun testVerbs() {
        check("kusoma", "som")
        check("ninasoma", "som")
        check("anasoma", "som")
        check("walisoma", "som")
        check("tutasoma", "som")
        check("somani", "som")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = SwahiliStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, SwahiliStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
