package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import kotlin.math.max
import kotlin.math.min


/**
 * A [DocIdSetIterator] like [BitSetIterator] but has a doc base in order to avoid
 * storing previous 0s.
 */
class DocBaseBitSetIterator(bits: FixedBitSet, cost: Long, docBase: Int) : DocIdSetIterator() {
    /**
     * Get the [FixedBitSet]. A docId will exist in this [DocIdSetIterator] if the bitset
     * contains the (docId - [.getDocBase])
     *
     * @return the offset docId bitset
     */
    val bitSet: FixedBitSet
    private val length: Int
    private val cost: Long

    /**
     * Get the docBase. It is guaranteed that docBase is a multiple of 64.
     *
     * @return the docBase
     */
    val docBase: Int
    private var doc = -1

    init {
        require(cost >= 0) { "cost must be >= 0, got $cost" }
        require((docBase and 63) == 0) { "docBase need to be a multiple of 64, got $docBase" }
        this.bitSet = bits
        this.length = bits.length() + docBase
        this.cost = cost
        this.docBase = docBase
    }

    override fun docID(): Int {
        return doc
    }

    override fun nextDoc(): Int {
        return advance(doc + 1)
    }

    override fun advance(target: Int): Int {
        if (target >= length) {
            return NO_MORE_DOCS.also { doc = it }
        }
        val next = bitSet.nextSetBit(max(0, target - docBase))
        return if (next == NO_MORE_DOCS) {
            NO_MORE_DOCS.also { doc = it }
        } else {
            (next + docBase).also { doc = it }
        }
    }

    override fun cost(): Long {
        return cost
    }

    @Throws(IOException::class)
    override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
        var actualUpto = min(upTo, length)
        // The destination bit set may be shorter than this bit set. This is only legal if all bits
        // beyond offset + bitSet.length() are clear. If not, the below call to `super.intoBitSet` will
        // throw an exception.
        actualUpto = min(actualUpto, offset + bitSet.length())
        if (actualUpto > doc) {
            FixedBitSet.orRange(this.bitSet, doc - docBase, bitSet, doc - offset, actualUpto - doc)
            advance(actualUpto) // set the current doc
        }
        super.intoBitSet(upTo, bitSet, offset)
    }
}
