package org.gnit.lucenekmp.jdkport

inline fun<reified E> MutableList<E>.sort(Comparator: Comparator<E>) {
    val size = this.size
    if (size < 2) return

    val array = this.toTypedArray()
    array.sortWith(Comparator)
    for (i in 0 until size) {
        this[i] = array[i]
    }
}
