package org.gnit.lucenekmp.analysis.ko

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.ResourceLoader
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** Simple tests for [KoreanReadingFormFilterFactory] */
class TestKoreanReadingFormFilterFactory : BaseTokenStreamTestCase() {
    private class EmptyResourceLoader : ResourceLoader {
        override fun openResource(resource: String) = throw UnsupportedOperationException()
        override fun <T> findClass(cname: String, expectedType: KClass<*>) = throw UnsupportedOperationException()
    }

    @Test
    @Throws(IOException::class)
    fun testReadings() {
        val tokenizerFactory = KoreanTokenizerFactory(mutableMapOf())
        tokenizerFactory.inform(EmptyResourceLoader())
        val tokenStream: Tokenizer = tokenizerFactory.create()
        tokenStream.setReader(StringReader("丞相"))
        val filterFactory = KoreanReadingFormFilterFactory(mutableMapOf())
        assertTokenStreamContents(filterFactory.create(tokenStream), arrayOf("승상"))
    }

    @Test
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            KoreanReadingFormFilterFactory(mutableMapOf("bogusArg" to "bogusValue"))
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
