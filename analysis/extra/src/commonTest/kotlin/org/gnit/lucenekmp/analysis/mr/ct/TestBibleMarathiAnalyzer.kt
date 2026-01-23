package org.gnit.lucenekmp.analysis.mr.ct

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

class TestBibleMarathiAnalyzer : BaseTokenStreamTestCase() {

    @Test
    fun testNormalizeJesusChristOrder() {
        val analyzer = BibleMarathiAnalyzer()
        assertAnalyzesTo(analyzer, "ख्रिस्त येशू", arrayOf("येश", "खरिसत"))
        analyzer.close()
    }

    @Test
    fun testCanonicalOrderUnchanged() {
        val analyzer = BibleMarathiAnalyzer()
        assertAnalyzesTo(analyzer, "येशू ख्रिस्त", arrayOf("येश", "खरिसत"))
        analyzer.close()
    }

    @Test
    fun testMatthew1_1ContainsJesusChristTokens() {
        val analyzer = BibleMarathiAnalyzer()
        val text = "अब्राहामाचा पुत्र दावीद याचा पुत्र जो येशू ख्रिस्त याची वंशावळ."
        val terms = termsFromTokenStream(analyzer.tokenStream("text", text))
        assertTrue(terms.contains("येश"), "Expected token 'येश' in $terms")
        assertTrue(terms.contains("खरिसत"), "Expected token 'खरिसत' in $terms")
        analyzer.close()
    }

    @Test
    fun testChristGenitiveIsNormalized() {
        val analyzer = BibleMarathiAnalyzer()
        val terms = termsFromTokenStream(analyzer.tokenStream("text", "येशू ख्रिस्ताचा दास"))
        assertTrue(terms.contains("येश"), "Expected token 'येश' in $terms")
        assertTrue(terms.contains("खरिसत"), "Expected token 'खरिसत' in $terms")
        analyzer.close()
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
