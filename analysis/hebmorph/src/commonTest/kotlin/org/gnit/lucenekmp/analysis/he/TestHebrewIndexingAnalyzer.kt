package org.gnit.lucenekmp.analysis.he

import kotlin.test.Test
import kotlin.test.assertEquals

class TestHebrewIndexingAnalyzer {
    @Test
    fun testDictionaryLoaded() {
        val a = HebrewIndexingAnalyzer(HebrewTestUtil.dictionary)
        assertEquals(WordType.HEBREW, a.isRecognizedWord("אימא", false))
        assertEquals(WordType.HEBREW, a.isRecognizedWord("בדיקה", false))
        assertEquals(WordType.UNRECOGNIZED, a.isRecognizedWord("ץץץץץץ", false))
    }

    @Test
    fun testBasics() {
        val a = HebrewIndexingAnalyzer(HebrewTestUtil.dictionary)

        HebrewTestUtil.assertAnalyzesTo(a, "אימא", arrayOf("אימא$", "אימא"))
        HebrewTestUtil.assertAnalyzesTo(a, "אימא$", arrayOf("אימא$", "אימא"))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקהבדיקה", arrayOf("בדיקהבדיקה$", "בדיקהבדיקה"))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקהבדיקה$", arrayOf("בדיקהבדיקה$", "בדיקהבדיקה"))
        HebrewTestUtil.assertAnalyzesTo(a, "ץץץץץץץץץץץ", arrayOf("ץץץץץץץץץץץ$", "ץץץץץץץץץץץ"))
        HebrewTestUtil.assertAnalyzesTo(a, "ץץץץץץץץץץץ$", arrayOf("ץץץץץץץץץץץ$", "ץץץץץץץץץץץ"))

        HebrewTestUtil.assertAnalyzesTo(a, "אנציקלופדיה", arrayOf("אנציקלופדיה$", "אנציקלופדיה"))
        HebrewTestUtil.assertAnalyzesTo(a, "אנצקלופדיה", arrayOf("אנצקלופדיה$", "אנציקלופדיה"))

        HebrewTestUtil.assertAnalyzesTo(a, "שמלות", arrayOf("שמלות$", "שמלה", "מל"))

        HebrewTestUtil.assertAnalyzesTo(a, "book", arrayOf("book$", "book"))
        HebrewTestUtil.assertAnalyzesTo(a, "book$", arrayOf("book$", "book"))
        HebrewTestUtil.assertAnalyzesTo(a, "steven's", arrayOf("steven's$", "steven's"))
        HebrewTestUtil.assertAnalyzesTo(a, "steven\u2019s", arrayOf("steven's$", "steven's"))
        HebrewTestUtil.checkOneTerm(a, "3", "3")
    }
}
