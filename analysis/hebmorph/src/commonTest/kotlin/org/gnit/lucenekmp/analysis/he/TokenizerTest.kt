package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenizerTest {
    private val tokenizer = HebMorphTokenizer(StringReader(""), HebrewTestUtil.dictionary.getPref())

    @BeforeTest
    fun setUp() {
        tokenizer.setSuffixForExactMatch('$')
        tokenizer.clearSpecialCases()
    }

    private fun assertTokenizesTo(stream: String, token: String) {
        assertTokenizesTo(stream, arrayOf(token), null)
    }

    private fun assertTokenizesTo(stream: String, token: String, tokenType: Int) {
        assertTokenizesTo(stream, arrayOf(token), intArrayOf(tokenType))
    }

    private fun assertTokenizesTo(stream: String, tokens: Array<String>) {
        assertTokenizesTo(stream, tokens, null)
    }

    private fun assertTokenizesTo(text: String, tokens: Array<String>, tokenTypes: IntArray?) {
        require(tokenTypes == null || tokens.size == tokenTypes.size)
        for (j in 4096 - text.length - 3 until 4096 + text.length + 2) {
            tokenizer.reset(StringReader(" ".repeat(j) + text))
            var i = 0
            val test = Reference("")
            while (true) {
                val tokenType = tokenizer.nextToken(test)
                if (tokenType <= 0) break
                assertEquals(tokens[i], test.ref, "[Added space $j]")
                if (tokenTypes != null) assertEquals(tokenTypes[i], tokenType)
                i++
            }
            assertEquals(tokens.size, i, "[Added space $j]")
        }
    }

    @Test
    fun tokenizesCorrectly() {
        assertTokenizesTo("test", "test")
        assertTokenizesTo("test's", "test's")
        assertTokenizesTo("tests'", "tests")
        assertTokenizesTo("test123", "test123")
        assertTokenizesTo("test two", arrayOf("test", "two"))
        assertTokenizesTo("décimo", "décimo")
        assertTokenizesTo("traducción", "traducción")
        assertTokenizesTo("el árbol", arrayOf("el", "árbol"))
        assertTokenizesTo("בדיקה", "בדיקה")
        assertTokenizesTo("בדיקה.", "בדיקה")
        assertTokenizesTo("בדיקה שניה", arrayOf("בדיקה", "שניה"))
        assertTokenizesTo("בדיקה.שניה", arrayOf("בדיקה", "שניה"))
        assertTokenizesTo("בדיקה-שניה", arrayOf("בדיקה", "שניה"))
        assertTokenizesTo("בדיקה\u05BEשניה", arrayOf("בדיקה", "שניה"))
        assertTokenizesTo(" (\"דייט בחשיכה\",פרק 5) ", arrayOf("דייט", "בחשיכה", "פרק", "5"))
        assertTokenizesTo("בדיקה\"", "בדיקה")
        assertTokenizesTo("\u05AAבדיקה", "בדיקה")
        assertTokenizesTo("ב\u05B0דיקה", "ב\u05B0דיקה")
        assertTokenizesTo("ץבדיקה", "בדיקה")
        assertTokenizesTo("שלומי999", "שלומי999")
        assertTokenizesTo("שלומיabc", "שלומיabc")
        assertTokenizesTo("אימג’בנק", "אימג'בנק")
        assertTokenizesTo("בלונים$", "בלונים", HebMorphTokenizer.TokenType.Hebrew or HebMorphTokenizer.TokenType.Exact)
        assertTokenizesTo("test$", "test", HebMorphTokenizer.TokenType.NonHebrew or HebMorphTokenizer.TokenType.Exact)
        assertTokenizesTo("123$", "123", HebMorphTokenizer.TokenType.NonHebrew or HebMorphTokenizer.TokenType.Numeric or HebMorphTokenizer.TokenType.Exact)
        assertTokenizesTo("צה\"ל", "צה\"ל")
        assertTokenizesTo("צה''ל", "צה\"ל")
        assertTokenizesTo("צה\u05F3\u05F3ל", "צה\"ל")
        assertTokenizesTo("צה\uFF07\uFF07ל", "צה\"ל")
        assertTokenizesTo("צה\u201Cל", "צה\"ל")
        assertTokenizesTo("ד'אור", "ד'אור")
        assertTokenizesTo("אורנג'", "אורנג'")
        assertTokenizesTo("אורנג\u05F3", "אורנג'")
        assertTokenizesTo("אורנג\uFF07", "אורנג'")
        assertTokenizesTo("אורנג' שלום", arrayOf("אורנג'", "שלום"))
        assertTokenizesTo("סמית'", "סמית")
        assertTokenizesTo("ומש\"א$", "ומש\"א")
        assertTokenizesTo("של", "של")
        assertTokenizesTo("שלך", "שלך")
        assertTokenizesTo("לשלם", "לשלם")
    }

    @Test
    fun tokenizesWithExceptions() {
        assertTokenizesTo("C++", "C")
        tokenizer.addSpecialCase("C++")
        assertTokenizesTo("C++", "C++", HebMorphTokenizer.TokenType.NonHebrew or HebMorphTokenizer.TokenType.Custom)
        assertTokenizesTo("c++", "c++", HebMorphTokenizer.TokenType.NonHebrew or HebMorphTokenizer.TokenType.Custom)
        assertTokenizesTo("C++.", "C++", HebMorphTokenizer.TokenType.NonHebrew or HebMorphTokenizer.TokenType.Custom)
        assertTokenizesTo("a++ b++ c++", arrayOf("a", "b", "c++"))
        assertTokenizesTo(".NET", "NET")
        tokenizer.addSpecialCase(".NET")
        assertTokenizesTo(".NET", ".NET")
        assertTokenizesTo(".NET.", ".NET")
        assertTokenizesTo(".NETify", "NETify")
        assertTokenizesTo("B+++", "B")
        tokenizer.addSpecialCase("B+++")
        assertTokenizesTo("B+++", "B+++")
        assertTokenizesTo("שלום+", "שלום")
        tokenizer.addSpecialCase("שלום+")
        assertTokenizesTo("שלום+", "שלום+")
        assertTokenizesTo("שלום", "שלום")
    }

    @Test
    fun incrementsOffsetCorrectly() {
        val expectedOffsets = intArrayOf(0, 5, 10, 15)
        var curPos = 0
        val token = Reference("")
        tokenizer.reset(StringReader("test test test test"))
        while (true) {
            val tokenType = tokenizer.nextToken(token)
            if (tokenType == 0) break
            assertEquals(expectedOffsets[curPos++], tokenizer.getOffset())
            assertEquals(4, tokenizer.getLengthInSource())
        }
    }
}
