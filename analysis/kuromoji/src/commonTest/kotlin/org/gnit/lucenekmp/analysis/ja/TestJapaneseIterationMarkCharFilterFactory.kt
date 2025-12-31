package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests for [JapaneseIterationMarkCharFilterFactory]  */
class TestJapaneseIterationMarkCharFilterFactory : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun testIterationMarksWithKeywordTokenizer() {
        val text = "時々馬鹿々々しいところゞゝゝミスヾ"
        val filterFactory = JapaneseIterationMarkCharFilterFactory(HashMap())
        val filter: Reader = filterFactory.create(StringReader(text))
        val tokenStream: TokenStream =
            MockTokenizer(
                MockTokenizer.KEYWORD,
                false
            )
        (tokenStream as Tokenizer).setReader(filter)
        assertTokenStreamContents(
            tokenStream,
            arrayOf("時時馬鹿馬鹿しいところどころミスズ")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testIterationMarksWithJapaneseTokenizer() {
        val tokenizerFactory =
            JapaneseTokenizerFactory(HashMap())
        tokenizerFactory.inform(StringMockResourceLoader(""))

        val filterFactory =
            JapaneseIterationMarkCharFilterFactory(HashMap())
        val filter: Reader =
            filterFactory.create(StringReader("時々馬鹿々々しいところゞゝゝミスヾ"))
        val tokenStream: TokenStream =
            tokenizerFactory.create(newAttributeFactory())
        (tokenStream as Tokenizer).setReader(filter)
        assertTokenStreamContents(
            tokenStream,
            arrayOf("時時", "馬鹿馬鹿しい", "ところどころ", "ミ", "スズ")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testKanjiOnlyIterationMarksWithJapaneseTokenizer() {
        val tokenizerFactory =
            JapaneseTokenizerFactory(HashMap())
        tokenizerFactory.inform(StringMockResourceLoader(""))

        val filterArgs: MutableMap<String, String> = HashMap()
        filterArgs["normalizeKanji"] = "true"
        filterArgs["normalizeKana"] = "false"
        val filterFactory =
            JapaneseIterationMarkCharFilterFactory(filterArgs)

        val filter: Reader =
            filterFactory.create(StringReader("時々馬鹿々々しいところゞゝゝミスヾ"))
        val tokenStream: TokenStream =
            tokenizerFactory.create(newAttributeFactory())
        (tokenStream as Tokenizer).setReader(filter)
        assertTokenStreamContents(
            tokenStream,
            arrayOf("時時", "馬鹿馬鹿しい", "ところ", "ゞ", "ゝ", "ゝ", "ミス", "ヾ")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testKanaOnlyIterationMarksWithJapaneseTokenizer() {
        val tokenizerFactory =
            JapaneseTokenizerFactory(HashMap())
        tokenizerFactory.inform(StringMockResourceLoader(""))

        val filterArgs: MutableMap<String, String> = HashMap()
        filterArgs["normalizeKanji"] = "false"
        filterArgs["normalizeKana"] = "true"
        val filterFactory =
            JapaneseIterationMarkCharFilterFactory(filterArgs)

        val filter: Reader =
            filterFactory.create(StringReader("時々馬鹿々々しいところゞゝゝミスヾ"))
        val tokenStream: TokenStream =
            tokenizerFactory.create(newAttributeFactory())
        (tokenStream as Tokenizer).setReader(filter)
        assertTokenStreamContents(
            tokenStream,
            arrayOf("時々", "馬鹿", "々", "々", "しい", "ところどころ", "ミ", "スズ")
        )
    }

    @Test
    /** Test that bogus arguments result in exception  */
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected: IllegalArgumentException =
            expectThrows(
                IllegalArgumentException::class
            ) {
                JapaneseIterationMarkCharFilterFactory(mutableMapOf("bogusArg" to "bogusValue"))
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
