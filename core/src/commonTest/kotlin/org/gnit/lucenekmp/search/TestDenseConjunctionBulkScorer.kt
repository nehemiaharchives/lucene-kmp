package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.tests.search.AssertingBulkScorer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestDenseConjunctionBulkScorer : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testSameMatches() {
        val maxDoc = 100_000
        val clause1 = FixedBitSet(maxDoc)
        val clause2 = FixedBitSet(maxDoc)
        val clause3 = FixedBitSet(maxDoc)
        for (i in 0..<maxDoc step 2) {
            clause1.set(i)
            clause2.set(i)
            clause3.set(i)
        }
        var scorer: BulkScorer =
            DenseConjunctionBulkScorer(
                mutableListOf(
                    BitSetIterator(clause1, clause1.approximateCardinality().toLong()),
                    BitSetIterator(clause2, clause2.approximateCardinality().toLong()),
                    BitSetIterator(clause3, clause3.approximateCardinality().toLong()),
                ),
                maxDoc,
                0f,
            )
        val result = FixedBitSet(maxDoc)
        scorer.score(
            object : LeafCollector {
                override var scorer: Scorable? = null

                override fun collect(doc: Int) {
                    result.set(doc)
                }
            },
            null,
            0,
            DocIdSetIterator.NO_MORE_DOCS,
        )

        assertEquals(clause1, result)

        // Now exercise DocIdStream.count()
        scorer =
            DenseConjunctionBulkScorer(
                mutableListOf(
                    BitSetIterator(clause1, clause1.approximateCardinality().toLong()),
                    BitSetIterator(clause2, clause2.approximateCardinality().toLong()),
                    BitSetIterator(clause3, clause3.approximateCardinality().toLong()),
                ),
                maxDoc,
                0f,
            )
        val collector = CountingLeafCollector()
        scorer.score(collector, null, 0, DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(clause1.cardinality(), collector.count)
    }

    @Test
    @Throws(IOException::class)
    fun testApplyAcceptDocs() {
        val maxDoc = 100_000
        val clause1 = FixedBitSet(maxDoc)
        val clause2 = FixedBitSet(maxDoc)
        clause1.set(0, maxDoc)
        clause2.set(0, maxDoc)
        val acceptDocs = FixedBitSet(maxDoc)
        for (i in 0..<maxDoc step 2) {
            acceptDocs.set(i)
        }
        var scorer: BulkScorer =
            DenseConjunctionBulkScorer(
                mutableListOf(
                    BitSetIterator(clause1, clause1.approximateCardinality().toLong()),
                    BitSetIterator(clause2, clause2.approximateCardinality().toLong()),
                ),
                maxDoc,
                0f,
            )
        val result = FixedBitSet(maxDoc)
        scorer.score(
            object : LeafCollector {
                override var scorer: Scorable? = null

                override fun collect(doc: Int) {
                    result.set(doc)
                }
            },
            acceptDocs,
            0,
            DocIdSetIterator.NO_MORE_DOCS,
        )

        assertEquals(acceptDocs, result)

        // Now exercise DocIdStream.count()
        scorer =
            DenseConjunctionBulkScorer(
                mutableListOf(
                    BitSetIterator(clause1, clause1.approximateCardinality().toLong()),
                    BitSetIterator(clause2, clause2.approximateCardinality().toLong()),
                ),
                maxDoc,
                0f,
            )
        val collector = CountingLeafCollector()
        scorer.score(collector, acceptDocs, 0, DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(acceptDocs.cardinality(), collector.count)
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyIntersection() {
        val maxDoc = 100_000
        val clause1 = FixedBitSet(maxDoc)
        val clause2 = FixedBitSet(maxDoc)
        for (i in 0..<maxDoc - 1 step 2) {
            clause1.set(i)
            clause2.set(i + 1)
        }
        var scorer: BulkScorer =
            DenseConjunctionBulkScorer(
                mutableListOf(
                    BitSetIterator(clause1, clause1.approximateCardinality().toLong()),
                    BitSetIterator(clause2, clause2.approximateCardinality().toLong()),
                ),
                maxDoc,
                0f,
            )
        val result = FixedBitSet(maxDoc)
        scorer.score(
            object : LeafCollector {
                override var scorer: Scorable? = null

                override fun collect(doc: Int) {
                    result.set(doc)
                }
            },
            null,
            0,
            DocIdSetIterator.NO_MORE_DOCS,
        )

        assertTrue(result.scanIsEmpty())

        // Now exercise DocIdStream.count()
        scorer =
            DenseConjunctionBulkScorer(
                mutableListOf(
                    BitSetIterator(clause1, clause1.approximateCardinality().toLong()),
                    BitSetIterator(clause2, clause2.approximateCardinality().toLong()),
                ),
                maxDoc,
                0f,
            )
        val collector = CountingLeafCollector()
        scorer.score(collector, null, 0, DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(0, collector.count)
    }

    @Test
    @Throws(IOException::class)
    fun testClustered() {
        val maxDoc = 1_000
        // TODO reduced maxDoc = 100000 to 1000, rangeStart1 = 10000 to 100, rangeEnd1 = 90000 to 900, rangeEnd2 = 80000 to 800, rangeStart3 = 20000 to 200, rangeEnd3 = 100000 to 1000, expectedStart = 20000 to 200, expectedEnd = 80000 to 800 for dev speed
        val clause1 = FixedBitSet(maxDoc)
        val clause2 = FixedBitSet(maxDoc)
        val clause3 = FixedBitSet(maxDoc)
        clause1.set(100, 900)
        clause2.set(0, 800)
        clause3.set(200, 1_000)
        var scorer: BulkScorer =
            DenseConjunctionBulkScorer(
                mutableListOf(
                    BitSetIterator(clause1, clause1.approximateCardinality().toLong()),
                    BitSetIterator(clause2, clause2.approximateCardinality().toLong()),
                    BitSetIterator(clause3, clause3.approximateCardinality().toLong()),
                ),
                maxDoc,
                0f,
            )
        val result = FixedBitSet(maxDoc)
        scorer.score(
            object : LeafCollector {
                override var scorer: Scorable? = null

                override fun collect(doc: Int) {
                    result.set(doc)
                }
            },
            null,
            0,
            DocIdSetIterator.NO_MORE_DOCS,
        )

        val expected = FixedBitSet(maxDoc)
        expected.set(200, 800)
        assertContentEquals(expected.bits, result.bits)
        assertEquals(expected, result)
    }

    @Test
    @Throws(IOException::class)
    fun testSparseAfter2ndClause() {
        val maxDoc = 100_000
        val clause1 = FixedBitSet(maxDoc)
        val clause2 = FixedBitSet(maxDoc)
        val clause3 = FixedBitSet(maxDoc)
        // 13 and 17 are primes, so their only intersection is on multiples of both 13 and 17
        // Likewise, 19 is prime, so the only intersection of the conjunction is on multiples of 13, 17
        // and 19
        for (i in 0..<maxDoc step 13) {
            clause1.set(i)
        }
        for (i in 0..<maxDoc step 17) {
            clause2.set(i)
        }
        for (i in 0..<maxDoc step 19) {
            clause3.set(i)
        }
        var scorer: BulkScorer =
            DenseConjunctionBulkScorer(
                mutableListOf(
                    BitSetIterator(clause1, clause1.approximateCardinality().toLong()),
                    BitSetIterator(clause2, clause2.approximateCardinality().toLong()),
                    BitSetIterator(clause3, clause3.approximateCardinality().toLong()),
                ),
                maxDoc,
                0f,
            )
        val result = FixedBitSet(maxDoc)
        scorer.score(
            object : LeafCollector {
                override var scorer: Scorable? = null

                override fun collect(doc: Int) {
                    result.set(doc)
                }
            },
            null,
            0,
            DocIdSetIterator.NO_MORE_DOCS,
        )

        val expected = FixedBitSet(maxDoc)
        for (i in 0..<maxDoc step 13 * 17 * 19) {
            expected.set(i)
        }
        assertEquals(expected, result)

        // Now exercise DocIdStream.count()
        scorer =
            DenseConjunctionBulkScorer(
                mutableListOf(
                    BitSetIterator(clause1, clause1.approximateCardinality().toLong()),
                    BitSetIterator(clause2, clause2.approximateCardinality().toLong()),
                    BitSetIterator(clause3, clause3.approximateCardinality().toLong()),
                ),
                maxDoc,
                0f,
            )
        val collector = CountingLeafCollector()
        scorer.score(collector, null, 0, DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(expected.cardinality(), collector.count)
    }

    @Test
    @Throws(IOException::class)
    fun testZeroClauseNoLiveDocs() {
        val maxDoc = 100_000
        var scorer: BulkScorer = DenseConjunctionBulkScorer(mutableListOf(), maxDoc, 0f)
        val result = FixedBitSet(maxDoc)
        scorer.score(
            object : LeafCollector {
                override var scorer: Scorable? = null

                override fun collect(doc: Int) {
                    result.set(doc)
                }
            },
            null,
            0,
            DocIdSetIterator.NO_MORE_DOCS,
        )

        result.flip(0, maxDoc)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, result.nextSetBit(0))

        // Now exercise DocIdStream.count()
        scorer = DenseConjunctionBulkScorer(mutableListOf(), maxDoc, 0f)
        val collector = CountingLeafCollector()
        scorer.score(collector, null, 0, DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(maxDoc, collector.count)
    }

    @Test
    @Throws(IOException::class)
    fun testZeroClauseWithLiveDocs() {
        val maxDoc = 100_000
        var scorer: BulkScorer = DenseConjunctionBulkScorer(mutableListOf(), maxDoc, 0f)
        // AssertingBulkScorer randomly splits the scored range into smaller ranges
        scorer = AssertingBulkScorer.wrap(random(), scorer, maxDoc)
        val acceptDocs = FixedBitSet(maxDoc)
        acceptDocs.set(10_000, 20_000)
        for (i in 30_000..<maxDoc step 3) {
            acceptDocs.set(i)
        }
        val result = FixedBitSet(maxDoc)
        scorer.score(
            object : LeafCollector {
                override var scorer: Scorable? = null

                override fun collect(doc: Int) {
                    result.set(doc)
                }
            },
            acceptDocs,
            0,
            DocIdSetIterator.NO_MORE_DOCS,
        )

        assertEquals(acceptDocs, result)

        // Now exercise DocIdStream.count()
        scorer = DenseConjunctionBulkScorer(mutableListOf(), maxDoc, 0f)
        val collector = CountingLeafCollector()
        scorer.score(collector, acceptDocs, 0, DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(acceptDocs.cardinality(), collector.count)
    }

    @Test
    @Throws(IOException::class)
    fun testOneClauseNoLiveDocs() {
        val maxDoc = 100_000
        val clause1 = FixedBitSet(maxDoc)
        for (i in 0..<maxDoc step 2) {
            clause1.set(i)
        }
        var scorer: BulkScorer =
            DenseConjunctionBulkScorer(
                mutableListOf(BitSetIterator(clause1, clause1.approximateCardinality().toLong())),
                maxDoc,
                0f,
            )
        val result = FixedBitSet(maxDoc)
        scorer.score(
            object : LeafCollector {
                override var scorer: Scorable? = null

                override fun collect(doc: Int) {
                    result.set(doc)
                }
            },
            null,
            0,
            DocIdSetIterator.NO_MORE_DOCS,
        )

        assertEquals(clause1, result)

        // Now exercise DocIdStream.count()
        scorer =
            DenseConjunctionBulkScorer(
                mutableListOf(BitSetIterator(clause1, clause1.approximateCardinality().toLong())),
                maxDoc,
                0f,
            )
        val collector = CountingLeafCollector()
        scorer.score(collector, null, 0, DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(clause1.cardinality(), collector.count)
    }

    @Test
    @Throws(IOException::class)
    fun testOneClauseWithLiveDocs() {
        val maxDoc = 100_000
        val clause1 = FixedBitSet(maxDoc)
        for (i in 0..<maxDoc step 2) {
            clause1.set(i)
        }
        var scorer: BulkScorer =
            DenseConjunctionBulkScorer(
                mutableListOf(BitSetIterator(clause1, clause1.approximateCardinality().toLong())),
                maxDoc,
                0f,
            )
        // AssertingBulkScorer randomly splits the scored range into smaller ranges
        scorer = AssertingBulkScorer.wrap(random(), scorer, maxDoc)
        val acceptDocs = FixedBitSet(maxDoc)
        acceptDocs.set(10_000, 20_000)
        for (i in 30_000..<maxDoc step 3) {
            acceptDocs.set(i)
        }
        val result = FixedBitSet(maxDoc)
        scorer.score(
            object : LeafCollector {
                override var scorer: Scorable? = null

                override fun collect(doc: Int) {
                    result.set(doc)
                }
            },
            acceptDocs,
            0,
            DocIdSetIterator.NO_MORE_DOCS,
        )

        val expected = FixedBitSet(maxDoc)
        expected.or(acceptDocs)
        expected.and(clause1)
        assertEquals(expected, result)

        // Now exercise DocIdStream.count()
        scorer =
            DenseConjunctionBulkScorer(
                mutableListOf(BitSetIterator(clause1, clause1.approximateCardinality().toLong())),
                maxDoc,
                0f,
            )
        val collector = CountingLeafCollector()
        scorer.score(collector, acceptDocs, 0, DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(expected.cardinality(), collector.count)
    }

    @Test
    @Throws(IOException::class)
    fun testStopOnMinCompetitiveScore() {
        val maxDoc = 100_000
        val clause1 = FixedBitSet(maxDoc)
        val clause2 = FixedBitSet(maxDoc)
        for (i in 0..<maxDoc step 2) {
            clause1.set(i)
        }
        for (i in 0..<maxDoc step 5) {
            clause2.set(i)
        }
        var scorer: BulkScorer =
            DenseConjunctionBulkScorer(
                mutableListOf(
                    BitSetIterator(clause1, clause1.approximateCardinality().toLong()),
                    BitSetIterator(clause2, clause2.approximateCardinality().toLong()),
                ),
                maxDoc,
                0f,
            )
        val result = FixedBitSet(maxDoc)
        scorer.score(
            object : LeafCollector {
                private var scorable: Scorable? = null

                override var scorer: Scorable?
                    get() = scorable
                    set(value) {
                        scorable = value
                    }

                override fun collect(doc: Int) {
                    result.set(doc)
                    if (doc == 50_000) {
                        scorable!!.minCompetitiveScore = Float.MIN_VALUE
                    }
                    // It should never go above the doc when setMinCompetitiveScore was called, plus the
                    // window size
                    assertTrue(doc < 50_000 + DenseConjunctionBulkScorer.WINDOW_SIZE)
                }
            },
            null,
            0,
            DocIdSetIterator.NO_MORE_DOCS,
        )
    }

    private class CountingLeafCollector : LeafCollector {
        var count = 0

        override var scorer: Scorable? = null

        override fun collect(doc: Int) {
            count++
        }

        override fun collect(stream: DocIdStream) {
            count += stream.count()
        }
    }
}
