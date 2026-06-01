package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests to ensure the simple truncation filter factory is working. */
class TestTruncateTokenFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    /** Ensure the filter actually truncates text. */
    @Test
    fun testTruncating() {
        val reader = StringReader("abcdefg 1234567 ABCDEFG abcde abc 12345 123")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("Truncate", TruncateTokenFilterFactory.PREFIX_LENGTH_KEY, "5").create(stream)
        assertTokenStreamContents(stream, arrayOf("abcde", "12345", "ABCDE", "abcde", "abc", "12345", "123"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory(
                "Truncate",
                TruncateTokenFilterFactory.PREFIX_LENGTH_KEY,
                "5",
                "bogusArg",
                "bogusValue"
            )
        }
        assertTrue(expected.message!!.contains("Unknown parameter(s):"))
    }

    /** Test that negative prefix length result in exception */
    @Test
    fun testNonPositivePrefixLengthArgument() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("Truncate", TruncateTokenFilterFactory.PREFIX_LENGTH_KEY, "-5")
        }
        assertTrue(expected.message!!.contains("${TruncateTokenFilterFactory.PREFIX_LENGTH_KEY} parameter must be a positive number: -5"))
    }
}
