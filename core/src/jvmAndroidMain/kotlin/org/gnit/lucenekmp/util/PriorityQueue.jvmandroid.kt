package org.gnit.lucenekmp.util

import okio.ArrayIndexOutOfBoundsException
import kotlin.jvm.JvmOverloads

actual abstract class PriorityQueue<T> @JvmOverloads actual constructor(
    maxSize: Int,
    sentinelObjectSupplier: () -> T?
) : Iterable<T> {
    private var size = 0
    private val maxSize: Int
    private val heap: Array<Any?>

    @Suppress("UNCHECKED_CAST")
    private fun element(i: Int): T? {
        return heap[i] as T?
    }

    @Suppress("UNCHECKED_CAST")
    private fun elementNotNull(i: Int): T {
        return heap[i] as T
    }

    private fun setElement(i: Int, value: T?) {
        heap[i] = value
    }

    init {
        val heapSize: Int

        if (0 == maxSize) {
            heapSize = 2
        } else {
            require(!((maxSize < 0) || (maxSize >= ArrayUtil.MAX_ARRAY_LENGTH))) {
                "maxSize must be >= 0 and < " + (ArrayUtil.MAX_ARRAY_LENGTH) + "; got: " + maxSize
            }
            heapSize = maxSize + 1
        }

        val h = kotlin.arrayOfNulls<Any>(heapSize)
        this.heap = h
        this.maxSize = maxSize

        val sentinel: T? = sentinelObjectSupplier()
        if (sentinel != null) {
            setElement(1, sentinel)
            for (i in 2..<heap.size) {
                setElement(i, sentinelObjectSupplier()!!)
            }
            size = maxSize
        }
    }

    actual abstract fun lessThan(a: T, b: T): Boolean

    actual fun addAll(elements: MutableCollection<T>) {
        if (this.size + elements.size > this.maxSize) {
            throw ArrayIndexOutOfBoundsException(
                ("Cannot add "
                        + elements.size
                        + " elements to a queue with remaining capacity: "
                        + (maxSize - size))
            )
        }

        val iterator = elements.iterator()
        while (iterator.hasNext()) {
            setElement(size + 1, iterator.next())
            this.size++
        }

        for (i in (size ushr 1) downTo 1) {
            downHeap(i)
        }
    }

    actual fun add(element: T): T? {
        val index = size + 1
        setElement(index, element)
        size = index
        upHeap(index)
        return element(1)
    }

    actual fun insertWithOverflow(element: T): T? {
        if (size < maxSize) {
            add(element)
            return null
        } else if (size > 0 && lessThan(elementNotNull(1), element)) {
            val ret = element(1)
            setElement(1, element)
            updateTop()
            return ret
        } else {
            return element
        }
    }

    actual fun top(): T {
        return elementNotNull(1)
    }

    actual fun topOrNull(): T? {
        return element(1)
    }

    actual fun pop(): T? {
        if (size > 0) {
            val result = element(1)
            setElement(1, element(size))
            setElement(size, null)
            size--
            downHeap(1)
            return result
        } else {
            return null
        }
    }

    actual fun updateTop(): T {
        downHeap(1)
        return elementNotNull(1)
    }

    actual fun updateTop(newTop: T): T {
        setElement(1, newTop)
        return updateTop()
    }

    actual fun size(): Int {
        return size
    }

    actual fun clear() {
        for (i in 0..size) {
            setElement(i, null)
        }
        size = 0
    }

    actual fun remove(element: T): Boolean {
        for (i in 1..size) {
            if (element(i) == element) {
                setElement(i, element(size))
                setElement(size, null)
                size--
                if (i <= size) {
                    if (!upHeap(i)) {
                        downHeap(i)
                    }
                }
                return true
            }
        }
        return false
    }

    private fun upHeap(origPos: Int): Boolean {
        var i = origPos
        val node = elementNotNull(i)
        var j = i ushr 1
        while (j > 0 && lessThan(node, elementNotNull(j))) {
            setElement(i, elementNotNull(j))
            i = j
            j = j ushr 1
        }
        setElement(i, node)
        return i != origPos
    }

    private fun downHeap(i: Int) {
        var current = i
        val node = element(current) ?: return
        var child = current shl 1
        var right = child + 1
        if (right <= size && lessThan(elementNotNull(right), elementNotNull(child))) {
            child = right
        }
        while (child <= size && lessThan(elementNotNull(child), node)) {
            setElement(current, elementNotNull(child))
            current = child
            child = current shl 1
            right = child + 1
            if (right <= size && lessThan(elementNotNull(right), elementNotNull(child))) {
                child = right
            }
        }
        setElement(current, node)
    }

    protected actual val heapArray: Array<Any?>
        get() = heap

    actual override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            var i: Int = 1

            override fun hasNext(): Boolean {
                return i <= size
            }

            override fun next(): T {
                if (hasNext() == false) {
                    throw NoSuchElementException()
                }
                return elementNotNull(i++)
            }
        }
    }
}
