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

/** Simple tests for [KoreanPartOfSpeechStopFilterFactory] */
class TestKoreanPartOfSpeechStopFilterFactory : BaseTokenStreamTestCase() {
    private class EmptyResourceLoader : ResourceLoader {
        override fun openResource(resource: String) = throw UnsupportedOperationException()
        override fun <T> findClass(cname: String, expectedType: KClass<*>) = throw UnsupportedOperationException()
    }

    @Test
    @Throws(IOException::class)
    fun testStopTags() {
        val tokenizerFactory = KoreanTokenizerFactory(mutableMapOf())
        tokenizerFactory.inform(EmptyResourceLoader())
        var ts: TokenStream = tokenizerFactory.create()
        (ts as Tokenizer).setReader(StringReader(" 한국은 대단한 나라입니다."))
        val args = mutableMapOf("tags" to "EP, EF, EC, ETN, ETM, JKS, JKC, JKG, JKO, JKB, JKV, JKQ, JX, JC")
        val factory = KoreanPartOfSpeechStopFilterFactory(args)
        ts = factory.create(ts)
        assertTokenStreamContents(ts, arrayOf("한국", "대단", "하", "나라", "이"))
    }

    @Test
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            KoreanPartOfSpeechStopFilterFactory(mutableMapOf("bogusArg" to "bogusValue"))
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
