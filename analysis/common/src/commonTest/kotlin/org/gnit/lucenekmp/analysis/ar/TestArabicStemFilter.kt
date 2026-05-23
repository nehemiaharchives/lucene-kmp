package org.gnit.lucenekmp.analysis.ar

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test the Arabic Stem Filter */
class TestArabicStemFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testAlPrefix() {
        check("الحسن", "حسن")
    }

    @Test
    @Throws(IOException::class)
    fun testWalPrefix() {
        check("والحسن", "حسن")
    }

    @Test
    @Throws(IOException::class)
    fun testBalPrefix() {
        check("بالحسن", "حسن")
    }

    @Test
    @Throws(IOException::class)
    fun testKalPrefix() {
        check("كالحسن", "حسن")
    }

    @Test
    @Throws(IOException::class)
    fun testFalPrefix() {
        check("فالحسن", "حسن")
    }

    @Test
    @Throws(IOException::class)
    fun testLlPrefix() {
        check("للاخر", "اخر")
    }

    @Test
    @Throws(IOException::class)
    fun testWaPrefix() {
        check("وحسن", "حسن")
    }

    @Test
    @Throws(IOException::class)
    fun testAhSuffix() {
        check("زوجها", "زوج")
    }

    @Test
    @Throws(IOException::class)
    fun testAnSuffix() {
        check("ساهدان", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testAtSuffix() {
        check("ساهدات", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testWnSuffix() {
        check("ساهدون", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testYnSuffix() {
        check("ساهدين", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testYhSuffix() {
        check("ساهديه", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testYpSuffix() {
        check("ساهدية", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testHSuffix() {
        check("ساهده", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testPSuffix() {
        check("ساهدة", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testYSuffix() {
        check("ساهدي", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testComboPrefSuf() {
        check("وساهدون", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testComboSuf() {
        check("ساهدهات", "ساهد")
    }

    @Test
    @Throws(IOException::class)
    fun testShouldntStem() {
        check("الو", "الو")
    }

    @Test
    @Throws(IOException::class)
    fun testNonArabic() {
        check("English", "English")
    }

    @Test
    @Throws(IOException::class)
    fun testWithKeywordAttribute() {
        val set = CharArraySet(1, true)
        set.add("ساهدهات")
        val tokenStream = whitespaceMockTokenizer("ساهدهات")

        val filter = ArabicStemFilter(SetKeywordMarkerFilter(tokenStream, set))
        assertTokenStreamContents(filter, arrayOf("ساهدهات"))
    }

    @Throws(IOException::class)
    private fun check(input: String, expected: String) {
        val tokenStream = whitespaceMockTokenizer(input)
        val filter = ArabicStemFilter(tokenStream)
        assertTokenStreamContents(filter, arrayOf(expected))
    }


    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, ArabicStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}


