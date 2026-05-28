package org.gnit.lucenekmp.analysis.my

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Tests the Burmese normalizer. */
class TestBurmeseNormalizer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testDigitsAndJoiners() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source = MockTokenizer(MockTokenizer.WHITESPACE, false)
                var result: TokenStream = source
                result = BurmeseNormalizationFilter(result)
                return TokenStreamComponents(source, result)
            }
        }
        checkOneTerm(analyzer, "၁၂\u200B၃၄", "1234")
        analyzer.close()
    }
}
