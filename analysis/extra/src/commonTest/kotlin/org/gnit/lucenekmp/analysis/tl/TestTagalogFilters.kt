package org.gnit.lucenekmp.analysis.tl

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

/** Simple tests to ensure the Tagalog filter Factories are working. */
class TestTagalogFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test TagalogNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testTagalogNormalizer() {
        val reader: Reader = StringReader("Tagalog")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("TagalogNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("Tagalog"))
    }

    /** Test TagalogStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("Pilipino")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("TagalogStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("Pilipino"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedTagalogNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("TagalogNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedTagalogNorm.message?.contains("Unknown parameters") == true)

        val expectedTagalogStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("TagalogStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedTagalogStem.message?.contains("Unknown parameters") == true)
    }
}
