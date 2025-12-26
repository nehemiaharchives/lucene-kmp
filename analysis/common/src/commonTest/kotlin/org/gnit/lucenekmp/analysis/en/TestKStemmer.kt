package org.gnit.lucenekmp.analysis.en

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

/** Tests for [KStemmer]. */
class TestKStemmer : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
                return TokenStreamComponents(tokenizer, KStemFilter(tokenizer))
            }
        }
    }

    @AfterTest
    fun tearDown() {
        analyzer.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
    }

    /** Test against a vocabulary from the reference impl. */
    @Ignore
    @Test
    @Throws(Exception::class)
    fun testVocabulary() {
        // TODO Requires VocabularyAssert and test data path infrastructure.
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, KStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }

    @Ignore
    @Test
    fun testCreateMap() {
        // TODO Sample map creation code.
    }
}
