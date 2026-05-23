package org.gnit.lucenekmp.analysis.ar

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test the Arabic Normalization Filter */
class TestArabicNormalizationFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testAlifMadda() {
        check("آجن", "اجن")
    }

    @Test
    @Throws(IOException::class)
    fun testAlifHamzaAbove() {
        check("أحمد", "احمد")
    }

    @Test
    @Throws(IOException::class)
    fun testAlifHamzaBelow() {
        check("إعاذ", "اعاذ")
    }

    @Test
    @Throws(IOException::class)
    fun testAlifMaksura() {
        check("بنى", "بني")
    }

    @Test
    @Throws(IOException::class)
    fun testTehMarbuta() {
        check("فاطمة", "فاطمه")
    }

    @Test
    @Throws(IOException::class)
    fun testTatweel() {
        check("روبرـــــت", "روبرت")
    }

    @Test
    @Throws(IOException::class)
    fun testFatha() {
        check("مَبنا", "مبنا")
    }

    @Test
    @Throws(IOException::class)
    fun testKasra() {
        check("علِي", "علي")
    }

    @Test
    @Throws(IOException::class)
    fun testDamma() {
        check("بُوات", "بوات")
    }

    @Test
    @Throws(IOException::class)
    fun testFathatan() {
        check("ولداً", "ولدا")
    }

    @Test
    @Throws(IOException::class)
    fun testKasratan() {
        check("ولدٍ", "ولد")
    }

    @Test
    @Throws(IOException::class)
    fun testDammatan() {
        check("ولدٌ", "ولد")
    }

    @Test
    @Throws(IOException::class)
    fun testSukun() {
        check("نلْسون", "نلسون")
    }

    @Test
    @Throws(IOException::class)
    fun testShaddah() {
        check("هتميّ", "هتمي")
    }

    @Throws(IOException::class)
    private fun check(input: String, expected: String) {
        val tokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenStream.setReader(StringReader(input))
        val filter = ArabicNormalizationFilter(tokenStream)
        assertTokenStreamContents(filter, arrayOf(expected))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, ArabicNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}

