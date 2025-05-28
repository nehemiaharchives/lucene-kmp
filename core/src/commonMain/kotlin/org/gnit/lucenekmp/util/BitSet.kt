package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator


/**
 * Base implementation for a bit set.
 *
 * @lucene.internal
 */
abstract class BitSet : Bits, Accountable {
    /**
     * Clear all the bits of the set.
     *
     *
     * Depending on the implementation, this may be significantly faster than clear(0, length).
     */
    open fun clear() {
        // default implementation for compatibility
        clear(0, length())
    }

    /** Set the bit at `i`.  */
    abstract fun set(i: Int)

    /** Set the bit at `i`, returning `true` if it was previously set.  */
    abstract fun getAndSet(i: Int): Boolean

    /** Clear the bit at `i`.  */
    abstract fun clear(i: Int)

    /**
     * Clears a range of bits.
     *
     * @param startIndex lower index
     * @param endIndex one-past the last bit to clear
     */
    abstract fun clear(startIndex: Int, endIndex: Int)

    /** Return the number of bits that are set. NOTE: this method is likely to run in linear time  */
    abstract fun cardinality(): Int

    /**
     * Return an approximation of the cardinality of this set. Some implementations may trade accuracy
     * for speed if they have the ability to estimate the cardinality of the set without iterating
     * over all the data. The default implementation returns [.cardinality].
     */
    abstract fun approximateCardinality(): Int

    /**
     * Returns the index of the last set bit before or on the index specified. -1 is returned if there
     * are no more set bits.
     */
    abstract fun prevSetBit(index: Int): Int

    /**
     * Returns the index of the first set bit starting at the index specified. [ ][DocIdSetIterator.NO_MORE_DOCS] is returned if there are no more set bits.
     */
    open fun nextSetBit(index: Int): Int {
        // Default implementation. Subclasses may be able to override with a more performant
        // implementation.
        return nextSetBit(index, length())
    }

    /**
     * Returns the index of the first set bit from start (inclusive) until end (exclusive). [ ][DocIdSetIterator.NO_MORE_DOCS] is returned if there are no more set bits.
     */
    abstract fun nextSetBit(start: Int, end: Int): Int

    /** Assert that the current doc is -1.  */
    protected fun checkUnpositioned(iter: DocIdSetIterator) {
        check(iter.docID() == -1) {
            ("This operation only works with an unpositioned iterator, got current position = "
                    + iter.docID())
        }
    }

    /**
     * Does in-place OR of the bits provided by the iterator. The state of the iterator after this
     * operation terminates is undefined.
     */
    @Throws(IOException::class)
    open fun or(iter: DocIdSetIterator) {
        checkUnpositioned(iter)
        var doc: Int = iter.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            set(doc)
            doc = iter.nextDoc()
        }
    }

    companion object {
        /**
         * Build a [BitSet] from the content of the provided [DocIdSetIterator]. NOTE: this
         * will fully consume the [DocIdSetIterator].
         */
        @Throws(IOException::class)
        fun of(it: DocIdSetIterator, maxDoc: Int): BitSet {
            val cost: Long = it.cost()
            val threshold = maxDoc ushr 7
            val set: BitSet
            if (cost < threshold) {
                set = SparseFixedBitSet(maxDoc)
            } else {
                set = FixedBitSet(maxDoc)
            }
            set.or(it)
            return set
        }
    }
}
