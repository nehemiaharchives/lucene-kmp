package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests for [JapaneseReadingFormFilterFactory] */
class TestJapaneseReadingFormFilterFactory : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun testReadings() {
        val tokenizerFactory = JapaneseTokenizerFactory(HashMap())
        tokenizerFactory.inform(StringMockResourceLoader(""))
        val tokenStream: TokenStream = tokenizerFactory.create()
        (tokenStream as Tokenizer).setReader(StringReader("先ほどベルリンから来ました。"))
        val filterFactory = JapaneseReadingFormFilterFactory(HashMap())
        assertTokenStreamContents(
            filterFactory.create(tokenStream),
            arrayOf("サキ", "ホド", "ベルリン", "カラ", "キ", "マシ", "タ")
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected: IllegalArgumentException =
            expectThrows(IllegalArgumentException::class) {
                JapaneseReadingFormFilterFactory(mutableMapOf("bogusArg" to "bogusValue"))
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
