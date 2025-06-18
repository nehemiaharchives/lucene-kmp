package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import kotlin.experimental.and
import kotlin.math.min


/**
 * Radix sorter for variable-length strings. This class sorts based on the most significant byte
 * first and falls back to [IntroSorter] when the size of the buckets to sort becomes small.
 *
 *
 * This algorithm is **NOT** stable. Worst-case memory usage is about `2.3 KB`.
 *
 * @lucene.internal
 */
abstract class MSBRadixSorter protected constructor(protected val maxLength: Int) : Sorter() {
    // we store one histogram per recursion level
    private val histograms = kotlin.arrayOfNulls<IntArray>(LEVEL_THRESHOLD)
    private val endOffsets = IntArray(HISTOGRAM_SIZE)
    private val commonPrefix: IntArray = IntArray(min(24, maxLength))

    /**
     * Return the k-th byte of the entry at index `i`, or `-1` if its length is less than
     * or equal to `k`. This may only be called with a value of `i` between `0`
     * included and `maxLength` excluded.
     */
    protected abstract fun byteAt(i: Int, k: Int): Int

    /**
     * Get a fall-back sorter which may assume that the first k bytes of all compared strings are
     * equal.
     */
    protected open fun getFallbackSorter(k: Int): Sorter? {
        return object : IntroSorter() {
            override fun swap(i: Int, j: Int) {
                this@MSBRadixSorter.swap(i, j)
            }

            override fun compare(i: Int, j: Int): Int {
                for (o in k..<maxLength) {
                    val b1 = byteAt(i, o).toInt()
                    val b2 = byteAt(j, o).toInt()
                    if (b1 != b2) {
                        val ub1 = if (b1 == -1) -1 else (b1 and 0xff)
                        val ub2 = if (b2 == -1) -1 else (b2 and 0xff)
                        return ub1 - ub2
                    } else if (b1 == -1) {
                        break
                    }
                }
                return 0
            }

            override fun setPivot(i: Int) {
                pivot.setLength(0)
                for (o in k..<maxLength) {
                    val b = byteAt(i, o).toInt()
                    if (b == -1) {
                        break
                    }
                    pivot.append((b and 0xff).toByte())
                }
            }

            override fun comparePivot(j: Int): Int {
                for (o in 0..<pivot.length()) {
                    val b1 = pivot.byteAt(o).toInt() and 0xff
                    val b2 = byteAt(j, k + o).toInt()
                    val ub2 = if (b2 == -1) -1 else (b2 and 0xff)
                    if (b1 != ub2) {
                        return b1 - ub2
                    }
                }
                if (k + pivot.length() == maxLength) {
                    return 0
                }
                return -1 - byteAt(j, k + pivot.length())
            }

            private val pivot = BytesRefBuilder()
        }
    }

    override fun compare(i: Int, j: Int): Int {
        throw UnsupportedOperationException("unused: not a comparison-based sort")
    }

    override fun sort(from: Int, to: Int) {
        checkRange(from, to)
        sort(from, to, 0, 0)
    }

    protected fun sort(from: Int, to: Int, k: Int, l: Int) {
        if (shouldFallback(from, to, l)) {
            getFallbackSorter(k)!!.sort(from, to)
        } else {
            radixSort(from, to, k, l)
        }
    }

    protected open fun shouldFallback(from: Int, to: Int, l: Int): Boolean {
        return to - from <= LENGTH_THRESHOLD || l >= LEVEL_THRESHOLD
    }

    /**
     * @param k the character number to compare
     * @param l the level of recursion
     */
    private fun radixSort(from: Int, to: Int, k: Int, l: Int) {
        var histogram = histograms[l]
        if (histogram == null) {
            histograms[l] = IntArray(HISTOGRAM_SIZE)
            histogram = histograms[l]
        } else {
            Arrays.fill(histogram, 0)
        }

        val commonPrefixLength =
            computeCommonPrefixLengthAndBuildHistogram(from, to, k, histogram!!)
        if (commonPrefixLength > 0) {
            // if there are no more chars to compare or if all entries fell into the
            // first bucket (which means strings are shorter than k) then we are done
            // otherwise recurse
            if (k + commonPrefixLength < maxLength && histogram[0] < to - from) {
                radixSort(from, to, k + commonPrefixLength, l)
            }
            return
        }
        require(assertHistogram(commonPrefixLength, histogram))

        val startOffsets = histogram
        var endOffsets: IntArray? = this.endOffsets
        sumHistogram(histogram, endOffsets!!)
        reorder(from, to, startOffsets, endOffsets, k)
        endOffsets = startOffsets

        if (k + 1 < maxLength) {
            // recurse on all but the first bucket since all keys are equals in this
            // bucket (we already compared all bytes)
            var prev = endOffsets[0]
            var i = 1
            while (i < HISTOGRAM_SIZE) {
                val h = endOffsets[i]
                val bucketLen = h - prev
                if (bucketLen > 1) {
                    sort(from + prev, from + h, k + 1, l + 1)
                }
                prev = h
                ++i
            }
        }
    }

    // only used from assert
    private fun assertHistogram(commonPrefixLength: Int, histogram: IntArray): Boolean {
        var numberOfUniqueBytes = 0
        for (freq in histogram) {
            if (freq > 0) {
                numberOfUniqueBytes++
            }
        }
        if (numberOfUniqueBytes == 1) {
            require(commonPrefixLength >= 1)
        } else {
            require(commonPrefixLength == 0) { commonPrefixLength }
        }
        return true
    }

    /** Return a number for the k-th character between 0 and [.HISTOGRAM_SIZE].  */
    protected fun getBucket(i: Int, k: Int): Int {
        val b = byteAt(i, k).toInt()
        return if (b == -1) 0 else (b and 0xff) + 1
    }

    /**
     * Build a histogram of the number of values per [bucket][.getBucket] and return a
     * common prefix length for all visited values.
     *
     * @see .buildHistogram
     */
    // This method, and its namesakes, have been manually split to work around a JVM crash.
    // See https://github.com/apache/lucene/issues/12898
    private fun computeCommonPrefixLengthAndBuildHistogram(from: Int, to: Int, k: Int, histogram: IntArray): Int {
        val commonPrefixLength = computeInitialCommonPrefixLength(from, k)
        return computeCommonPrefixLengthAndBuildHistogramPart1(
            from, to, k, histogram, commonPrefixLength
        )
    }

    // This method, and its namesakes, have been manually split to work around a JVM crash.
    private fun computeInitialCommonPrefixLength(from: Int, k: Int): Int {
        val commonPrefix = this.commonPrefix
        var commonPrefixLength = min(commonPrefix.size, maxLength - k)
        var j = 0
        while (j < commonPrefixLength) {
            val b = byteAt(from, k + j)
            commonPrefix[j] = b
            if (b == -1) {
                commonPrefixLength = j + 1
                break
            }
            ++j
        }
        return commonPrefixLength
    }

    // This method, and its namesakes, have been manually split to work around a JVM crash.
    private fun computeCommonPrefixLengthAndBuildHistogramPart1(
        from: Int, to: Int, k: Int, histogram: IntArray, commonPrefixLength: Int
    ): Int {
        var commonPrefixLength = commonPrefixLength
        val commonPrefix = this.commonPrefix
        var i: Int = from + 1
        outer@ while (i < to) {
            for (j in 0..<commonPrefixLength) {
                val b = byteAt(i, k + j)
                if (b != commonPrefix[j]) {
                    commonPrefixLength = j
                    if (commonPrefixLength == 0) { // we have no common prefix
                        break@outer
                    }
                    break
                }
            }
            ++i
        }
        return computeCommonPrefixLengthAndBuildHistogramPart2(
            from, to, k, histogram, commonPrefixLength, i
        )
    }

    // This method, and its namesakes, have been manually split to work around a JVM crash.
    private fun computeCommonPrefixLengthAndBuildHistogramPart2(
        from: Int, to: Int, k: Int, histogram: IntArray, commonPrefixLength: Int, i: Int
    ): Int {
        if (i < to) {
            // the loop got broken because there is no common prefix
            require(commonPrefixLength == 0)
            buildHistogram(commonPrefix[0] + 1, i - from, i, to, k, histogram)
        } else {
            require(commonPrefixLength > 0)
            histogram[commonPrefix[0] + 1] = to - from
        }

        return commonPrefixLength
    }

    /**
     * Build an histogram of the k-th characters of values occurring between offsets `from` and
     * `to`, using [.getBucket].
     */
    protected open fun buildHistogram(
        prefixCommonBucket: Int, prefixCommonLen: Int, from: Int, to: Int, k: Int, histogram: IntArray
    ) {
        histogram[prefixCommonBucket] = prefixCommonLen
        for (i in from..<to) {
            histogram[getBucket(i, k)]++
        }
    }

    /**
     * Reorder based on start/end offsets for each bucket. When this method returns, startOffsets and
     * endOffsets are equal.
     *
     * @param startOffsets start offsets per bucket
     * @param endOffsets end offsets per bucket
     */
    protected open fun reorder(from: Int, to: Int, startOffsets: IntArray, endOffsets: IntArray, k: Int) {
        // reorder in place, like the dutch flag problem
        for (i in 0..<HISTOGRAM_SIZE) {
            val limit = endOffsets[i]
            var h1 = startOffsets[i]
            while (h1 < limit) {
                val b = getBucket(from + h1, k)
                val h2: Int = startOffsets[b]++
                swap(from + h1, from + h2)
                h1 = startOffsets[i]
            }
        }
    }

    companion object {
        // after that many levels of recursion we fall back to introsort anyway
        // this is used as a protection against the fact that radix sort performs
        // worse when there are long common prefixes (probably because of cache
        // locality)
        protected const val LEVEL_THRESHOLD: Int = 8

        // size of histograms: 256 + 1 to indicate that the string is finished
        protected const val HISTOGRAM_SIZE: Int = 257

        // buckets below this size will be sorted with fallback sorter
        protected const val LENGTH_THRESHOLD: Int = 100

        /**
         * Accumulate values of the histogram so that it does not store counts but start offsets. `endOffsets` will store the end offsets.
         */
        private fun sumHistogram(histogram: IntArray, endOffsets: IntArray) {
            var accum = 0
            for (i in 0..<HISTOGRAM_SIZE) {
                val count = histogram[i]
                histogram[i] = accum
                accum += count
                endOffsets[i] = accum
            }
        }
    }
}
