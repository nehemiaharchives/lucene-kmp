package org.gnit.lucenekmp.analysis.ko.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBibleKoreanAnalyzer {
    @Test
    @Throws(IOException::class)
    fun testBibleNameDictionary() {
        assertEquals(listOf("예수아"), collectTokens("예수아"))
        assertEquals(listOf("예수"), collectTokens("예수"))
        assertEquals(listOf("예수", "그리스도"), collectTokens("예수 그리스도"))
    }

    private fun collectTokens(text: String): List<String> {
        BibleKoreanAnalyzer().use { analyzer ->
            val result = ArrayList<String>()
            val stream = analyzer.tokenStream("text", text)
            stream.use {
                val termAtt = it.addAttribute(CharTermAttribute::class)
                it.reset()
                while (it.incrementToken()) {
                    result.add(termAtt.toString())
                }
                it.end()
            }
            return result
        }
    }
}
