package org.gnit.lucenekmp.analysis.standard

import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals

@Ignore
class TestStandardAnalyzer : LuceneTestCase() {
    private fun tokensFromText(text: String): List<String> {
        val analyzer = StandardAnalyzer()
        val ts = analyzer.tokenStream("dummy", text)
        val termAtt = ts.addAttribute(CharTermAttribute::class)
        val tokens = mutableListOf<String>()
        ts.reset()
        while (ts.incrementToken()) {
            tokens.add(termAtt.toString())
        }
        ts.end()
        ts.close()
        analyzer.close()
        return tokens
    }

    @Test
    fun testAlphanumericSA() {
        assertEquals(listOf("B2B"), tokensFromText("B2B"))
        assertEquals(listOf("2B"), tokensFromText("2B"))
    }

    @Test
    fun testOffsets() {
        val analyzer = StandardAnalyzer()
        val ts = analyzer.tokenStream("dummy", "David has 5000 bones")
        val termAtt = ts.addAttribute(CharTermAttribute::class)
        val tokens = mutableListOf<String>()
        ts.reset()
        while (ts.incrementToken()) {
            tokens.add(termAtt.toString())
        }
        ts.end()
        ts.close()
        analyzer.close()
        assertEquals(listOf("David", "has", "5000", "bones"), tokens)
    }

    @Test
    fun testMaxTokenLengthNonDefault() {
        val analyzer = StandardAnalyzer()
        analyzer.maxTokenLength = 5
        assertEquals(listOf("ab", "cd", "toolo", "ng", "xy", "z"), tokensFromText("ab cd toolong xy z"))
        analyzer.close()
    }
}
