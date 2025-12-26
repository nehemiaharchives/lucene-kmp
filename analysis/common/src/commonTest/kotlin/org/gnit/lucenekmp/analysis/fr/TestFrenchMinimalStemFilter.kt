package org.gnit.lucenekmp.analysis.fr

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

/** Simple tests for [FrenchMinimalStemFilter]. */
class TestFrenchMinimalStemFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                return TokenStreamComponents(source, FrenchMinimalStemFilter(source))
            }
        }
    }

    /** Test some examples from the paper. */
    @Test
    @Throws(IOException::class)
    fun testExamples() {
        checkOneTerm(analyzer, "chevaux", "cheval")
        checkOneTerm(analyzer, "hiboux", "hibou")
        checkOneTerm(analyzer, "chantés", "chant")
        checkOneTerm(analyzer, "chanter", "chant")
        checkOneTerm(analyzer, "chante", "chant")
        checkOneTerm(analyzer, "baronnes", "baron")
        checkOneTerm(analyzer, "barons", "baron")
        checkOneTerm(analyzer, "baron", "baron")
    }

    @Test
    @Throws(IOException::class)
    fun testIntergerWithLastCharactersEqual() {
        checkOneTerm(analyzer, "1234555", "1234555")
        checkOneTerm(analyzer, "12333345", "12333345")
        checkOneTerm(analyzer, "1234", "1234")
        checkOneTerm(analyzer, "abcdeff", "abcdef")
        checkOneTerm(analyzer, "abcccddeef", "abcccddeef")
        checkOneTerm(analyzer, "créées", "cré")
        checkOneTerm(analyzer, "22hh00", "22hh00")
    }

    @Test
    @Throws(IOException::class)
    fun testKeyword() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("chevaux"), false)
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val sink: TokenStream = SetKeywordMarkerFilter(source, exclusionSet)
                return TokenStreamComponents(source, FrenchMinimalStemFilter(sink))
            }
        }
        checkOneTerm(a, "chevaux", "chevaux")
        a.close()
    }

    /** Test against a vocabulary from the reference impl. */
    @Ignore
    @Test
    @Throws(IOException::class)
    fun testVocabulary() {
        // Requires VocabularyAssert and test data path infrastructure.
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
                return TokenStreamComponents(tokenizer, FrenchMinimalStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
