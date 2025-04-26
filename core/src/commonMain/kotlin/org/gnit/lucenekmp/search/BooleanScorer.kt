package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.internal.hppc.LongArrayList
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.PriorityQueue
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import kotlin.math.max
import kotlin.math.min

/**
 * [BulkScorer] that is used for pure disjunctions and disjunctions that have low values of
 * [BooleanQuery.Builder.setMinimumNumberShouldMatch] and dense clauses. This scorer
 * scores documents by batches of 4,096 docs.
 */
internal class BooleanScorer(scorers: MutableCollection<Scorer>, minShouldMatch: Int, needsScores: Boolean) :
    BulkScorer() {
    internal class Bucket {
        var score: Double = 0.0
        var freq: Int = 0
    }

    internal class HeadPriorityQueue(maxSize: Int) : PriorityQueue<DisiWrapper>(maxSize) {
        override fun lessThan(a: DisiWrapper, b: DisiWrapper): Boolean {
            return a.doc < b.doc
        }
    }

    internal class TailPriorityQueue(maxSize: Int) : PriorityQueue<DisiWrapper>(maxSize) {
        override fun lessThan(a: DisiWrapper, b: DisiWrapper): Boolean {
            return a.cost < b.cost
        }

        fun get(i: Int): DisiWrapper {
            Objects.checkIndex(i, size())
            return getHeapArray()[1 + i] as DisiWrapper
        }
    }

    // One bucket per doc ID in the window, non-null if scores are needed or if frequencies need to be
    // counted
    val buckets: Array<Bucket?>?
    val matching: FixedBitSet = FixedBitSet(SIZE)

    val leads: Array<DisiWrapper?>
    val head: HeadPriorityQueue
    val tail: TailPriorityQueue
    val score: Score = Score()
    val minShouldMatch: Int
    val cost: Long
    val needsScores: Boolean

    internal inner class DocIdStreamView : DocIdStream() {
        var base: Int = 0

        @Throws(IOException::class)
        override fun forEach(consumer: CheckedIntConsumer<IOException>) {
            val matching: FixedBitSet = this@BooleanScorer.matching
            val buckets = this@BooleanScorer.buckets
            val base = this.base
            val bitArray: LongArray = matching.getBits()
            for (idx in bitArray.indices) {
                var bits = bitArray[idx]
                while (bits != 0L) {
                    val ntz: Int = Long.numberOfTrailingZeros(bits)
                    if (buckets != null) {
                        val indexInWindow = (idx shl 6) or ntz
                        val bucket = buckets[indexInWindow]!!
                        if (bucket.freq >= minShouldMatch) {
                            score.score = bucket.score.toFloat()
                            consumer.accept(base or indexInWindow)
                        }
                        bucket.freq = 0
                        bucket.score = 0.0
                    } else {
                        consumer.accept(base or (idx shl 6) or ntz)
                    }
                    bits = bits xor (1L shl ntz)
                }
            }
        }

        @Throws(IOException::class)
        override fun count(): Int {
            if (minShouldMatch > 1) {
                // We can't just count bits in that case
                return super.count()
            }
            return matching.cardinality()
        }
    }

    private val docIdStreamView = DocIdStreamView()

    init {
        require(!(minShouldMatch < 1 || minShouldMatch > scorers.size)) { "minShouldMatch should be within 1..num_scorers. Got $minShouldMatch" }
        require(scorers.size > 1) { "This scorer can only be used with two scorers or more, got " + scorers.size }
        if (needsScores || minShouldMatch > 1) {
            buckets = kotlin.arrayOfNulls<Bucket>(SIZE)
            for (i in buckets.indices) {
                buckets[i] = Bucket()
            }
        } else {
            buckets = null
        }
        this.leads = kotlin.arrayOfNulls<DisiWrapper>(scorers.size)
        this.head = HeadPriorityQueue(scorers.size - minShouldMatch + 1)
        this.tail = TailPriorityQueue(minShouldMatch - 1)
        this.minShouldMatch = minShouldMatch
        this.needsScores = needsScores
        val costs = LongArrayList(scorers.size)
        for (scorer in scorers) {
            val w = DisiWrapper(scorer, false)
            costs.add(w.cost)
            val evicted: DisiWrapper? = tail.insertWithOverflow(w)
            if (evicted != null) {
                head.add(evicted)
            }
        }
        this.cost = ScorerUtil.costWithMinShouldMatch(costs.asSequence(), costs.size(), minShouldMatch)
    }

    override fun cost(): Long {
        return cost
    }

    @Throws(IOException::class)
    private fun scoreWindowIntoBitSetAndReplay(
        collector: LeafCollector,
        acceptDocs: Bits?,
        base: Int,
        min: Int,
        max: Int,
        scorers: Array<DisiWrapper>,
        numScorers: Int
    ) {
        for (i in 0..<numScorers) {
            val w = scorers[i]
            require(w.doc < max)

            val it: DocIdSetIterator = w.iterator!!
            var doc = w.doc
            if (doc < min) {
                doc = it.advance(min)
            }
            if (buckets == null) {
                // This doesn't apply live docs, so we'll need to apply them later
                it.intoBitSet(max, matching, base)
            } else {
                while (doc < max) {
                    if (acceptDocs == null || acceptDocs.get(doc)) {
                        val d = doc and MASK
                        matching.set(d)
                        val bucket = buckets[d]!!
                        bucket.freq++
                        if (needsScores) {
                            bucket.score += w.scorable!!.score()
                        }
                    }
                    doc = it.nextDoc()
                }
            }

            w.doc = it.docID()
        }

        if (buckets == null && acceptDocs != null) {
            // In this case, live docs have not been applied yet.
            acceptDocs.applyMask(matching, base)
        }

        docIdStreamView.base = base
        collector.collect(docIdStreamView)

        matching.clear()
    }

    @Throws(IOException::class)
    private fun advance(min: Int): DisiWrapper {
        require(tail.size() == minShouldMatch - 1)
        val head = this.head
        val tail = this.tail
        var headTop: DisiWrapper = head.top()
        var tailTop: DisiWrapper? = tail.top()
        while (headTop.doc < min) {
            if (tailTop == null || headTop.cost <= tailTop.cost) {
                headTop.doc = headTop.iterator!!.advance(min)
                headTop = head.updateTop()
            } else {
                // swap the top of head and tail
                val previousHeadTop = headTop
                tailTop.doc = tailTop.iterator!!.advance(min)
                headTop = head.updateTop(tailTop)
                tailTop = tail.updateTop(previousHeadTop)
            }
        }
        return headTop
    }

    @Throws(IOException::class)
    private fun scoreWindowMultipleScorers(
        collector: LeafCollector,
        acceptDocs: Bits,
        windowBase: Int,
        windowMin: Int,
        windowMax: Int,
        maxFreq: Int
    ) {
        var maxFreq = maxFreq
        while (maxFreq < minShouldMatch && maxFreq + tail.size() >= minShouldMatch) {
            // a match is still possible
            val candidate: DisiWrapper = tail.pop()!!
            if (candidate.doc < windowMin) {
                candidate.doc = candidate.iterator!!.advance(windowMin)
            }
            if (candidate.doc < windowMax) {
                leads[maxFreq++] = candidate
            } else {
                head.add(candidate)
            }
        }

        if (maxFreq >= minShouldMatch) {
            // There might be matches in other scorers from the tail too
            for (i in 0..<tail.size()) {
                leads[maxFreq++] = tail.get(i)
            }
            tail.clear()

            scoreWindowIntoBitSetAndReplay(
                collector, acceptDocs, windowBase, windowMin, windowMax, leads as Array<DisiWrapper>, maxFreq
            )
        }

        // Push back scorers into head and tail
        for (i in 0..<maxFreq) {
            val evicted: DisiWrapper? = head.insertWithOverflow(leads[i]!!)
            if (evicted != null) {
                tail.add(evicted)
            }
        }
    }

    @Throws(IOException::class)
    private fun scoreWindowSingleScorer(
        w: DisiWrapper,
        collector: LeafCollector,
        acceptDocs: Bits?,
        windowMin: Int,
        windowMax: Int,
        max: Int
    ) {
        require(tail.size() == 0)
        val nextWindowBase: Int = head.top().doc and MASK.inv()
        val end = max(windowMax, min(max, nextWindowBase))

        val it: DocIdSetIterator = w.iterator!!
        var doc = w.doc
        if (doc < windowMin) {
            doc = it.advance(windowMin)
        }
        collector.setScorer(w.scorer)
        while (doc < end) {
            if (acceptDocs == null || acceptDocs.get(doc)) {
                collector.collect(doc)
            }
            doc = it.nextDoc()
        }
        w.doc = doc

        // reset the scorer that should be used for the general case
        collector.setScorer(score)
    }

    @Throws(IOException::class)
    private fun scoreWindow(
        top: DisiWrapper, collector: LeafCollector, acceptDocs: Bits, min: Int, max: Int
    ): DisiWrapper {
        val windowBase = top.doc and MASK.inv() // find the window that the next match belongs to
        val windowMin = max(min, windowBase)
        val windowMax = min(max, windowBase + SIZE)

        // Fill 'leads' with all scorers from 'head' that are in the right window
        leads[0] = head.pop()!!
        var maxFreq = 1
        while (head.size() > 0 && head.top().doc < windowMax) {
            leads[maxFreq++] = head.pop()!!
        }

        if (minShouldMatch == 1 && maxFreq == 1) {
            // special case: only one scorer can match in the current window,
            // we can collect directly
            val bulkScorer = leads[0]!!
            scoreWindowSingleScorer(bulkScorer, collector, acceptDocs, windowMin, windowMax, max)
            return head.add(bulkScorer)!!
        } else {
            // general case, collect through a bit set first and then replay
            scoreWindowMultipleScorers(collector, acceptDocs, windowBase, windowMin, windowMax, maxFreq)
            return head.top()
        }
    }

    @Throws(IOException::class)
    override fun score(collector: LeafCollector, acceptDocs: Bits, min: Int, max: Int): Int {
        collector.setScorer(score)

        var top = advance(min)
        while (top.doc < max) {
            top = scoreWindow(top, collector, acceptDocs, min, max)
        }

        return top.doc
    }

    companion object {
        const val SHIFT: Int = 12
        const val SIZE: Int = 1 shl SHIFT
        const val MASK: Int = SIZE - 1
    }
}
