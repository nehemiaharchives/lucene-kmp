package org.gnit.lucenekmp.analysis.pa

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [PunjabiStemmer]. */
class TestPunjabiStemmer : BaseTokenStreamTestCase() {
    /** Test noun inflections */
    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("ਪੰਜਾਬੀ", "ਪੰਜਾਬੀ")
        check("ਪੰਜਾਬੀਆਂ", "ਪੰਜਾਬੀ")
        check("ਕਿਤਾਬਾਂ", "ਕਿਤਾਬ")
        check("ਕੁੜੀਆਂ", "ਕੁੜੀ")
    }

    /** Test some verb forms */
    @Test
    @Throws(IOException::class)
    fun testVerbs() {
        check("ਭੱਜਣਾ", "ਭੱਜ")
        check("ਪੜਾਉਂਦਾ", "ਪੜਾ")
        check("ਪੜਾਉਂਦੀ", "ਪੜਾ")
        check("ਪੜਾਉਂਦੇ", "ਪੜਾ")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = PunjabiStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, PunjabiStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
