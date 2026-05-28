package org.gnit.lucenekmp.analysis.my

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Tests the Burmese stemmer. */
class TestBurmeseStemmer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testStemming() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source = MockTokenizer(MockTokenizer.WHITESPACE, false)
                var result: TokenStream = source
                result = BurmeseStemFilter(result)
                return TokenStreamComponents(source, result)
            }
        }
        checkOneTerm(analyzer, "စာအုပ်တွေ", "စာအုပ်")
        checkOneTerm(analyzer, "လူများ", "လူ")
        checkOneTerm(analyzer, "မြန်မာတို့", "မြန်မာ")
        analyzer.close()
    }
}
