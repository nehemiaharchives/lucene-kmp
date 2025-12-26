package org.gnit.lucenekmp.analysis.en

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

/** Test the PorterStemFilter with Martin Porter's test data. */
class TestPorterStemFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val t = MockTokenizer(MockTokenizer.KEYWORD, false)
                return TokenStreamComponents(t, PorterStemFilter(t))
            }
        }
    }

    @AfterTest
    fun tearDown() {
        analyzer.close()
    }

    /** Run the stemmer against all strings in voc.txt */
    @Ignore
    @Test
    @Throws(Exception::class)
    fun testPorterStemFilter() {
        // TODO Requires VocabularyAssert and test data path infrastructure.
    }

    @Test
    @Throws(IOException::class)
    fun testWithKeywordAttribute() {
        val set = CharArraySet(1, true)
        set.add("yourselves")
        val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("yourselves yours"))
        val filter: TokenStream = PorterStemFilter(SetKeywordMarkerFilter(tokenizer, set))
        assertTokenStreamContents(filter, arrayOf("yourselves", "your"))
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
                return TokenStreamComponents(tokenizer, PorterStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
