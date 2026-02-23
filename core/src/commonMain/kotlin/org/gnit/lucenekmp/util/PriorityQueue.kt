package org.gnit.lucenekmp.util

expect abstract class PriorityQueue<T>(
    maxSize: Int,
    sentinelObjectSupplier: () -> T? = { null }
) : Iterable<T> {
    abstract fun lessThan(a: T, b: T): Boolean

    fun addAll(elements: MutableCollection<T>)

    fun add(element: T): T?

    fun insertWithOverflow(element: T): T?

    fun top(): T

    fun topOrNull(): T?

    fun pop(): T?

    fun updateTop(): T

    fun updateTop(newTop: T): T

    fun size(): Int

    fun clear()

    fun remove(element: T): Boolean

    protected val heapArray: Array<Any?>

    override fun iterator(): Iterator<T>
}
