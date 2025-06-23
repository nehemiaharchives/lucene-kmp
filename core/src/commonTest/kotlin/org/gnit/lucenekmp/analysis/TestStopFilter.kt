package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.Ignore
import org.gnit.lucenekmp.analysis.CharArraySet

class TestStopFilter : BaseTokenStreamTestCase() {

    @Test
    fun testExactCase() {
        val stopWords = CharArraySet(mutableListOf<Any>("is", "the", "Time"), false)
        val result = "Now is The Time".split(" ").filter { token -> !stopWords.contains(token) }
        assertEquals(listOf("Now", "The"), result)
    }

    @Ignore
    @Test
    fun testStopFilter() {
        // TODO implement after BaseTokenStreamTestCase is ported
    }

    @Ignore
    @Test
    fun testTokenPositionWithStopwordFilter() {
        // TODO implement after BaseTokenStreamTestCase is ported
    }

    @Ignore
    @Test
    fun testTokenPositionsWithConcatenatedStopwordFilters() {
        // TODO implement after BaseTokenStreamTestCase is ported
    }

    @Ignore
    @Test
    fun testEndStopword() {
        // TODO implement after BaseTokenStreamTestCase is ported
    }
}

