package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests for [JapaneseKatakanaStemFilterFactory]  */
class TestJapaneseKatakanaStemFilterFactory : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun testKatakanaStemming() {
        val tokenizerFactory = JapaneseTokenizerFactory(HashMap())
        tokenizerFactory.inform(StringMockResourceLoader(""))
        val tokenStream: TokenStream = tokenizerFactory.create(newAttributeFactory())
        (tokenStream as Tokenizer).setReader(StringReader("明後日パーティーに行く予定がある。図書館で資料をコピーしました。"))
        val filterFactory = JapaneseKatakanaStemFilterFactory(HashMap())

        assertTokenStreamContents(
            filterFactory.create(tokenStream),
            arrayOf( // パーティー should be stemmed
                "明後日",
                "パーティ",
                "に",
                "行く",
                "予定",
                "が",
                "ある",  // コピー should not be stemmed
                "図書館",
                "で",
                "資料",
                "を",
                "コピー",
                "し",
                "まし",
                "た"
            )
        )
    }

    @Test
    /** Test that bogus arguments result in exception  */
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected: IllegalArgumentException =
            expectThrows(IllegalArgumentException::class) {
                JapaneseKatakanaStemFilterFactory(mutableMapOf("bogusArg" to "bogusValue"))
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
