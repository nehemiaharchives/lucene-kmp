package org.gnit.lucenekmp.analysis.su

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

/** Simple tests to ensure the Sundanese filter Factories are working. */
class TestSundaneseFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test SundaneseNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testSundaneseNormalizer() {
        val reader: Reader = StringReader("bébas–bébas")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("SundaneseNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("bebas-bebas"))
    }

    /** Test SundaneseStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("dibacakeun")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("SundaneseStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("baca"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedSundaneseNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("SundaneseNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedSundaneseNorm.message?.contains("Unknown parameters") == true)

        val expectedSundaneseStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("SundaneseStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedSundaneseStem.message?.contains("Unknown parameters") == true)
    }
}
