package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.test.Test

class TestIgnoreOriginalTokenFilter {
    private val a = object : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val t = HebrewTokenizer(HebrewTestUtil.dictionary.getPref())
            return TokenStreamComponents(t, IgnoreOriginalTokenFilter(t))
        }
    }

    private val a2 = object : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val src = HebrewTokenizer(HebrewTestUtil.dictionary.getPref())
            var tok: TokenStream = HebrewLemmatizerTokenFilter(src, HebrewTestUtil.dictionary)
            tok = IgnoreOriginalTokenFilter(tok)
            return TokenStreamComponents(src, tok)
        }
    }

    @Test
    fun testBasicTerms() {
        HebrewTestUtil.assertAnalyzesTo(a, "book", arrayOf("book"))
        HebrewTestUtil.assertAnalyzesTo(a, "שלום", emptyArray())
        HebrewTestUtil.assertAnalyzesTo(a, "בי\"ס", emptyArray())
        HebrewTestUtil.assertAnalyzesTo(a, "57", arrayOf("57"))
    }

    @Test
    fun testBasicStream() {
        HebrewTestUtil.assertAnalyzesTo(a2, "book", arrayOf("book", "book"))
        HebrewTestUtil.assertAnalyzesTo(a2, "שלום", arrayOf("שלום", "שלום"))
        HebrewTestUtil.assertAnalyzesTo(a2, "בי\"ס", arrayOf("בי\"ס"))
        HebrewTestUtil.assertAnalyzesTo(a2, "57", arrayOf("57"))
        HebrewTestUtil.assertAnalyzesTo(a2, "בדיקה אחת שתיים שולחן", arrayOf("בדיקה", "אחד", "שניים", "שולחן", "שולח"))
    }
}
