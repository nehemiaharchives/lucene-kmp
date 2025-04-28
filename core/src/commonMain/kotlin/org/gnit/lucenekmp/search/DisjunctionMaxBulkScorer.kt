package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min


/** Bulk scorer for [DisjunctionMaxQuery] when the tie-break multiplier is zero.  */
internal class DisjunctionMaxBulkScorer(scorers: MutableList<BulkScorer>) : BulkScorer() {
    private class BulkScorerAndNext(val scorer: BulkScorer) {
        var next: Int = 0
    }

    // WINDOW_SIZE + 1 to ease iteration on the bit set
    private val windowMatches: FixedBitSet = FixedBitSet(WINDOW_SIZE + 1)
    private val windowScores = FloatArray(WINDOW_SIZE)
    private val scorers: PriorityQueue<BulkScorerAndNext>
    private val topLevelScorable = SimpleScorable()

    init {
        require(scorers.size >= 2)
        this.scorers =
            object : PriorityQueue<BulkScorerAndNext>(scorers.size) {
                override fun lessThan(a: BulkScorerAndNext, b: BulkScorerAndNext): Boolean {
                    return a.next < b.next
                }
            }
        for (scorer in scorers) {
            this.scorers.add(BulkScorerAndNext(scorer))
        }
    }

    @Throws(IOException::class)
    override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
        var top: BulkScorerAndNext = scorers.top()

        while (top.next < max) {
            val windowMin = max(top.next, min)
            val windowMax = min(max, windowMin + WINDOW_SIZE)

            // First compute matches / scores in the window
            do {
                top.next =
                    top.scorer.score(
                        object : LeafCollector {
                            private var scorer: Scorable? = null

                            @Throws(IOException::class)
                            override fun setScorer(scorer: Scorable) {
                                this.scorer = scorer
                                if (topLevelScorable.minCompetitiveScore != 0f) {
                                    scorer.minCompetitiveScore = topLevelScorable.minCompetitiveScore
                                }
                            }

                            @Throws(IOException::class)
                            override fun collect(doc: Int) {
                                val delta = doc - windowMin
                                windowMatches.set(doc - windowMin)
                                windowScores[delta] = max(windowScores[delta], scorer!!.score())
                            }
                        },
                        acceptDocs,
                        windowMin,
                        windowMax
                    )
                top = scorers.updateTop()
            } while (top.next < windowMax)

            // Then replay
            collector.setScorer(topLevelScorable)
            var windowDoc: Int = windowMatches.nextSetBit(0)
            while (windowDoc != DocIdSetIterator.NO_MORE_DOCS
            ) {
                val doc = windowMin + windowDoc
                topLevelScorable.score = windowScores[windowDoc]
                collector.collect(doc)
                windowDoc = windowMatches.nextSetBit(windowDoc + 1)
            }

            // Finally clean up state
            windowMatches.clear()
            Arrays.fill(windowScores, 0f)
        }

        return top.next
    }

    override fun cost(): Long {
        var cost: Long = 0
        for (scorer in scorers) {
            cost += scorer.scorer.cost()
        }
        return cost
    }

    companion object {
        // Same window size as BooleanScorer
        private const val WINDOW_SIZE = 4096
    }
}
