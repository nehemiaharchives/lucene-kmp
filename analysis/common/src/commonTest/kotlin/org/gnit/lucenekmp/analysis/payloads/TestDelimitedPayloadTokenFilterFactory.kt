package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestDelimitedPayloadTokenFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testEncoder() {
        val reader: Reader = StringReader("the|0.1 quick|0.1 red|0.1")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("DelimitedPayload", "encoder", "float").create(stream)

        stream.reset()
        while (stream.incrementToken()) {
            val payAttr = stream.getAttribute(PayloadAttribute::class)
            assertNotNull(payAttr)
            val payData = payAttr.payload!!.bytes
            assertNotNull(payData)
            val payFloat = PayloadHelper.decodeFloat(payData)
            assertEquals(0.1f, payFloat, 0.0f)
        }
        stream.end()
        stream.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDelim() {
        val reader: Reader = StringReader("the*0.1 quick*0.1 red*0.1")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("DelimitedPayload", "encoder", "float", "delimiter", "*").create(stream)
        stream.reset()
        while (stream.incrementToken()) {
            val payAttr = stream.getAttribute(PayloadAttribute::class)
            assertNotNull(payAttr)
            val payData = payAttr.payload!!.bytes
            assertNotNull(payData)
            val payFloat = PayloadHelper.decodeFloat(payData)
            assertEquals(0.1f, payFloat, 0.0f)
        }
        stream.end()
        stream.close()
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("DelimitedPayload", "encoder", "float", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
