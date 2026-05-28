package org.gnit.lucenekmp.analysis.kn

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

/** Simple tests to ensure the Kannada filter factories are working. */
class TestKannadaFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test KannadaNormalizationFilterFactory. */
    @Test
    @Throws(Exception::class)
    fun testKannadaNormalizer() {
        val reader: Reader = StringReader("ಕನ್ನಡ ಭಾಷೆ:")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("KannadaNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ಕನ್ನಡ", "ಭಾಷೆಃ"))
    }

    /** Test KannadaStemFilterFactory. */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("ಮನೆಗೆ")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("KannadaNormalization").create(stream)
        stream = tokenFilterFactory("KannadaStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("ಮನೆ"))
    }

    /** Test that bogus arguments result in exception. */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedKannadaNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("KannadaNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedKannadaNorm.message?.contains("Unknown parameters") == true)

        val expectedKannadaStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("KannadaStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedKannadaStem.message?.contains("Unknown parameters") == true)
    }
}
