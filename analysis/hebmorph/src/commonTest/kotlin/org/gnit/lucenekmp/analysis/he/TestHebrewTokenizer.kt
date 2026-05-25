package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import kotlin.test.Test

class TestHebrewTokenizer {
    private val dict = HebrewTestUtil.dictionary
    private val a = object : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val src = HebrewTokenizer(dict.getPref())
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
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקה אחת שתיים", arrayOf("בדיקה", "אחת", "שתיים"))
    }

    @Test
    fun testHyphen() {
        HebrewTestUtil.assertAnalyzesTo(a, "some-dashed-phrase", arrayOf("some", "dashed", "phrase"))
    }
}
