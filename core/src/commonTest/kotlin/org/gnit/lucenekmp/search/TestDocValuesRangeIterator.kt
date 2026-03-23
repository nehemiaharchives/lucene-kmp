package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestDocValuesRangeIterator : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testSingleLevel() {
        doTestBasics(false)
    }

    @Test
    @Throws(IOException::class)
    fun testMultipleLevels() {
        doTestBasics(true)
    }

    @Throws(IOException::class)
    private fun doTestBasics(doLevels: Boolean) {
        val queryMin = 10L
        val queryMax = 20L

        // Test with both gaps and no-gaps in the ranges:
        val values = docValues(queryMin, queryMax)
        val values2 = docValues(queryMin, queryMax)

        val twoPhaseCalled = AtomicBoolean(false)
        val twoPhase = twoPhaseIterator(values, queryMin, queryMax, twoPhaseCalled)
        val twoPhaseCalled2 = AtomicBoolean(false)
        val twoPhase2 = twoPhaseIterator(values2, queryMin, queryMax, twoPhaseCalled2)

        val skipper = docValuesSkipper(queryMin, queryMax, doLevels)
        val skipper2 = docValuesSkipper(queryMin, queryMax, doLevels)

        val rangeIterator =
            DocValuesRangeIterator(twoPhase, skipper, queryMin, queryMax, false)
        val rangeIteratorWithGaps =
            DocValuesRangeIterator(twoPhase2, skipper2, queryMin, queryMax, true)
        val rangeApproximation =
            rangeIterator.approximation() as DocValuesRangeIterator.Approximation
        val rangeApproximationWithGaps =
            rangeIteratorWithGaps.approximation() as DocValuesRangeIterator.Approximation

        assertEquals(100, rangeApproximation.advance(100))
        assertEquals(100, rangeApproximationWithGaps.advance(100))
        assertEquals(DocValuesRangeIterator.Match.YES, rangeApproximation.match)
        assertEquals(DocValuesRangeIterator.Match.MAYBE, rangeApproximationWithGaps.match)
        assertEquals(255, rangeApproximation.upTo)
        if (doLevels) {
            assertEquals(127, rangeApproximationWithGaps.upTo)
        } else {
            assertEquals(255, rangeApproximationWithGaps.upTo)
        }
        assertTrue(rangeIterator.matches())
        assertTrue(rangeIteratorWithGaps.matches())
        assertTrue(values.docID() < rangeApproximation.docID()) // we did not advance doc values
        assertEquals(
            values2.docID(),
            rangeApproximationWithGaps.docID()
        ) // we _did_ advance doc values
        assertFalse(twoPhaseCalled.load())
        assertTrue(twoPhaseCalled2.load())
        twoPhaseCalled2.store(false)

        assertEquals(768, rangeApproximation.advance(300))
        assertEquals(768, rangeApproximationWithGaps.advance(300))
        assertEquals(DocValuesRangeIterator.Match.MAYBE, rangeApproximation.match)
        assertEquals(DocValuesRangeIterator.Match.MAYBE, rangeApproximationWithGaps.match)
        if (doLevels) {
            assertEquals(831, rangeApproximation.upTo)
            assertEquals(831, rangeApproximationWithGaps.upTo)
        } else {
            assertEquals(1023, rangeApproximation.upTo)
            assertEquals(1023, rangeApproximationWithGaps.upTo)
        }
        for (i in 0..<10) {
            assertEquals(values.docID(), rangeApproximation.docID())
            assertEquals(values2.docID(), rangeApproximationWithGaps.docID())
            assertEquals(twoPhase.matches(), rangeIterator.matches())
            assertEquals(twoPhase2.matches(), rangeIteratorWithGaps.matches())
            assertTrue(twoPhaseCalled.load())
            assertTrue(twoPhaseCalled2.load())
            twoPhaseCalled.store(false)
            twoPhaseCalled2.store(false)
            rangeApproximation.nextDoc()
            rangeApproximationWithGaps.nextDoc()
        }

        assertEquals(1100, rangeApproximation.advance(1099))
        assertEquals(1100, rangeApproximationWithGaps.advance(1099))
        assertEquals(DocValuesRangeIterator.Match.IF_DOC_HAS_VALUE, rangeApproximation.match)
        assertEquals(DocValuesRangeIterator.Match.MAYBE, rangeApproximationWithGaps.match)
        assertEquals(1024 + 256 - 1, rangeApproximation.upTo)
        if (doLevels) {
            assertEquals(1024 + 128 - 1, rangeApproximationWithGaps.upTo)
        } else {
            assertEquals(1024 + 256 - 1, rangeApproximationWithGaps.upTo)
        }
        assertEquals(values.docID(), rangeApproximation.docID())
        assertEquals(values2.docID(), rangeApproximationWithGaps.docID())
        assertTrue(rangeIterator.matches())
        assertTrue(rangeIteratorWithGaps.matches())
        assertFalse(twoPhaseCalled.load())
        assertTrue(twoPhaseCalled2.load())
        twoPhaseCalled2.store(false)

        assertEquals(1024 + 768, rangeApproximation.advance(1024 + 300))
        assertEquals(1024 + 768, rangeApproximationWithGaps.advance(1024 + 300))
        assertEquals(DocValuesRangeIterator.Match.MAYBE, rangeApproximation.match)
        assertEquals(DocValuesRangeIterator.Match.MAYBE, rangeApproximationWithGaps.match)
        if (doLevels) {
            assertEquals(1024 + 831, rangeApproximation.upTo)
            assertEquals(1024 + 831, rangeApproximationWithGaps.upTo)
        } else {
            assertEquals(2047, rangeApproximation.upTo)
            assertEquals(2047, rangeApproximationWithGaps.upTo)
        }
        for (i in 0..<10) {
            assertEquals(values.docID(), rangeApproximation.docID())
            assertEquals(values2.docID(), rangeApproximationWithGaps.docID())
            assertEquals(twoPhase.matches(), rangeIterator.matches())
            assertEquals(twoPhase2.matches(), rangeIteratorWithGaps.matches())
            assertTrue(twoPhaseCalled.load())
            assertTrue(twoPhaseCalled2.load())
            twoPhaseCalled.store(false)
            twoPhaseCalled2.store(false)
            rangeApproximation.nextDoc()
            rangeApproximationWithGaps.nextDoc()
        }

        assertEquals(DocIdSetIterator.NO_MORE_DOCS, rangeApproximation.advance(2048))
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, rangeApproximationWithGaps.advance(2048))
    }

    // Fake numeric doc values so that:
    // docs 0-256 all match
    // docs in 256-512 are all greater than queryMax
    // docs in 512-768 are all less than queryMin
    // docs in 768-1024 have some docs that match the range, others not
    // docs in 1024-2048 follow a similar pattern as docs in 0-1024 except that not all docs have a
    // value
    private fun docValues(queryMin: Long, queryMax: Long): NumericDocValues {
        return object : NumericDocValues() {
            var doc = -1

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                throw UnsupportedOperationException()
            }

            override fun docID(): Int {
                return doc
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                return advance(doc + 1)
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                if (target < 1024) {
                    // dense up to 1024
                    return target.also { doc = it }
                } else if (doc < 2047) {
                    // 50% docs have a value up to 2048
                    return (target + (target and 1)).also { doc = it }
                } else {
                    return DocIdSetIterator.NO_MORE_DOCS.also { doc = it }
                }
            }

            @Throws(IOException::class)
            override fun longValue(): Long {
                val d = doc % 1024
                return if (d < 128) {
                    (queryMin + queryMax) shr 1
                } else if (d < 256) {
                    queryMax + 1
                } else if (d < 512) {
                    queryMin - 1
                } else {
                    when ((d / 2) % 3) {
                        0 -> queryMin - 1
                        1 -> queryMax + 1
                        2 -> (queryMin + queryMax) shr 1
                        else -> throw AssertionError()
                    }
                }
            }

            override fun cost(): Long {
                return 42
            }
        }
    }

    private fun twoPhaseIterator(
        values: NumericDocValues,
        queryMin: Long,
        queryMax: Long,
        twoPhaseCalled: AtomicBoolean
    ): TwoPhaseIterator {
        return object : TwoPhaseIterator(values) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                twoPhaseCalled.store(true)
                val v = values.longValue()
                return v >= queryMin && v <= queryMax
            }

            override fun matchCost(): Float {
                return 2f // 2 comparisons
            }
        }
    }

    private fun docValuesSkipper(queryMin: Long, queryMax: Long, doLevels: Boolean): DocValuesSkipper {
        return object : DocValuesSkipper() {
            var doc = -1

            @Throws(IOException::class)
            override fun advance(target: Int) {
                doc = target
            }

            override fun numLevels(): Int {
                return if (doLevels) 3 else 1
            }

            override fun minDocID(level: Int): Int {
                val rangeLog = 9 - numLevels() + level

                // the level is the log2 of the interval
                return if (doc < 0) {
                    -1
                } else if (doc >= 2048) {
                    DocIdSetIterator.NO_MORE_DOCS
                } else {
                    val mask = (1 shl rangeLog) - 1
                    // prior multiple of 2^level
                    doc and mask.inv()
                }
            }

            override fun maxDocID(level: Int): Int {
                val rangeLog = 9 - numLevels() + level

                val minDocID = minDocID(level)
                return when (minDocID) {
                    -1 -> -1
                    DocIdSetIterator.NO_MORE_DOCS -> DocIdSetIterator.NO_MORE_DOCS
                    else -> minDocID + (1 shl rangeLog) - 1
                }
            }

            override fun minValue(level: Int): Long {
                val d = doc % 1024
                return if (d < 128) {
                    queryMin
                } else if (d < 256) {
                    queryMax + 1
                } else if (d < 768) {
                    queryMin - 1
                } else {
                    queryMin - 1
                }
            }

            override fun maxValue(level: Int): Long {
                val d = doc % 1024
                return if (d < 128) {
                    queryMax
                } else if (d < 256) {
                    queryMax + 1
                } else if (d < 768) {
                    queryMin - 1
                } else {
                    queryMax + 1
                }
            }

            override fun docCount(level: Int): Int {
                val rangeLog = 9 - numLevels() + level

                return if (doc < 1024) {
                    1 shl rangeLog
                } else {
                    // half docs have a value
                    (1 shl rangeLog) shr 1
                }
            }

            override fun minValue(): Long {
                return Long.MIN_VALUE
            }

            override fun maxValue(): Long {
                return Long.MAX_VALUE
            }

            override fun docCount(): Int {
                return 1024 + 1024 / 2
            }
        }
    }
}
