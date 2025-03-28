package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.search.DocIdSetIterator


/**
 * Implementation of the [DocIdSet] interface on top of a [BitSet].
 *
 * @lucene.internal
 */
class BitDocIdSet(set: BitSet, cost: Long) : DocIdSet() {
    private val set: BitSet
    private val cost: Long

    /**
     * Wrap the given [BitSet] as a [DocIdSet]. The provided [BitSet] must not be
     * modified afterwards.
     */
    init {
        require(cost >= 0) { "cost must be >= 0, got $cost" }
        this.set = set
        this.cost = cost
    }

    /**
     * Same as [.BitDocIdSet] but uses the set's [ ][BitSet.approximateCardinality] as a cost.
     */
    constructor(set: BitSet) : this(set, set.approximateCardinality().toLong())

    override fun iterator(): DocIdSetIterator {
        return BitSetIterator(set, cost)
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + set.ramBytesUsed()
    }

    override fun toString(): String {
        return this::class.simpleName + "(set=" + set + ",cost=" + cost + ")"
    }

    companion object {
        private val BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(BitDocIdSet::class)
    }
}
