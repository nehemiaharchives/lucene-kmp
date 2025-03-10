package org.gnit.lucenekmp.util

/**
 * A [TimSorter] for object arrays.
 *
 * @lucene.internal
 */
internal class ArrayTimSorter<T>(private val arr: Array<T>, comparator: Comparator<in T>, maxTempSlots: Int) :
    TimSorter(maxTempSlots) {
    private val comparator: Comparator<in T> = comparator
    private val tmp: Array<T>?

    /** Create a new [ArrayTimSorter].  */
    init {
        if (maxTempSlots > 0) {
            val tmp = arrayOfNulls<Any>(maxTempSlots) as Array<T>
            this.tmp = tmp
        } else {
            this.tmp = null
        }
    }

    override fun compare(i: Int, j: Int): Int {
        return comparator.compare(arr[i], arr[j])
    }

    override fun swap(i: Int, j: Int) {
        ArrayUtil.swap(arr, i, j)
    }

    override fun copy(src: Int, dest: Int) {
        arr[dest] = arr[src]
    }

    override fun save(start: Int, len: Int) {
        /*java.lang.System.arraycopy(arr, start, tmp, 0, len)*/
        arr.copyInto(tmp!!, 0, start, start + len)
    }

    override fun restore(src: Int, dest: Int) {
        arr[dest] = tmp!![src]
    }

    override fun compareSaved(i: Int, j: Int): Int {
        return comparator.compare(tmp!![i], arr[j])
    }
}
