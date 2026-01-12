package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.BulkScorer
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.LeafCollector
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.util.Bits
import kotlin.math.min
import kotlin.random.Random

/** Wraps a Scorer with additional checks  */
class AssertingBulkScorer private constructor(
    val random: Random,
    val `in`: BulkScorer,
    val maxDoc: Int
) : BulkScorer() {
    var max: Int = 0

    override fun cost(): Long {
        return `in`.cost()
    }

    @Throws(IOException::class)
    override fun score(
        collector: LeafCollector,
        acceptDocs: Bits?,
        min: Int,
        max: Int
    ): Int {
        var collector: LeafCollector = collector
        assert(
            min >= this.max
        ) { "Scoring backward: min=" + min + " while previous max was max=" + this.max }
        assert(min <= max) { "max must be greater than min, got min=$min, and max=$max" }
        this.max = max
        collector = AssertingLeafCollector(collector, min, max)
        var next = min
        do {
            val upTo: Int
            if (random.nextBoolean()) {
                upTo = max
            } else {
                val interval: Long
                if (random.nextInt(100) <= 5) {
                    interval = (1 + random.nextInt(10)).toLong()
                } else {
                    interval =
                        (1 + random.nextInt(if (random.nextBoolean()) 100 else 5000)).toLong()
                }
                upTo = Math.toIntExact(min(next + interval, max.toLong()))
            }
            next = `in`.score(
                AssertingLeafCollector(
                    collector,
                    next,
                    upTo
                ), acceptDocs, next, upTo
            )
        } while (next < max)

        if (max >= maxDoc || next >= maxDoc) {
            assert(next == DocIdSetIterator.NO_MORE_DOCS)
            return DocIdSetIterator.NO_MORE_DOCS
        } else {
            return RandomNumbers.randomIntBetween(
                random,
                max,
                next
            )
        }
    }

    override fun toString(): String {
        return "AssertingBulkScorer($`in`)"
    }

    companion object {
        fun wrap(
            random: Random,
            other: BulkScorer,
            maxDoc: Int
        ): BulkScorer {
            if (other == null || other is AssertingBulkScorer) {
                return other
            }
            return AssertingBulkScorer(random, other, maxDoc)
        }
    }
}
