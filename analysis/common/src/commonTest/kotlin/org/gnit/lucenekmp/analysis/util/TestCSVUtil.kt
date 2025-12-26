package org.gnit.lucenekmp.analysis.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCSVUtil : LuceneTestCase() {

    @Test
    fun testQuoteEscapeQuotes() {
        val input = "\"Let It Be\" is a song and album by the The Beatles."
        val expectedOutput = input.replace("\"", "\"\"")
        implTestQuoteEscape(input, expectedOutput)
    }

    @Test
    fun testQuoteEscapeComma() {
        val input = "To be, or not to be ..."
        val expectedOutput = "\"" + input + "\""
        implTestQuoteEscape(input, expectedOutput)
    }

    @Test
    fun testQuoteEscapeQuotesAndComma() {
        val input = "\"To be, or not to be ...\" is a well-known phrase from Shakespeare's Hamlet."
        val expectedOutput = "\"" + input.replace("\"", "\"\"") + "\""
        implTestQuoteEscape(input, expectedOutput)
    }

    private fun implTestQuoteEscape(input: String, expectedOutput: String) {
        val actualOutput = CSVUtil.quoteEscape(input)
        assertEquals(expectedOutput, actualOutput)
    }
}
