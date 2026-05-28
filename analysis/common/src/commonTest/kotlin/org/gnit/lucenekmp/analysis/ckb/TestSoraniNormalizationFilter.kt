package org.gnit.lucenekmp.analysis.ckb

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Tests normalization for Sorani (this is more critical than stemming...) */
class TestSoraniNormalizationFilter : BaseTokenStreamTestCase() {
    private lateinit var a: Analyzer

    @BeforeTest
    fun setUp() {
        a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.KEYWORD, false)
                return TokenStreamComponents(tokenizer, SoraniNormalizationFilter(tokenizer))
            }
        }
    }

    @AfterTest
    fun tearDown() {
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testY() {
        checkOneTerm(a, "\u064A", "\u06CC")
        checkOneTerm(a, "\u0649", "\u06CC")
        checkOneTerm(a, "\u06CC", "\u06CC")
    }

    @Test
    @Throws(Exception::class)
    fun testK() {
        checkOneTerm(a, "\u0643", "\u06A9")
        checkOneTerm(a, "\u06A9", "\u06A9")
    }

    @Test
    @Throws(Exception::class)
    fun testH() {
        // initial
        checkOneTerm(a, "\u0647\u200C", "\u06D5")
        // medial
        checkOneTerm(a, "\u0647\u200C\u06A9", "\u06D5\u06A9")

        checkOneTerm(a, "\u06BE", "\u0647")
        checkOneTerm(a, "\u0629", "\u06D5")
    }

    @Test
    @Throws(Exception::class)
    fun testFinalH() {
        // always (and in final form by def), so frequently omitted
        checkOneTerm(a, "\u0647\u0647\u0647", "\u0647\u0647\u06D5")
    }

    @Test
    @Throws(Exception::class)
    fun testRR() {
        checkOneTerm(a, "\u0692", "\u0695")
    }

    @Test
    @Throws(Exception::class)
    fun testInitialRR() {
        // always, so frequently omitted
        checkOneTerm(a, "\u0631\u0631\u0631", "\u0695\u0631\u0631")
    }

    @Test
    @Throws(Exception::class)
    fun testRemove() {
        checkOneTerm(a, "\u0640", "")
        checkOneTerm(a, "\u064B", "")
        checkOneTerm(a, "\u064C", "")
        checkOneTerm(a, "\u064D", "")
        checkOneTerm(a, "\u064E", "")
        checkOneTerm(a, "\u064F", "")
        checkOneTerm(a, "\u0650", "")
        checkOneTerm(a, "\u0651", "")
        checkOneTerm(a, "\u0652", "")
        // we peek backwards in this case to look for h+200C, ensure this works
        checkOneTerm(a, "\u200C", "")
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, SoraniNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}

