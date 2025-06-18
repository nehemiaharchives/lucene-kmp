package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import kotlin.experimental.and
import kotlin.math.min


/**
 * Radix selector.
 *
 *
 * This implementation works similarly to a MSB radix sort except that it only recurses into the
 * sub partition that contains the desired value.
 *
 * @lucene.internal
 */
abstract class RadixSelector protected constructor(private val maxLength: Int) : Selector() {
    // we store one histogram per recursion level
    private val histogram = IntArray(HISTOGRAM_SIZE)
    private val commonPrefix: IntArray = IntArray(min(24, maxLength))

    /**
     * Return the k-th byte of the entry at index `i`, or `-1` if its length is less than
     * or equal to `k`. This may only be called with a value of `k` between `0`
     * included and `maxLength` excluded.
     */
    protected abstract fun byteAt(i: Int, k: Int): Int

    /**
     * Get a fall-back selector which may assume that the first `d` bytes of all compared
     * strings are equal. This fallback selector is used when the range becomes narrow or when the
     * maximum level of recursion has been exceeded.
     */
    protected open fun getFallbackSelector(d: Int): Selector? {
        return object : IntroSelector() {
            override fun swap(i: Int, j: Int) {
                this@RadixSelector.swap(i, j)
            }

            override fun compare(i: Int, j: Int): Int {
                for (o in d..<maxLength) {
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
                for (o in d..<maxLength) {
                    val b = byteAt(i, o)
                    if (b == -1) {
                        break
                    }
                    pivot.append((b and 0xff).toByte())
                }
            }

            override fun comparePivot(j: Int): Int {
                for (o in 0..<pivot.length()) {
                    val b1 = pivot.byteAt(o).toInt() and 0xff
                    val b2 = byteAt(j, d + o).toInt()
                    val ub2 = if (b2 == -1) -1 else (b2 and 0xff)
                    if (b1 != ub2) {
                        return b1 - ub2
                    }
                }
                if (d + pivot.length() == maxLength) {
                    return 0
                }
                return -1 - byteAt(j, d + pivot.length())
            }

            private val pivot = BytesRefBuilder()
        }
    }

    override fun select(from: Int, to: Int, k: Int) {
        checkArgs(from, to, k)
        select(from, to, k, 0, 0)
    }

    private fun select(from: Int, to: Int, k: Int, d: Int, l: Int) {
        if (to - from <= LENGTH_THRESHOLD || l >= LEVEL_THRESHOLD) {
            getFallbackSelector(d)!!.select(from, to, k)
        } else {
            radixSelect(from, to, k, d, l)
        }
    }

    /**
     * @param d the character number to compare
     * @param l the level of recursion
     */
    private fun radixSelect(from: Int, to: Int, k: Int, d: Int, l: Int) {
        val histogram = this.histogram
        Arrays.fill(histogram, 0)

        val commonPrefixLength =
            computeCommonPrefixLengthAndBuildHistogram(from, to, d, histogram)
        if (commonPrefixLength > 0) {
            // if there are no more chars to compare or if all entries fell into the
            // first bucket (which means strings are shorter than d) then we are done
            // otherwise recurse
            if (d + commonPrefixLength < maxLength && histogram[0] < to - from) {
                radixSelect(from, to, k, d + commonPrefixLength, l)
            }
            return
        }
        require(assertHistogram(commonPrefixLength, histogram))

        var bucketFrom = from
        for (bucket in 0..<HISTOGRAM_SIZE) {
            val bucketTo = bucketFrom + histogram[bucket]

            if (bucketTo > k) {
                partition(from, to, bucket, bucketFrom, bucketTo, d)

                if (bucket != 0 && d + 1 < maxLength) {
                    // all elements in bucket 0 are equal so we only need to recurse if bucket != 0
                    select(bucketFrom, bucketTo, k, d + 1, l + 1)
                }
                return
            }
            bucketFrom = bucketTo
        }
        throw AssertionError("Unreachable code")
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
            require(commonPrefixLength == 0)
        }
        return true
    }

    /** Return a number for the k-th character between 0 and [.HISTOGRAM_SIZE].  */
    private fun getBucket(i: Int, k: Int): Int {
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
            val b = byteAt(from, k + j).toInt()
            commonPrefix[j] = if (b == -1) -1 else (b and 0xff)
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
                val b = byteAt(i, k + j).toInt()
                val ub = if (b == -1) -1 else (b and 0xff)
                if (ub != commonPrefix[j]) {
                    commonPrefixLength = j
                    if (commonPrefixLength == 0) { // we have no common prefix
                        histogram[commonPrefix[0] + 1] = i - from
                        histogram[ub + 1] = 1
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
            buildHistogram(i + 1, to, k, histogram)
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
    private fun buildHistogram(from: Int, to: Int, k: Int, histogram: IntArray) {
        for (i in from..<to) {
            histogram[getBucket(i, k)]++
        }
    }

    /**
     * Reorder elements so that all of them that fall into `bucket` are between offsets `bucketFrom` and `bucketTo`.
     */
    private fun partition(from: Int, to: Int, bucket: Int, bucketFrom: Int, bucketTo: Int, d: Int) {
        var left = from
        var right = to - 1

        var slot = bucketFrom

        while (true) {
            var leftBucket = getBucket(left, d)
            var rightBucket = getBucket(right, d)

            while (leftBucket <= bucket && left < bucketFrom) {
                if (leftBucket == bucket) {
                    swap(left, slot++)
                } else {
                    ++left
                }
                leftBucket = getBucket(left, d)
            }

            while (rightBucket >= bucket && right >= bucketTo) {
                if (rightBucket == bucket) {
                    swap(right, slot++)
                } else {
                    --right
                }
                rightBucket = getBucket(right, d)
            }

            if (left < bucketFrom && right >= bucketTo) {
                swap(left++, right--)
            } else {
                require(left == bucketFrom)
                require(right == bucketTo - 1)
                break
            }
        }
    }

    companion object {
        // after that many levels of recursion we fall back to introselect anyway
        // this is used as a protection against the fact that radix sort performs
        // worse when there are long common prefixes (probably because of cache
        // locality)
        private const val LEVEL_THRESHOLD = 8

        // size of histograms: 256 + 1 to indicate that the string is finished
        private const val HISTOGRAM_SIZE = 257

        // buckets below this size will be sorted with introselect
        private const val LENGTH_THRESHOLD = 100
    }
}
