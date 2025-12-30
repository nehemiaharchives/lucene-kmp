package org.gnit.lucenekmp.analysis.ko

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.ResourceLoader
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** Simple tests for [KoreanTokenizerFactory] */
class TestKoreanTokenizerFactory : BaseTokenStreamTestCase() {
    private class StringResourceLoader(private val content: String) : ResourceLoader {
        override fun openResource(resource: String) = ByteArrayInputStream(content.encodeToByteArray())
        override fun <T> findClass(cname: String, expectedType: KClass<*>) = throw UnsupportedOperationException()
    }

    @Test
    @Throws(IOException::class)
    fun testSimple() {
        val factory = KoreanTokenizerFactory(mutableMapOf())
        factory.inform(StringResourceLoader(""))
        val ts: Tokenizer = factory.create(newAttributeFactory())
        ts.setReader(StringReader("안녕하세요"))
        assertTokenStreamContents(
            ts,
            arrayOf("안녕", "하", "시", "어요"),
            intArrayOf(0, 2, 3, 3),
            intArrayOf(2, 3, 5, 5)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testDiscardDecompound() {
        val args = mutableMapOf("decompoundMode" to "discard")
        val factory = KoreanTokenizerFactory(args)
        factory.inform(StringResourceLoader(""))
        val ts: Tokenizer = factory.create(newAttributeFactory())
        ts.setReader(StringReader("갠지스강"))
        assertTokenStreamContents(ts, arrayOf("갠지스", "강"))
    }

    @Test
    @Throws(IOException::class)
    fun testNoDecompound() {
        val args = mutableMapOf("decompoundMode" to "none")
        val factory = KoreanTokenizerFactory(args)
        factory.inform(StringResourceLoader(""))
        val ts: Tokenizer = factory.create(newAttributeFactory())
        ts.setReader(StringReader("갠지스강"))
        assertTokenStreamContents(ts, arrayOf("갠지스강"))
    }

    @Test
    @Throws(IOException::class)
    fun testMixedDecompound() {
        val args = mutableMapOf("decompoundMode" to "mixed")
        val factory = KoreanTokenizerFactory(args)
        factory.inform(StringResourceLoader(""))
        val ts: Tokenizer = factory.create(newAttributeFactory())
        ts.setReader(StringReader("갠지스강"))
        assertTokenStreamContents(ts, arrayOf("갠지스강", "갠지스", "강"))
    }

    @Test
    @Throws(IOException::class)
    fun testUserDict() {
        val args = mutableMapOf("userDictionary" to "userdict.txt")
        val factory = KoreanTokenizerFactory(args)
        factory.inform(StringResourceLoader(TEST_KOREAN_USER_DICT))
        val ts: Tokenizer = factory.create(newAttributeFactory())
        ts.setReader(StringReader("세종시"))
        assertTokenStreamContents(ts, arrayOf("세종", "시"))
    }

    @Test
    @Throws(IOException::class)
    fun testDiscardPunctuation_true() {
        val args = mutableMapOf("discardPunctuation" to "true")
        val factory = KoreanTokenizerFactory(args)
        factory.inform(StringResourceLoader(""))
        val ts: Tokenizer = factory.create(newAttributeFactory())
        ts.setReader(StringReader("10.1 인치 모니터"))
        assertTokenStreamContents(ts, arrayOf("10", "1", "인치", "모니터"))
    }

    @Test
    @Throws(IOException::class)
    fun testDiscardPunctuation_false() {
        val args = mutableMapOf("discardPunctuation" to "false")
        val factory = KoreanTokenizerFactory(args)
        factory.inform(StringResourceLoader(""))
        val ts: Tokenizer = factory.create(newAttributeFactory())
        ts.setReader(StringReader("10.1 인치 모니터"))
        assertTokenStreamContents(ts, arrayOf("10", ".", "1", " ", "인치", " ", "모니터"))
    }

    @Test
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            KoreanTokenizerFactory(mutableMapOf("bogusArg" to "bogusValue"))
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
