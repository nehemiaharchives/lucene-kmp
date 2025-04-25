package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Collections


/**
 * Methods for manipulating (sorting) and creating collections. Sort methods work directly on the
 * supplied lists and don't copy to/from arrays before/after. For medium size collections as used in
 * the Lucene indexer that is much more efficient.
 *
 * @lucene.internal
 */
object CollectionUtil {
    /**
     * Returns a new [HashMap] sized to contain `size` items without resizing the internal
     * array.
     */
    fun <K, V> newHashMap(size: Int): MutableMap<K, V> {
        // This should be replaced with HashMap.newHashMap when lucene moves to jdk19 minimum version
        return /* not sure what it does
        java.util.HashMap<K, V>((size / 0.75f).toInt() + 1)*/ mutableMapOf()
    }

    /**
     * Returns a new [HashSet] sized to contain `size` items without resizing the internal
     * array.
     */
    fun <E> newHashSet(size: Int): MutableSet<E> {
        // This should be replaced with HashSet.newHashSet when lucene moves to jdk19 minimum version
        return /*java.util.HashSet<E>((size / 0.75f).toInt() + 1)*/ mutableSetOf()
    }

    /**
     * Sorts the given random access [List] using the [Comparator]. The list must
     * implement [RandomAccess]. This method uses the intro sort algorithm, but falls back to
     * insertion sort for small lists.
     *
     * @see IntroSorter
     *
     * @throws IllegalArgumentException if list is e.g. a linked list without random access.
     */
    fun <T> introSort(list: MutableList<T>, comp: Comparator<T>) {
        val size = list.size
        if (size <= 1) return
        ListIntroSorter<T>(list, comp).sort(0, size)
    }

    /**
     * Sorts the given random access [List] in natural order. The list must implement [ ]. This method uses the intro sort algorithm, but falls back to insertion sort for
     * small lists.
     *
     * @see IntroSorter
     *
     * @throws IllegalArgumentException if list is e.g. a linked list without random access.
     */
    fun <T : Comparable<T>> introSort(list: MutableList<T>) {
        val size = list.size
        if (size <= 1) return
        introSort<T>(list, naturalOrder<T>())
    }

    // Tim sorts:
    /**
     * Sorts the given random access [List] using the [Comparator]. The list must
     * implement [RandomAccess]. This method uses the Tim sort algorithm, but falls back to
     * binary sort for small lists.
     *
     * @see TimSorter
     *
     * @throws IllegalArgumentException if list is e.g. a linked list without random access.
     */
    fun <T> timSort(list: MutableList<T>, comp: Comparator<T>) {
        val size = list.size
        if (size <= 1) return
        ListTimSorter<T>(list, comp, list.size / 64).sort(0, size)
    }

    /**
     * Sorts the given random access [List] in natural order. The list must implement [ ]. This method uses the Tim sort algorithm, but falls back to binary sort for small
     * lists.
     *
     * @see TimSorter
     *
     * @throws IllegalArgumentException if list is e.g. a linked list without random access.
     */
    fun <T : Comparable<T>> timSort(list: MutableList<T>) {
        val size = list.size
        if (size <= 1) return
        timSort<T>(list, naturalOrder<T>())
    }

    private class ListIntroSorter<T>(list: MutableList<T>, comp: Comparator<T>) : IntroSorter() {
        var pivot: T? = null
        val list: MutableList<T>
        val comp: Comparator<in T>

        init {
            require(list is RandomAccess) { "CollectionUtil can only sort random access lists in-place." }
            this.list = list
            this.comp = comp
        }

        override fun setPivot(i: Int) {
            pivot = list[i]
        }

        override fun swap(i: Int, j: Int) {
            Collections.swap(list, i, j)
        }

        override fun compare(i: Int, j: Int): Int {
            return comp.compare(list[i], list[j])
        }

        override fun comparePivot(j: Int): Int {
            return comp.compare(pivot!!, list[j])
        }
    }

    private class ListTimSorter<T>(list: MutableList<T>, comp: Comparator<T>, maxTempSlots: Int) :
        TimSorter(maxTempSlots) {
        val list: MutableList<T>
        val comp: Comparator<T>
        val tmp: Array<T>

        init {
            require(list is RandomAccess) { "CollectionUtil can only sort random access lists in-place." }
            this.list = list
            this.comp = comp
            if (maxTempSlots > 0) {
                this.tmp = kotlin.arrayOfNulls<Any>(maxTempSlots) as Array<T>
            } else {
                this.tmp = kotlin.arrayOfNulls<Any>(0) as Array<T>
            }
        }

        override fun swap(i: Int, j: Int) {
            Collections.swap(list, i, j)
        }

        override fun copy(src: Int, dest: Int) {
            list[dest] = list[src]
        }

        override fun save(i: Int, len: Int) {
            for (j in 0..<len) {
                tmp[j] = list[i + j]
            }
        }

        override fun restore(i: Int, j: Int) {
            list[j] = tmp[i]
        }

        override fun compare(i: Int, j: Int): Int {
            return comp.compare(list.get(i), list.get(j))
        }

        override fun compareSaved(i: Int, j: Int): Int {
            return comp.compare(tmp[i], list.get(j))
        }
    }
}

