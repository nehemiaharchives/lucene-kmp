package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMaxScoreAccumulator : LuceneTestCase() {
    @Test
    fun testSimple() {
        val acc = MaxScoreAccumulator()
        acc.accumulate(0, 0f)
        assertEquals(0f, MaxScoreAccumulator.toScore(acc.raw), 0f)
        assertEquals(0, MaxScoreAccumulator.docId(acc.raw))
        acc.accumulate(10, 0f)
        assertEquals(0f, MaxScoreAccumulator.toScore(acc.raw), 0f)
        assertEquals(0, MaxScoreAccumulator.docId(acc.raw))
        acc.accumulate(100, 1000f)
        assertEquals(1000f, MaxScoreAccumulator.toScore(acc.raw), 0f)
        assertEquals(100, MaxScoreAccumulator.docId(acc.raw))
        acc.accumulate(1000, 5f)
        assertEquals(1000f, MaxScoreAccumulator.toScore(acc.raw), 0f)
        assertEquals(100, MaxScoreAccumulator.docId(acc.raw))
        acc.accumulate(99, 1000f)
        assertEquals(1000f, MaxScoreAccumulator.toScore(acc.raw), 0f)
        assertEquals(99, MaxScoreAccumulator.docId(acc.raw))
        acc.accumulate(1000, 1001f)
        assertEquals(1001f, MaxScoreAccumulator.toScore(acc.raw), 0f)
        assertEquals(1000, MaxScoreAccumulator.docId(acc.raw))
        acc.accumulate(10, 1001f)
        assertEquals(1001f, MaxScoreAccumulator.toScore(acc.raw), 0f)
        assertEquals(10, MaxScoreAccumulator.docId(acc.raw))
        acc.accumulate(100, 1001f)
        assertEquals(1001f, MaxScoreAccumulator.toScore(acc.raw), 0f)
        assertEquals(10, MaxScoreAccumulator.docId(acc.raw))
    }
}
