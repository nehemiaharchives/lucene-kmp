package org.gnit.lucenekmp.util

/**
 * An [IntroSorter] for object arrays.
 *
 * @lucene.internal
 */
internal class ArrayIntroSorter<T>(private val arr: Array<T>, comparator: Comparator<in T>) : IntroSorter() {
    private val comparator: Comparator<in T> = comparator
    private var pivot: T?

    /** Create a new [ArrayInPlaceMergeSorter].  */
    init {
        pivot = null
    }

    protected override fun compare(i: Int, j: Int): Int {
        return comparator.compare(arr[i], arr[j])
    }

    protected override fun swap(i: Int, j: Int) {
        ArrayUtil.swap(arr, i, j)
    }

    protected override fun setPivot(i: Int) {
        pivot = arr[i]
    }

    protected override fun comparePivot(i: Int): Int {
        return comparator.compare(pivot!!, arr[i])
    }
}