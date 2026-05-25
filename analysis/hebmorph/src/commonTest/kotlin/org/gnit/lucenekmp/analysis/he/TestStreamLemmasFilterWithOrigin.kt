package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import kotlin.test.Test

class TestStreamLemmasFilterWithOrigin {
    private val a = object : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val src = StreamLemmasFilter(HebrewTestUtil.dictionary)
            src.setKeepOriginalWord(true)
            return TokenStreamComponents(src)
        }
    }

    /**
     * test basic cases
     */
    @Test
    fun testBasics() {
        HebrewTestUtil.checkOneTerm(a, "books", "books")
        HebrewTestUtil.checkOneTerm(a, "book", "book")
        HebrewTestUtil.checkOneTerm(a, "steven's", "steven's")
        HebrewTestUtil.checkOneTerm(a, "steven\u2019s", "steven's")

        HebrewTestUtil.assertAnalyzesTo(a, "בדיקה", arrayOf("בדיקה", "בדיקה"), intArrayOf(0, 0), intArrayOf(5, 5), intArrayOf(1, 0))
        HebrewTestUtil.assertAnalyzesTo(a, "צה\"ל", arrayOf("צה\"ל", "צה\"ל"), intArrayOf(0, 0), intArrayOf(4, 4), intArrayOf(1, 0))
        HebrewTestUtil.assertAnalyzesTo(a, "צה''ל", arrayOf("צה\"ל", "צה\"ל"), intArrayOf(0, 0), intArrayOf(5, 5), intArrayOf(1, 0))
    }

    @Test
    fun testLemmatization() {
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקה", arrayOf("בדיקה", "בדיקה"), intArrayOf(0, 0), intArrayOf(5, 5))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקות", arrayOf("בדיקות", "בדיקה"), intArrayOf(0, 0), intArrayOf(6, 6))
        HebrewTestUtil.assertAnalyzesTo(a, "אימא", arrayOf("אימא", "אימא"), intArrayOf(0, 0), intArrayOf(4, 4))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקות אמא", arrayOf("בדיקות", "בדיקה", "אמא", "אימא"), intArrayOf(0, 0, 7, 7), intArrayOf(6, 6, 10, 10))
    }
}
