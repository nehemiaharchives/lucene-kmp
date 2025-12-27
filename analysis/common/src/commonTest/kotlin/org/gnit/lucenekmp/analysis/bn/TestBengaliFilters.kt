package org.gnit.lucenekmp.analysis.bn

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

/** Test Bengali filter factories. */
class TestBengaliFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    /** Test IndicNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testIndicNormalizer() {
        val reader: Reader = StringReader("ত্‍ আমি")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ৎ", "আমি"))
    }

    /** Test BengaliNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testBengaliNormalizer() {
        val reader: Reader = StringReader("বাড়ী")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("BengaliNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("বারি"))
    }

    /** Test BengaliStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("বাড়ী")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("BengaliNormalization").create(stream)
        stream = tokenFilterFactory("BengaliStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("বার"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("IndicNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)

        val expectedBengaliNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("BengaliNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedBengaliNorm.message?.contains("Unknown parameters") == true)

        val expectedBengaliStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("BengaliStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedBengaliStem.message?.contains("Unknown parameters") == true)
    }
}
