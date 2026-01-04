package org.gnit.lucenekmp.analysis.ur

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

/** Simple tests to ensure the Urdu filter Factories are working. */
class TestUrduFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test UrduNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testUrduNormalizer() {
        val reader: Reader = StringReader("اردو")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("UrduNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("اردو"))
    }

    /** Test UrduStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("پاکستانی")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("UrduStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("پاکستانی"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedUrduNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("UrduNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedUrduNorm.message?.contains("Unknown parameters") == true)

        val expectedUrduStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("UrduStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedUrduStem.message?.contains("Unknown parameters") == true)
    }
}
