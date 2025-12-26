package org.gnit.lucenekmp.analysis.pt

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

/** Simple tests for [PortugueseMinimalStemFilter]. */
class TestPortugueseMinimalStemFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = MockTokenizer(MockTokenizer.SIMPLE, true)
                return TokenStreamComponents(source, PortugueseMinimalStemFilter(source))
            }
        }
    }

    @AfterTest
    fun tearDown() {
        analyzer.close()
    }

    /**
     * Test the example from the paper "Assessing the impact of stemming accuracy on information retrieval".
     */
    @Test
    @Throws(IOException::class)
    fun testExamples() {
        assertAnalyzesTo(
            analyzer,
            "O debate político, pelo menos o que vem a público, parece, de modo nada " +
                "surpreendente, restrito a temas menores. Mas há, evidentemente, " +
                "grandes questões em jogo nas eleições que se aproximam.",
            arrayOf(
                "o",
                "debate",
                "político",
                "pelo",
                "menos",
                "o",
                "que",
                "vem",
                "a",
                "público",
                "parece",
                "de",
                "modo",
                "nada",
                "surpreendente",
                "restrito",
                "a",
                "tema",
                "menor",
                "mas",
                "há",
                "evidentemente",
                "grande",
                "questão",
                "em",
                "jogo",
                "na",
                "eleição",
                "que",
                "se",
                "aproximam"
            )
        )
    }

    /** Test against a vocabulary from the reference impl. */
    @Ignore
    @Test
    @Throws(IOException::class)
    fun testVocabulary() {
        // Requires VocabularyAssert and test data path infrastructure.
    }

    @Test
    @Throws(IOException::class)
    fun testKeyword() {
        val exclusionSet = CharArraySet(1, false)
        exclusionSet.add("quilométricas")
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val sink: TokenStream = SetKeywordMarkerFilter(source, exclusionSet)
                return TokenStreamComponents(source, PortugueseMinimalStemFilter(sink))
            }
        }
        checkOneTerm(a, "quilométricas", "quilométricas")
        a.close()
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
                return TokenStreamComponents(tokenizer, PortugueseMinimalStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
