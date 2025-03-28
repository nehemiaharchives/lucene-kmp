package org.gnit.lucenekmp.util

/**
 * [Sorter] implementation based on the merge-sort algorithm that merges in place (no extra
 * memory will be allocated). Small arrays are sorted with binary sort.
 *
 *
 * This algorithm is stable. It's especially suited to sorting small lists where we'd rather
 * optimize for avoiding allocating memory for this task. It performs well on lists that are already
 * sorted.
 *
 * @lucene.internal
 */
abstract class InPlaceMergeSorter
/** Create a new [InPlaceMergeSorter]  */
    : Sorter() {
    override fun sort(from: Int, to: Int) {
        checkRange(from, to)
        mergeSort(from, to)
    }

    fun mergeSort(from: Int, to: Int) {
        if (to - from < BINARY_SORT_THRESHOLD) {
            binarySort(from, to)
        } else {
            val mid = (from + to) ushr 1
            mergeSort(from, mid)
            mergeSort(mid, to)
            mergeInPlace(from, mid, to)
        }
    }
}
