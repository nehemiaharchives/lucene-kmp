package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertTrue

class TestLimitTokenOffsetFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun test() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val reader = StringReader("A1 B2 C3 D4 E5 F6")
            val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
            tokenizer.setReader(reader)
            tokenizer.enableChecks = consumeAll
            var stream: TokenStream = tokenizer
            stream =
                tokenFilterFactory(
                    "LimitTokenOffset",
                    LimitTokenOffsetFilterFactory.MAX_START_OFFSET,
                    "3",
                    LimitTokenOffsetFilterFactory.CONSUME_ALL_TOKENS_KEY,
                    consumeAll.toString()
                ).create(stream)
            assertTokenStreamContents(stream, arrayOf("A1", "B2"))
        }
    }

    @Test
    fun testRequired() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("LimitTokenOffset")
        }
        assertTrue(
            0 < expected.message!!.indexOf(LimitTokenOffsetFilterFactory.MAX_START_OFFSET),
            "exception doesn't mention param: ${expected.message}"
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory(
                "LimitTokenOffset",
                LimitTokenOffsetFilterFactory.MAX_START_OFFSET,
                "3",
                "bogusArg",
                "bogusValue"
            )
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
