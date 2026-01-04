package org.gnit.lucenekmp.analysis.ur

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [UrduStemmer]. */
class TestUrduStemmer : BaseTokenStreamTestCase() {
    /** Test noun inflections */
    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("کتابوں", "کتاب")
        check("لڑکیاں", "لڑک")
        check("پاکستان", "پاکستان")
        check("پاکستانی", "پاکستانی")
    }

    @Test
    @Throws(IOException::class)
    fun testPrefixStrip() {
        check("الکتاب", "کتاب")
    }

    @Test
    @Throws(IOException::class)
    fun testInfixRules() {
        check("اخبار", "خبر")
        check("انصاف", "نصف")
    }

    @Test
    @Throws(IOException::class)
    fun testIsamMafool() {
        check("محصول", "حصل")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = UrduStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, UrduStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
