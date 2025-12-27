package org.gnit.lucenekmp.analysis.te

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the Telugu filter factories are working. */
class TestTeluguFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    /** Test IndicNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testIndicNormalizer() {
        val reader: Reader = StringReader("ప్  अाैर")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ప్", "और"))
    }

    /** Test TeluguNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testTeluguNormalizer() {
        val reader: Reader = StringReader("వస్తువులు")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("TeluguNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("వస్తుమలు"))
    }

    /** Test TeluguStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("వస్తువులు")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("TeluguNormalization").create(stream)
        stream = tokenFilterFactory("TeluguStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("వస్తుమ"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("IndicNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)

        val expectedTeluguNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("TeluguNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedTeluguNorm.message?.contains("Unknown parameters") == true)

        val expectedTeluguStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("TeluguStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedTeluguStem.message?.contains("Unknown parameters") == true)
    }
}
