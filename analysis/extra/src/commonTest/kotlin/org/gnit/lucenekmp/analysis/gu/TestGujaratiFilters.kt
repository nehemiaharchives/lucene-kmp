package org.gnit.lucenekmp.analysis.gu

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

/** Simple tests to ensure the Gujarati filter Factories are working. */
class TestGujaratiFilters : BaseTokenStreamFactoryTestCase() {
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
        val reader: Reader = StringReader("ত্‍ અાેર")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ৎ", "ઓર"))
    }

    /** Test GujaratiNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testGujaratiNormalizer() {
        val reader: Reader = StringReader("ગુજરાતી")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("GujaratiNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ગુજરાતી"))
    }

    /** Test GujaratiStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("ગુજરાતીઓ")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("GujaratiNormalization").create(stream)
        stream = tokenFilterFactory("GujaratiStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("ગુજરાતી"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("IndicNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)

        val expectedGujaratiNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("GujaratiNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedGujaratiNorm.message?.contains("Unknown parameters") == true)

        val expectedGujaratiStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("GujaratiStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedGujaratiStem.message?.contains("Unknown parameters") == true)
    }
}
