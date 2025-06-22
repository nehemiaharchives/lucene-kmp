package org.gnit.lucenekmp.analysis.standard

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals

@Ignore
class TestStandardAnalyzer : LuceneTestCase() {
    private fun tokensFromText(text: String): List<String> {
        val analyzer = StandardAnalyzer()
        val tokens = analyzeTokens(analyzer, text)
        analyzer.close()
        return tokens
    }

    private fun analyzeTokens(analyzer: Analyzer, text: String): List<String> {
        val ts = analyzer.tokenStream("dummy", text)
        val termAtt = ts.addAttribute(CharTermAttribute::class)
        val tokens = mutableListOf<String>()
        ts.reset()
        while (ts.incrementToken()) {
            tokens.add(termAtt.toString())
        }
        ts.end()
        ts.close()
        return tokens
    }

    private fun assertAnalyzesTo(
        analyzer: Analyzer,
        text: String,
        tokens: Array<String>,
        startOffsets: IntArray? = null,
        endOffsets: IntArray? = null,
        types: Array<String>? = null
    ) {
        val ts = analyzer.tokenStream("dummy", text)
        val termAtt = ts.addAttribute(CharTermAttribute::class)
        val offsetAtt = ts.addAttribute(OffsetAttribute::class)
        val typeAtt = ts.addAttribute(TypeAttribute::class)
        val actual = mutableListOf<String>()
        val starts = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        val typeList = mutableListOf<String>()
        ts.reset()
        while (ts.incrementToken()) {
            actual.add(termAtt.toString())
            if (startOffsets != null) starts.add(offsetAtt.startOffset())
            if (endOffsets != null) ends.add(offsetAtt.endOffset())
            if (types != null) typeList.add(typeAtt.type())
        }
        ts.end()
        ts.close()
        assertEquals(tokens.toList(), actual)
        startOffsets?.let { assertEquals(it.toList(), starts) }
        endOffsets?.let { assertEquals(it.toList(), ends) }
        types?.let { assertEquals(it.toList(), typeList) }
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

    @Test
    fun testDelimitersSA() {
        val analyzer = StandardAnalyzer()
        assertAnalyzesTo(analyzer, "some-dashed-phrase", arrayOf("some", "dashed", "phrase"))
        assertAnalyzesTo(analyzer, "dogs,chase,cats", arrayOf("dogs", "chase", "cats"))
        assertAnalyzesTo(analyzer, "ac/dc", arrayOf("ac", "dc"))
        analyzer.close()
    }

    @Test
    fun testApostrophesSA() {
        val analyzer = StandardAnalyzer()
        assertAnalyzesTo(analyzer, "O'Reilly", arrayOf("O'Reilly"))
        assertAnalyzesTo(analyzer, "you're", arrayOf("you're"))
        assertAnalyzesTo(analyzer, "she's", arrayOf("she's"))
        assertAnalyzesTo(analyzer, "Jim's", arrayOf("Jim's"))
        assertAnalyzesTo(analyzer, "don't", arrayOf("don't"))
        assertAnalyzesTo(analyzer, "O'Reilly's", arrayOf("O'Reilly's"))
        analyzer.close()
    }

    @Test
    fun testNumericSA() {
        val analyzer = StandardAnalyzer()
        assertAnalyzesTo(analyzer, "21.35", arrayOf("21.35"))
        assertAnalyzesTo(analyzer, "R2D2 C3PO", arrayOf("R2D2", "C3PO"))
        assertAnalyzesTo(analyzer, "216.239.63.104", arrayOf("216.239.63.104"))
        analyzer.close()
    }

    @Test
    fun testTextWithNumbersSA() {
        val analyzer = StandardAnalyzer()
        assertAnalyzesTo(analyzer, "David has 5000 bones", arrayOf("David", "has", "5000", "bones"))
        analyzer.close()
    }

    @Test
    fun testVariousTextSA() {
        val analyzer = StandardAnalyzer()
        assertAnalyzesTo(analyzer, "C embedded developers wanted", arrayOf("C", "embedded", "developers", "wanted"))
        assertAnalyzesTo(analyzer, "foo bar FOO BAR", arrayOf("foo", "bar", "FOO", "BAR"))
        assertAnalyzesTo(analyzer, "foo      bar .  FOO <> BAR", arrayOf("foo", "bar", "FOO", "BAR"))
        assertAnalyzesTo(analyzer, "\"QUOTED\" word", arrayOf("QUOTED", "word"))
        analyzer.close()
    }

    @Test
    fun testKoreanSA() {
        val analyzer = StandardAnalyzer()
        assertAnalyzesTo(analyzer, "안녕하세요 한글입니다", arrayOf("안녕하세요", "한글입니다"))
        analyzer.close()
    }

    @Test
    fun testTypes() {
        val analyzer = StandardAnalyzer()
        assertAnalyzesTo(
            analyzer,
            "David has 5000 bones",
            arrayOf("David", "has", "5000", "bones"),
            types = arrayOf("<ALPHANUM>", "<ALPHANUM>", "<NUM>", "<ALPHANUM>")
        )
        analyzer.close()
    }

    @Test
    fun testNormalize() {
        val analyzer = StandardAnalyzer()
        assertEquals("\"\\à3[]()! cz@", analyzer.normalize("dummy", "\"\\À3[]()! Cz@" ).utf8ToString())
        analyzer.close()
    }

    @Test
    fun testMaxTokenLengthDefault() {
        val analyzer = StandardAnalyzer()
        val bString = "b".repeat(StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH)
        val input = "x " + bString + " " + bString + "b"
        assertAnalyzesTo(analyzer, input, arrayOf("x", bString, bString, "b"))
        analyzer.close()
    }
}
