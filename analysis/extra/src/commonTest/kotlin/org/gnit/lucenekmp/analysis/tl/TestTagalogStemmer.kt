package org.gnit.lucenekmp.analysis.tl

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [TagalogStemmer]. */
class TestTagalogStemmer : BaseTokenStreamTestCase() {
    /** Test noun inflections */
    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("tagalog", "tagalog")
        check("pilipino", "pilipino")
    }

    @Test
    @Throws(IOException::class)
    fun testAffixesAndReduplication() {
        check("kumain", "kain")
        check("sinulat", "sulat")
        check("umalis", "alis")
        check("inabot", "abot")
        check("magluto", "luto")
        check("nagluluto", "luto")
        check("bibili", "bili")
        check("aalis", "alis")
        check("kainan", "kain")
        check("kainin", "kain")
        check("bahay-bahay", "bahay")
        check("ito'y", "ito")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = TagalogStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, TagalogStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
