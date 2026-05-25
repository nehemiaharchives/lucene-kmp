package org.gnit.lucenekmp.analysis.jv

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

/** Simple tests to ensure the Javanese filter Factories are working. */
class TestJavaneseFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test JavaneseNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testJavaneseNormalizer() {
        val reader: Reader = StringReader("bocah–bocah")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("JavaneseNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("bocah-bocah"))
    }

    /** Test JavaneseStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("ditulisake")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("JavaneseStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("tulis"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedJavaneseNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("JavaneseNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedJavaneseNorm.message?.contains("Unknown parameters") == true)

        val expectedJavaneseStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("JavaneseStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedJavaneseStem.message?.contains("Unknown parameters") == true)
    }
}
