package org.gnit.lucenekmp.util

import kotlin.jvm.JvmOverloads


/**
 * Base class for sorting algorithms implementations.
 *
 *
 * There are a number of subclasses to choose from that vary in performance and [stability](https://en.wikipedia.org/wiki/Sorting_algorithm#Stability). We suggest that
 * you pick the first from this ranked list that meets your requirements:
 *
 *
 *  1. [MSBRadixSorter] for strings (array of bytes/chars). Not a stable sort.
 *  1. [StableMSBRadixSorter] for strings (array of bytes/chars). Stable sort.
 *  1. [IntroSorter]. Not a stable sort.
 *  1. [InPlaceMergeSorter]. When the data to sort is typically small. Stable sort.
 *  1. [TimSorter]. Stable sort.
 *
 *
 * @lucene.internal
 */
abstract class Sorter
/** Sole constructor, used for inheritance.  */
protected constructor() {
    /**
     * Compare entries found in slots `i` and `j`. The contract for the returned
     * value is the same as [Comparator.compare].
     */
    protected abstract fun compare(i: Int, j: Int): Int

    /** Swap values at slots `i` and `j`.  */
    protected abstract fun swap(i: Int, j: Int)

    private var pivotIndex = 0

    /**
     * Save the value at slot `i` so that it can later be used as a pivot, see [ ][.comparePivot].
     */
    protected open fun setPivot(i: Int) {
        pivotIndex = i
    }

    /**
     * Compare the pivot with the slot at `j`, similarly to [ compare(i, j)][.compare].
     */
    protected open fun comparePivot(j: Int): Int {
        return compare(pivotIndex, j)
    }

    /**
     * Sort the slice which starts at `from` (inclusive) and ends at `to`
     * (exclusive).
     */
    abstract fun sort(from: Int, to: Int)

    fun checkRange(from: Int, to: Int) {
        require(to >= from) { "'to' must be >= 'from', got from=$from and to=$to" }
    }

    fun mergeInPlace(from: Int, mid: Int, to: Int) {
        var from = from
        var to = to
        if (from == mid || mid == to || compare(mid - 1, mid) <= 0) {
            return
        } else if (to - from == 2) {
            swap(mid - 1, mid)
            return
        }
        while (compare(from, mid) <= 0) {
            ++from
        }
        while (compare(mid - 1, to - 1) <= 0) {
            --to
        }
        val first_cut: Int
        val second_cut: Int
        val len11: Int
        val len22: Int
        if (mid - from > to - mid) {
            len11 = (mid - from) ushr 1
            first_cut = from + len11
            second_cut = lower(mid, to, first_cut)
            len22 = second_cut - mid
        } else {
            len22 = (to - mid) ushr 1
            second_cut = mid + len22
            first_cut = upper(from, mid, second_cut)
            len11 = first_cut - from
        }
        rotate(first_cut, mid, second_cut)
        val new_mid = first_cut + len22
        mergeInPlace(from, first_cut, new_mid)
        mergeInPlace(new_mid, second_cut, to)
    }

    fun lower(from: Int, to: Int, `val`: Int): Int {
        var from = from
        var len = to - from
        while (len > 0) {
            val half = len ushr 1
            val mid = from + half
            if (compare(mid, `val`) < 0) {
                from = mid + 1
                len = len - half - 1
            } else {
                len = half
            }
        }
        return from
    }

    fun upper(from: Int, to: Int, `val`: Int): Int {
        var from = from
        var len = to - from
        while (len > 0) {
            val half = len ushr 1
            val mid = from + half
            if (compare(`val`, mid) < 0) {
                len = half
            } else {
                from = mid + 1
                len = len - half - 1
            }
        }
        return from
    }

    // faster than lower when val is at the end of [from:to[
    fun lower2(from: Int, to: Int, `val`: Int): Int {
        var f = to - 1
        var t = to
        while (f > from) {
            if (compare(f, `val`) < 0) {
                return lower(f, t, `val`)
            }
            val delta = t - f
            t = f
            f -= delta shl 1
        }
        return lower(from, t, `val`)
    }

    // faster than upper when val is at the beginning of [from:to[
    fun upper2(from: Int, to: Int, `val`: Int): Int {
        var f = from
        var t = f + 1
        while (t < to) {
            if (compare(t, `val`) > 0) {
                return upper(f, t, `val`)
            }
            val delta = t - f
            f = t
            t += delta shl 1
        }
        return upper(f, to, `val`)
    }

    fun reverse(from: Int, to: Int) {
        var from = from
        var to = to
        --to
        while (from < to) {
            swap(from, to)
            ++from
            --to
        }
    }

    fun rotate(lo: Int, mid: Int, hi: Int) {
        require(lo <= mid && mid <= hi)
        if (lo == mid || mid == hi) {
            return
        }
        doRotate(lo, mid, hi)
    }

    open fun doRotate(lo: Int, mid: Int, hi: Int) {
        var lo = lo
        var mid = mid
        if (mid - lo == hi - mid) {
            // happens rarely but saves n/2 swaps
            while (mid < hi) {
                swap(lo++, mid++)
            }
        } else {
            reverse(lo, mid)
            reverse(mid, hi)
            reverse(lo, hi)
        }
    }

    /**
     * A binary sort implementation. This performs `O(n*log(n))` comparisons and `O(n^2)`
     * swaps. It is typically used by more sophisticated implementations as a fall-back when the
     * number of items to sort has become less than {@value #BINARY_SORT_THRESHOLD}. This algorithm is
     * stable.
     */
    @JvmOverloads
    fun binarySort(from: Int, to: Int, i: Int = from + 1) {
        var i = i
        while (i < to) {
            setPivot(i)
            var l = from
            var h = i - 1
            while (l <= h) {
                val mid = (l + h) ushr 1
                val cmp = comparePivot(mid)
                if (cmp < 0) {
                    h = mid - 1
                } else {
                    l = mid + 1
                }
            }
            for (j in i downTo l + 1) {
                swap(j - 1, j)
            }
            ++i
        }
    }

    /**
     * Sorts between from (inclusive) and to (exclusive) with insertion sort. Runs in `O(n^2)`.
     * It is typically used by more sophisticated implementations as a fall-back when the number of
     * items to sort becomes less than {@value #INSERTION_SORT_THRESHOLD}. This algorithm is stable.
     */
    fun insertionSort(from: Int, to: Int) {
        var i = from + 1
        while (i < to) {
            var current = i++
            var previous: Int
            while (compare(((current - 1).also { previous = it }), current) > 0) {
                swap(previous, current)
                if (previous == from) {
                    break
                }
                current = previous
            }
        }
    }

    /**
     * Use heap sort to sort items between `from` inclusive and `to` exclusive. This runs
     * in `O(n*log(n))` and is used as a fall-back by [IntroSorter]. This algorithm is NOT
     * stable.
     */
    fun heapSort(from: Int, to: Int) {
        if (to - from <= 1) {
            return
        }
        heapify(from, to)
        for (end in to - 1 downTo from + 1) {
            swap(from, end)
            siftDown(from, from, end)
        }
    }

    fun heapify(from: Int, to: Int) {
        for (i in heapParent(from, to - 1) downTo from) {
            siftDown(i, from, to)
        }
    }

    fun siftDown(i: Int, from: Int, to: Int) {
        var i = i
        var leftChild = heapChild(from, i)
        while (leftChild < to) {
            val rightChild = leftChild + 1
            if (compare(i, leftChild) < 0) {
                if (rightChild < to && compare(leftChild, rightChild) < 0) {
                    swap(i, rightChild)
                    i = rightChild
                } else {
                    swap(i, leftChild)
                    i = leftChild
                }
            } else if (rightChild < to && compare(i, rightChild) < 0) {
                swap(i, rightChild)
                i = rightChild
            } else {
                break
            }
            leftChild = heapChild(from, i)
        }
    }

    companion object {
        const val BINARY_SORT_THRESHOLD: Int = 20

        /** Below this size threshold, the sub-range is sorted using Insertion sort.  */
        const val INSERTION_SORT_THRESHOLD: Int = 16

        fun heapParent(from: Int, i: Int): Int {
            return ((i - 1 - from) ushr 1) + from
        }

        fun heapChild(from: Int, i: Int): Int {
            return ((i - from) shl 1) + 1 + from
        }
    }
}
