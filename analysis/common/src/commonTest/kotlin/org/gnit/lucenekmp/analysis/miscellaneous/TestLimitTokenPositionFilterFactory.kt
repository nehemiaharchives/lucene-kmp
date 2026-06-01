package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertTrue

class TestLimitTokenPositionFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testMaxPosition1() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val reader = StringReader("A1 B2 C3 D4 E5 F6")
            val tokenizer = whitespaceMockTokenizer(reader)
            tokenizer.enableChecks = consumeAll
            var stream: TokenStream = tokenizer
            stream =
                tokenFilterFactory(
                    "LimitTokenPosition",
                    LimitTokenPositionFilterFactory.MAX_TOKEN_POSITION_KEY,
                    "1",
                    LimitTokenPositionFilterFactory.CONSUME_ALL_TOKENS_KEY,
                    consumeAll.toString()
                ).create(stream)
            assertTokenStreamContents(stream, arrayOf("A1"))
        }
    }

    @Test
    fun testMissingParam() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("LimitTokenPosition")
        }
        assertTrue(
            0 < expected.message!!.indexOf(LimitTokenPositionFilterFactory.MAX_TOKEN_POSITION_KEY),
            "exception doesn't mention param: ${expected.message}"
        )
    }

    @Test
    fun testMaxPosition1WithShingles() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val reader = StringReader("one two three four five")
            val tokenizer: MockTokenizer = whitespaceMockTokenizer(reader)
            tokenizer.enableChecks = consumeAll
            var stream: TokenStream = tokenizer
            stream =
                tokenFilterFactory("Shingle", "minShingleSize", "2", "maxShingleSize", "3", "outputUnigrams", "true")
                    .create(stream)
            stream =
                tokenFilterFactory(
                    "LimitTokenPosition",
                    LimitTokenPositionFilterFactory.MAX_TOKEN_POSITION_KEY,
                    "1",
                    LimitTokenPositionFilterFactory.CONSUME_ALL_TOKENS_KEY,
                    consumeAll.toString()
                ).create(stream)
            assertTokenStreamContents(stream, arrayOf("one", "one two", "one two three"))
        }
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("LimitTokenPosition", "maxTokenPosition", "3", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
