package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestJapaneseBaseFormFilter : BaseTokenStreamTestCase() {

    private fun newJapaneseAnalyzer(): Analyzer {
        return object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = JapaneseTokenizer(newAttributeFactory(), null, true, JapaneseTokenizer.DEFAULT_MODE)
                return TokenStreamComponents(tokenizer, JapaneseBaseFormFilter(tokenizer))
            }
        }
    }

    @Throws(IOException::class)
    @Test
    fun testBasics() {
        val analyzer = newJapaneseAnalyzer()
        try {
            assertAnalyzesTo(
                analyzer,
                "それはまだ実験段階にあります",
                arrayOf("それ", "は", "まだ", "実験", "段階", "に", "ある", "ます")
            )
        } finally {
            analyzer.close()
        }
    }

    @Throws(IOException::class)
    @Test
    fun testKeyword() {
        val exclusionSet = CharArraySet(mutableListOf("あり"), false)
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = JapaneseTokenizer(newAttributeFactory(), null, true, JapaneseTokenizer.DEFAULT_MODE)
                val sink: TokenStream = SetKeywordMarkerFilter(source, exclusionSet)
                return TokenStreamComponents(source, JapaneseBaseFormFilter(sink))
            }
        }
        try {
            assertAnalyzesTo(
                a,
                "それはまだ実験段階にあります",
                arrayOf("それ", "は", "まだ", "実験", "段階", "に", "あり", "ます")
            )
        } finally {
            a.close()
        }
    }

    @Throws(IOException::class)
    @Test
    fun testEnglish() {
        val analyzer = newJapaneseAnalyzer()
        try {
            assertAnalyzesTo(analyzer, "this atest", arrayOf("this", "atest"))
        } finally {
            analyzer.close()
        }
    }

    @Throws(IOException::class)
    @Test
    fun testRandomStrings() {
        val analyzer = newJapaneseAnalyzer()
        try {
            checkRandomData(random(), analyzer, atLeast(200))
        } finally {
            analyzer.close()
        }
    }

    @Throws(IOException::class)
    @Test
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, JapaneseBaseFormFilter(tokenizer))
            }
        }
        try {
            checkOneTerm(a, "", "")
        } finally {
            a.close()
        }
    }
}
