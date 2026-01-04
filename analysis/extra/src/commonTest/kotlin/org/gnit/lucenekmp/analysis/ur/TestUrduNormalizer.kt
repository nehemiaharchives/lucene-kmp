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

/** Test [UrduNormalizer]. */
class TestUrduNormalizer : BaseTokenStreamTestCase() {
    /** Test some basic normalization. */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("اردو", "اردو")
        check("پاکستانی", "پاکستانی")
        check("پاکستان", "پاکستان")
    }

    @Test
    @Throws(IOException::class)
    fun testDiacritics() {
        check("شیرِ", "شیر")
    }

    @Test
    @Throws(IOException::class)
    fun testCharacterNormalization() {
        check("ك", "ک")
        check("آ", "آ")
        check("أ", "أ")
        check("ۓ", "ۓ")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = UrduNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, UrduNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
