package org.gnit.lucenekmp.analysis.gl

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
import kotlin.test.Test

/** Simple tests for [GalicianMinimalStemmer] */
class TestGalicianMinimalStemFilter : BaseTokenStreamTestCase() {
    private lateinit var a: Analyzer

    @BeforeTest
    fun setUp() {
        a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                return TokenStreamComponents(tokenizer, GalicianMinimalStemFilter(tokenizer))
            }
        }
    }

    @AfterTest
    fun tearDown() {
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testPlural() {
        checkOneTerm(a, "elefantes", "elefante")
        checkOneTerm(a, "elefante", "elefante")
        checkOneTerm(a, "kalóres", "kalór")
        checkOneTerm(a, "kalór", "kalór")
    }

    @Test
    @Throws(Exception::class)
    fun testExceptions() {
        checkOneTerm(a, "mas", "mas")
        checkOneTerm(a, "barcelonês", "barcelonês")
    }

    @Test
    @Throws(IOException::class)
    fun testKeyword() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("elefantes"), false)
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val sink: TokenStream = SetKeywordMarkerFilter(source, exclusionSet)
                return TokenStreamComponents(source, GalicianMinimalStemFilter(sink))
            }
        }
        checkOneTerm(a, "elefantes", "elefantes")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, GalicianMinimalStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
