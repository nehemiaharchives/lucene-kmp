package org.gnit.lucenekmp.util

import kotlin.random.Random


/**
 * Adaptive selection algorithm based on the introspective quick select algorithm. The quick select
 * algorithm uses an interpolation variant of Tukey's ninther median-of-medians for pivot, and
 * Bentley-McIlroy 3-way partitioning. For the introspective protection, it shuffles the sub-range
 * if the max recursive depth is exceeded.
 *
 *
 * This selection algorithm is fast on most data shapes, especially on nearly sorted data, or
 * when k is close to the boundaries. It runs in linear time on average.
 *
 * @lucene.internal
 */
abstract class IntroSelector : Selector() {
    // This selector is used repeatedly by the radix selector for sub-ranges of less than
    // 100 entries. This means this selector is also optimized to be fast on small ranges.
    // It uses the variant of medians-of-medians and 3-way partitioning, and finishes the
    // last tiny range (3 entries or less) with a very specialized sort.
    private var random: Random? = null

    override fun select(from: Int, to: Int, k: Int) {
        checkArgs(from, to, k)
        select(from, to, k, 2 * MathUtil.log(to.toLong() - from, 2))
    }

    // Visible for testing.
    fun select(from: Int, to: Int, k: Int, maxDepth: Int) {
        // This code is inspired from IntroSorter#sort, adapted to loop on a single partition.

        // For efficiency, we must enter the loop with at least 4 entries to be able to skip
        // some boundary tests during the 3-way partitioning.

        var from = from
        var to = to
        var maxDepth = maxDepth
        var size: Int
        while (((to - from).also { size = it }) > 3) {
            if (--maxDepth == -1) {
                // Max recursion depth exceeded: shuffle (only once) and continue.
                shuffle(from, to)
            }

            // Pivot selection based on medians.
            val last = to - 1
            val mid = (from + last) ushr 1
            val pivot: Int
            if (size <= IntroSorter.SINGLE_MEDIAN_THRESHOLD) {
                // Select the pivot with a single median around the middle element.
                // Do not take the median between [from, mid, last] because it hurts performance
                // if the order is descending in conjunction with the 3-way partitioning.
                val range = size shr 2
                pivot = median(mid - range, mid, mid + range)
            } else {
                // Select the pivot with a variant of the Tukey's ninther median of medians.
                // If k is close to the boundaries, select either the lowest or highest median (this variant
                // is inspired from the interpolation search).
                val range = size shr 3
                val doubleRange = range shl 1
                val medianFirst = median(from, from + range, from + doubleRange)
                val medianMiddle = median(mid - range, mid, mid + range)
                val medianLast = median(last - doubleRange, last - range, last)
                pivot = if (k - from < range) {
                    // k is close to 'from': select the lowest median.
                    min(medianFirst, medianMiddle, medianLast)
                } else if (to - k <= range) {
                    // k is close to 'to': select the highest median.
                    max(medianFirst, medianMiddle, medianLast)
                } else {
                    // Otherwise select the median of medians.
                    median(medianFirst, medianMiddle, medianLast)
                }
            }

            // Bentley-McIlroy 3-way partitioning.
            setPivot(pivot)
            swap(from, pivot)
            var i = from
            var j = to
            var p = from + 1
            var q = last
            while (true) {
                var leftCmp: Int
                var rightCmp: Int
                while ((comparePivot(++i).also { leftCmp = it }) > 0) {
                }
                while ((comparePivot(--j).also { rightCmp = it }) < 0) {
                }
                if (i >= j) {
                    if (i == j && rightCmp == 0) {
                        swap(i, p)
                    }
                    break
                }
                swap(i, j)
                if (rightCmp == 0) {
                    swap(i, p++)
                }
                if (leftCmp == 0) {
                    swap(j, q--)
                }
            }
            i = j + 1
            run {
                var l = from
                while (l < p) {
                    swap(l++, j--)
                }
            }
            var l = last
            while (l > q) {
                swap(l--, i++)
            }

            // Select the partition containing the k-th element.
            if (k <= j) {
                to = j + 1
            } else if (k >= i) {
                from = i
            } else {
                return
            }
        }

        // Sort the final tiny range (3 entries or less) with a very specialized sort.
        when (size) {
            2 -> if (compare(from, from + 1) > 0) {
                swap(from, from + 1)
            }

            3 -> sort3(from)
        }
    }

    /** Returns the index of the min element among three elements at provided indices.  */
    private fun min(i: Int, j: Int, k: Int): Int {
        if (compare(i, j) <= 0) {
            return if (compare(i, k) <= 0) i else k
        }
        return if (compare(j, k) <= 0) j else k
    }

    /** Returns the index of the max element among three elements at provided indices.  */
    private fun max(i: Int, j: Int, k: Int): Int {
        if (compare(i, j) <= 0) {
            return if (compare(j, k) < 0) k else j
        }
        return if (compare(i, k) < 0) k else i
    }

    /** Copy of `IntroSorter#median`.  */
    private fun median(i: Int, j: Int, k: Int): Int {
        if (compare(i, j) < 0) {
            if (compare(j, k) <= 0) {
                return j
            }
            return if (compare(i, k) < 0) k else i
        }
        if (compare(j, k) >= 0) {
            return j
        }
        return if (compare(i, k) < 0) i else k
    }

    /**
     * Sorts 3 entries starting at from (inclusive). This specialized method is more efficient than
     * calling [Sorter.insertionSort].
     */
    private fun sort3(from: Int) {
        val mid = from + 1
        val last = from + 2
        if (compare(from, mid) <= 0) {
            if (compare(mid, last) > 0) {
                swap(mid, last)
                if (compare(from, mid) > 0) {
                    swap(from, mid)
                }
            }
        } else if (compare(mid, last) >= 0) {
            swap(from, last)
        } else {
            swap(from, mid)
            if (compare(mid, last) > 0) {
                swap(mid, last)
            }
        }
    }

    /**
     * Shuffles the entries between from (inclusive) and to (exclusive) with Durstenfeld's algorithm.
     */
    private fun shuffle(from: Int, to: Int) {
        if (this.random == null) {
            this.random = Random
        }
        val random: Random = this.random!!
        for (i in to - 1 downTo from + 1) {
            swap(i, random.nextInt(from, i + 1))
        }
    }

    /**
     * Save the value at slot `i` so that it can later be used as a pivot, see [ ][.comparePivot].
     */
    protected abstract fun setPivot(i: Int)

    /**
     * Compare the pivot with the slot at `j`, similarly to [ compare(i, j)][.compare].
     */
    protected abstract fun comparePivot(j: Int): Int

    /**
     * Compare entries found in slots `i` and `j`. The contract for the returned
     * value is the same as [Comparator.compare].
     */
    protected open fun compare(i: Int, j: Int): Int {
        setPivot(i)
        return comparePivot(j)
    }
}
