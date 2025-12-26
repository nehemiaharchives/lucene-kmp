package org.gnit.lucenekmp.analysis.de

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Tests [GermanNormalizationFilter]. */
class TestGermanNormalizationFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val stream: TokenStream = GermanNormalizationFilter(tokenizer)
                return TokenStreamComponents(tokenizer, stream)
            }
        }
    }

    @AfterTest
    fun tearDown() {
        analyzer.close()
    }

    /** Tests that a/o/u + e is equivalent to the umlaut form. */
    @Test
    @Throws(IOException::class)
    fun testBasicExamples() {
        checkOneTerm(analyzer, "Schaltflächen", "Schaltflachen")
        checkOneTerm(analyzer, "Schaltflaechen", "Schaltflachen")
    }

    /** Tests the specific heuristic that ue is not folded after a vowel or q. */
    @Test
    @Throws(IOException::class)
    fun testUHeuristic() {
        checkOneTerm(analyzer, "dauer", "dauer")
    }

    /** Tests german specific folding of sharp-s. */
    @Test
    @Throws(IOException::class)
    fun testSpecialFolding() {
        checkOneTerm(analyzer, "weißbier", "weissbier")
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, GermanNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
