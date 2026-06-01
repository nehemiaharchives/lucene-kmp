package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

class TestFingerprintFilter : BaseTokenStreamTestCase() {
    @Test
    fun testDupsAndSorting() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val tokenizer = whitespaceMockTokenizer("B A B E")
            tokenizer.enableChecks = consumeAll
            val stream: TokenStream = FingerprintFilter(tokenizer)
            assertTokenStreamContents(stream, arrayOf("A B E"))
        }
    }

    @Test
    fun testAllDupValues() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val tokenizer = whitespaceMockTokenizer("B2 B2")
            tokenizer.enableChecks = consumeAll
            val stream: TokenStream = FingerprintFilter(tokenizer)
            assertTokenStreamContents(stream, arrayOf("B2"))
        }
    }

    @Test
    fun testMaxFingerprintSize() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val tokenizer = whitespaceMockTokenizer("B2 A1 C3 D4 E5 F6 G7 H1")
            tokenizer.enableChecks = consumeAll
            val stream: TokenStream = FingerprintFilter(tokenizer, 4, ' ')
            assertTokenStreamContents(stream, emptyArray())
        }
    }

    @Test
    fun testCustomSeparator() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val tokenizer = whitespaceMockTokenizer("B2 A1 C3 B2")
            tokenizer.enableChecks = consumeAll
            val stream: TokenStream =
                FingerprintFilter(tokenizer, FingerprintFilter.DEFAULT_MAX_OUTPUT_TOKEN_SIZE, '_')
            assertTokenStreamContents(stream, arrayOf("A1_B2_C3"))
        }
    }

    @Test
    fun testSingleToken() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val tokenizer = whitespaceMockTokenizer("A1")
            tokenizer.enableChecks = consumeAll
            val stream: TokenStream = FingerprintFilter(tokenizer)
            assertTokenStreamContents(stream, arrayOf("A1"))
        }
    }

    @Test
    fun testEmpty() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val tokenizer = whitespaceMockTokenizer("")
            tokenizer.enableChecks = consumeAll
            val stream: TokenStream = FingerprintFilter(tokenizer)
            assertTokenStreamContents(stream, emptyArray())
        }
    }
}
