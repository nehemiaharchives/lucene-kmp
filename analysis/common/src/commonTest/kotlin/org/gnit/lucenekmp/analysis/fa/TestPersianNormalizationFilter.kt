package org.gnit.lucenekmp.analysis.fa

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test the Persian Normalization Filter */
class TestPersianNormalizationFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testFarsiYeh() {
        check("های", "هاي")
    }

    @Test
    @Throws(IOException::class)
    fun testYehBarree() {
        check("هاے", "هاي")
    }

    @Test
    @Throws(IOException::class)
    fun testKeheh() {
        check("کشاندن", "كشاندن")
    }

    @Test
    @Throws(IOException::class)
    fun testHehYeh() {
        check("كتابۀ", "كتابه")
    }

    @Test
    @Throws(IOException::class)
    fun testHehHamzaAbove() {
        check("كتابهٔ", "كتابه")
    }

    @Test
    @Throws(IOException::class)
    fun testHehGoal() {
        check("زادہ", "زاده")
    }

    @Throws(IOException::class)
    private fun check(input: String, expected: String) {
        val tokenStream = whitespaceMockTokenizer(input)
        val filter = PersianNormalizationFilter(tokenStream)
        assertTokenStreamContents(filter, arrayOf(expected))
    }


    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, PersianNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}

