package org.gnit.lucenekmp.analysis.ha

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

/** Simple tests to ensure the Hausa filter Factories are working. */
class TestHausaFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test HausaNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testHausaNormalizer() {
        val reader: Reader = StringReader("ɗalibi–ɓangare")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("HausaNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("dalibi-bangare"))
    }

    /** Test HausaStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("nakarantawa")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("HausaStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("karanta"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedHausaNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("HausaNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedHausaNorm.message?.contains("Unknown parameters") == true)

        val expectedHausaStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("HausaStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedHausaStem.message?.contains("Unknown parameters") == true)
    }
}
