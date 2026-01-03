package org.gnit.lucenekmp.analysis.vi

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

class TestVietnameseAnalyzer {
    @Test
    fun testVietnameseAnalyzer() {
        val analyzer = VietnameseAnalyzer(VietnameseConfig())
        val ts = analyzer.tokenStream("field", "công nghệ thông tin Việt Nam")
        assertEquals(listOf("công nghệ", "thông tin", "việt nam"), termsFromTokenStream(ts))
    }

    @Test
    fun testVietnameseTokenizer() {
        val tokenizer = VietnameseTokenizer(VietnameseConfig())
        tokenizer.setReader(StringReader("công nghệ thông tin Việt Nam"))
        assertEquals(listOf("công nghệ", "thông tin", "việt nam"), termsFromTokenStream(tokenizer))
    }

    @Test
    fun testVietnameseTokenizerSplitsUrl() {
        val analyzer = VietnameseAnalyzer(VietnameseConfig())
        val ts = analyzer.tokenStream("field", "Công nghệ thông tin Việt Nam https://duydo.me")
        assertEquals(
            listOf("công nghệ", "thông tin", "việt nam", "https", "duydo", "me"),
            termsFromTokenStream(ts)
        )
    }

    private fun termsFromTokenStream(ts: TokenStream): List<String> {
        val termAtt = ts.addAttribute(CharTermAttribute::class)
        val terms = mutableListOf<String>()
        ts.reset()
        while (ts.incrementToken()) {
            terms.add(termAtt.toString())
        }
        ts.end()
        ts.close()
        return terms
    }
}
