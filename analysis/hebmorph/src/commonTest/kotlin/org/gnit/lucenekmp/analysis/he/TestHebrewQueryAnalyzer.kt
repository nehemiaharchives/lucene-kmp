package org.gnit.lucenekmp.analysis.he

import kotlin.test.Test

class TestHebrewQueryAnalyzer {
    @Test
    fun testBasics() {
        val a = HebrewQueryAnalyzer(HebrewTestUtil.dictionary)
        HebrewTestUtil.assertAnalyzesTo(a, "אימא", arrayOf("אימא$", "אימא"))
        HebrewTestUtil.assertAnalyzesTo(a, "אימא$", arrayOf("אימא$", "אימא"))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקהבדיקה", arrayOf("בדיקהבדיקה$", "בדיקהבדיקה"))
        HebrewTestUtil.assertAnalyzesTo(a, "בדיקהבדיקה$", arrayOf("בדיקהבדיקה$", "בדיקהבדיקה"))
        HebrewTestUtil.assertAnalyzesTo(a, "אנצקלופדיה", arrayOf("אנצקלופדיה$", "אנציקלופדיה"))
        HebrewTestUtil.assertAnalyzesTo(a, "book", arrayOf("book$", "book"))
        HebrewTestUtil.assertAnalyzesTo(a, "book$", arrayOf("book$", "book"))
        HebrewTestUtil.checkOneTerm(a, "3", "3")
    }
}
