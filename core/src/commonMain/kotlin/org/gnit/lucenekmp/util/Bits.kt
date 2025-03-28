package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.search.DocIdSetIterator


/**
 * Interface for Bitset-like structures.
 *
 * @lucene.experimental
 */
interface Bits {
    /**
     * Returns the value of the bit with the specified `index`.
     *
     * @param index index, should be non-negative and &lt; [.length]. The result of passing
     * negative or out of bounds values is undefined by this interface, **just don't do it!**
     * @return `true` if the bit is set, `false` otherwise.
     */
    fun get(index: Int): Boolean

    /** Returns the number of bits in this set  */
    fun length(): Int

    /**
     * Apply this `Bits` instance to the given [FixedBitSet], which starts at the given
     * `offset`.
     *
     *
     * This should behave the same way as the default implementation, which does the following:
     *
     * <pre class="prettyprint">
     * for (int i = bitSet.nextSetBit(0);
     * i != DocIdSetIterator.NO_MORE_DOCS;
     * i = i + 1 >= bitSet.length() ? DocIdSetIterator.NO_MORE_DOCS : bitSet.nextSetBit(i + 1)) {
     * if (get(offset + i) == false) {
     * bitSet.clear(i);
     * }
     * }
    </pre> *
     */
    fun applyMask(bitSet: FixedBitSet, offset: Int) {
        var i: Int = bitSet.nextSetBit(0)
        while (i != DocIdSetIterator.NO_MORE_DOCS
        ) {
            if (get(offset + i) == false) {
                bitSet.clear(i)
            }
            i = if (i + 1 >= bitSet.length()) DocIdSetIterator.NO_MORE_DOCS else bitSet.nextSetBit(i + 1)
        }
    }

    /** Bits impl of the specified length with all bits set.  */
    class MatchAllBits(val len: Int) : Bits {
        override fun get(index: Int): Boolean {
            return true
        }

        override fun length(): Int {
            return len
        }
    }

    /** Bits impl of the specified length with no bits set.  */
    class MatchNoBits(val len: Int) : Bits {
        override fun get(index: Int): Boolean {
            return false
        }

        override fun length(): Int {
            return len
        }
    }

    companion object {
        val EMPTY_ARRAY: Array<Bits?> = kotlin.arrayOfNulls<Bits>(0)
    }
}
