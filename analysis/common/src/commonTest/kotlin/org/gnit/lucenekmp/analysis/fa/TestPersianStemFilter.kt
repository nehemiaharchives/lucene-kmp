package org.gnit.lucenekmp.analysis.fa

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Test the Persian Normalization Filter */
class TestPersianStemFilter : BaseTokenStreamTestCase() {
    private lateinit var a: Analyzer

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = MockTokenizer()
                return TokenStreamComponents(source, PersianStemFilter(source))
            }
        }
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testAnSuffix() {
        checkOneTerm(a, "دوستان", "دوست")
    }

    @Test
    @Throws(IOException::class)
    fun testHaSuffix() {
        checkOneTerm(a, "كتابها", "كتاب")
    }

    @Test
    @Throws(IOException::class)
    fun testAtSuffix() {
        checkOneTerm(a, "جامدات", "جامد")
    }

    @Test
    @Throws(IOException::class)
    fun testYeeSuffix() {
        checkOneTerm(a, "عليرضايي", "عليرضا")
    }

    @Test
    @Throws(IOException::class)
    fun testYeSuffix() {
        checkOneTerm(a, "شادماني", "شادمان")
    }

    @Test
    @Throws(IOException::class)
    fun testTarSuffix() {
        checkOneTerm(a, "باحالتر", "باحال")
    }

    @Test
    @Throws(IOException::class)
    fun testTarinSuffix() {
        checkOneTerm(a, "خوبترين", "خوب")
    }

    @Test
    @Throws(IOException::class)
    fun testShouldntStem() {
        checkOneTerm(a, "كباب", "كباب")
    }

    @Test
    @Throws(IOException::class)
    fun testNonArabic() {
        checkOneTerm(a, "English", "english")
    }

    @Test
    @Throws(IOException::class)
    fun testWithKeywordAttribute() {
        val set = CharArraySet(1, true)
        set.add("ساهدهات")
        val tokenStream = whitespaceMockTokenizer("ساهدهات")

        val filter = PersianStemFilter(SetKeywordMarkerFilter(tokenStream, set))
        assertTokenStreamContents(filter, arrayOf("ساهدهات"))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, PersianStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }

}

