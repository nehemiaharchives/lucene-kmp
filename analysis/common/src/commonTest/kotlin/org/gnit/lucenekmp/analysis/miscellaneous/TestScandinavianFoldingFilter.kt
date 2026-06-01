package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestScandinavianFoldingFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    @Throws(Exception::class)
    fun setUpAnalyzer() {
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val stream: TokenStream = ScandinavianFoldingFilter(tokenizer)
                return TokenStreamComponents(tokenizer, stream)
            }
        }
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDownAnalyzer() {
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun test() {
        checkOneTerm(analyzer, "aeäaeeea", "aaaeea") // should not cause ArrayOutOfBoundsException

        checkOneTerm(analyzer, "aeäaeeeae", "aaaeea")
        checkOneTerm(analyzer, "aeaeeeae", "aaeea")

        checkOneTerm(analyzer, "bøen", "boen")
        checkOneTerm(analyzer, "åene", "aene")

        checkOneTerm(analyzer, "blåbærsyltetøj", "blabarsyltetoj")
        checkOneTerm(analyzer, "blaabaarsyltetoej", "blabarsyltetoj")
        checkOneTerm(analyzer, "blåbärsyltetöj", "blabarsyltetoj")

        checkOneTerm(analyzer, "raksmorgas", "raksmorgas")
        checkOneTerm(analyzer, "räksmörgås", "raksmorgas")
        checkOneTerm(analyzer, "ræksmørgås", "raksmorgas")
        checkOneTerm(analyzer, "raeksmoergaas", "raksmorgas")
        checkOneTerm(analyzer, "ræksmörgaos", "raksmorgas")

        checkOneTerm(analyzer, "ab", "ab")
        checkOneTerm(analyzer, "ob", "ob")
        checkOneTerm(analyzer, "Ab", "Ab")
        checkOneTerm(analyzer, "Ob", "Ob")

        checkOneTerm(analyzer, "å", "a")

        checkOneTerm(analyzer, "aa", "a")
        checkOneTerm(analyzer, "aA", "a")
        checkOneTerm(analyzer, "ao", "a")
        checkOneTerm(analyzer, "aO", "a")

        checkOneTerm(analyzer, "AA", "A")
        checkOneTerm(analyzer, "Aa", "A")
        checkOneTerm(analyzer, "Ao", "A")
        checkOneTerm(analyzer, "AO", "A")

        checkOneTerm(analyzer, "æ", "a")
        checkOneTerm(analyzer, "ä", "a")

        checkOneTerm(analyzer, "Æ", "A")
        checkOneTerm(analyzer, "Ä", "A")

        checkOneTerm(analyzer, "ae", "a")
        checkOneTerm(analyzer, "aE", "a")

        checkOneTerm(analyzer, "Ae", "A")
        checkOneTerm(analyzer, "AE", "A")

        checkOneTerm(analyzer, "ö", "o")
        checkOneTerm(analyzer, "ø", "o")
        checkOneTerm(analyzer, "Ö", "O")
        checkOneTerm(analyzer, "Ø", "O")

        checkOneTerm(analyzer, "oo", "o")
        checkOneTerm(analyzer, "oe", "o")
        checkOneTerm(analyzer, "oO", "o")
        checkOneTerm(analyzer, "oE", "o")

        checkOneTerm(analyzer, "Oo", "O")
        checkOneTerm(analyzer, "Oe", "O")
        checkOneTerm(analyzer, "OO", "O")
        checkOneTerm(analyzer, "OE", "O")
    }

    /** check that the empty string doesn't cause issues */
    @Test
    @Throws(Exception::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, ScandinavianFoldingFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomData() {
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
    }
}
