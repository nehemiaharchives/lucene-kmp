package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TestLimitTokenCountFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun test() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val tokenizer: MockTokenizer = whitespaceMockTokenizer("A1 B2 C3 D4 E5 F6")
            tokenizer.enableChecks = consumeAll
            val stream: TokenStream = LimitTokenCountFilter(tokenizer, 3, consumeAll)
            assertTokenStreamContents(stream, arrayOf("A1", "B2", "C3"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIllegalArguments() {
        assertFailsWith<IllegalArgumentException> {
            LimitTokenCountFilter(whitespaceMockTokenizer("A1 B2 C3 D4 E5 F6"), -1)
        }
    }
}
