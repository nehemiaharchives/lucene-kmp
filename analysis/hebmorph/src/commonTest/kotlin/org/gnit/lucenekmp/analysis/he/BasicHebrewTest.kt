package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BasicHebrewTest {
    private val analyzer: Analyzer = HebrewIndexingAnalyzer(HebrewTestUtil.dictionary)

    @Test
    fun test() {
        // Warm up with exact matches...
        assertFoundInText("בת", "בת")
        assertFoundInText("שבתו", "שבתו")
        assertFoundInText("אנציקלופדיה", "אנציקלופדיה")

        // Same written word, several different ways to read it. Even a human won't know which is correct
        // without Niqqud or some context.
        assertFoundInText("שבתו", "בת") // prefix + suffix
        assertFoundInText("שבתו", "תו") // prefixes
        assertFoundInText("שבתו", "ישב") // verb inflections
        assertFoundInText("שבתו", "שבתנו")

        assertNotFoundInText("שבתו", "שיבה") // too much of a tolerance for searches...
        assertNotFoundInText("שבתו", "שביו") // incorrect

        assertFoundInText("כלבי", "כלבי")
        assertFoundInText("כלבי", "לב")
        assertFoundInText("כלבי", "כלב")

        // Prefixes
        assertFoundInText("ליונתן", "יונתן")
        assertFoundInText("כלבי", "לכלבי")
        assertFoundInText("לכלבי", "כלבי")
        assertFoundInText("לכלבי", "לכלבי")

        assertNotFoundInText("לליונתן", "ליונתן") // invalid prefix

        // Singular -> plural, with affixes and non-standard plurals
        assertFoundInText("דמעות", "דמעה")
        assertFoundInText("דמעות", "דמעתי")
        assertFoundInText("דמעות", "דמעותינו")
        assertFoundInText("לתפילתנו", "תפילה")
        assertFoundInText("תפילתנו", "לתפילתי")

        assertFoundInText("אחשוורוש", "אחשורוש") // consonant vav tolerance
        assertFoundInText("לאחשוורוש", "אחשורוש") // consonant vav tolerance + prefix
        assertFoundInText("אימא", "אמא") // yud tolerance (yep, this is the correct spelling...)
        assertFoundInText("אמא", "אמא") // double tolerance - both in indexing and QP

        assertFoundInText("אצטרולב", "אצטרולב") // OOV case, should be stored as-is
        assertFoundInText("test", "test") // Non hebrew, should be stored as-is
        assertFoundInText("test sun", "sun") // Non hebrew, multiple
        assertFoundInText("1234", "1234") // Numeric, should be stored as-is
    }

    @Test
    fun testLemmatization() {
        val terms = HebrewTestUtil.tokens(analyzer, "מינהל").toHashSet()
        assertTrue(terms.isNotEmpty())
    }

    @Test
    fun testFinalOffset() {
        val ts = analyzer.tokenStream("foo", StringReader("מינהל"))
        ts.use {
            val offsetAttribute = it.addAttribute(OffsetAttribute::class)
            it.reset()
            while (it.incrementToken()) {
            }
            it.end()
            assertEquals(5, offsetAttribute.endOffset())
        }
    }

    private fun assertFoundInText(whatToIndex: String, whatToSearch: String) {
        assertEquals(1, findInText(whatToIndex, whatToSearch), "index=$whatToIndex search=$whatToSearch ${debugTerms(whatToIndex, whatToSearch)}")
    }

    private fun assertNotFoundInText(whatToIndex: String, whatToSearch: String) {
        assertEquals(0, findInText(whatToIndex, whatToSearch), "index=$whatToIndex search=$whatToSearch ${debugTerms(whatToIndex, whatToSearch)}")
    }

    private fun findInText(whatToIndex: String, whatToSearch: String): Int {
        val indexedTerms = HebrewTestUtil.tokens(analyzer, whatToIndex).toHashSet()
        val searchTerms = HebrewTestUtil.tokens(HebrewQueryAnalyzer(HebrewTestUtil.dictionary), whatToSearch).toHashSet()
        return if (searchTerms.any { indexedTerms.contains(it) }) 1 else 0
    }

    private fun debugTerms(whatToIndex: String, whatToSearch: String): String {
        val indexedTerms = HebrewTestUtil.tokens(analyzer, whatToIndex)
        val searchTerms = HebrewTestUtil.tokens(HebrewQueryAnalyzer(HebrewTestUtil.dictionary), whatToSearch)
        return "indexedTerms=$indexedTerms searchTerms=$searchTerms"
    }
}
