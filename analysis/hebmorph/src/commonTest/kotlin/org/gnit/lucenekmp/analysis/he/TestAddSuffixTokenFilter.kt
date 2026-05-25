package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import kotlin.test.Test

class TestAddSuffixTokenFilter {
    private val a = object : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val t = HebrewTokenizer(HebrewTestUtil.dictionary.getPref())
            return TokenStreamComponents(t, AddSuffixTokenFilter(t, '$'))
        }
    }

    @Test
    fun testBasicTerms() {
        HebrewTestUtil.assertAnalyzesTo(a, "book", arrayOf("book$"))
        HebrewTestUtil.assertAnalyzesTo(a, "שלום", arrayOf("שלום$"))
        HebrewTestUtil.assertAnalyzesTo(a, "בי\"ס", arrayOf("בי\"ס$"))
        HebrewTestUtil.assertAnalyzesTo(a, "57", arrayOf("57"))
    }
}
