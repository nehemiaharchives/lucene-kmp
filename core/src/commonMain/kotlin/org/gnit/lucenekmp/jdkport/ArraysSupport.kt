package org.gnit.lucenekmp.jdkport

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


}
