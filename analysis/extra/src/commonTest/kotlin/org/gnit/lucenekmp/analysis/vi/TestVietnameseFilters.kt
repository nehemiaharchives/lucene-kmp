package org.gnit.lucenekmp.analysis.vi

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

/** Simple tests to ensure the Vietnamese filter Factories are working. */
class TestVietnameseFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test VietnameseNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testVietnameseNormalizer() {
        val reader: Reader = StringReader("điện")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("VietnameseNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("dien"))
    }

    /** Test VietnameseStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("dien")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("VietnameseStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("dien"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("VietnameseNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedNorm.message?.contains("Unknown parameters") == true)

        val expectedStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("VietnameseStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedStem.message?.contains("Unknown parameters") == true)
    }
}
