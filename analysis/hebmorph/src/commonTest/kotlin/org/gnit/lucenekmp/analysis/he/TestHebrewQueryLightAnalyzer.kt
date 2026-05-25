package org.gnit.lucenekmp.analysis.he

import kotlin.test.Test

class TestHebrewQueryLightAnalyzer {
    @Test
    fun testBasics() {
        val a = HebrewQueryLightAnalyzer(HebrewTestUtil.dictionary)
        HebrewTestUtil.assertAnalyzesTo(a, "אימא", arrayOf("אימא"))
        HebrewTestUtil.assertAnalyzesTo(a, "אימא$", arrayOf("אימא$"))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקהבדיקה", arrayOf("בדיקהבדיקה"))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקהבדיקה$", arrayOf("בדיקהבדיקה$"))
        HebrewTestUtil.assertAnalyzesTo(a, "ץץץץץץץץץץץ", emptyArray())
        HebrewTestUtil.assertAnalyzesTo(a, "ץץץץץץץץץץץ$", emptyArray())
        HebrewTestUtil.assertAnalyzesTo(a, "book", arrayOf("book$", "book"))
        HebrewTestUtil.assertAnalyzesTo(a, "book$", arrayOf("book$"))
        HebrewTestUtil.assertAnalyzesTo(a, "steven's", arrayOf("steven's$", "steven's"))
        HebrewTestUtil.assertAnalyzesTo(a, "steven\u2019s", arrayOf("steven's$", "steven's"))
        HebrewTestUtil.checkOneTerm(a, "3", "3")
    }
}
