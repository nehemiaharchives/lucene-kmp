package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import kotlin.experimental.and


/**
 * Specialized [BytesRef] comparator that [StringSorter] has optimizations for.
 *
 * @lucene.internal
 */
abstract class BytesRefComparator
/**
 * Sole constructor.
 *
 * @param comparedBytesCount the maximum number of bytes to compare.
 */ protected constructor(val comparedBytesCount: Int) : Comparator<BytesRef> {
    /**
     * Return the unsigned byte to use for comparison at index `i`, or `-1` if all bytes
     * that are useful for comparisons are exhausted. This may only be called with a value of `i` between `0` included and `comparedBytesCount` excluded.
     */
    abstract fun byteAt(ref: BytesRef, i: Int): Int

    override fun compare(o1: BytesRef, o2: BytesRef): Int {
        return compare(o1, o2, 0)
    }

    /** Compare two bytes refs that first k bytes are already guaranteed to be equal.  */
    open fun compare(o1: BytesRef, o2: BytesRef, k: Int): Int {
        for (i in k..<comparedBytesCount) {
            val b1 = byteAt(o1, i)
            val b2 = byteAt(o2, i)
            if (b1 != b2) {
                return b1 - b2
            } else if (b1 == -1) {
                break
            }
        }
        return 0
    }

    companion object {
        /** Comparing ByteRefs in natual order.  */
        val NATURAL: BytesRefComparator = object : BytesRefComparator(Int.Companion.MAX_VALUE) {

            override fun byteAt(ref: BytesRef, i: Int): Int {
                if (ref.length <= i) {
                    return -1
                }
                return ref.bytes[ref.offset + i].toInt() and 0xFF
            }

            override fun compare(o1: BytesRef, o2: BytesRef, k: Int): Int {
                return Arrays.compareUnsigned(
                    o1.bytes,
                    o1.offset + k,
                    o1.offset + o1.length,
                    o2.bytes,
                    o2.offset + k,
                    o2.offset + o2.length
                )
            }
        }
    }
}
