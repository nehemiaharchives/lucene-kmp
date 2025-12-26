package org.gnit.lucenekmp.analysis.util

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.fr.FrenchAnalyzer
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestElision : BaseTokenStreamTestCase() {

    @Test
    @Throws(Exception::class)
    fun testElision() {
        val test = "Plop, juste pour voir l'embrouille avec O'brian. M'enfin."
        val tokenizer: Tokenizer = StandardTokenizer(newAttributeFactory())
        tokenizer.setReader(StringReader(test))
        val articles = CharArraySet(mutableListOf<Any>("l", "M"), false)
        val filter: TokenFilter = ElisionFilter(tokenizer, articles)
        val tas = filter(filter)
        assertEquals("embrouille", tas[4])
        assertEquals("O'brian", tas[6])
        assertEquals("enfin", tas[7])
    }

    private fun filter(filter: TokenFilter): MutableList<String> {
        val tas: MutableList<String> = mutableListOf()
        val termAtt: CharTermAttribute = filter.getAttribute(CharTermAttribute::class)
        filter.reset()
        while (filter.incrementToken()) {
            tas.add(termAtt.toString())
        }
        filter.end()
        filter.close()
        return tas
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                val stream: TokenStream = ElisionFilter(tokenizer, FrenchAnalyzer.DEFAULT_ARTICLES)
                return TokenStreamComponents(tokenizer, stream)
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
