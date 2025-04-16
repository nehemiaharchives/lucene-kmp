package org.gnit.lucenekmp.util.bkd

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.reverseBytes
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import org.gnit.lucenekmp.util.BitUtil

/** Utility functions to build BKD trees.  */
internal object BKDUtil {
    /**
     * Return a comparator that computes the common prefix length across the next `numBytes` of
     * the provided arrays.
     */
    fun getPrefixLengthComparator(numBytes: Int): ByteArrayComparator {
        if (numBytes == Long.SIZE_BYTES) {
            // Used by LongPoint, DoublePoint
            return ByteArrayComparator { a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int ->
                commonPrefixLength8(
                    a,
                    aOffset,
                    b,
                    bOffset
                )
            }
        } else if (numBytes == Int.SIZE_BYTES) {
            // Used by IntPoint, FloatPoint, LatLonPoint, LatLonShape
            return ByteArrayComparator { a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int ->
                commonPrefixLength4(
                    a,
                    aOffset,
                    b,
                    bOffset
                )
            }
        } else {
            return ByteArrayComparator { a, aOffset, b, bOffset ->
                commonPrefixLengthN(
                    a,
                    aOffset,
                    b,
                    bOffset,
                    numBytes
                )
            }
        }
    }

    /** Return the length of the common prefix across the next 8 bytes of both provided arrays.  */
    fun commonPrefixLength8(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int): Int {
        val aLong = BitUtil.VH_LE_LONG.get(a, aOffset)
        val bLong = BitUtil.VH_LE_LONG.get(b, bOffset)
        val commonPrefixInBits: Int = Long.numberOfLeadingZeros(Long.reverseBytes(aLong xor bLong))
        return commonPrefixInBits ushr 3
    }

    /** Return the length of the common prefix across the next 4 bytes of both provided arrays.  */
    fun commonPrefixLength4(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int): Int {
        val aInt = BitUtil.VH_LE_INT.get(a, aOffset)
        val bInt = BitUtil.VH_LE_INT.get(b, bOffset)
        val commonPrefixInBits: Int =
            Int.numberOfLeadingZeros(Int.reverseBytes(aInt xor bInt))
        return commonPrefixInBits ushr 3
    }

    fun commonPrefixLengthN(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int, numBytes: Int): Int {
        val cmp: Int = Arrays.mismatch(a, aOffset, aOffset + numBytes, b, bOffset, bOffset + numBytes)
        return if (cmp == -1) {
            numBytes
        } else {
            cmp
        }
    }

    /** Return a predicate that tells whether the next `numBytes` bytes are equal.  */
    fun getEqualsPredicate(numBytes: Int): ByteArrayPredicate {
        if (numBytes == Long.SIZE_BYTES) {
            // Used by LongPoint, DoublePoint
            return ByteArrayPredicate { a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int ->
                equals8(
                    a,
                    aOffset,
                    b,
                    bOffset
                )
            }
        } else if (numBytes == Int.SIZE_BYTES) {
            // Used by IntPoint, FloatPoint, LatLonPoint, LatLonShape
            return ByteArrayPredicate { a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int ->
                equals4(
                    a,
                    aOffset,
                    b,
                    bOffset
                )
            }
        } else {
            return ByteArrayPredicate { a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int ->
                Arrays.equals(
                    a,
                    aOffset,
                    aOffset + numBytes,
                    b,
                    bOffset,
                    bOffset + numBytes
                )
            }
        }
    }

    /** Check whether the next 8 bytes are exactly the same in the provided arrays.  */
    fun equals8(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int): Boolean {
        val aLong = BitUtil.VH_LE_LONG.get(a, aOffset)
        val bLong = BitUtil.VH_LE_LONG.get(b, bOffset)
        return aLong == bLong
    }

    /** Check whether the next 4 bytes are exactly the same in the provided arrays.  */
    fun equals4(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int): Boolean {
        val aInt = BitUtil.VH_LE_INT.get(a, aOffset)
        val bInt = BitUtil.VH_LE_INT.get(b, bOffset)
        return aInt == bInt
    }

    /** Predicate for a fixed number of bytes.  */
    fun interface ByteArrayPredicate {
        /** Test bytes starting from the given offsets.  */
        fun test(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int): Boolean
    }
}
