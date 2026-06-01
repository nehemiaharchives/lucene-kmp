package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertTrue

class TestCodepointCountFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testPositionIncrements() {
        val reader = StringReader("foo foobar super-duper-trooper")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("CodepointCount", "min", "4", "max", "10").create(stream)
        assertTokenStreamContents(stream, arrayOf("foobar"), intArrayOf(2))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("CodepointCount", "min", "4", "max", "5", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }

    /** Test that invalid arguments result in exception */
    @Test
    fun testInvalidArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            val reader = StringReader("foo foobar super-duper-trooper")
            var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
            (stream as Tokenizer).setReader(reader)
            tokenFilterFactory("CodepointCount", CodepointCountFilterFactory.MIN_KEY, "5", CodepointCountFilterFactory.MAX_KEY, "4")
                .create(stream)
        }
        assertTrue(expected.message!!.contains("maximum length must not be greater than minimum length"))
    }
}
