package org.gnit.lucenekmp.analysis.ckb

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

/** Test the Sorani Stemmer. */
class TestSoraniStemFilter : BaseTokenStreamTestCase() {
    private lateinit var a: Analyzer

    @BeforeTest
    fun setUp() {
        a = SoraniAnalyzer()
    }

    @AfterTest
    fun tearDown() {
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testIndefiniteSingular() {
        checkOneTerm(a, "پیاوێک", "پیاو") // -ek
        checkOneTerm(a, "دەرگایەک", "دەرگا") // -yek
    }

    @Test
    @Throws(Exception::class)
    fun testDefiniteSingular() {
        checkOneTerm(a, "پیاوەكە", "پیاو") // -aka
        checkOneTerm(a, "دەرگاكە", "دەرگا") // -ka
    }

    @Test
    @Throws(Exception::class)
    fun testDemonstrativeSingular() {
        checkOneTerm(a, "کتاویە", "کتاوی") // -a
        checkOneTerm(a, "دەرگایە", "دەرگا") // -ya
    }

    @Test
    @Throws(Exception::class)
    fun testIndefinitePlural() {
        checkOneTerm(a, "پیاوان", "پیاو") // -An
        checkOneTerm(a, "دەرگایان", "دەرگا") // -yAn
    }

    @Test
    @Throws(Exception::class)
    fun testDefinitePlural() {
        checkOneTerm(a, "پیاوەکان", "پیاو") // -akAn
        checkOneTerm(a, "دەرگاکان", "دەرگا") // -kAn
    }

    @Test
    @Throws(Exception::class)
    fun testDemonstrativePlural() {
        checkOneTerm(a, "پیاوانە", "پیاو") // -Ana
        checkOneTerm(a, "دەرگایانە", "دەرگا") // -yAna
    }

    @Test
    @Throws(Exception::class)
    fun testEzafe() {
        checkOneTerm(a, "هۆتیلی", "هۆتیل") // singular
        checkOneTerm(a, "هۆتیلێکی", "هۆتیل") // indefinite
        checkOneTerm(a, "هۆتیلانی", "هۆتیل") // plural
    }

    @Test
    @Throws(Exception::class)
    fun testPostpositions() {
        checkOneTerm(a, "دوورەوە", "دوور") // -awa
        checkOneTerm(a, "نیوەشەودا", "نیوەشەو") // -dA
        checkOneTerm(a, "سۆرانا", "سۆران") // -A
    }

    @Test
    @Throws(Exception::class)
    fun testPossessives() {
        checkOneTerm(a, "پارەمان", "پارە") // -mAn
        checkOneTerm(a, "پارەتان", "پارە") // -tAn
        checkOneTerm(a, "پارەیان", "پارە") // -yAn
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, SoraniStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }

    /** test against a basic vocabulary file */
    @Ignore
    @Test
    @Throws(Exception::class)
    fun testVocabulary() {
        // top 8k words or so: freq > 1000
        // Requires VocabularyAssert/data-path infrastructure.
    }

}
