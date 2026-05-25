package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import kotlin.test.Test

class TestStreamLemmasFilter {
    private val a = object : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val src = StreamLemmasFilter(HebrewTestUtil.dictionary)
            return TokenStreamComponents(src)
        }
    }

    @Test
    fun testBasics() {
        HebrewTestUtil.checkOneTerm(a, "books", "books")
        HebrewTestUtil.checkOneTerm(a, "book", "book")
        HebrewTestUtil.checkOneTerm(a, "steven's", "steven's")
        HebrewTestUtil.checkOneTerm(a, "steven\u2019s", "steven's")

        HebrewTestUtil.checkOneTerm(a, "בדיקה", "בדיקה")
        HebrewTestUtil.checkOneTerm(a, "צה\"ל", "צה\"ל")
        HebrewTestUtil.checkOneTerm(a, "צה''ל", "צה\"ל")
    }

    @Test
    fun testLemmatization() {
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקה", arrayOf("בדיקה"))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקות", arrayOf("בדיקה"))
        HebrewTestUtil.assertAnalyzesTo(a, "אימא", arrayOf("אימא"))
    }
}
