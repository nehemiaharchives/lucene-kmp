package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.cjk.CJKWidthFilterFactory
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

class TestJapaneseCompletionFilterFactory :
    BaseTokenStreamFactoryTestCase() {
    @Test
    @Throws(IOException::class)
    fun testCompletion() {
        val tokenizerFactory = JapaneseTokenizerFactory(HashMap())
        var tokenStream: TokenStream = tokenizerFactory.create()
        (tokenStream as Tokenizer).setReader(StringReader("東京ｔ"))
        val cjkWidthFactory = CJKWidthFilterFactory(HashMap())
        tokenStream = cjkWidthFactory.create(tokenStream)
        val map: MutableMap<String, String> = HashMap()
        map["mode"] = "QUERY"
        val filterFactory = JapaneseCompletionFilterFactory(map)
        assertTokenStreamContents(filterFactory.create(tokenStream), arrayOf("東京t", "toukyout"))
    }

    /** Test that bogus arguments result in exception  */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected: IllegalArgumentException =
            expectThrows(IllegalArgumentException::class) {
                JapaneseCompletionFilterFactory(mutableMapOf("bogusArg" to "bogusValue"))
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
