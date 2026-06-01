package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Test the truncate token filter. */
class TestTruncateTokenFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testTruncating() {
        var stream: TokenStream =
            whitespaceMockTokenizer("abcdefg 1234567 ABCDEFG abcde abc 12345 123")
        stream = TruncateTokenFilter(stream, 5)
        assertTokenStreamContents(
            stream,
            arrayOf("abcde", "12345", "ABCDE", "abcde", "abc", "12345", "123")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNonPositiveLength() {
        assertFailsWith<IllegalArgumentException> {
            TruncateTokenFilter(whitespaceMockTokenizer("length must be a positive number"), -48)
        }
    }
}
