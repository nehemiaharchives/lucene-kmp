package org.gnit.lucenekmp.analysis.es

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.en.EnglishMinimalStemFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Simple tests for [SpanishMinimalStemFilter]
 *
 * @deprecated Remove with SpanishMinimalStemFilter
 */
@Deprecated("Remove with SpanishMinimalStemFilter")
class TestSpanishMinimalStemFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(source, SpanishMinimalStemFilter(source))
                }
            }
    }

    @AfterTest
    fun tearDown() {
        analyzer.close()
    }

    /** Test some examples */
    @Test
    @Throws(IOException::class)
    fun testExamples() {
        checkOneTerm(analyzer, "actrices", "actriz")
        checkOneTerm(analyzer, "niños", "nino")
        checkOneTerm(analyzer, "países", "pais")
        checkOneTerm(analyzer, "caragodor", "caragodor")
        checkOneTerm(analyzer, "móviles", "movil")
        checkOneTerm(analyzer, "chicas", "chica")
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), analyzer, 50 * RANDOM_MULTIPLIER)
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, EnglishMinimalStemFilter(tokenizer))
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }
}
