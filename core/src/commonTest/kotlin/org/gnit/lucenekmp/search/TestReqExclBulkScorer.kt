package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.tests.search.RandomApproximationQuery.RandomTwoPhaseView
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class TestReqExclBulkScorer : LuceneTestCase() {

    @Test
    fun testRandom() {
        val iters = atLeast(10)
        for (iter in 0 until iters) {
            doTestRandom(false)
        }
    }

    @Test
    fun testRandomTwoPhase() {
        val iters = atLeast(10)
        for (iter in 0 until iters) {
            doTestRandom(true)
        }
    }

    @Throws(okio.IOException::class)
    private fun doTestRandom(twoPhase: Boolean) {
        val maxDoc = TestUtil.nextInt(random(), 1, 1000)
        val reqBuilder = DocIdSetBuilder(maxDoc)
        val exclBuilder = DocIdSetBuilder(maxDoc)
        val numIncludedDocs = TestUtil.nextInt(random(), 1, maxDoc)
        val numExcludedDocs = TestUtil.nextInt(random(), 1, maxDoc)
        val reqAdder = reqBuilder.grow(numIncludedDocs)
        for (i in 0 until numIncludedDocs) {
            reqAdder.add(random().nextInt(maxDoc))
        }
        val exclAdder = exclBuilder.grow(numExcludedDocs)
        for (i in 0 until numExcludedDocs) {
            exclAdder.add(random().nextInt(maxDoc))
        }

        val req = reqBuilder.build()
        val excl = exclBuilder.build()

        val reqBulkScorer = object : BulkScorer() {
            private val iterator = req.iterator()

            override fun score(
                collector: LeafCollector,
                acceptDocs: Bits?,
                min: Int,
                max: Int
            ): Int {
                var doc = iterator.docID()
                if (iterator.docID() < min) {
                    doc = iterator.advance(min)
                }
                while (doc < max) {
                    if (acceptDocs == null || acceptDocs.get(doc)) {
                        collector.collect(doc)
                    }
                    doc = iterator.nextDoc()
                }
                return doc
            }

            override fun cost(): Long {
                return iterator.cost()
            }
        }

        val reqExcl = if (twoPhase) {
            ReqExclBulkScorer(reqBulkScorer, RandomTwoPhaseView(random(), excl.iterator()))
        } else {
            ReqExclBulkScorer(reqBulkScorer, excl.iterator())
        }
        val actualMatches = FixedBitSet(maxDoc)
        if (random().nextBoolean()) {
            reqExcl.score(object : LeafCollector {
                override var scorer: Scorable? = null
                override fun collect(doc: Int) {
                    actualMatches.set(doc)
                }
            }, null, 0, DocIdSetIterator.NO_MORE_DOCS)
        } else {
            var next = 0
            while (next < maxDoc) {
                val min = next
                val max = min + random().nextInt(10)
                next = reqExcl.score(object : LeafCollector {
                    override var scorer: Scorable? = null
                    override fun collect(doc: Int) {
                        actualMatches.set(doc)
                    }
                }, null, min, max)
                assertTrue(next >= max)
            }
        }

        val expectedMatches = FixedBitSet(maxDoc)
        expectedMatches.or(req.iterator())
        val excludedSet = FixedBitSet(maxDoc)
        excludedSet.or(excl.iterator())
        expectedMatches.andNot(excludedSet)

        assertContentEquals(expectedMatches.bits, actualMatches.bits)
    }
}

