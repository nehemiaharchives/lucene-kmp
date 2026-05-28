package org.gnit.lucenekmp.analysis.uz

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Tests the Uzbek normalizer. */
class TestUzbekNormalizer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testApostropheVariants() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source = MockTokenizer(MockTokenizer.WHITESPACE, false)
                var result: TokenStream = source
                result = UzbekNormalizationFilter(result)
                return TokenStreamComponents(source, result)
            }
        }
        checkOneTerm(analyzer, "Oʻzbekiston", "O'zbekiston")
        checkOneTerm(analyzer, "g‘isht", "g'isht")
        analyzer.close()
    }
}
