package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests for [JapaneseNumberFilterFactory] */
class TestJapaneseNumberFilterFactory : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val args = HashMap<String, String>()
        args["discardPunctuation"] = "false"

        val tokenizerFactory = JapaneseTokenizerFactory(args)
        tokenizerFactory.inform(StringMockResourceLoader(""))
        var tokenStream: TokenStream = tokenizerFactory.create(newAttributeFactory())
        (tokenStream as Tokenizer).setReader(StringReader("昨日のお寿司は1０万円でした。"))

        val factory = JapaneseNumberFilterFactory(HashMap())
        tokenStream = factory.create(tokenStream)
        assertTokenStreamContents(
            tokenStream,
            arrayOf("昨日", "の", "お", "寿司", "は", "100000", "円", "でし", "た", "。")
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected: IllegalArgumentException =
            expectThrows(IllegalArgumentException::class) {
                JapaneseNumberFilterFactory(mutableMapOf("bogusArg" to "bogusValue"))
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
