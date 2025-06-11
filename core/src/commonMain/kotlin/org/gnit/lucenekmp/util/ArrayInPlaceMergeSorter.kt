package org.gnit.lucenekmp.util

/**
 * An [InPlaceMergeSorter] for object arrays.
 *
 * @lucene.internal
 */
internal class ArrayInPlaceMergeSorter<T>(
    private val arr: Array<T>,
    private val comparator: Comparator<in T>
) : InPlaceMergeSorter() {
    /** Create a new [ArrayInPlaceMergeSorter]. */
    override fun compare(i: Int, j: Int): Int {
        return comparator.compare(arr[i], arr[j])
    }

    override fun swap(i: Int, j: Int) {
        ArrayUtil.swap(arr, i, j)
    }
}
