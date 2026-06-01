package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

class TestScandinavianNormalizationFilter : BaseTokenStreamTestCase() {
    @Test
    fun testDefault() {
        val analyzer = createAnalyzer()

        checkOneTerm(analyzer, "aeäaeeea", "æææeea")

        checkOneTerm(analyzer, "aeäaeeeae", "æææeeæ")
        checkOneTerm(analyzer, "aeaeeeae", "ææeeæ")

        checkOneTerm(analyzer, "bøen", "bøen")
        checkOneTerm(analyzer, "bOEen", "bØen")
        checkOneTerm(analyzer, "åene", "åene")

        checkOneTerm(analyzer, "blåbærsyltetøj", "blåbærsyltetøj")
        checkOneTerm(analyzer, "blaabaersyltetöj", "blåbærsyltetøj")
        checkOneTerm(analyzer, "räksmörgås", "ræksmørgås")
        checkOneTerm(analyzer, "raeksmörgaos", "ræksmørgås")
        checkOneTerm(analyzer, "raeksmörgaas", "ræksmørgås")
        checkOneTerm(analyzer, "raeksmoergås", "ræksmørgås")

        checkOneTerm(analyzer, "ab", "ab")
        checkOneTerm(analyzer, "ob", "ob")
        checkOneTerm(analyzer, "Ab", "Ab")
        checkOneTerm(analyzer, "Ob", "Ob")

        checkOneTerm(analyzer, "å", "å")

        checkOneTerm(analyzer, "aa", "å")
        checkOneTerm(analyzer, "aA", "å")
        checkOneTerm(analyzer, "ao", "å")
        checkOneTerm(analyzer, "aO", "å")

        checkOneTerm(analyzer, "AA", "Å")
        checkOneTerm(analyzer, "Aa", "Å")
        checkOneTerm(analyzer, "Ao", "Å")
        checkOneTerm(analyzer, "AO", "Å")

        checkOneTerm(analyzer, "æ", "æ")
        checkOneTerm(analyzer, "ä", "æ")

        checkOneTerm(analyzer, "Æ", "Æ")
        checkOneTerm(analyzer, "Ä", "Æ")

        checkOneTerm(analyzer, "ae", "æ")
        checkOneTerm(analyzer, "aE", "æ")

        checkOneTerm(analyzer, "Ae", "Æ")
        checkOneTerm(analyzer, "AE", "Æ")

        checkOneTerm(analyzer, "ö", "ø")
        checkOneTerm(analyzer, "ø", "ø")
        checkOneTerm(analyzer, "Ö", "Ø")
        checkOneTerm(analyzer, "Ø", "Ø")

        checkOneTerm(analyzer, "oo", "ø")
        checkOneTerm(analyzer, "oe", "ø")
        checkOneTerm(analyzer, "oO", "ø")
        checkOneTerm(analyzer, "oE", "ø")

        checkOneTerm(analyzer, "Oo", "Ø")
        checkOneTerm(analyzer, "Oe", "Ø")
        checkOneTerm(analyzer, "OO", "Ø")
        checkOneTerm(analyzer, "OE", "Ø")
        analyzer.close()
    }

    /** check that the empty string doesn't cause issues */
    @Test
    fun testEmptyTerm() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(
                        tokenizer,
                        ScandinavianNormalizationFilter(tokenizer)
                    )
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    fun testRandomData() {
        val analyzer = createAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }

    private fun createAnalyzer(): Analyzer {
        return object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val stream: TokenStream = ScandinavianNormalizationFilter(tokenizer)
                return TokenStreamComponents(tokenizer, stream)
            }
        }
    }
}
