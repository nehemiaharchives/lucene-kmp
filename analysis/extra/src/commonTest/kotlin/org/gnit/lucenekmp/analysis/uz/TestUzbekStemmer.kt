package org.gnit.lucenekmp.analysis.uz

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Tests the Uzbek stemmer. */
class TestUzbekStemmer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testNominalSuffixes() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source = MockTokenizer(MockTokenizer.WHITESPACE, false)
                var result: TokenStream = source
                result = UzbekStemFilter(result)
                return TokenStreamComponents(source, result)
            }
        }
        checkOneTerm(analyzer, "kitoblar", "kitob")
        checkOneTerm(analyzer, "kitoblarning", "kitob")
        checkOneTerm(analyzer, "uylarimizdan", "uy")
        analyzer.close()
    }
}
