package org.gnit.lucenekmp.analysis.bn

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertTrue

/** Test [BengaliNormalizer]. */
class TestBengaliNormalizer : BaseTokenStreamTestCase() {
    /** Test some basic normalization, with an example from the paper. */
    @Test
    @Throws(IOException::class)
    fun testChndrobindu() {
        check("চাঁদ", "চাদ")
    }

    @Test
    @Throws(IOException::class)
    fun testRosshoIKar() {
        check("বাড়ী", "বারি")
        check("তীর", "তির")
    }

    @Test
    @Throws(IOException::class)
    fun testRosshoUKar() {
        check("ভূল", "ভুল")
        check("অনূপ", "অনুপ")
    }

    @Test
    @Throws(IOException::class)
    fun testNga() {
        check("বাঙলা", "বাংলা")
    }

    @Test
    @Throws(IOException::class)
    fun testJaPhaala() {
        check("ব্যাক্তি", "বেক্তি")
        check("সন্ধ্যা", "সন্ধা")
    }

    @Test
    @Throws(IOException::class)
    fun testBaPhalaa() {
        check("স্বদেশ", "সদেস")
        check("তত্ত্ব", "তত্ত")
        check("বিশ্ব", "বিসস")
    }

    @Test
    @Throws(IOException::class)
    fun testVisarga() {
        check("দুঃখ", "দুখখ")
        check("উঃ", "উহ")
        check("পুনঃ", "পুন")
    }

    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("কণা", "কনা")
        check("শরীর", "সরির")
        check("বাড়ি", "বারি")
    }

    /** creates random strings in the bengali block and ensures the normalizer doesn't trip up on them */
    @Test
    @Throws(IOException::class)
    fun testRandom() {
        val normalizer = BengaliNormalizer()
        for (i in 0 until 100000) {
            val randomBengali = TestUtil.randomSimpleStringRange(random(), '\u0980', '\u09FF', 7)
            try {
                val newLen = normalizer.normalize(randomBengali.toCharArray(), randomBengali.length)
                assertTrue(newLen >= 0) // should not return negative length
                assertTrue(newLen <= randomBengali.length) // should not increase length of string
            } catch (e: Exception) {
                println(
                    "normalizer failed on input: '$randomBengali' (${BaseTokenStreamTestCase.escape(randomBengali)})"
                )
                throw e
            }
        }
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = BengaliNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, BengaliNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
