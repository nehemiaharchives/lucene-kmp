package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import kotlin.test.Test

class TestNiqqudFilter {
    private val a = object : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val t: Tokenizer = KeywordTokenizer()
            return TokenStreamComponents(t, NiqqudFilter(t))
        }
    }

    @Test
    fun testBasicTerms() {
        HebrewTestUtil.checkOneTerm(a, "foo", "foo")
        HebrewTestUtil.checkOneTerm(a, "ב\u05B0דיקה", "בדיקה")
    }
}
