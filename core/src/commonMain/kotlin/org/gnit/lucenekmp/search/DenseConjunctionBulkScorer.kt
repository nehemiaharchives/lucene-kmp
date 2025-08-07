package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.math.min


/**
 * BulkScorer implementation of [ConjunctionScorer] that is specialized for dense clauses.
 * Whenever sensible, it intersects clauses by loading their matches into a bit set and computing
 * the intersection of clauses by and-ing these bit sets.
 *
 *
 * An empty set of iterators is interpreted as meaning that all docs in [0, maxDoc) match.
 */
internal class DenseConjunctionBulkScorer(iterators: MutableList<DocIdSetIterator>, maxDoc: Int, constantScore: Float) :
    BulkScorer() {
    private val maxDoc: Int
    private val iterators: MutableList<DocIdSetIterator>
    private val scorable: SimpleScorable

    private val windowMatches: FixedBitSet = FixedBitSet(WINDOW_SIZE)
    private val clauseWindowMatches: FixedBitSet = FixedBitSet(WINDOW_SIZE)
    private val docIdStreamView: DocIdStreamView = this.DocIdStreamView()
    private val rangeDocIdStream: RangeDocIdStream = this.RangeDocIdStream()
    private val singleIteratorDocIdStream: SingleIteratorDocIdStream =
        this.SingleIteratorDocIdStream()

    init {
        var iterators = iterators
        this.maxDoc = maxDoc
        iterators = ArrayList<DocIdSetIterator>(iterators)
        iterators.sortBy { it.cost() }
        this.iterators = iterators
        this.scorable = SimpleScorable()
        scorable.score = constantScore
    }

    @Throws(IOException::class)
    override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
        var min = min
        var max = max
        collector.scorer = scorable
        var iterators = this.iterators
        if (collector.competitiveIterator() != null) {
            iterators = ArrayList(iterators)
            iterators.add(collector.competitiveIterator()!!)
        }

        for (it in iterators) {
            min = kotlin.math.max(min, it.docID())
        }

        max = min(max, maxDoc)

        var lead: DocIdSetIterator? = null
        if (!iterators.isEmpty()) {
            lead = iterators[0]
            if (lead.docID() < min) {
                min = lead.advance(min)
            }
        }

        if (min >= max) {
            return if (min >= maxDoc) DocIdSetIterator.NO_MORE_DOCS else min
        }

        var windowMax = min
        do {
            if (scorable.minCompetitiveScore > scorable.score) {
                return DocIdSetIterator.NO_MORE_DOCS
            }

            val windowBase = lead?.docID() ?: windowMax
            windowMax = min(max, windowBase + WINDOW_SIZE)
            if (windowMax > windowBase) {
                scoreWindowUsingBitSet(collector, acceptDocs, iterators, windowBase, windowMax)
            }
        } while (windowMax < max)

        return lead?.docID()
            ?: if (windowMax >= maxDoc) {
                DocIdSetIterator.NO_MORE_DOCS
            } else {
                windowMax
            }
    }

    @Throws(IOException::class)
    private fun scoreWindowUsingBitSet(
        collector: LeafCollector,
        acceptDocs: Bits?,
        iterators: MutableList<DocIdSetIterator>,
        windowBase: Int,
        windowMax: Int
    ) {
        require(windowMax > windowBase)
        require(windowMatches.scanIsEmpty())
        require(clauseWindowMatches.scanIsEmpty())

        if (acceptDocs == null) {
            if (iterators.isEmpty()) {
                // All docs in the range match.
                rangeDocIdStream.from = windowBase
                rangeDocIdStream.to = windowMax
                collector.collect(rangeDocIdStream)
                return
            } else if (iterators.size == 1) {
                singleIteratorDocIdStream.iterator = iterators[0]
                singleIteratorDocIdStream.from = windowBase
                singleIteratorDocIdStream.to = windowMax
                collector.collect(singleIteratorDocIdStream)
                return
            }
        }

        if (iterators.isEmpty()) {
            windowMatches.set(0, windowMax - windowBase)
        } else {
            val lead = iterators[0]
            lead.intoBitSet(windowMax, windowMatches, windowBase)
        }

        acceptDocs?.applyMask(windowMatches, windowBase)

        val windowSize = windowMax - windowBase
        val threshold = windowSize / DENSITY_THRESHOLD_INVERSE
        var upTo = 1 // the leading clause at index 0 is already applied
        while (upTo < iterators.size && windowMatches.cardinality() >= threshold) {
            val other = iterators[upTo]
            if (other.docID() < windowBase) {
                other.advance(windowBase)
            }
            other.intoBitSet(windowMax, clauseWindowMatches, windowBase)
            windowMatches.and(clauseWindowMatches)
            clauseWindowMatches.clear()
            upTo++
        }

        if (upTo < iterators.size) {
            // If the leading clause is sparse on this doc ID range or if the intersection became sparse
            // after applying a few clauses, we finish evaluating the intersection using the traditional
            // leap-frog approach. This proved important with a query such as "+secretary +of +state" on
            // wikibigall, where the intersection becomes sparse after intersecting "secretary" and
            // "state".
            var windowMatch: Int = windowMatches.nextSetBit(0)
            advanceHead@ while (windowMatch != DocIdSetIterator.NO_MORE_DOCS) {
                val doc = windowBase + windowMatch
                for (i in upTo..<iterators.size) {
                    val other = iterators[i]
                    var otherDoc = other.docID()
                    if (otherDoc < doc) {
                        otherDoc = other.advance(doc)
                    }
                    if (doc != otherDoc) {
                        val clearUpTo = min(WINDOW_SIZE, otherDoc - windowBase)
                        windowMatches.clear(windowMatch, clearUpTo)
                        windowMatch = advance(windowMatches, clearUpTo)
                        continue@advanceHead
                    }
                }
                windowMatch = advance(windowMatches, windowMatch + 1)
            }
        }

        docIdStreamView.windowBase = windowBase
        collector.collect(docIdStreamView)
        windowMatches.clear()

        // If another clause is more advanced than the leading clause then advance the leading clause,
        // it's important to take advantage of large gaps in the postings lists of other clauses.
        if (iterators.size >= 2) {
            val lead = iterators[0]
            var maxOtherDocID = -1
            for (i in 1..<iterators.size) {
                maxOtherDocID = kotlin.math.max(maxOtherDocID, iterators[i].docID())
            }
            if (lead.docID() < maxOtherDocID) {
                lead.advance(maxOtherDocID)
            }
        }
    }

    override fun cost(): Long {
        return if (iterators.isEmpty()) {
            maxDoc.toLong()
        } else {
            iterators[0].cost()
        }
    }

    internal inner class DocIdStreamView : DocIdStream() {
        var windowBase: Int = 0

        @Throws(IOException::class)
        override fun forEach(consumer: CheckedIntConsumer<IOException>) {
            val windowBase = this.windowBase
            val bitArray: LongArray = windowMatches.bits
            for (idx in bitArray.indices) {
                var bits = bitArray[idx]
                while (bits != 0L) {
                    val ntz: Int = Long.numberOfTrailingZeros(bits)
                    consumer.accept(windowBase + ((idx shl 6) or ntz))
                    bits = bits xor (1L shl ntz)
                }
            }
        }

        @Throws(IOException::class)
        override fun count(): Int {
            return windowMatches.cardinality()
        }
    }

    internal inner class RangeDocIdStream : DocIdStream() {
        var from: Int = 0
        var to: Int = 0

        @Throws(IOException::class)
        override fun forEach(consumer: CheckedIntConsumer<IOException>) {
            for (i in from..<to) {
                consumer.accept(i)
            }
        }

        @Throws(IOException::class)
        override fun count(): Int {
            return to - from
        }
    }

    /** [DocIdStream] for a [DocIdSetIterator] with no live docs to apply.  */
    internal inner class SingleIteratorDocIdStream : DocIdStream() {
        var from: Int = 0
        var to: Int = 0
        var iterator: DocIdSetIterator? = null

        @Throws(IOException::class)
        override fun forEach(consumer: CheckedIntConsumer<IOException>) {
            // If there are no live docs to apply, loading matching docs into a bit set and then iterating
            // bits is unlikely to beat iterating the iterator directly.
            if (iterator!!.docID() < from) {
                iterator!!.advance(from)
            }
            var doc = iterator!!.docID()
            while (doc < to) {
                consumer.accept(doc)
                doc = iterator!!.nextDoc()
            }
        }

        @Throws(IOException::class)
        override fun count(): Int {
            // If the collector is just interested in the count, loading in a bit set and counting bits is
            // often faster than incrementing a counter on every call to nextDoc().
            require(windowMatches.scanIsEmpty())
            iterator!!.intoBitSet(to, clauseWindowMatches, from)
            val count: Int = clauseWindowMatches.cardinality()
            clauseWindowMatches.clear()
            return count
        }
    }

    companion object {
        // Use a small-ish window size to make sure that we can take advantage of gaps in the postings of
        // clauses that are not leading iteration.
        const val WINDOW_SIZE: Int = 4096

        // Only use bit sets to compute the intersection if more than 1/32th of the docs are expected to
        // match. Experiments suggested that values that are a bit higher than this would work better, but
        // we're erring on the conservative side.
        const val DENSITY_THRESHOLD_INVERSE: Int = Long.SIZE_BITS / 2

        private fun advance(set: FixedBitSet, i: Int): Int {
            return if (i >= WINDOW_SIZE) {
                DocIdSetIterator.NO_MORE_DOCS
            } else {
                set.nextSetBit(i)
            }
        }
    }
}
