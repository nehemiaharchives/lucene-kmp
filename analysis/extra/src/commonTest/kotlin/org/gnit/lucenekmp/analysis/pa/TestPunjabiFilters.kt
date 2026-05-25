package org.gnit.lucenekmp.analysis.pa

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.AnalysisExtraFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the Punjabi filter Factories are working. */
class TestPunjabiFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test IndicNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testIndicNormalizer() {
        val reader: Reader = StringReader("ੳੂਰ")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ਊਰ"))
    }

    /** Test PunjabiNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testPunjabiNormalizer() {
        val reader: Reader = StringReader("ਪੰਜਾਬੀ੤")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("PunjabiNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ਪੰਜਾਬੀ।"))
    }

    /** Test PunjabiStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("ਭੱਜਣਾ")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("PunjabiNormalization").create(stream)
        stream = tokenFilterFactory("PunjabiStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("ਭੱਜ"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("IndicNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)

        val expectedPunjabiNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("PunjabiNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedPunjabiNorm.message?.contains("Unknown parameters") == true)

        val expectedPunjabiStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("PunjabiStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedPunjabiStem.message?.contains("Unknown parameters") == true)
    }
}
