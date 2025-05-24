package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Arrays.mismatch
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import kotlin.jvm.JvmName
import kotlin.math.min

class ArrayUtil {

    companion object {

        /** Maximum length for an array (Integer.MAX_VALUE - RamUsageEstimator.NUM_BYTES_ARRAY_HEADER).  */
        val MAX_ARRAY_LENGTH: Int = Int.MAX_VALUE - RamUsageEstimator.NUM_BYTES_ARRAY_HEADER

        /*
          Begin Apache Harmony code

          Revision taken on Friday, June 12. https://svn.apache.org/repos/asf/harmony/enhanced/classlib/archive/java6/modules/luni/src/main/java/java/lang/Integer.java

        */

        /**
         * Parses a char array into an int.
         *
         * @param chars the character array
         * @param offset The offset into the array
         * @param len The length
         * @return the int
         * @throws NumberFormatException if it can't parse
         */
        @Throws(NumberFormatException::class)
        fun parseInt(chars: CharArray, offset: Int, len: Int): Int {
            return parseInt(chars, offset, len, 10)
        }

        /**
         * Parses the string argument as if it was an int value and returns the result. Throws
         * NumberFormatException if the string does not represent an int quantity. The second argument
         * specifies the radix to use when parsing the value.
         *
         * @param chars a string representation of an int quantity.
         * @param radix the base to use for conversion.
         * @return int the value represented by the argument
         * @throws NumberFormatException if the argument could not be parsed as an int quantity.
         */
        @Throws(NumberFormatException::class)
        fun parseInt(chars: CharArray, offset: Int, len: Int, radix: Int): Int {
            var offset = offset
            var len = len
            if (chars == null || radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
                throw NumberFormatException()
            }
            var i = 0
            if (len == 0) {
                throw NumberFormatException("chars length is 0")
            }
            val negative = chars[offset + i] == '-'
            if (negative && ++i == len) {
                throw NumberFormatException("can't convert to an int")
            }
            if (negative == true) {
                offset++
                len--
            }
            return parse(chars, offset, len, radix, negative)
        }

        @Throws(NumberFormatException::class)
        private fun parse(chars: CharArray, offset: Int, len: Int, radix: Int, negative: Boolean): Int {
            val max = Int.MIN_VALUE / radix
            var result = 0
            for (i in 0..<len) {
                val digit = chars[i + offset].digitToIntOrNull(radix) ?: -1
                if (digit == -1) {
                    throw NumberFormatException("Unable to parse")
                }
                if (max > result) {
                    throw NumberFormatException("Unable to parse")
                }
                val next = result * radix - digit
                if (next > result) {
                    throw NumberFormatException("Unable to parse")
                }
                result = next
            }
            /*while (offset < len) {

            }*/
            if (!negative) {
                result = -result
                if (result < 0) {
                    throw NumberFormatException("Unable to parse")
                }
            }
            return result
        }
        /*

        END APACHE HARMONY CODE
         */

        /**
         * Returns an array size &gt;= minTargetSize, generally over-allocating exponentially to achieve
         * amortized linear-time cost as the array grows.
         *
         *
         * NOTE: this was originally borrowed from Python 2.4.2 listobject.c sources (attribution in
         * LICENSE.txt), but has now been substantially changed based on discussions from java-dev thread
         * with subject "Dynamic array reallocation algorithms", started on Jan 12 2010.
         *
         * @param minTargetSize Minimum required value to be returned.
         * @param bytesPerElement Bytes used by each element of the array. See constants in [     ].
         * @lucene.internal
         */
        fun oversize(minTargetSize: Int, bytesPerElement: Int): Int {
            require(minTargetSize >= 0) { "invalid array size $minTargetSize" }

            if (minTargetSize == 0) {
                // wait until at least one element is requested
                return 0
            }

            require(minTargetSize <= MAX_ARRAY_LENGTH) {
                ("requested array size "
                        + minTargetSize
                        + " exceeds maximum array in java ("
                        + MAX_ARRAY_LENGTH
                        + ")")
            }

            // asymptotic exponential growth by 1/8th, favors
            // spending a bit more CPU to not tie up too much wasted
            // RAM:
            var extra = minTargetSize shr 3

            if (extra < 3) {
                // for very small arrays, where constant overhead of
                // realloc is presumably relatively high, we grow
                // faster
                extra = 3
            }

            val newSize = minTargetSize + extra

            // add 7 to allow for worst case byte alignment addition below:
            if (newSize + 7 < 0 || newSize + 7 > MAX_ARRAY_LENGTH) {
                // int overflowed, or we exceeded the maximum array length
                return MAX_ARRAY_LENGTH
            }

            return if (Constants.JRE_IS_64BIT) {
                // round up to 8 byte alignment in 64bit env
                when (bytesPerElement) {
                    4 -> (newSize + 1) and 0x7ffffffe
                    2 -> (newSize + 3) and 0x7ffffffc
                    1 -> (newSize + 7) and 0x7ffffff8
                    8 -> newSize
                    else -> newSize
                }
            } else {
                // In 32bit jvm, it's still 8-byte aligned,
                // but the array header is 12 bytes, not a multiple of 8.
                // So saving 4,12,20,28... bytes of data is the most cost-effective.
                when (bytesPerElement) {
                    1 -> ((newSize + 3) and 0x7ffffff8) + 4
                    2 -> ((newSize + 1) and 0x7ffffffc) + 2
                    4 -> (newSize and 0x7ffffffe) + 1
                    8 -> newSize
                    else -> newSize
                }
            }
        }

        /**
         * Returns a new array whose size is exact the specified `newLength` without over-allocating
         */
        inline fun <reified T> growExact(array: Array<T>, newLength: Int): Array<T> {
            if (newLength < array.size) throw IndexOutOfBoundsException("newLength ($newLength) < array.size (${array.size})")
            return Array<T>(newLength) { i -> if (i < array.size) array[i] else null as T }
        }

        @JvmName("growExactNullable")
        inline fun <reified T> growExact(array: Array<T?>, newLength: Int): Array<T?> {
            if (newLength < array.size) throw IndexOutOfBoundsException("newLength ($newLength) < array.size (${array.size})")
            return Array(newLength) { i -> if (i < array.size) array[i] else null }
        }

        /** Returns a larger array, generally over-allocating exponentially  */
        /*fun <T> grow(array: Array<T?>): Array<T?> {
            return grow<T?>(array, 1 + array.size)
        }*/

        inline fun <reified T> grow(array: Array<T>): Array<T> {
            return grow(array, 1 + array.size)
        }

        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating
         * exponentially
         */
        inline fun <reified T> grow(array: Array<T>, minSize: Int): Array<T> {
            if (minSize < 0) throw Exception("size must be positive (got $minSize): likely integer overflow?")
            if (array.size < minSize) {
                val newLength: Int =
                    oversize(minSize, RamUsageEstimator.NUM_BYTES_OBJECT_REF)
                return growExact<T>(array, newLength)
            } else return array
        }

        @JvmName("growNullable")
        inline fun <reified T> grow(array: Array<T?>, minSize: Int): Array<T?> {
            if (minSize < 0) throw Exception("size must be positive (got $minSize): likely integer overflow?")
            if (array.size < minSize) {
                val newLength: Int =
                    oversize(minSize, RamUsageEstimator.NUM_BYTES_OBJECT_REF)
                return growExact<T?>(array, newLength)
            } else return array
        }

        /**
         * Returns a new array whose size is exact the specified `newLength` without over-allocating
         */
        fun growExact(array: ShortArray, newLength: Int): ShortArray {
            val copy = ShortArray(newLength)
            array.copyInto(copy, 0, 0, array.size)
            return copy
        }

        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating exponentially
         */
        fun grow(array: ShortArray, minSize: Int): ShortArray {
            if (minSize < 0) throw IllegalArgumentException("size must be positive (got $minSize): likely integer overflow?")
            return if (array.size < minSize) {
                growExact(array, oversize(minSize, Short.SIZE_BYTES))
            } else array
        }

        /** Returns a larger array, generally over-allocating exponentially  */
        fun grow(array: ShortArray): ShortArray {
            return grow(array, 1 + array.size)
        }

        /**
         * Returns a new array whose size is exact the specified `newLength` without over-allocating
         */
        fun growExact(array: FloatArray, newLength: Int): FloatArray {
            val copy = FloatArray(newLength)
            array.copyInto(copy, 0, 0, array.size)
            return copy
        }

        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating
         * exponentially
         */
        fun grow(array: FloatArray, minSize: Int): FloatArray {
            if(minSize < 0) throw Exception("size must be positive (got $minSize): likely integer overflow?" )
            if (array.size < minSize) {
                val copy = FloatArray(oversize(minSize, Float.SIZE_BYTES))
                array.copyInto(copy, 0, 0, array.size)
                return copy
            } else return array
        }

        /** Returns a larger array, generally over-allocating exponentially  */
        fun grow(array: FloatArray): FloatArray {
            return grow(array, 1 + array.size)
        }

        /**
         * Returns a new array whose size is exact the specified `newLength` without over-allocating
         */
        fun growExact(array: DoubleArray, newLength: Int): DoubleArray {
            val copy = DoubleArray(newLength)
            array.copyInto(copy, 0, 0, array.size)
            return copy
        }

        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating
         * exponentially
         */
        fun grow(array: DoubleArray, minSize: Int): DoubleArray {
            require(minSize >= 0) { "size must be positive (got $minSize): likely integer overflow?" }
            return if (array.size < minSize) {
                growExact(
                    array,
                    oversize(minSize, Double.SIZE_BYTES)
                )
            } else array
        }

        /** Returns a larger array, generally over-allocating exponentially  */
        fun grow(array: DoubleArray): DoubleArray {
            return grow(array, 1 + array.size)
        }

        /**
         * Returns a new array whose size is exact the specified `newLength` without over-allocating
         */
        fun growExact(array: IntArray, newLength: Int): IntArray {
            val copy = IntArray(newLength)
            array.copyInto(copy, 0, 0, array.size)
            return copy
        }

        /**
         * Returns an array whose size is at least `minLength`, generally over-allocating
         * exponentially, but never allocating more than `maxLength` elements.
         */
        fun growInRange(array: IntArray, minLength: Int, maxLength: Int): IntArray {
            assert(
                minLength >= 0
            ) { "length must be positive (got $minLength): likely integer overflow?" }

            require(minLength <= maxLength) {
                ("requested minimum array length "
                        + minLength
                        + " is larger than requested maximum array length "
                        + maxLength)
            }

            if (array.size >= minLength) {
                return array
            }

            val potentialLength: Int = oversize(minLength, Int.SIZE_BYTES)
            return growExact(array, min(maxLength, potentialLength))
        }


        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating
         * exponentially
         */
        fun grow(array: IntArray, minSize: Int): IntArray {
            return growInRange(array, minSize, Int.MAX_VALUE)
        }

        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating
         * exponentially, and it will not copy the origin data to the new array
         */
        fun growNoCopy(array: IntArray, minSize: Int): IntArray {
            require(minSize >= 0) { "size must be positive (got $minSize): likely integer overflow?" }
            return if (array.size < minSize) {
                IntArray(oversize(minSize, Int.SIZE_BYTES))
            } else array
        }

        /** Returns a larger array, generally over-allocating exponentially  */
        fun grow(array: IntArray): IntArray {
            return grow(array, 1 + array.size)
        }

        /**
         * Returns a new array whose size is exact the specified `newLength` without over-allocating
         */
        fun growExact(array: LongArray, newLength: Int): LongArray {
            val copy = LongArray(newLength)
            array.copyInto(copy, 0, 0, array.size)
            return copy
        }

        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating
         * exponentially
         */
        fun grow(array: LongArray, minSize: Int): LongArray {
            require(minSize >= 0) { "size must be positive (got $minSize): likely integer overflow?" }
            return if (array.size < minSize) {
                growExact(array, oversize(minSize, Long.SIZE_BYTES))
            } else array
        }

        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating
         * exponentially, and it will not copy the origin data to the new array
         */
        fun growNoCopy(array: LongArray, minSize: Int): LongArray {
            require(minSize >= 0) { "size must be positive (got $minSize): likely integer overflow?" }
            return if (array.size < minSize) {
                LongArray(oversize(minSize, Long.SIZE_BYTES))
            } else array
        }

        /** Returns a larger array, generally over-allocating exponentially  */
        fun grow(array: LongArray): LongArray {
            return grow(array, 1 + array.size)
        }

        /**
         * Returns a new array whose size is exact the specified `newLength` without over-allocating
         */
        fun growExact(array: ByteArray, newLength: Int): ByteArray {
            val copy = ByteArray(newLength)
            array.copyInto(copy, 0, 0, array.size)
            return copy
        }

        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating
         * exponentially
         */
        fun grow(array: ByteArray, minSize: Int): ByteArray {
            require(minSize >= 0) { "size must be positive (got $minSize): likely integer overflow?" }
            return if (array.size < minSize) {
                growExact(
                    array,
                    oversize(minSize, Byte.SIZE_BYTES)
                )
            } else array
        }

        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating
         * exponentially, and it will not copy the origin data to the new array
         */
        fun growNoCopy(array: ByteArray, minSize: Int): ByteArray {
            require(minSize >= 0) { "size must be positive (got $minSize): likely integer overflow?" }
            return if (array.size < minSize) {
                ByteArray(oversize(minSize, Byte.SIZE_BYTES))
            } else array
        }

        /** Returns a larger array, generally over-allocating exponentially  */
        fun grow(array: ByteArray): ByteArray {
            return grow(array, 1 + array.size)
        }

        /**
         * Returns a new array whose size is exact the specified `newLength` without over-allocating
         */
        fun growExact(array: CharArray, newLength: Int): CharArray {
            val copy = CharArray(newLength)
            array.copyInto(copy, 0, 0, array.size)
            return copy
        }

        /**
         * Returns an array whose size is at least `minSize`, generally over-allocating
         * exponentially
         */
        fun grow(array: CharArray, minSize: Int): CharArray {
            require(minSize >= 0) { "size must be positive (got $minSize): likely integer overflow?" }
            return if (array.size < minSize) {
                growExact(array, oversize(minSize, Char.SIZE_BYTES))
            } else array
        }

        /** Returns a larger array, generally over-allocating exponentially  */
        fun grow(array: CharArray): CharArray {
            return grow(array, 1 + array.size)
        }

        /** Returns hash of chars in range start (inclusive) to end (inclusive)  */
        fun hashCode(array: CharArray, start: Int, end: Int): Int {
            var code = 0
            for (i in end - 1 downTo start) code = code * 31 + array[i].code
            return code
        }

        /** Swap values stored in slots `i` and `j`  */
        fun <T> swap(arr: Array<T>, i: Int, j: Int) {
            val tmp = arr[i]
            arr[i] = arr[j]
            arr[j] = tmp
        }


        // intro-sorts
        /**
         * Sorts the given array slice using the [Comparator]. This method uses the intro sort
         * algorithm, but falls back to insertion sort for small arrays.
         *
         * @see IntroSorter
         *
         * @param fromIndex start index (inclusive)
         * @param toIndex end index (exclusive)
         */
        fun <T> introSort(a: Array<T>, fromIndex: Int, toIndex: Int, comp: Comparator<in T>) {
            if (toIndex - fromIndex <= 1) return
            ArrayIntroSorter(a, comp).sort(fromIndex, toIndex)
        }

        /**
         * Sorts the given array using the [Comparator]. This method uses the intro sort algorithm,
         * but falls back to insertion sort for small arrays.
         *
         * @see IntroSorter
         */
        fun <T> introSort(a: Array<T>, comp: Comparator<in T>) {
            introSort<T>(a, 0, a.size, comp)
        }

        /**
         * Sorts the given array slice in natural order. This method uses the intro sort algorithm, but
         * falls back to insertion sort for small arrays.
         *
         * @see IntroSorter
         *
         * @param fromIndex start index (inclusive)
         * @param toIndex end index (exclusive)
         */
        fun <T : Comparable<T>> introSort(
            a: Array<T>, fromIndex: Int, toIndex: Int
        ) {
            if (toIndex - fromIndex <= 1) return
            introSort<T>(a, fromIndex, toIndex, naturalOrder() /*java.util.Comparator.naturalOrder<T>()*/)
        }

        /**
         * Sorts the given array in natural order. This method uses the intro sort algorithm, but falls
         * back to insertion sort for small arrays.
         *
         * @see IntroSorter
         */
        fun <T : Comparable<T>> introSort(a: Array<T>) {
            introSort(a, 0, a.size)
        }


        // tim sorts:
        /**
         * Sorts the given array slice using the [Comparator]. This method uses the Tim sort
         * algorithm, but falls back to binary sort for small arrays.
         *
         * @see TimSorter
         *
         * @param fromIndex start index (inclusive)
         * @param toIndex end index (exclusive)
         */
        fun <T> timSort(a: Array<T>, fromIndex: Int, toIndex: Int, comp: Comparator<in T>) {
            if (toIndex - fromIndex <= 1) return
            ArrayTimSorter(a, comp, a.size / 64).sort(fromIndex, toIndex)
        }

        /**
         * Sorts the given array using the [Comparator]. This method uses the Tim sort algorithm,
         * but falls back to binary sort for small arrays.
         *
         * @see TimSorter
         */
        fun <T> timSort(a: Array<T>, comp: Comparator<in T>) {
            timSort<T>(a, 0, a.size, comp)
        }

        /**
         * Sorts the given array slice in natural order. This method uses the Tim sort algorithm, but
         * falls back to binary sort for small arrays.
         *
         * @see TimSorter
         *
         * @param fromIndex start index (inclusive)
         * @param toIndex end index (exclusive)
         */
        fun <T: Comparable<T>> timSort(a: Array<T>, fromIndex: Int, toIndex: Int) {
            if (toIndex - fromIndex <= 1) return
            timSort(a, fromIndex, toIndex, naturalOrder() /*java.util.Comparator.naturalOrder<T>()*/)
        }

        /**
         * Sorts the given array in natural order. This method uses the Tim sort algorithm, but falls back
         * to binary sort for small arrays.
         *
         * @see TimSorter
         */
        fun <T : Comparable<T>> timSort(a: Array<T>) {
            timSort<T>(a, 0, a.size)
        }

        /**
         * Reorganize `arr[from:to[` so that the element at offset k is at the same position as if
         * `arr[from:to]` was sorted, and all elements on its left are less than or equal to it, and
         * all elements on its right are greater than or equal to it.
         *
         *
         * This runs in linear time on average and in `n log(n)` time in the worst case.
         *
         * @param arr Array to be re-organized.
         * @param from Starting index for re-organization. Elements before this index will be left as is.
         * @param to Ending index. Elements after this index will be left as is.
         * @param k Index of element to sort from. Value must be less than 'to' and greater than or equal
         * to 'from'.
         * @param comparator Comparator to use for sorting
         */
        fun <T> select(
            arr: Array<T>, from: Int, to: Int, k: Int, comparator: Comparator<in T>
        ) {
            object : IntroSelector() {
                var pivot: T? = null

                protected override fun swap(i: Int, j: Int) {
                    ArrayUtil.swap<T>(arr, i, j)
                }

                protected override fun setPivot(i: Int) {
                    pivot = arr[i]
                }

                protected override fun comparePivot(j: Int): Int {
                    return comparator.compare(pivot!!, arr[j])
                }
            }.select(from, to, k)
        }

        /** Copies an array into a new array.  */
        fun copyArray(array: ByteArray): ByteArray {
            return copyOfSubArray(array, 0, array.size)
        }

        /**
         * Copies the specified range of the given array into a new sub array.
         *
         * @param array the input array
         * @param from the initial index of range to be copied (inclusive)
         * @param to the final index of range to be copied (exclusive)
         */
        fun copyOfSubArray(array: ByteArray, from: Int, to: Int): ByteArray {
            val copy = ByteArray(to - from)
            array.copyInto(copy, 0, from, to) /*java.lang.System.arraycopy(array, from, copy, 0, to - from)*/
            return copy
        }

        /** Copies an array into a new array.  */
        fun copyArray(array: CharArray): CharArray {
            return copyOfSubArray(array, 0, array.size)
        }

        /**
         * Copies the specified range of the given array into a new sub array.
         *
         * @param array the input array
         * @param from the initial index of range to be copied (inclusive)
         * @param to the final index of range to be copied (exclusive)
         */
        fun copyOfSubArray(array: CharArray, from: Int, to: Int): CharArray {
            val copy = CharArray(to - from)
            /*java.lang.System.arraycopy(array, from, copy, 0, to - from)*/
            array.copyInto(copy, 0, from, to)
            return copy
        }

        /** Copies an array into a new array.  */
        fun copyArray(array: ShortArray): ShortArray {
            return copyOfSubArray(array, 0, array.size)
        }

        /**
         * Copies the specified range of the given array into a new sub array.
         *
         * @param array the input array
         * @param from the initial index of range to be copied (inclusive)
         * @param to the final index of range to be copied (exclusive)
         */
        fun copyOfSubArray(array: ShortArray, from: Int, to: Int): ShortArray {
            val copy = ShortArray(to - from)
            /*java.lang.System.arraycopy(array, from, copy, 0, to - from)*/
            array.copyInto(copy, 0, from, to)
            return copy
        }

        /** Copies an array into a new array.  */
        fun copyArray(array: IntArray): IntArray {
            return copyOfSubArray(array, 0, array.size)
        }

        /**
         * Copies the specified range of the given array into a new sub array.
         *
         * @param array the input array
         * @param from the initial index of range to be copied (inclusive)
         * @param to the final index of range to be copied (exclusive)
         */
        fun copyOfSubArray(array: IntArray, from: Int, to: Int): IntArray {
            val copy = IntArray(to - from)
            /*java.lang.System.arraycopy(array, from, copy, 0, to - from)*/
            array.copyInto(copy, 0, from, to)
            return copy
        }

        /** Copies an array into a new array.  */
        fun copyArray(array: LongArray): LongArray {
            return copyOfSubArray(array, 0, array.size)
        }

        /**
         * Copies the specified range of the given array into a new sub array.
         *
         * @param array the input array
         * @param from the initial index of range to be copied (inclusive)
         * @param to the final index of range to be copied (exclusive)
         */
        fun copyOfSubArray(array: LongArray, from: Int, to: Int): LongArray {
            val copy = LongArray(to - from)
            /*java.lang.System.arraycopy(array, from, copy, 0, to - from)*/
            array.copyInto(copy, 0, from, to)
            return copy
        }


        /** Copies an array into a new array.  */
        fun copyArray(array: FloatArray): FloatArray {
            return copyOfSubArray(array, 0, array.size)
        }

        /**
         * Copies the specified range of the given array into a new sub array.
         *
         * @param array the input array
         * @param from the initial index of range to be copied (inclusive)
         * @param to the final index of range to be copied (exclusive)
         */
        fun copyOfSubArray(array: FloatArray, from: Int, to: Int): FloatArray {
            val copy = FloatArray(to - from)
            /*java.lang.System.arraycopy(array, from, copy, 0, to - from)*/
            array.copyInto(copy, 0, from, to)
            return copy
        }

        /** Copies an array into a new array.  */
        fun copyArray(array: DoubleArray): DoubleArray {
            return copyOfSubArray(array, 0, array.size)
        }

        /**
         * Copies the specified range of the given array into a new sub array.
         *
         * @param array the input array
         * @param from the initial index of range to be copied (inclusive)
         * @param to the final index of range to be copied (exclusive)
         */
        fun copyOfSubArray(array: DoubleArray, from: Int, to: Int): DoubleArray {
            val copy = DoubleArray(to - from)
            /*java.lang.System.arraycopy(array, from, copy, 0, to - from)*/
            array.copyInto(copy, 0, from, to)
            return copy
        }

        /** Copies an array into a new array.  */
        inline fun <reified T> copyArray(array: Array<T>): Array<T> {
            return copyOfSubArray(array, 0, array.size)
        }

        /**
         * Copies the specified range of the given array into a new sub array.
         *
         * @param array the input array
         * @param from the initial index of range to be copied (inclusive)
         * @param to the final index of range to be copied (exclusive)
         */
        inline fun <reified T> copyOfSubArray(array: Array<T>, from: Int, to: Int): Array<T> {
            val subLength = to - from
            return Array(subLength) { i -> array[from + i] }
        }

        @JvmName("copyOfSubArrayNullable")
        inline fun <reified T> copyOfSubArray(array: Array<T?>, from: Int, to: Int): Array<T?> {
            val subLength = to - from
            return Array(subLength) { i -> array[from + i] }
        }

        /** Comparator for a fixed number of bytes.  */
        fun interface ByteArrayComparator {
            /**
             * Compare bytes starting from the given offsets. The return value has the same contract as
             * [Comparator.compare].
             */
            fun compare(a: ByteArray, aI: Int, b: ByteArray, bI: Int): Int
        }

        /** Return a comparator for exactly the specified number of bytes.  */
        fun getUnsignedComparator(numBytes: Int): ByteArrayComparator {
            return if (numBytes == Long.SIZE_BYTES) {
                // Used by LongPoint, DoublePoint
                ByteArrayComparator { a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int ->
                    compareUnsigned8(
                        a,
                        aOffset,
                        b,
                        bOffset
                    )
                }
            } else if (numBytes == Int.SIZE_BYTES) {
                // Used by IntPoint, FloatPoint, LatLonPoint, LatLonShape
                ByteArrayComparator { a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int ->
                    compareUnsigned4(
                        a,
                        aOffset,
                        b,
                        bOffset
                    )
                }
            } else {
                ByteArrayComparator { a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int ->
                    compareUnsigned(
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

        // walk around to avoid jvm specific VH_BE_LONG
        /** Compare exactly 8 unsigned bytes from the provided arrays.  */
        /*fun compareUnsigned8(a: ByteArray?, aOffset: Int, b: ByteArray?, bOffset: Int): Int {
            return java.lang.Long.compareUnsigned(
                BitUtil.VH_BE_LONG.get(a, aOffset) as Long, BitUtil.VH_BE_LONG.get(b, bOffset) as Long
            )
        }*/

        /**
         * Compare exactly 8 unsigned bytes from the provided arrays.
         * This function reads a big-endian long from each array and compares them as unsigned values.
         */
        fun compareUnsigned8(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int): Int {
            val valueA = a.getLongBE(aOffset).toULong()
            val valueB = b.getLongBE(bOffset).toULong()
            return valueA.compareTo(valueB)
        }

        // walk around to avoid jvm specific VH_BE_INT
        /** Compare exactly 4 unsigned bytes from the provided arrays.  */
        /*fun compareUnsigned4(a: ByteArray?, aOffset: Int, b: ByteArray?, bOffset: Int): Int {
            return java.lang.Integer.compareUnsigned(
                BitUtil.VH_BE_INT.get(a, aOffset) as Int, BitUtil.VH_BE_INT.get(b, bOffset) as Int
            )
        }*/

        /** Compare exactly 4 unsigned bytes from the provided arrays. */
        fun compareUnsigned4(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int): Int {
            val intA = a.getIntBE(aOffset).toUInt()
            val intB = b.getIntBE(bOffset).toUInt()
            return intA.compareTo(intB)
        }


        /**
         * Compare exactly the bytes in the slices [aFromIndex, aToIndex) and [bFromIndex, bToIndex)
         * as unsigned bytes.
         *
         * If a mismatch is found at index i (relative to the slices), it returns the result of
         * comparing the two differing unsigned byte values.
         *
         * If the slices are equal up to the length of the shorter slice, then the lengths are compared.
         */
        fun compareUnsigned(
            a: ByteArray, aFromIndex: Int, aToIndex: Int,
            b: ByteArray, bFromIndex: Int, bToIndex: Int
        ): Int {
            Arrays.rangeCheck(a.size, aFromIndex, aToIndex)
            Arrays.rangeCheck(b.size, bFromIndex, bToIndex)

            val aLength = aToIndex - aFromIndex
            val bLength = bToIndex - bFromIndex
            val len = minOf(aLength, bLength)
            val i = mismatch(a, aFromIndex, b, bFromIndex, len)
            return if (i >= 0) {
                // Compare the unsigned bytes using toUByte() which represents them as 0..255.
                a[aFromIndex + i].toUByte().compareTo(b[bFromIndex + i].toUByte())
            } else {
                // All compared bytes are equal; compare lengths.
                aLength - bLength
            }
        }
    }// end of companion object
}
