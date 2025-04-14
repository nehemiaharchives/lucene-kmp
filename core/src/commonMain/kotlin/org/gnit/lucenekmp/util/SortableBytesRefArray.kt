package org.gnit.lucenekmp.util


internal interface SortableBytesRefArray {
    /** Append a new value  */
    fun append(bytes: BytesRef): Int

    /** Clear all previously stored values  */
    fun clear()

    /** Returns the number of values appended so far  */
    fun size(): Int

    /** Sort all values by the provided comparator and return an iterator over the sorted values  */
    fun iterator(comp: Comparator<BytesRef>): BytesRefIterator
}
