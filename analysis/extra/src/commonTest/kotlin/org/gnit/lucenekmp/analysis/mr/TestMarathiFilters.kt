package org.gnit.lucenekmp.analysis.mr

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

/** Simple tests to ensure the Marathi filter Factories are working. */
class TestMarathiFilters : BaseTokenStreamFactoryTestCase() {
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
        val reader: Reader = StringReader("ত্‍ अाैर")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ৎ", "और"))
    }

    /** Test MarathiNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testMarathiNormalizer() {
        val reader: Reader = StringReader("क़िताब")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("MarathiNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("किताब"))
    }

    /** Test MarathiStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("पुस्तके")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("MarathiNormalization").create(stream)
        stream = tokenFilterFactory("MarathiStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("पुसतक"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("IndicNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)

        val expectedMarathiNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("MarathiNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedMarathiNorm.message?.contains("Unknown parameters") == true)

        val expectedMarathiStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("MarathiStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedMarathiStem.message?.contains("Unknown parameters") == true)
    }
}
