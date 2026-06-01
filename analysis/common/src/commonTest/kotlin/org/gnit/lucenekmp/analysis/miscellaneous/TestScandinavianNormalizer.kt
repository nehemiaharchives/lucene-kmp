package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.miscellaneous.ScandinavianNormalizer.Foldings
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Tests low level the normalizer functionality */
class TestScandinavianNormalizer : BaseTokenStreamTestCase() {
    @Test
    fun testNoFoldings() {
        val analyzer = createAnalyzer(emptySet())
        checkOneTerm(analyzer, "aa", "aa")
        checkOneTerm(analyzer, "ao", "ao")
        checkOneTerm(analyzer, "ae", "ae")
        checkOneTerm(analyzer, "oo", "oo")
        checkOneTerm(analyzer, "oe", "oe")
        analyzer.close()
    }

    @Test
    fun testAeFolding() {
        val analyzer = createAnalyzer(setOf(Foldings.AE))
        checkOneTerm(analyzer, "aa", "aa")
        checkOneTerm(analyzer, "ao", "ao")
        checkOneTerm(analyzer, "ae", "æ")
        checkOneTerm(analyzer, "aE", "æ")
        checkOneTerm(analyzer, "Ae", "Æ")
        checkOneTerm(analyzer, "AE", "Æ")
        checkOneTerm(analyzer, "oo", "oo")
        checkOneTerm(analyzer, "oe", "oe")
        analyzer.close()
    }

    @Test
    fun testAaFolding() {
        val analyzer = createAnalyzer(setOf(Foldings.AA))
        checkOneTerm(analyzer, "aa", "å")
        checkOneTerm(analyzer, "aA", "å")
        checkOneTerm(analyzer, "Aa", "Å")
        checkOneTerm(analyzer, "AA", "Å")
        checkOneTerm(analyzer, "ao", "ao")
        checkOneTerm(analyzer, "ae", "ae")
        checkOneTerm(analyzer, "oo", "oo")
        checkOneTerm(analyzer, "oe", "oe")
        analyzer.close()
    }

    @Test
    fun testOeFolding() {
        val analyzer = createAnalyzer(setOf(Foldings.OE))
        checkOneTerm(analyzer, "aa", "aa")
        checkOneTerm(analyzer, "ao", "ao")
        checkOneTerm(analyzer, "ae", "ae")
        checkOneTerm(analyzer, "oo", "oo")
        checkOneTerm(analyzer, "oe", "ø")
        checkOneTerm(analyzer, "oE", "ø")
        checkOneTerm(analyzer, "Oe", "Ø")
        checkOneTerm(analyzer, "OE", "Ø")
        analyzer.close()
    }

    @Test
    fun testOoFolding() {
        val analyzer = createAnalyzer(setOf(Foldings.OO))
        checkOneTerm(analyzer, "aa", "aa")
        checkOneTerm(analyzer, "ao", "ao")
        checkOneTerm(analyzer, "ae", "ae")
        checkOneTerm(analyzer, "oo", "ø")
        checkOneTerm(analyzer, "oO", "ø")
        checkOneTerm(analyzer, "Oo", "Ø")
        checkOneTerm(analyzer, "OO", "Ø")
        checkOneTerm(analyzer, "oe", "oe")
        analyzer.close()
    }

    @Test
    fun testAoFolding() {
        val analyzer = createAnalyzer(setOf(Foldings.AO))
        checkOneTerm(analyzer, "aa", "aa")
        checkOneTerm(analyzer, "ao", "å")
        checkOneTerm(analyzer, "aO", "å")
        checkOneTerm(analyzer, "Ao", "Å")
        checkOneTerm(analyzer, "AO", "Å")
        checkOneTerm(analyzer, "ae", "ae")
        checkOneTerm(analyzer, "oo", "oo")
        checkOneTerm(analyzer, "oe", "oe")
        analyzer.close()
    }

    private fun createAnalyzer(foldings: Set<Foldings>): Analyzer {
        return object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val stream: TokenStream =
                    object : TokenFilter(tokenizer) {
                        private val charTermAttribute = addAttribute(CharTermAttribute::class)
                        private val normalizer = ScandinavianNormalizer(foldings)

                        override fun incrementToken(): Boolean {
                            if (!input.incrementToken()) {
                                return false
                            }
                            charTermAttribute.setLength(
                                normalizer.processToken(
                                    charTermAttribute.buffer(),
                                    charTermAttribute.length
                                )
                            )
                            return true
                        }
                    }
                return TokenStreamComponents(tokenizer, stream)
            }
        }
    }
}
