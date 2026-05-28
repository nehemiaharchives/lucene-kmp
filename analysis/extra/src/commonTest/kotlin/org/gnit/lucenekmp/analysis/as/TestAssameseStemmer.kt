package org.gnit.lucenekmp.analysis.`as`

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [AssameseStemmer]. */
class TestAssameseStemmer : BaseTokenStreamTestCase() {
    /** Test noun inflections, plural suffixes, and postpositions. */
    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("ঘৰলৈ", "ঘৰ")
        check("ঘৰত", "ঘৰ")
        check("ঘৰৰ", "ঘৰ")
        check("মানুহবোৰ", "মানুহ")
        check("মানুহবোৰৰ", "মানুহ")
        check("ছাত্ৰবিলাকক", "ছাত্ৰ")
        check("কিতাপখনত", "কিতাপ")
        check("শিক্ষকসকলৰ", "শিক্ষক")
        check("লৰাজনক", "লৰা")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = AssameseStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, AssameseStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
