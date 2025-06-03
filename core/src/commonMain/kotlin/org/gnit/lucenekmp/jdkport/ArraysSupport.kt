package org.gnit.lucenekmp.jdkport

import kotlin.math.max

object ArraysSupport {
    /**
     * Returns the index of the first element that differs between the two [LongArray]s,
     * starting at [aFromIndex] in [a] and [bFromIndex] in [b], for up to [length] elements.
     *
     * If no mismatch is found in the given range, returns -1.
     */
    fun mismatch(
        a: LongArray, aFromIndex: Int,
        b: LongArray, bFromIndex: Int,
        length: Int
    ): Int {
        // If there are no elements to compare, return -1.
        if (length == 0) return -1

        // Iterate over the specified range.
        for (i in 0 until length) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i
            }
        }
        return -1
    }

    fun mismatch(
        a: FloatArray, aFromIndex: Int,
        b: FloatArray, bFromIndex: Int,
        length: Int
    ): Int {
        if (length <= 0) return -1

        val aRem = a.size - aFromIndex
        val bRem = b.size - bFromIndex
        // if we try to compare beyond either array, report that index
        if (length > aRem || length > bRem) {
            return minOf(aRem, bRem)
        }

        for (i in 0 until length) {
            if (Float.floatToRawIntBits(a[aFromIndex + i]) != Float.floatToRawIntBits(b[bFromIndex + i]))
                return i
        }
        return -1
    }

    /**
     * Returns the relative index of the first mismatch between two subranges of the given arrays,
     * or -1 if there is no mismatch.
     *
     * This function compares up to [length] elements from [a] starting at [aFromIndex] and
     * from [b] starting at [bFromIndex].
     */
    fun mismatch(
        a: IntArray, aFromIndex: Int,
        b: IntArray, bFromIndex: Int,
        length: Int
    ): Int {
        // Optional: You may wish to add additional validation, such as checking
        // that aFromIndex + length <= a.size, bFromIndex + length <= b.size, etc.
        if (length <= 0) return -1

        // Quick check if the very first element differs (like the Java code does).
        // If mismatch, return 0 immediately.
        if (length > 1 && a[aFromIndex] != b[bFromIndex]) {
            return 0
        }

        // Fallback: do a simple loop over the range.
        // If length == 1, we skip the quick-check above anyway.
        val start = if (length > 1) 1 else 0
        for (i in start until length) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i
            }
        }
        return -1
    }

    fun mismatch(
        a: CharArray, aFromIndex: Int,
        b: CharArray, bFromIndex: Int,
        length: Int
    ): Int {
        for (i in 0 until length) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i
            }
        }
        return -1
    }

    /**
     * Finds the relative index of a mismatch between two arrays starting from the given indexes.
     *
     * This method does not perform bounds checks. It is the responsibility of the caller
     * to ensure that the indexes and length are valid.
     *
     * @param a the first array to be tested for a mismatch
     * @param aFromIndex the index of the first element (inclusive) in the first array to be compared
     * @param b the second array to be tested for a mismatch
     * @param bFromIndex the index of the first element (inclusive) in the second array to be compared
     * @param length the number of bytes from each array to check
     * @return the relative index of a mismatch between the two arrays, or -1 if no mismatch is found.
     */
    fun mismatch(a: ByteArray, aFromIndex: Int, b: ByteArray, bFromIndex: Int, length: Int): Int {
        for (i in 0 until length) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i
            }
        }
        return -1
    }

    /**
     * A soft maximum array length imposed by array growth computations.
     * Some JVMs (such as HotSpot) have an implementation limit that will cause
     *
     * OutOfMemoryError("Requested array size exceeds VM limit")
     *
     * to be thrown if a request is made to allocate an array of some length near
     * Integer.MAX_VALUE, even if there is sufficient heap available. The actual
     * limit might depend on some JVM implementation-specific characteristics such
     * as the object header size. The soft maximum value is chosen conservatively so
     * as to be smaller than any implementation limit that is likely to be encountered.
     */
    const val SOFT_MAX_ARRAY_LENGTH: Int = Int.Companion.MAX_VALUE - 8

    /**
     * Computes a new array length given an array's current length, a minimum growth
     * amount, and a preferred growth amount. The computation is done in an overflow-safe
     * fashion.
     *
     * This method is used by objects that contain an array that might need to be grown
     * in order to fulfill some immediate need (the minimum growth amount) but would also
     * like to request more space (the preferred growth amount) in order to accommodate
     * potential future needs. The returned length is usually clamped at the soft maximum
     * length in order to avoid hitting the JVM implementation limit. However, the soft
     * maximum will be exceeded if the minimum growth amount requires it.
     *
     * If the preferred growth amount is less than the minimum growth amount, the
     * minimum growth amount is used as the preferred growth amount.
     *
     * The preferred length is determined by adding the preferred growth amount to the
     * current length. If the preferred length does not exceed the soft maximum length
     * (SOFT_MAX_ARRAY_LENGTH) then the preferred length is returned.
     *
     * If the preferred length exceeds the soft maximum, we use the minimum growth
     * amount. The minimum required length is determined by adding the minimum growth
     * amount to the current length. If the minimum required length exceeds Integer.MAX_VALUE,
     * then this method throws OutOfMemoryError. Otherwise, this method returns the greater of
     * the soft maximum or the minimum required length.
     *
     * Note that this method does not do any array allocation itself; it only does array
     * length growth computations. However, it will throw OutOfMemoryError as noted above.
     *
     * Note also that this method cannot detect the JVM's implementation limit, and it
     * may compute and return a length value up to and including Integer.MAX_VALUE that
     * might exceed the JVM's implementation limit. In that case, the caller will likely
     * attempt an array allocation with that length and encounter an OutOfMemoryError.
     * Of course, regardless of the length value returned from this method, the caller
     * may encounter OutOfMemoryError if there is insufficient heap to fulfill the request.
     *
     * @param oldLength   current length of the array (must be nonnegative)
     * @param minGrowth   minimum required growth amount (must be positive)
     * @param prefGrowth  preferred growth amount
     * @return the new array length
     * @throws OutOfMemoryError if the new length would exceed Integer.MAX_VALUE
     */
    fun newLength(oldLength: Int, minGrowth: Int, prefGrowth: Int): Int {
        // preconditions not checked because of inlining
        // assert oldLength >= 0
        // assert minGrowth > 0

        val prefLength = oldLength + max(minGrowth, prefGrowth) // might overflow
        if (0 < prefLength && prefLength <= SOFT_MAX_ARRAY_LENGTH) {
            return prefLength
        } else {
            // put code cold in a separate method
            return hugeLength(oldLength, minGrowth)
        }
    }

    private fun hugeLength(oldLength: Int, minGrowth: Int): Int {
        val minLength = oldLength + minGrowth
        if (minLength < 0) { // overflow
            throw /*java.lang.OutOfMemory*/Error(
                "Required array length $oldLength + $minGrowth is too large"
            )
        } else if (minLength <= SOFT_MAX_ARRAY_LENGTH) {
            return SOFT_MAX_ARRAY_LENGTH
        } else {
            return minLength
        }
    }
}
