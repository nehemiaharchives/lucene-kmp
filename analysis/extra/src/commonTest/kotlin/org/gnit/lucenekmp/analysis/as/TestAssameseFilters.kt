package org.gnit.lucenekmp.analysis.`as`

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

/** Simple tests to ensure the Assamese filter factories are working. */
class TestAssameseFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test AssameseNormalizationFilterFactory. */
    @Test
    @Throws(Exception::class)
    fun testAssameseNormalizer() {
        val reader: Reader = StringReader("অসমর ভাষা৷")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("AssameseNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("অসমৰ", "ভাষা।"))
    }

    /** Test AssameseStemFilterFactory. */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("ঘৰলৈ")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("AssameseNormalization").create(stream)
        stream = tokenFilterFactory("AssameseStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("ঘৰ"))
    }

    /** Test that bogus arguments result in exception. */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedAssameseNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("AssameseNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedAssameseNorm.message?.contains("Unknown parameters") == true)

        val expectedAssameseStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("AssameseStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedAssameseStem.message?.contains("Unknown parameters") == true)
    }
}
