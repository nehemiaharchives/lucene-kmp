package org.gnit.lucenekmp.analysis.ht

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

/** Simple tests to ensure the Haitian Creole filter Factories are working. */
class TestHaitianCreoleFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test HaitianCreoleNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testHaitianCreoleNormalizer() {
        val reader: Reader = StringReader("Kreyòl–Ayisyen")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("HaitianCreoleNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("Kreyol-Ayisyen"))
    }

    /** Test HaitianCreoleStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("rapidman")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("HaitianCreoleStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("rapid"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedHaitianCreoleNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("HaitianCreoleNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedHaitianCreoleNorm.message?.contains("Unknown parameters") == true)

        val expectedHaitianCreoleStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("HaitianCreoleStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedHaitianCreoleStem.message?.contains("Unknown parameters") == true)
    }
}
