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

/** Test [BelarusianStemmer]. */
class TestBelarusianStemmer : BaseTokenStreamTestCase() {
    /** Test noun and adjective inflections. */
    @Test
    @Throws(IOException::class)
    fun testNominals() {
        check("мінску", "мінск")
        check("словаў", "слов")
        check("словамі", "слов")
        check("добрага", "добр")
        check("добрымі", "добр")
    }

    /** Test conservative verb-like endings. */
    @Test
    @Throws(IOException::class)
    fun testVerbs() {
        check("чытаць", "чыта")
        check("чыталі", "чыта")
        check("маліцца", "малі")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = BelarusianStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, BelarusianStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
