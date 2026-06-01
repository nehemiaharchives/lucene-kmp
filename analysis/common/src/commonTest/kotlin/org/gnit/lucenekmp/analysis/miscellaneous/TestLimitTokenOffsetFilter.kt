package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TestLimitTokenOffsetFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun test() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val tokenizer: MockTokenizer = whitespaceMockTokenizer("A1 B2 C3 D4 E5 F6")
            tokenizer.enableChecks = consumeAll
            // note with '3', this test would fail if erroneously the filter used endOffset instead
            val stream: TokenStream = LimitTokenOffsetFilter(tokenizer, 3, consumeAll)
            assertTokenStreamContents(stream, arrayOf("A1", "B2"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIllegalArguments() {
        assertFailsWith<IllegalArgumentException> {
            LimitTokenOffsetFilter(whitespaceMockTokenizer("A1 B2 C3 D4 E5 F6"), -1)
        }
    }
}
