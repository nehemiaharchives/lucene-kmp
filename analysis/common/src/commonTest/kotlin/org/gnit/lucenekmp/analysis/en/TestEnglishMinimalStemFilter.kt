package org.gnit.lucenekmp.analysis.en

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Simple tests for [EnglishMinimalStemFilter]. */
class TestEnglishMinimalStemFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                return TokenStreamComponents(source, EnglishMinimalStemFilter(source))
            }
        }
    }

    @AfterTest
    fun tearDown() {
        analyzer.close()
    }

    /** Test some examples from various papers about this technique. */
    @Test
    @Throws(IOException::class)
    fun testExamples() {
        checkOneTerm(analyzer, "queries", "query")
        checkOneTerm(analyzer, "phrases", "phrase")
        checkOneTerm(analyzer, "corpus", "corpus")
        checkOneTerm(analyzer, "stress", "stress")
        checkOneTerm(analyzer, "kings", "king")
        checkOneTerm(analyzer, "panels", "panel")
        checkOneTerm(analyzer, "aerodynamics", "aerodynamic")
        checkOneTerm(analyzer, "congress", "congress")
        checkOneTerm(analyzer, "serious", "serious")
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
                return TokenStreamComponents(tokenizer, EnglishMinimalStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
