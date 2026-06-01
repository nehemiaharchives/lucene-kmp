package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertTrue

class TestFingerprintFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun test() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val reader = StringReader("A1 B2 A1 D4 C3")
            val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
            tokenizer.setReader(reader)
            tokenizer.enableChecks = consumeAll
            var stream: TokenStream = tokenizer
            stream =
                tokenFilterFactory(
                    "Fingerprint",
                    FingerprintFilterFactory.MAX_OUTPUT_TOKEN_SIZE_KEY,
                    "256",
                    FingerprintFilterFactory.SEPARATOR_KEY,
                    "_"
                ).create(stream)
            assertTokenStreamContents(stream, arrayOf("A1_B2_C3_D4"))
        }
    }

    @Test
    fun testRequired() {
        tokenFilterFactory("Fingerprint")
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory(
                "Fingerprint",
                FingerprintFilterFactory.MAX_OUTPUT_TOKEN_SIZE_KEY,
                "3",
                "bogusArg",
                "bogusValue"
            )
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
