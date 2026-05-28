package org.gnit.lucenekmp.analysis.no

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.ScandinavianNormalizationFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

class TestNorwegianNormalizationFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testDefault() {
        val analyzer = createAnalyzer()

        checkOneTerm(analyzer, "aeäaeeea", "æææeea") // should not cause ArrayIndexOutOfBoundsException

        checkOneTerm(analyzer, "aeäaeeeae", "æææeeæ")
        checkOneTerm(analyzer, "aeaeeeae", "ææeeæ")

        checkOneTerm(analyzer, "bøen", "bøen")
        checkOneTerm(analyzer, "bOEen", "bØen")
        checkOneTerm(analyzer, "åene", "åene")

        checkOneTerm(analyzer, "blåbærsyltetøj", "blåbærsyltetøj")
        checkOneTerm(analyzer, "blaabaersyltetöj", "blåbærsyltetøj")
        checkOneTerm(analyzer, "räksmörgås", "ræksmørgås")
        checkOneTerm(analyzer, "raeksmörgaas", "ræksmørgås")
        checkOneTerm(analyzer, "raeksmoergås", "ræksmørgås")

        checkOneTerm(analyzer, "ab", "ab")
        checkOneTerm(analyzer, "ob", "ob")
        checkOneTerm(analyzer, "Ab", "Ab")
        checkOneTerm(analyzer, "Ob", "Ob")

        checkOneTerm(analyzer, "å", "å")

        checkOneTerm(analyzer, "aa", "å")
        checkOneTerm(analyzer, "aA", "å")
        checkOneTerm(analyzer, "ao", "ao")
        checkOneTerm(analyzer, "aO", "aO")

        checkOneTerm(analyzer, "AA", "Å")
        checkOneTerm(analyzer, "Aa", "Å")
        checkOneTerm(analyzer, "Ao", "Ao")
        checkOneTerm(analyzer, "AO", "AO")

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

        checkOneTerm(analyzer, "oo", "oo")
        checkOneTerm(analyzer, "oe", "ø")
        checkOneTerm(analyzer, "oO", "oO")
        checkOneTerm(analyzer, "oE", "ø")

        checkOneTerm(analyzer, "Oo", "Oo")
        checkOneTerm(analyzer, "Oe", "Ø")
        checkOneTerm(analyzer, "OO", "OO")
        checkOneTerm(analyzer, "OE", "Ø")
        analyzer.close()
    }

    /** check that the empty string doesn't cause issues */
    @Test
    @Throws(Exception::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, ScandinavianNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomData() {
        val analyzer = createAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }

    private fun createAnalyzer(): Analyzer {
        return object : Analyzer() {
            override fun createComponents(field: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val stream: TokenStream = NorwegianNormalizationFilter(tokenizer)
                return TokenStreamComponents(tokenizer, stream)
            }
        }
    }
}

