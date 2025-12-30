package org.gnit.lucenekmp.analysis.cn.smart

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

/** Tests for HMMChineseTokenizerFactory */
class TestHMMChineseTokenizerFactory : BaseTokenStreamTestCase() {

    /** Test showing the behavior */
    @Test
    @Throws(IOException::class)
    fun testSimple() {
        val reader = StringReader("我购买了道具和服装。")
        val factory: TokenizerFactory = HMMChineseTokenizerFactory(mutableMapOf())
        val tokenizer: Tokenizer = factory.create(newAttributeFactory())
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("我", "购买", "了", "道具", "和", "服装", ","))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            HMMChineseTokenizerFactory(mutableMapOf("bogusArg" to "bogusValue"))
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
