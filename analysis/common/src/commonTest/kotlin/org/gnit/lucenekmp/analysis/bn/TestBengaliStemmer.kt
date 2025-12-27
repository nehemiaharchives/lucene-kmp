package org.gnit.lucenekmp.analysis.bn

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test codes for [BengaliStemmer]. */
class TestBengaliStemmer : BaseTokenStreamTestCase() {
    /** Testing few verbal words */
    @Test
    @Throws(IOException::class)
    fun testVerbsInShadhuForm() {
        check("করেছিলাম", "কর")
        check("করিতেছিলে", "কর")
        check("খাইতাম", "খাই")
        check("যাইবে", "যা")
    }

    @Test
    @Throws(IOException::class)
    fun testVerbsInCholitoForm() {
        check("করছিলাম", "কর")
        check("করছিলে", "কর")
        check("করতাম", "কর")
        check("যাব", "যা")
        check("যাবে", "যা")
        check("করি", "কর")
        check("করো", "কর")
    }

    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("মেয়েরা", "মে")
        check("মেয়েদেরকে", "মে")
        check("মেয়েদের", "মে")

        check("একটি", "এক")
        check("মানুষগুলি", "মানুষ")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = BengaliStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, BengaliStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
