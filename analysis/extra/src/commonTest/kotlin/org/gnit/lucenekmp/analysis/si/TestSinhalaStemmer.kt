package org.gnit.lucenekmp.analysis.si

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [SinhalaStemmer]. */
class TestSinhalaStemmer : BaseTokenStreamTestCase() {
    /** Test common noun case, plural, and indefinite suffixes. */
    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("ගෙදරට", "ගෙදර")
        check("ගෙදරින්", "ගෙදර")
        check("පොත්වල", "පොත්")
        check("පොත්වලට", "පොත්")
        check("කතාවක්", "කතා")
        check("කතාවෙන්", "කතා")
        check("කතාවලින්", "කතා")
        check("ළමයින්ගේ", "ළමයින්")
        check("නගරයෙන්", "නගර")
        check("සිංහලයේ", "සිංහල")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = SinhalaStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, SinhalaStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
