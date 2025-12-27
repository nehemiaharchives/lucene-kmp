package org.gnit.lucenekmp.analysis.hi

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

/** Simple tests to ensure the Hindi filter Factories are working. */
class TestHindiFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    /** Test IndicNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testIndicNormalizer() {
        val reader: Reader = StringReader("ত্‍ अाैर")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ৎ", "और"))
    }

    /** Test HindiNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testHindiNormalizer() {
        val reader: Reader = StringReader("क़िताब")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("HindiNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("किताब"))
    }

    /** Test HindiStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("किताबें")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("HindiNormalization").create(stream)
        stream = tokenFilterFactory("HindiStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("किताब"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("IndicNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)

        val expectedHindiNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("HindiNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedHindiNorm.message?.contains("Unknown parameters") == true)

        val expectedHindiStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("HindiStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedHindiStem.message?.contains("Unknown parameters") == true)
    }
}
