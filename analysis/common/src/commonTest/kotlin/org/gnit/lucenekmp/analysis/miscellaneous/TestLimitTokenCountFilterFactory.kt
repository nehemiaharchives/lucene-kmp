package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertTrue

class TestLimitTokenCountFilterFactory : BaseTokenStreamFactoryTestCase() {
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
                    "LimitTokenCount",
                    LimitTokenCountFilterFactory.MAX_TOKEN_COUNT_KEY,
                    "3",
                    LimitTokenCountFilterFactory.CONSUME_ALL_TOKENS_KEY,
                    consumeAll.toString()
                ).create(stream)
            assertTokenStreamContents(stream, arrayOf("A1", "B2", "C3"))
        }
    }

    @Test
    fun testRequired() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("LimitTokenCount")
        }
        assertTrue(
            0 < expected.message!!.indexOf(LimitTokenCountFilterFactory.MAX_TOKEN_COUNT_KEY),
            "exception doesn't mention param: ${expected.message}"
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory(
                "LimitTokenCount",
                LimitTokenCountFilterFactory.MAX_TOKEN_COUNT_KEY,
                "3",
                "bogusArg",
                "bogusValue"
            )
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
