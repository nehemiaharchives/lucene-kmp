package org.gnit.lucenekmp.analysis.he

import kotlin.test.Test

class TestHebrewExactAnalyzer {
    @Test
    fun testBasics() {
        val a = HebrewExactAnalyzer(HebrewTestUtil.dictionary)
        HebrewTestUtil.checkOneTerm(a, "בדיקה", "בדיקה$")
        HebrewTestUtil.checkOneTerm(a, "בדיקה$", "בדיקה$")
        HebrewTestUtil.checkOneTerm(a, "books", "books$")
        HebrewTestUtil.checkOneTerm(a, "book", "book$")
        HebrewTestUtil.checkOneTerm(a, "book$", "book$")
        HebrewTestUtil.checkOneTerm(a, "steven's", "steven's$")
        HebrewTestUtil.checkOneTerm(a, "steven\u2019s", "steven's$")
        HebrewTestUtil.checkOneTerm(a, "3", "3")
    }
}
