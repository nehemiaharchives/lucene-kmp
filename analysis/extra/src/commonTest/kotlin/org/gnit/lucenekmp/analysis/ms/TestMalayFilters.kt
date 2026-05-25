package org.gnit.lucenekmp.analysis.ms

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

/** Simple tests to ensure the Malay filter Factories are working. */
class TestMalayFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test MalayNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testMalayNormalizer() {
        val reader: Reader = StringReader("kata–kata")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("MalayNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("kata-kata"))
    }

    /** Test MalayStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("dituliskan")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("MalayStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("tulis"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedMalayNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("MalayNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedMalayNorm.message?.contains("Unknown parameters") == true)

        val expectedMalayStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("MalayStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedMalayStem.message?.contains("Unknown parameters") == true)
    }
}
