package org.gnit.lucenekmp.jdkport

import kotlin.math.min

object Arrays {

    /**
     * Returns the first index i (0 ≤ i < len) for which
     * a[aOffset + i] != b[bOffset + i]. If no such index exists, returns -1.
     */
    fun mismatch(
        a: ByteArray, aOffset: Int,
        b: ByteArray, bOffset: Int,
        len: Int
    ): Int {
        for (i in 0 until len) {
            if (a[aOffset + i] != b[bOffset + i]) {
                return i
            }
        }
        return -1
    }

    /**
     * Finds and returns the relative index of the first mismatch between two
     * `byte` arrays over the specified ranges, otherwise return -1 if no
     * mismatch is found.  The index will be in the range of 0 (inclusive) up to
     * the length (inclusive) of the smaller range.
     *
     *
     * If the two arrays, over the specified ranges, share a common prefix
     * then the returned relative index is the length of the common prefix and
     * it follows that there is a mismatch between the two elements at that
     * relative index within the respective arrays.
     * If one array is a proper prefix of the other, over the specified ranges,
     * then the returned relative index is the length of the smaller range and
     * it follows that the relative index is only valid for the array with the
     * larger range.
     * Otherwise, there is no mismatch.
     *
     *
     * Two non-`null` arrays, `a` and `b` with specified
     * ranges [`aFromIndex`, `aToIndex`) and
     * [`bFromIndex`, `bToIndex`) respectively, share a common
     * prefix of length `pl` if the following expression is true:
     * <pre>`pl >= 0 &&
     * pl < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex) &&
     * Arrays.equals(a, aFromIndex, aFromIndex + pl, b, bFromIndex, bFromIndex + pl) &&
     * a[aFromIndex + pl] != b[bFromIndex + pl]
    `</pre> *
     * Note that a common prefix length of `0` indicates that the first
     * elements from each array mismatch.
     *
     *
     * Two non-`null` arrays, `a` and `b` with specified
     * ranges [`aFromIndex`, `aToIndex`) and
     * [`bFromIndex`, `bToIndex`) respectively, share a proper
     * prefix if the following expression is true:
     * <pre>`(aToIndex - aFromIndex) != (bToIndex - bFromIndex) &&
     * Arrays.equals(a, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex),
     * b, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
    `</pre> *
     *
     * @param a the first array to be tested for a mismatch
     * @param aFromIndex the index (inclusive) of the first element in the
     * first array to be tested
     * @param aToIndex the index (exclusive) of the last element in the
     * first array to be tested
     * @param b the second array to be tested for a mismatch
     * @param bFromIndex the index (inclusive) of the first element in the
     * second array to be tested
     * @param bToIndex the index (exclusive) of the last element in the
     * second array to be tested
     * @return the relative index of the first mismatch between the two arrays
     * over the specified ranges, otherwise `-1`.
     * @throws IllegalArgumentException
     * if `aFromIndex > aToIndex` or
     * if `bFromIndex > bToIndex`
     * @throws ArrayIndexOutOfBoundsException
     * if `aFromIndex < 0 or aToIndex > a.length` or
     * if `bFromIndex < 0 or bToIndex > b.length`
     * @throws NullPointerException
     * if either array is `null`
     * @since 9
     */
    fun mismatch(
        a: ByteArray, aFromIndex: Int, aToIndex: Int,
        b: ByteArray, bFromIndex: Int, bToIndex: Int
    ): Int {
        rangeCheck(a.size, aFromIndex, aToIndex)
        rangeCheck(b.size, bFromIndex, bToIndex)

        val aLength = aToIndex - aFromIndex
        val bLength = bToIndex - bFromIndex
        val length = min(aLength, bLength)
        val i: Int = mismatch(
            a, aFromIndex,
            b, bFromIndex,
            length
        )
        return if (i < 0 && aLength != bLength) length else i
    }

    /**
     * Returns the first index (0 ≤ i < len) at which the elements of the two [IntArray]s differ,
     * when compared over the given [length] starting at [aFromIndex] and [bFromIndex] respectively.
     * Returns -1 if no mismatch is found.
     */
    fun mismatch(
        a: IntArray, aFromIndex: Int,
        b: IntArray, bFromIndex: Int,
        len: Int
    ): Int {
        for (i in 0 until len) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i
            }
        }
        return -1
    }

    /**
     * Returns true if the two specified arrays of bytes, over the specified
     * ranges, are *equal* to one another.
     *
     *
     * Two arrays are considered equal if the number of elements covered by
     * each range is the same, and all corresponding pairs of elements over the
     * specified ranges in the two arrays are equal.  In other words, two arrays
     * are equal if they contain, over the specified ranges, the same elements
     * in the same order.
     *
     * @param a the first array to be tested for equality
     * @param aFromIndex the index (inclusive) of the first element in the
     * first array to be tested
     * @param aToIndex the index (exclusive) of the last element in the
     * first array to be tested
     * @param b the second array to be tested for equality
     * @param bFromIndex the index (inclusive) of the first element in the
     * second array to be tested
     * @param bToIndex the index (exclusive) of the last element in the
     * second array to be tested
     * @return `true` if the two arrays, over the specified ranges, are
     * equal
     * @throws IllegalArgumentException
     * if `aFromIndex > aToIndex` or
     * if `bFromIndex > bToIndex`
     * @throws ArrayIndexOutOfBoundsException
     * if `aFromIndex < 0 or aToIndex > a.length` or
     * if `bFromIndex < 0 or bToIndex > b.length`
     * @throws NullPointerException
     * if either array is `null`
     * @since 9
     */
    fun equals(
        a: ByteArray, aFromIndex: Int, aToIndex: Int,
        b: ByteArray, bFromIndex: Int, bToIndex: Int
    ): Boolean {
        rangeCheck(a.size, aFromIndex, aToIndex)
        rangeCheck(b.size, bFromIndex, bToIndex)

        val aLength = aToIndex - aFromIndex
        val bLength = bToIndex - bFromIndex
        if (aLength != bLength) return false

        return mismatch(
            a, aFromIndex,
            b, bFromIndex,
            aLength
        ) < 0
    }

    /**
     * Returns true if the two LongArrays, over the specified ranges,
     * are equal (i.e. they have the same length and each corresponding
     * element is equal).
     *
     * @param a the first array
     * @param aFromIndex the index (inclusive) of the first element in a to compare
     * @param aToIndex the index (exclusive) of the last element in a to compare
     * @param b the second array
     * @param bFromIndex the index (inclusive) of the first element in b to compare
     * @param bToIndex the index (exclusive) of the last element in b to compare
     * @return true if the specified ranges in a and b are equal; false otherwise.
     * @throws IllegalArgumentException if aFromIndex > aToIndex or bFromIndex > bToIndex.
     * @throws IndexOutOfBoundsException if any index is out of range.
     */
    fun equals(
        a: LongArray, aFromIndex: Int, aToIndex: Int,
        b: LongArray, bFromIndex: Int, bToIndex: Int
    ): Boolean {
        require(aFromIndex <= aToIndex) { "aFromIndex ($aFromIndex) > aToIndex ($aToIndex)" }
        require(bFromIndex <= bToIndex) { "bFromIndex ($bFromIndex) > bToIndex ($bToIndex)" }
        if (aFromIndex < 0 || aToIndex > a.size)
            throw IndexOutOfBoundsException("Range [$aFromIndex, $aToIndex) out of bounds for array of size ${a.size}")
        if (bFromIndex < 0 || bToIndex > b.size)
            throw IndexOutOfBoundsException("Range [$bFromIndex, $bToIndex) out of bounds for array of size ${b.size}")

        val aLength = aToIndex - aFromIndex
        val bLength = bToIndex - bFromIndex
        if (aLength != bLength) return false

        for (i in 0 until aLength) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) return false
        }
        return true
    }

    /**
     * ported from java.util.Arrays.equals(int[] a, int aFromIndex, int aToIndex,
     *                                  int[] b, int bFromIndex, int bToIndex)
     *
     * Returns true if the two specified subarrays of [IntArray]s are equal.
     *
     * Two arrays are considered equal if the number of elements in each range is the same and
     * every corresponding pair of elements in the ranges is equal.
     *
     * @param a the first array to be tested for equality.
     * @param aFromIndex the index (inclusive) of the first element in the first array to test.
     * @param aToIndex the index (exclusive) of the last element in the first array to test.
     * @param b the second array to be tested for equality.
     * @param bFromIndex the index (inclusive) of the first element in the second array to test.
     * @param bToIndex the index (exclusive) of the last element in the second array to test.
     * @return true if the two arrays are equal over the specified ranges.
     * @throws IllegalArgumentException if fromIndex > toIndex.
     * @throws IndexOutOfBoundsException if indices are out of bounds.
     */
    fun equals(
        a: IntArray, aFromIndex: Int, aToIndex: Int,
        b: IntArray, bFromIndex: Int, bToIndex: Int
    ): Boolean {
        rangeCheck(a.size, aFromIndex, aToIndex)
        rangeCheck(b.size, bFromIndex, bToIndex)

        val aLength = aToIndex - aFromIndex
        val bLength = bToIndex - bFromIndex
        if (aLength != bLength) return false

        // If mismatch returns -1, then no differing index was found.
        return mismatch(a, aFromIndex, b, bFromIndex, aLength) < 0
    }


    /**
     * Compares two `int` arrays lexicographically over the specified
     * ranges.
     *
     *
     * If the two arrays, over the specified ranges, share a common prefix
     * then the lexicographic comparison is the result of comparing two
     * elements, as if by [Integer.compare], at a relative index
     * within the respective arrays that is the length of the prefix.
     * Otherwise, one array is a proper prefix of the other and, lexicographic
     * comparison is the result of comparing the two range lengths.
     * (See [.mismatch] for the
     * definition of a common and proper prefix.)
     *
     *
     * The comparison is consistent with
     * [equals][.equals], more
     * specifically the following holds for arrays `a` and `b` with
     * specified ranges [`aFromIndex`, `aToIndex`) and
     * [`bFromIndex`, `bToIndex`) respectively:
     * <pre>`Arrays.equals(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) ==
     * (Arrays.compare(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) == 0)
    `</pre> *
     *
     * @apiNote
     *
     * This method behaves as if:
     * <pre>`int i = Arrays.mismatch(a, aFromIndex, aToIndex,
     * b, bFromIndex, bToIndex);
     * if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     * return Integer.compare(a[aFromIndex + i], b[bFromIndex + i]);
     * return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
    `</pre> *
     *
     * @param a the first array to compare
     * @param aFromIndex the index (inclusive) of the first element in the
     * first array to be compared
     * @param aToIndex the index (exclusive) of the last element in the
     * first array to be compared
     * @param b the second array to compare
     * @param bFromIndex the index (inclusive) of the first element in the
     * second array to be compared
     * @param bToIndex the index (exclusive) of the last element in the
     * second array to be compared
     * @return the value `0` if, over the specified ranges, the first and
     * second array are equal and contain the same elements in the same
     * order;
     * a value less than `0` if, over the specified ranges, the
     * first array is lexicographically less than the second array; and
     * a value greater than `0` if, over the specified ranges, the
     * first array is lexicographically greater than the second array
     * @throws IllegalArgumentException
     * if `aFromIndex > aToIndex` or
     * if `bFromIndex > bToIndex`
     * @throws ArrayIndexOutOfBoundsException
     * if `aFromIndex < 0 or aToIndex > a.length` or
     * if `bFromIndex < 0 or bToIndex > b.length`
     * @throws NullPointerException
     * if either array is `null`
     * @since 9
     */
    fun compare(
        a: IntArray, aFromIndex: Int, aToIndex: Int,
        b: IntArray, bFromIndex: Int, bToIndex: Int
    ): Int {
        rangeCheck(a.size, aFromIndex, aToIndex)
        rangeCheck(b.size, bFromIndex, bToIndex)

        val aLength = aToIndex - aFromIndex
        val bLength = bToIndex - bFromIndex
        val i: Int = ArraysSupport.mismatch(
            a, aFromIndex,
            b, bFromIndex,
            min(aLength, bLength)
        )
        if (i >= 0) {
            return Int.compare(a[aFromIndex + i], b[bFromIndex + i])
        }

        return aLength - bLength
    }

    /**
     * Compares two `long` arrays lexicographically over the specified
     * ranges.
     *
     *
     * If the two arrays, over the specified ranges, share a common prefix
     * then the lexicographic comparison is the result of comparing two
     * elements, as if by [Long.compare], at a relative index
     * within the respective arrays that is the length of the prefix.
     * Otherwise, one array is a proper prefix of the other and, lexicographic
     * comparison is the result of comparing the two range lengths.
     * (See [.mismatch] for the
     * definition of a common and proper prefix.)
     *
     *
     * The comparison is consistent with
     * [equals][.equals], more
     * specifically the following holds for arrays `a` and `b` with
     * specified ranges [`aFromIndex`, `aToIndex`) and
     * [`bFromIndex`, `bToIndex`) respectively:
     * <pre>`Arrays.equals(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) ==
     * (Arrays.compare(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) == 0)
    `</pre> *
     *
     * @apiNote
     *
     * This method behaves as if:
     * <pre>`int i = Arrays.mismatch(a, aFromIndex, aToIndex,
     * b, bFromIndex, bToIndex);
     * if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     * return Long.compare(a[aFromIndex + i], b[bFromIndex + i]);
     * return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
    `</pre> *
     *
     * @param a the first array to compare
     * @param aFromIndex the index (inclusive) of the first element in the
     * first array to be compared
     * @param aToIndex the index (exclusive) of the last element in the
     * first array to be compared
     * @param b the second array to compare
     * @param bFromIndex the index (inclusive) of the first element in the
     * second array to be compared
     * @param bToIndex the index (exclusive) of the last element in the
     * second array to be compared
     * @return the value `0` if, over the specified ranges, the first and
     * second array are equal and contain the same elements in the same
     * order;
     * a value less than `0` if, over the specified ranges, the
     * first array is lexicographically less than the second array; and
     * a value greater than `0` if, over the specified ranges, the
     * first array is lexicographically greater than the second array
     * @throws IllegalArgumentException
     * if `aFromIndex > aToIndex` or
     * if `bFromIndex > bToIndex`
     * @throws ArrayIndexOutOfBoundsException
     * if `aFromIndex < 0 or aToIndex > a.length` or
     * if `bFromIndex < 0 or bToIndex > b.length`
     * @throws NullPointerException
     * if either array is `null`
     * @since 9
     */
    fun compare(
        a: LongArray, aFromIndex: Int, aToIndex: Int,
        b: LongArray, bFromIndex: Int, bToIndex: Int
    ): Int {
        rangeCheck(a.size, aFromIndex, aToIndex)
        rangeCheck(b.size, bFromIndex, bToIndex)

        val aLength = aToIndex - aFromIndex
        val bLength = bToIndex - bFromIndex
        val i: Int = ArraysSupport.mismatch(
            a, aFromIndex,
            b, bFromIndex,
            min(aLength, bLength)
        )
        if (i >= 0) {
            return Long.compare(a[aFromIndex + i], b[bFromIndex + i])
        }

        return aLength - bLength
    }


    /**
     * Compares two `byte` arrays lexicographically over the specified
     * ranges, numerically treating elements as unsigned.
     *
     *
     * If the two arrays, over the specified ranges, share a common prefix
     * then the lexicographic comparison is the result of comparing two
     * elements, as if by [Byte.compareUnsigned], at a
     * relative index within the respective arrays that is the length of the
     * prefix.
     * Otherwise, one array is a proper prefix of the other and, lexicographic
     * comparison is the result of comparing the two range lengths.
     * (See [.mismatch] for the
     * definition of a common and proper prefix.)
     *
     * @apiNote
     *
     * This method behaves as if:
     * <pre>`int i = Arrays.mismatch(a, aFromIndex, aToIndex,
     * b, bFromIndex, bToIndex);
     * if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     * return Byte.compareUnsigned(a[aFromIndex + i], b[bFromIndex + i]);
     * return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
    `</pre> *
     *
     * @param a the first array to compare
     * @param aFromIndex the index (inclusive) of the first element in the
     * first array to be compared
     * @param aToIndex the index (exclusive) of the last element in the
     * first array to be compared
     * @param b the second array to compare
     * @param bFromIndex the index (inclusive) of the first element in the
     * second array to be compared
     * @param bToIndex the index (exclusive) of the last element in the
     * second array to be compared
     * @return the value `0` if, over the specified ranges, the first and
     * second array are equal and contain the same elements in the same
     * order;
     * a value less than `0` if, over the specified ranges, the
     * first array is lexicographically less than the second array; and
     * a value greater than `0` if, over the specified ranges, the
     * first array is lexicographically greater than the second array
     * @throws IllegalArgumentException
     * if `aFromIndex > aToIndex` or
     * if `bFromIndex > bToIndex`
     * @throws ArrayIndexOutOfBoundsException
     * if `aFromIndex < 0 or aToIndex > a.length` or
     * if `bFromIndex < 0 or bToIndex > b.length`
     * @throws NullPointerException
     * if either array is null
     * @since 9
     */
    fun compareUnsigned(
        a: ByteArray, aFromIndex: Int, aToIndex: Int,
        b: ByteArray, bFromIndex: Int, bToIndex: Int
    ): Int {
        rangeCheck(a.size, aFromIndex, aToIndex)
        rangeCheck(b.size, bFromIndex, bToIndex)

        val aLength = aToIndex - aFromIndex
        val bLength = bToIndex - bFromIndex
        val i: Int = ArraysSupport.mismatch(
            a, aFromIndex,
            b, bFromIndex,
            min(aLength, bLength)
        )
        if (i >= 0) {
            return Byte.compareUnsigned(a[aFromIndex + i], b[bFromIndex + i])
        }

        return aLength - bLength
    }

    /**
     * Throws an IndexOutOfBoundsException if the range [fromIndex, toIndex) is invalid
     * for an array of the given length.
     */
    fun rangeCheck(length: Int, fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || toIndex > length || fromIndex > toIndex) {
            throw IndexOutOfBoundsException("Range [$fromIndex, $toIndex) out of bounds for length $length")
        }
    }

    /**
     * Searches the specified array of ints for the specified value using the
     * binary search algorithm.  The array must be sorted (as
     * by the [.sort] method) prior to making this call.  If it
     * is not sorted, the results are undefined.  If the array contains
     * multiple elements with the specified value, there is no guarantee which
     * one will be found.
     *
     * @param a the array to be searched
     * @param key the value to be searched for
     * @return index of the search key, if it is contained in the array;
     * otherwise, `(-(*insertion point*) - 1)`.  The
     * *insertion point* is defined as the point at which the
     * key would be inserted into the array: the index of the first
     * element greater than the key, or `a.length` if all
     * elements in the array are less than the specified key.  Note
     * that this guarantees that the return value will be &gt;= 0 if
     * and only if the key is found.
     */
    fun binarySearch(a: IntArray, key: Int): Int {
        return binarySearch0(a, 0, a.size, key)
    }

    /**
     * Searches a range of
     * the specified array of ints for the specified value using the
     * binary search algorithm.
     * The range must be sorted (as
     * by the [.sort] method)
     * prior to making this call.  If it
     * is not sorted, the results are undefined.  If the range contains
     * multiple elements with the specified value, there is no guarantee which
     * one will be found.
     *
     * @param a the array to be searched
     * @param fromIndex the index of the first element (inclusive) to be
     * searched
     * @param toIndex the index of the last element (exclusive) to be searched
     * @param key the value to be searched for
     * @return index of the search key, if it is contained in the array
     * within the specified range;
     * otherwise, `(-(*insertion point*) - 1)`.  The
     * *insertion point* is defined as the point at which the
     * key would be inserted into the array: the index of the first
     * element in the range greater than the key,
     * or `toIndex` if all
     * elements in the range are less than the specified key.  Note
     * that this guarantees that the return value will be &gt;= 0 if
     * and only if the key is found.
     * @throws IllegalArgumentException
     * if `fromIndex > toIndex`
     * @throws ArrayIndexOutOfBoundsException
     * if `fromIndex < 0 or toIndex > a.length`
     * @since 1.6
     */
    fun binarySearch(
        a: IntArray, fromIndex: Int, toIndex: Int,
        key: Int
    ): Int {
        rangeCheck(a.size, fromIndex, toIndex)
        return binarySearch0(a, fromIndex, toIndex, key)
    }

    // Like public version, but without range checks.
    private fun binarySearch0(
        a: IntArray, fromIndex: Int, toIndex: Int,
        key: Int
    ): Int {
        var low = fromIndex
        var high = toIndex - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = a[mid]

            if (midVal < key) low = mid + 1
            else if (midVal > key) high = mid - 1
            else return mid // key found
        }
        return -(low + 1) // key not found.
    }


    /*
     * Sorting methods. Note that all public "sort" methods take the
     * same form: performing argument checks if necessary, and then
     * expanding arguments into those required for the internal
     * implementation methods residing in other package-private
     * classes (except for legacyMergeSort, included in this class).
     */
    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @implNote The sorting algorithm is a Dual-Pivot Quicksort
     * by Vladimir Yaroslavskiy, Jon Bentley, and Joshua Bloch. This algorithm
     * offers O(n log(n)) performance on all data sets, and is typically
     * faster than traditional (one-pivot) Quicksort implementations.
     *
     * @param a the array to be sorted
     */
    fun sort(a: IntArray) {
        dualPivotQuicksort(a, 0, a.size)
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @implNote The sorting algorithm is a Dual-Pivot Quicksort
     * by Vladimir Yaroslavskiy, Jon Bentley, and Joshua Bloch. This algorithm
     * offers O(n log(n)) performance on all data sets, and is typically
     * faster than traditional (one-pivot) Quicksort implementations.
     *
     * @param a the array to be sorted
     */
    fun sort(a: LongArray) {
        dualPivotQuicksort(a, 0, a.size)
    }


    fun sort(a: IntArray, fromIndex: Int, toIndex: Int) {
        rangeCheck(a.size, fromIndex, toIndex)
        dualPivotQuicksort(a, fromIndex, toIndex - 1)
    }

    /**
     * Sorts the specified array of objects according to the order induced by
     * the specified comparator.  All elements in the array must be
     * *mutually comparable* by the specified comparator (that is,
     * `c.compare(e1, e2)` must not throw a `ClassCastException`
     * for any elements `e1` and `e2` in the array).
     *
     *
     * This sort is guaranteed to be *stable*:  equal elements will
     * not be reordered as a result of the sort.
     *
     *
     * Implementation note: This implementation is a stable, adaptive,
     * iterative mergesort that requires far fewer than n lg(n) comparisons
     * when the input array is partially sorted, while offering the
     * performance of a traditional mergesort when the input array is
     * randomly ordered.  If the input array is nearly sorted, the
     * implementation requires approximately n comparisons.  Temporary
     * storage requirements vary from a small constant for nearly sorted
     * input arrays to n/2 object references for randomly ordered input
     * arrays.
     *
     *
     * The implementation takes equal advantage of ascending and
     * descending order in its input array, and can take advantage of
     * ascending and descending order in different parts of the same
     * input array.  It is well-suited to merging two or more sorted arrays:
     * simply concatenate the arrays and sort the resulting array.
     *
     *
     * The implementation was adapted from Tim Peters's list sort for Python
     * ([TimSort](http://svn.python.org/projects/python/trunk/Objects/listsort.txt)).  It uses techniques from Peter McIlroy's "Optimistic
     * Sorting and Information Theoretic Complexity", in Proceedings of the
     * Fourth Annual ACM-SIAM Symposium on Discrete Algorithms, pp 467-474,
     * January 1993.
     *
     * @param <T> the class of the objects to be sorted
     * @param a the array to be sorted
     * @param c the comparator to determine the order of the array.  A
     * `null` value indicates that the elements'
     * [natural ordering][Comparable] should be used.
     * @throws ClassCastException if the array contains elements that are
     * not *mutually comparable* using the specified comparator
     * @throws IllegalArgumentException (optional) if the comparator is
     * found to violate the [Comparator] contract
    </T> */
    fun <T> sort(a: Array<T>, c: Comparator<T>) {
        Timsort.sort(a, c)
    }

    private fun dualPivotQuicksort(a: IntArray, low: Int, high: Int) {
        if (low < high) {
            // Choose two pivots: p from a[low] and q from a[high]
            if (a[low] > a[high]) swap(a, low, high)
            val p = a[low]
            val q = a[high]

            var l = low + 1    // will track the boundary for elements < p
            var g = high - 1   // will track the boundary for elements > q
            var k = l

            while (k <= g) {
                when {
                    a[k] < p -> {
                        swap(a, k, l)
                        l++
                        k++
                    }

                    a[k] > q -> {
                        swap(a, k, g)
                        g--
                        // Don't increment k here as we need to re-examine the swapped element
                    }

                    else -> {
                        // Element is between p and q (inclusive)
                        k++
                    }
                }
            }

            l--  // pivot p will go here
            g++  // pivot q will go here
            swap(a, low, l)
            swap(a, high, g)

            // Recursively sort three parts:
            dualPivotQuicksort(a, low, l - 1)
            dualPivotQuicksort(a, l + 1, g - 1)
            dualPivotQuicksort(a, g + 1, high)
        }
    }

    private fun dualPivotQuicksort(a: LongArray, low: Int, high: Int) {
        if (low < high) {
            // Choose two pivots: p from a[low] and q from a[high]
            if (a[low] > a[high]) swap(a, low, high)
            val p = a[low]
            val q = a[high]

            var l = low + 1    // will track the boundary for elements < p
            var g = high - 1   // will track the boundary for elements > q
            var k = l

            while (k <= g) {
                when {
                    a[k] < p -> {
                        swap(a, k, l)
                        l++
                        k++
                    }

                    a[k] > q -> {
                        swap(a, k, g)
                        g--
                        // Don't increment k here as we need to re-examine the swapped element
                    }

                    else -> {
                        // Element is between p and q (inclusive)
                        k++
                    }
                }
            }

            l--  // pivot p will go here
            g++  // pivot q will go here
            swap(a, low, l)
            swap(a, high, g)

            // Recursively sort three parts:
            dualPivotQuicksort(a, low, l - 1)
            dualPivotQuicksort(a, l + 1, g - 1)
            dualPivotQuicksort(a, g + 1, high)
        }
    }

    private fun swap(a: IntArray, i: Int, j: Int) {
        val temp = a[i]
        a[i] = a[j]
        a[j] = temp
    }

    private fun swap(a: LongArray, i: Int, j: Int) {
        val temp = a[i]
        a[i] = a[j]
        a[j] = temp
    }

    fun toString(a: IntArray): String = a.joinToString()

    fun fill(a: ByteArray, fromIndex: Int, toIndex: Int, value: Byte) {
        a.fill(
            element = value,
            fromIndex = fromIndex,
            toIndex = toIndex
        )
    }

    fun fill(a: IntArray, fromIndex: Int, toIndex: Int, value: Int) {
        a.fill(
            element = value,
            fromIndex = fromIndex,
            toIndex = toIndex
        )
    }

    fun fill(a: LongArray, fromIndex: Int, toIndex: Int, value: Long) {
        a.fill(
            element = value,
            fromIndex = fromIndex,
            toIndex = toIndex
        )
    }

    fun <T> fill(a: Array<T>, fromIndex: Int, toIndex: Int, value: T) {
        a.fill(
            element = value,
            fromIndex = fromIndex,
            toIndex = toIndex
        )
    }

    fun <T> fill(a: Array<T?>, fromIndex: Int, toIndex: Int, value: T?) {
        a.fill(
            element = value,
            fromIndex = fromIndex,
            toIndex = toIndex
        )
    }

    fun fill(a: Array<ByteArray?>, fromIndex: Int, toIndex: Int, value: ByteArray?) {
        a.fill(
            element = value,
            fromIndex = fromIndex,
            toIndex = toIndex
        )
    }

    fun fill(a: ByteArray, value: Byte) {
        a.fill(
            element = value
        )
    }

    fun fill(a: IntArray, value: Int) {
        a.fill(
            element = value
        )
    }

    fun fill(a: FloatArray, value: Float) {
        a.fill(
            element = value
        )
    }

    fun <T> fill(a: Array<T>, value: T) {
        a.fill(
            element = value
        )
    }
}