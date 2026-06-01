package org.gnit.lucenekmp.analysis.ngram

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.payloads.PayloadHelper
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Simple tests to ensure the NGram filter factories are working. */
class TestNGramFilters : BaseTokenStreamFactoryTestCase() {
    /** Test NGramTokenizerFactory */
    @Test
    fun testNGramTokenizer() {
        val reader: Reader = StringReader("test")
        val stream = tokenizerFactory("NGram").create()
        (stream as Tokenizer).setReader(reader)
        assertTokenStreamContents(stream, arrayOf("t", "te", "e", "es", "s", "st", "t"))
    }

    /** Test NGramTokenizerFactory with min and max gram options */
    @Test
    fun testNGramTokenizer2() {
        val reader: Reader = StringReader("test")
        val stream = tokenizerFactory("NGram", "minGramSize", "2", "maxGramSize", "3").create()
        (stream as Tokenizer).setReader(reader)
        assertTokenStreamContents(stream, arrayOf("te", "tes", "es", "est", "st"))
    }

    /** Test the NGramFilterFactory with old defaults */
    @Test
    fun testNGramFilter() {
        val reader: Reader = StringReader("test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("NGram", "minGramSize", "1", "maxGramSize", "2").create(stream)
        assertTokenStreamContents(stream, arrayOf("t", "te", "e", "es", "s", "st", "t"))
    }

    /** Test the NGramFilterFactory with min and max gram options */
    @Test
    fun testNGramFilter2() {
        val reader: Reader = StringReader("test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("NGram", "minGramSize", "2", "maxGramSize", "3").create(stream)
        assertTokenStreamContents(stream, arrayOf("te", "tes", "es", "est", "st"))
    }

    /** Test the NGramFilterFactory with preserve option */
    @Test
    fun testNGramFilter3() {
        val reader: Reader = StringReader("test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("NGram", "minGramSize", "2", "maxGramSize", "3", "preserveOriginal", "true").create(stream)
        assertTokenStreamContents(stream, arrayOf("te", "tes", "es", "est", "st", "test"))
    }

    /** Test NGramFilterFactory on tokens with payloads */
    @Test
    fun testNGramFilterPayload() {
        val reader: Reader = StringReader("test|0.1")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("DelimitedPayload", "encoder", "float").create(stream)
        stream = tokenFilterFactory("NGram", "minGramSize", "1", "maxGramSize", "2").create(stream)

        stream.reset()
        while (stream.incrementToken()) {
            val payAttr: PayloadAttribute = stream.getAttribute(PayloadAttribute::class)!!
            assertNotNull(payAttr)
            val payData: BytesRef? = payAttr.payload
            assertNotNull(payData)
            val payFloat = PayloadHelper.decodeFloat(payData.bytes)
            assertEquals(0.1f, payFloat, 0.0f)
        }
        stream.end()
        stream.close()
    }

    /** Test EdgeNGramTokenizerFactory */
    @Test
    fun testEdgeNGramTokenizer() {
        val reader: Reader = StringReader("test")
        val stream = tokenizerFactory("EdgeNGram").create()
        (stream as Tokenizer).setReader(reader)
        assertTokenStreamContents(stream, arrayOf("t"))
    }

    /** Test EdgeNGramTokenizerFactory with min and max gram size */
    @Test
    fun testEdgeNGramTokenizer2() {
        val reader: Reader = StringReader("test")
        val stream = tokenizerFactory("EdgeNGram", "minGramSize", "1", "maxGramSize", "2").create()
        (stream as Tokenizer).setReader(reader)
        assertTokenStreamContents(stream, arrayOf("t", "te"))
    }

    /** Test EdgeNGramFilterFactory with old defaults */
    @Test
    fun testEdgeNGramFilter() {
        val reader: Reader = StringReader("test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("EdgeNGram", "minGramSize", "1", "maxGramSize", "1").create(stream)
        assertTokenStreamContents(stream, arrayOf("t"))
    }

    /** Test EdgeNGramFilterFactory with min and max gram size */
    @Test
    fun testEdgeNGramFilter2() {
        val reader: Reader = StringReader("test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("EdgeNGram", "minGramSize", "1", "maxGramSize", "2").create(stream)
        assertTokenStreamContents(stream, arrayOf("t", "te"))
    }

    /** Test EdgeNGramFilterFactory with preserve option */
    @Test
    fun testEdgeNGramFilter3() {
        val reader: Reader = StringReader("test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("EdgeNGram", "minGramSize", "1", "maxGramSize", "2", "preserveOriginal", "true").create(stream)
        assertTokenStreamContents(stream, arrayOf("t", "te", "test"))
    }

    /** Test EdgeNGramFilterFactory on tokens with payloads */
    @Test
    fun testEdgeNGramFilterPayload() {
        val reader: Reader = StringReader("test|0.1")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("DelimitedPayload", "encoder", "float").create(stream)
        stream = tokenFilterFactory("EdgeNGram", "minGramSize", "1", "maxGramSize", "2").create(stream)

        stream.reset()
        while (stream.incrementToken()) {
            val payAttr: PayloadAttribute = stream.getAttribute(PayloadAttribute::class)!!
            assertNotNull(payAttr)
            val payData: BytesRef? = payAttr.payload
            assertNotNull(payData)
            val payFloat = PayloadHelper.decodeFloat(payData.bytes)
            assertEquals(0.1f, payFloat, 0.0f)
        }
        stream.end()
        stream.close()
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        var expected =
            expectThrows(IllegalArgumentException::class) {
                tokenizerFactory("NGram", "bogusArg", "bogusValue")
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))

        expected =
            expectThrows(IllegalArgumentException::class) {
                tokenizerFactory("EdgeNGram", "bogusArg", "bogusValue")
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))

        expected =
            expectThrows(IllegalArgumentException::class) {
                tokenFilterFactory("NGram", "minGramSize", "2", "maxGramSize", "5", "bogusArg", "bogusValue")
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))

        expected =
            expectThrows(IllegalArgumentException::class) {
                tokenFilterFactory("EdgeNGram", "minGramSize", "2", "maxGramSize", "5", "bogusArg", "bogusValue")
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
