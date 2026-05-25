package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import kotlin.test.Test

class TestHebrewLemmatizerTokenFilter {
    private val a = object : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val src = HebrewTokenizer(HebrewTestUtil.dictionary.getPref())
            val tok = HebrewLemmatizerTokenFilter(src, HebrewTestUtil.dictionary)
            return TokenStreamComponents(src, tok)
        }
    }

    @Test
    fun testBasicTerms() {
        HebrewTestUtil.assertAnalyzesTo(a, "books", arrayOf("books", "books"))
        HebrewTestUtil.assertAnalyzesTo(a, "steven's", arrayOf("steven's", "steven's"))
        HebrewTestUtil.assertAnalyzesTo(a, "steven\u2019s", arrayOf("steven's", "steven's"))

        HebrewTestUtil.assertAnalyzesTo(a, "57", arrayOf("57"))

        HebrewTestUtil.assertAnalyzesTo(a, "בדיקה", arrayOf("בדיקה", "בדיקה"))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקות", arrayOf("בדיקות", "בדיקה"))
        HebrewTestUtil.assertAnalyzesTo(a, "אימא", arrayOf("אימא", "אימא"))
        HebrewTestUtil.assertAnalyzesTo(a, "אמא", arrayOf("אמא", "אימא"))
        HebrewTestUtil.assertAnalyzesTo(a, "צה\"ל", arrayOf("צה\"ל", "צה\"ל"))
        HebrewTestUtil.assertAnalyzesTo(a, "צה''ל", arrayOf("צה\"ל", "צה\"ל"))
    }

    @Test
    fun testBasicStreams() {
        HebrewTestUtil.assertAnalyzesTo(a, "one two three test", arrayOf("one", "one", "two", "two", "three", "three", "test", "test"))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקה אחת שתיים שולחן", arrayOf("בדיקה", "בדיקה", "אחת", "אחד", "שתיים", "שניים", "שולחן", "שולחן", "שולח"))
        HebrewTestUtil.assertAnalyzesTo(a, "one אחת 57", arrayOf("one", "one", "אחת", "אחד", "57"))
    }
}
