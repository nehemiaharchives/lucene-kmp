package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import kotlin.test.Test
import kotlin.test.assertTrue


/** Simple tests for [JapaneseBaseFormFilterFactory]  */
class TestJapaneseBaseFormFilterFactory :
    BaseTokenStreamTestCase() {
    @Throws(IOException::class)

    @Test
    fun testBasics() {
        val tokenizerFactory = JapaneseTokenizerFactory(HashMap())
        tokenizerFactory.inform(StringMockResourceLoader(""))
        var ts: TokenStream = tokenizerFactory.create(newAttributeFactory())
        (ts as Tokenizer).setReader(StringReader("それはまだ実験段階にあります"))
        val factory = JapaneseBaseFormFilterFactory(HashMap())
        ts = factory.create(ts)
        assertTokenStreamContents(ts, arrayOf<String>("それ", "は", "まだ", "実験", "段階", "に", "ある", "ます"))
    }

    @Test
    /** Test that bogus arguments result in exception  */
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected: IllegalArgumentException =
            expectThrows(
                IllegalArgumentException::class
            ) {
                JapaneseBaseFormFilterFactory(mutableMapOf("bogusArg" to "bogusValue"))
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
