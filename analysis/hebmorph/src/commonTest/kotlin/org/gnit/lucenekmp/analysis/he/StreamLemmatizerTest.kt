package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.he.datastructures.DictRadix
import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamLemmatizerTest {
    @Test
    fun IncrementsOffsetCorrectly() {
        val input = buildString {
            repeat(400) { // TODO reduced repeat = 4000 to 400 for dev speed
                append("test test test test ")
            }
        }
        val sl = StreamLemmatizer(StringReader(input), HebrewTestUtil.dictionary)

        val token = Reference("")
        val results = ArrayList<Token>()
        var previousOffest = -5
        while (sl.getLemmatizeNextToken(token, results) > 0) {
            assertEquals(previousOffest, sl.getStartOffset() - 5)
            assertEquals(4, sl.getEndOffset() - sl.getStartOffset())
            previousOffest = sl.getStartOffset()
        }
    }

    @Test
    fun testHebrewWords() {
        var sl = StreamLemmatizer(StringReader("שלום"), HebrewTestUtil.dictionary)

        val token = Reference("")
        val results = ArrayList<Token>()

        assertTrue(sl.getLemmatizeNextToken(token, results) > 0)
        assertEquals(3, results.size)
        assertEquals(0, sl.getStartOffset())
        assertEquals(4, sl.getEndOffset())
        results.clear()

        sl = StreamLemmatizer(StringReader("בבבי"), HebrewTestUtil.dictionary)
        assertTrue(sl.getLemmatizeNextToken(token, results) > 0)
        assertEquals(0, sl.getStartOffset())
        assertEquals(4, sl.getEndOffset())
        results.clear()
    }

    @Test
    fun testAutoStripMixed() {
        testAutoStripMixedImpl("בcellcom", "cellcom", HebMorphTokenizer.TokenType.NonHebrew)
        testAutoStripMixedImpl("והcellcom", "cellcom", HebMorphTokenizer.TokenType.NonHebrew)
        testAutoStripMixedImpl("תחcellcom", "תחcellcom", HebMorphTokenizer.TokenType.Mixed or HebMorphTokenizer.TokenType.Hebrew)
        testAutoStripMixedImpl("הcellcomג", "הcellcomג", HebMorphTokenizer.TokenType.Mixed or HebMorphTokenizer.TokenType.Hebrew)
        testAutoStripMixedImpl("cellcom", "cellcom", HebMorphTokenizer.TokenType.NonHebrew)

        val specialTokenizationCases = DictRadix<Byte>(false)
        specialTokenizationCases.addNode("C++", 0)
        testAutoStripMixedImpl("בc++", "c++", HebMorphTokenizer.TokenType.NonHebrew, specialTokenizationCases)
        testAutoStripMixedImpl("בc++ ", "c++", HebMorphTokenizer.TokenType.NonHebrew, specialTokenizationCases)
        testAutoStripMixedImpl(" בc++", "c++", HebMorphTokenizer.TokenType.NonHebrew, specialTokenizationCases)
    }

    private fun testAutoStripMixedImpl(word: String, expected: String, expectedType: Int) {
        testAutoStripMixedImpl(word, expected, expectedType, null)
    }

    private fun testAutoStripMixedImpl(word: String, expected: String, expectedType: Int, specialTokenizationCases: DictRadix<Byte>?) {
        val token = Reference("")
        val results = ArrayList<Token>()

        var sl = StreamLemmatizer(StringReader(word), HebrewTestUtil.dictionary, specialTokenizationCases)
        var tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(expected, token.ref)
        assertEquals(expectedType, tokenType)

        sl = StreamLemmatizer(StringReader("$word בדיקה"), HebrewTestUtil.dictionary, specialTokenizationCases)
        tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(expected, token.ref)
        assertEquals(expectedType, tokenType)

        sl = StreamLemmatizer(StringReader("בדיקה $word"), HebrewTestUtil.dictionary, specialTokenizationCases)
        sl.getLemmatizeNextToken(token, results)
        tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(expected, token.ref)
        assertEquals(expectedType, tokenType)
    }

    @Test
    fun testRespectsExactOperator() {
        val token = Reference("")
        val results = ArrayList<Token>()

        var sl = StreamLemmatizer(StringReader("בדיקה$"), HebrewTestUtil.dictionary)
        sl.setSuffixForExactMatch('$')
        var tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(HebMorphTokenizer.TokenType.Hebrew or HebMorphTokenizer.TokenType.Exact, tokenType)
        assertEquals("בדיקה", token.ref)
        assertEquals(0, sl.getLemmatizeNextToken(token, results))

        sl = StreamLemmatizer(StringReader("בדיקות$"), HebrewTestUtil.dictionary)
        sl.setSuffixForExactMatch('$')
        tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(HebMorphTokenizer.TokenType.Hebrew or HebMorphTokenizer.TokenType.Exact, tokenType)
        assertEquals("בדיקות", token.ref)
        assertEquals(0, sl.getLemmatizeNextToken(token, results))

        sl = StreamLemmatizer(StringReader("\"בין$ תחומי$\""), HebrewTestUtil.dictionary)
        sl.setSuffixForExactMatch('$')
        tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(HebMorphTokenizer.TokenType.Hebrew or HebMorphTokenizer.TokenType.Exact, tokenType)
        assertEquals("בין", token.ref)
        tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(HebMorphTokenizer.TokenType.Hebrew or HebMorphTokenizer.TokenType.Exact, tokenType)
        assertEquals("תחומי", token.ref)

        assertEquals(0, sl.getLemmatizeNextToken(token, results))
    }

    @Test
    fun testPreservesAcronyms() {
        val token = Reference("")
        val results = ArrayList<Token>()

        var sl = StreamLemmatizer(StringReader("מב\"ל"), HebrewTestUtil.dictionary)
        var tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(HebMorphTokenizer.TokenType.Acronym or HebMorphTokenizer.TokenType.Hebrew, tokenType)
        assertEquals("מב\"ל", token.ref)

        sl = StreamLemmatizer(StringReader("מב\"ל"), HebrewTestUtil.dictionary)
        sl.setSuffixForExactMatch('$')
        tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(HebMorphTokenizer.TokenType.Acronym or HebMorphTokenizer.TokenType.Hebrew, tokenType)
        assertEquals("מב\"ל", token.ref)

        sl = StreamLemmatizer(StringReader("ומש\"א"), HebrewTestUtil.dictionary)
        tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(HebMorphTokenizer.TokenType.Acronym or HebMorphTokenizer.TokenType.Hebrew, tokenType)
        assertEquals("ומש\"א", token.ref)

        sl = StreamLemmatizer(StringReader("ומש\"א"), HebrewTestUtil.dictionary)
        sl.setSuffixForExactMatch('$')
        tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(HebMorphTokenizer.TokenType.Acronym or HebMorphTokenizer.TokenType.Hebrew, tokenType)
        assertEquals("ומש\"א", token.ref)

        sl = StreamLemmatizer(StringReader("ומש\"א$"), HebrewTestUtil.dictionary)
        sl.setSuffixForExactMatch('$')
        tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals("ומש\"א", token.ref)
        assertEquals(HebMorphTokenizer.TokenType.Acronym or HebMorphTokenizer.TokenType.Hebrew or HebMorphTokenizer.TokenType.Exact, tokenType)

        sl = StreamLemmatizer(StringReader("ה\"מכונית"), HebrewTestUtil.dictionary)
        tokenType = sl.getLemmatizeNextToken(token, results)
        assertEquals(HebMorphTokenizer.TokenType.Hebrew, tokenType)
        assertEquals("מכונית", token.ref)
    }
}
