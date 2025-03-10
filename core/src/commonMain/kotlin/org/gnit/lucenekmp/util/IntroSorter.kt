package org.gnit.lucenekmp.util


/**
 * [Sorter] implementation based on a variant of the quicksort algorithm called [introsort](http://en.wikipedia.org/wiki/Introsort): when the recursion level exceeds the
 * log of the length of the array to sort, it falls back to heapsort. This prevents quicksort from
 * running into its worst-case quadratic runtime. Selects the pivot using Tukey's ninther
 * median-of-medians, and partitions using Bentley-McIlroy 3-way partitioning. Small ranges are
 * sorted with insertion sort.
 *
 *
 * This algorithm is **NOT** stable. It's fast on most data shapes, especially with low
 * cardinality. If the data to sort is known to be strictly ascending or descending, prefer [ ].
 *
 * @lucene.internal
 */
abstract class IntroSorter
/** Create a new [IntroSorter].  */
    : Sorter() {
    override fun sort(from: Int, to: Int) {
        checkRange(from, to)
        sort(from, to, 2 * MathUtil.log(to.toLong() - from, 2))
    }

    /**
     * Sorts between from (inclusive) and to (exclusive) with intro sort.
     *
     *
     * Sorts small ranges with insertion sort. Fallbacks to heap sort to avoid quadratic worst
     * case. Selects the pivot with medians and partitions with the Bentley-McIlroy fast 3-ways
     * algorithm (Engineering a Sort Function, Bentley-McIlroy).
     */
    fun sort(from: Int, to: Int, maxDepth: Int) {
        // Sort small ranges with insertion sort.
        var from = from
        var to = to
        var maxDepth = maxDepth
        var size: Int
        while (((to - from).also { size = it }) > INSERTION_SORT_THRESHOLD) {
            if (--maxDepth < 0) {
                // Max recursion depth exceeded: fallback to heap sort.
                heapSort(from, to)
                return
            }

            // Pivot selection based on medians.
            val last = to - 1
            val mid = (from + last) ushr 1
            val pivot: Int
            if (size <= SINGLE_MEDIAN_THRESHOLD) {
                // Select the pivot with a single median around the middle element.
                // Do not take the median between [from, mid, last] because it hurts performance
                // if the order is descending in conjunction with the 3-way partitioning.
                val range = size shr 2
                pivot = median(mid - range, mid, mid + range)
            } else {
                // Select the pivot with the Tukey's ninther median of medians.
                val range = size shr 3
                val doubleRange = range shl 1
                val medianFirst = median(from, from + range, from + doubleRange)
                val medianMiddle = median(mid - range, mid, mid + range)
                val medianLast = median(last - doubleRange, last - range, last)
                pivot = median(medianFirst, medianMiddle, medianLast)
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
                var k = from
                while (k < p) {
                    swap(k++, j--)
                }
            }
            var k = last
            while (k > q) {
                swap(k--, i++)
            }

            // Recursion on the smallest partition. Replace the tail recursion by a loop.
            if (j - from < last - i) {
                sort(from, j + 1, maxDepth)
                from = i
            } else {
                sort(i, to, maxDepth)
                to = j + 1
            }
        }

        insertionSort(from, to)
    }

    /** Returns the index of the median element among three elements at provided indices.  */
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

    // Don't rely on the slow default impl of setPivot/comparePivot since
    // quicksort relies on these methods to be fast for good performance
    protected abstract override fun setPivot(i: Int)

    protected abstract override fun comparePivot(j: Int): Int

    protected override fun compare(i: Int, j: Int): Int {
        setPivot(i)
        return comparePivot(j)
    }

    companion object {
        /** Below this size threshold, the partition selection is simplified to a single median.  */
        const val SINGLE_MEDIAN_THRESHOLD: Int = 40
    }
}