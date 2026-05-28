package org.gnit.lucenekmp.analysis.ml

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.`in`.IndicNormalizationFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Tests the Malayalam normalizer. */
class TestMalayalamNormalizer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testChilluSequence() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source = MockTokenizer(MockTokenizer.WHITESPACE, false)
                var result: TokenStream = source
                result = IndicNormalizationFilter(result)
                result = MalayalamNormalizationFilter(result)
                return TokenStreamComponents(source, result)
            }
        }
        checkOneTerm(analyzer, "അവന്‍", "അവൻ")
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testJoinerRemoval() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source = MockTokenizer(MockTokenizer.WHITESPACE, false)
                var result: TokenStream = source
                result = MalayalamNormalizationFilter(result)
                return TokenStreamComponents(source, result)
            }
        }
        checkOneTerm(analyzer, "മ\u200Cലയാളം", "മലയാളം")
        analyzer.close()
    }
}
