package org.gnit.lucenekmp.analysis.kn

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [KannadaStemmer]. */
class TestKannadaStemmer : BaseTokenStreamTestCase() {
    /** Test noun inflections, plural suffixes, and postpositions. */
    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("ಮನೆಗೆ", "ಮನೆ")
        check("ಮನೆಯಲ್ಲಿ", "ಮನೆ")
        check("ಮನೆಯಿಂದ", "ಮನೆ")
        check("ಪುಸ್ತಕಗಳು", "ಪುಸ್ತಕ")
        check("ಪುಸ್ತಕಗಳನ್ನು", "ಪುಸ್ತಕ")
        check("ಗುರುಗಳಿಂದ", "ಗುರು")
        check("ಹುಡುಗರಿಗೆ", "ಹುಡುಗ")
        check("ರಾಮನಿಗೆ", "ರಾಮ")
        check("ಕನ್ನಡದಲ್ಲಿ", "ಕನ್ನಡ")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = KannadaStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, KannadaStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
