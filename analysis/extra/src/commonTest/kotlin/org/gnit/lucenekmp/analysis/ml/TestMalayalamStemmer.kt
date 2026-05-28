package org.gnit.lucenekmp.analysis.ml

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Tests the Malayalam stemmer. */
class TestMalayalamStemmer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testNounSuffixes() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source = MockTokenizer(MockTokenizer.WHITESPACE, false)
                var result: TokenStream = source
                result = MalayalamStemFilter(result)
                return TokenStreamComponents(source, result)
            }
        }
        checkOneTerm(analyzer, "പുസ്തകങ്ങൾ", "പുസ്തക")
        checkOneTerm(analyzer, "കുട്ടികളുടെ", "കുട്ടി")
        checkOneTerm(analyzer, "രാജ്യത്തിൽ", "രാജ്യ")
        analyzer.close()
    }
}
