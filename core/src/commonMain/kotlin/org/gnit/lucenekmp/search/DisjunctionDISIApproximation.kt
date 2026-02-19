package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.math.min

/**
 * A [DocIdSetIterator] which is a disjunction of the approximations of the provided
 * iterators.
 *
 * @lucene.internal
 */
class DisjunctionDISIApproximation(
    subIterators: MutableCollection<out DisiWrapper>,
    leadCost: Long
) : DocIdSetIterator() {

    // Heap of iterators that lead iteration.
    private val leadIterators: DisiPriorityQueue

    // List of iterators that will likely advance on every call to nextDoc() / advance()
    private val otherIterators: Array<DisiWrapper>
    private val cost: Long
    private var leadTop: DisiWrapper
    private var minOtherDoc: Int

    init {
        val wrappers: Array<DisiWrapper> = subIterators.toList().toTypedArray()
        // Sort by descending cost.
        wrappers.sortWith(compareByDescending { it.cost })

        var reorderThreshold = leadCost + (leadCost shr 1)
        if (reorderThreshold < 0) {
            // overflow
            reorderThreshold = Long.MAX_VALUE
        }

        var cost = 0L // track total cost
        // Split `wrappers` into those that will remain out of the PQ, and those that will go in
        // (PQ entries at the end). `lastIdx` is the last index of the wrappers that will remain out.
        var reorderCost = 0L
        var lastIdx = wrappers.size - 1
        while (lastIdx >= 0) {
            val lastCost = wrappers[lastIdx].cost
            val inc = min(lastCost, leadCost)
            if (reorderCost + inc < 0 || reorderCost + inc > reorderThreshold) {
                break
            }
            reorderCost += inc
            cost += lastCost
            lastIdx--
        }

        // Make leadIterators not empty. This helps save conditionals in the implementation which are
        // rarely tested.
        if (lastIdx == wrappers.size - 1) {
            cost += wrappers[lastIdx].cost
            lastIdx--
        }

        // Build the PQ:
        check(lastIdx >= -1 && lastIdx < wrappers.size - 1)
        val pqLen = wrappers.size - lastIdx - 1
        leadIterators = DisiPriorityQueue.ofMaxSize(pqLen)
        leadIterators.addAll(wrappers, lastIdx + 1, pqLen)

        // Build the non-PQ list:
        otherIterators = ArrayUtil.copyOfSubArray(wrappers, 0, lastIdx + 1)
        minOtherDoc = Int.MAX_VALUE
        for (w in otherIterators) {
            cost += w.cost
            minOtherDoc = min(minOtherDoc, w.doc)
        }

        this.cost = cost
        leadTop = leadIterators.top()!!
    }

    override fun cost(): Long {
        return cost
    }

    override fun docID(): Int {
        return min(minOtherDoc, leadTop.doc)
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        return if (leadTop.doc < minOtherDoc) {
            val curDoc = leadTop.doc
            do {
                leadTop.doc = leadTop.approximation!!.nextDoc()
                leadTop = leadIterators.updateTop()
            } while (leadTop.doc == curDoc)
            min(leadTop.doc, minOtherDoc)
        } else {
            advance(minOtherDoc + 1)
        }
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        while (leadTop.doc < target) {
            leadTop.doc = leadTop.approximation!!.advance(target)
            leadTop = leadIterators.updateTop()
        }

        minOtherDoc = Int.MAX_VALUE
        for (w in otherIterators) {
            if (w.doc < target) {
                w.doc = w.approximation!!.advance(target)
            }
            minOtherDoc = min(minOtherDoc, w.doc)
        }

        return min(leadTop.doc, minOtherDoc)
    }

    @Throws(IOException::class)
    override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
        while (leadTop.doc < upTo) {
            leadTop.approximation!!.intoBitSet(upTo, bitSet, offset)
            leadTop.doc = leadTop.approximation!!.docID()
            leadTop = leadIterators.updateTop()
        }

        minOtherDoc = Int.MAX_VALUE
        for (w in otherIterators) {
            w.approximation!!.intoBitSet(upTo, bitSet, offset)
            w.doc = w.approximation!!.docID()
            minOtherDoc = min(minOtherDoc, w.doc)
        }
    }

    /** Return the linked list of iterators positioned on the current doc. */
    fun topList(): DisiWrapper? {
        return if (leadTop.doc < minOtherDoc) {
            leadIterators.topList()
        } else {
            computeTopList()
        }
    }

    private fun computeTopList(): DisiWrapper? {
        check(leadTop.doc >= minOtherDoc)
        var topList: DisiWrapper? = null
        if (leadTop.doc == minOtherDoc) {
            topList = leadIterators.topList()
        }
        for (w in otherIterators) {
            if (w.doc == minOtherDoc) {
                w.next = topList
                topList = w
            }
        }
        return topList
    }

    companion object {
        fun of(
            subIterators: MutableCollection<out DisiWrapper>,
            leadCost: Long
        ): DisjunctionDISIApproximation {
            return DisjunctionDISIApproximation(subIterators, leadCost)
        }
    }
}
