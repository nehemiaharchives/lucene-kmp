package org.gnit.lucenekmp.analysis.tr

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the apostrophe filter factory is working. */
class TestApostropheFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    /** Ensure the filter actually removes characters after an apostrophe. */
    @Test
    @Throws(Exception::class)
    fun testApostrophes() {
        val reader: Reader = StringReader("Türkiye'de 2003'te Van Gölü'nü gördüm")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("Apostrophe").create(stream)
        assertTokenStreamContents(stream, arrayOf("Türkiye", "2003", "Van", "Gölü", "gördüm"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("Apostrophe", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameter(s):") == true)
    }
}
