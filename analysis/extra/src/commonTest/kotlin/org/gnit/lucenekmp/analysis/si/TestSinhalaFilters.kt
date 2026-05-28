package org.gnit.lucenekmp.analysis.si

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

/** Simple tests to ensure the Sinhala filter factories are working. */
class TestSinhalaFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test SinhalaNormalizationFilterFactory. */
    @Test
    @Throws(Exception::class)
    fun testSinhalaNormalizer() {
        val reader: Reader = StringReader("සිංහල\u200D වාක්\u200Dය෴")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("SinhalaNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("සිංහල", "වාක්ය।"))
    }

    /** Test SinhalaStemFilterFactory. */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("ගෙදරට")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("SinhalaStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("ගෙදර"))
    }

    /** Test that bogus arguments result in exception. */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedSinhalaNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("SinhalaNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedSinhalaNorm.message?.contains("Unknown parameters") == true)

        val expectedSinhalaStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("SinhalaStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedSinhalaStem.message?.contains("Unknown parameters") == true)
    }
}
