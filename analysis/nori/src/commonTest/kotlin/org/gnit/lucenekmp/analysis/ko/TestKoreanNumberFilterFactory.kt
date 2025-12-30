package org.gnit.lucenekmp.analysis.ko

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.ResourceLoader
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests for [KoreanNumberFilterFactory] */
class TestKoreanNumberFilterFactory : BaseTokenStreamTestCase() {
    private class EmptyResourceLoader : ResourceLoader {
        override fun openResource(resource: String) = throw UnsupportedOperationException()
        override fun <T> findClass(cname: String, expectedType: KClass<*>) = throw UnsupportedOperationException()
    }

    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val args = mutableMapOf("discardPunctuation" to "false")
        val tokenizerFactory = KoreanTokenizerFactory(args)
        tokenizerFactory.inform(EmptyResourceLoader())
        var tokenStream: TokenStream = tokenizerFactory.create(newAttributeFactory())
        (tokenStream as Tokenizer).setReader(StringReader("어제 초밥 가격은 10만 원"))
        val factory = KoreanNumberFilterFactory(mutableMapOf())
        tokenStream = factory.create(tokenStream)
        assertTokenStreamContents(
            tokenStream,
            arrayOf("어제", " ", "초", "밥", " ", "가격", "은", " ", "100000", " ", "원")
        )
    }

    @Test
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            KoreanNumberFilterFactory(mutableMapOf("bogusArg" to "bogusValue"))
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
