package org.gnit.lucenekmp.analysis.sw

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

/** Simple tests to ensure the Swahili filter Factories are working. */
class TestSwahiliFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test SwahiliNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testSwahiliNormalizer() {
        val reader: Reader = StringReader("ng’ombe")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("SwahiliNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ng'ombe"))
    }

    /** Test SwahiliStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("ninasoma")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("SwahiliStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("som"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedSwahiliNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("SwahiliNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedSwahiliNorm.message?.contains("Unknown parameters") == true)

        val expectedSwahiliStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("SwahiliStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedSwahiliStem.message?.contains("Unknown parameters") == true)
    }
}
