package org.gnit.lucenekmp.util

import okio.ArrayIndexOutOfBoundsException

actual abstract class PriorityQueue<T> actual constructor(
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
        if (size <= 0) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val result = heap[1] as T?
        heap[1] = heap[size]
        heap[size] = null
        size--
        if (size > 0) {
            downHeap(1)
        }
        return result
    }

    actual fun updateTop(): T {
        if (size > 0) {
            downHeap(1)
        }
        @Suppress("UNCHECKED_CAST")
        return heap[1] as T
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
        val localHeap = heap
        var index = origPos
        @Suppress("UNCHECKED_CAST")
        val node = localHeap[index] as T
        var parent = index ushr 1
        while (parent > 0) {
            @Suppress("UNCHECKED_CAST")
            val parentValue = localHeap[parent] as T
            if (!lessThan(node, parentValue)) {
                break
            }
            localHeap[index] = parentValue
            index = parent
            parent = index ushr 1
        }
        localHeap[index] = node
        return index != origPos
    }

    private fun downHeap(i: Int) {
        val currentSize = size
        if (currentSize <= 0) {
            return
        }

        val localHeap = heap
        var index = i
        val nodeAny = localHeap[index] ?: return
        @Suppress("UNCHECKED_CAST")
        val node = nodeAny as T

        var child = index shl 1
        while (child <= currentSize) {
            var smallestChildIndex = child
            var smallestChildAny = localHeap[child]!!

            val right = child + 1
            if (right <= currentSize) {
                val rightAny = localHeap[right]!!
                @Suppress("UNCHECKED_CAST")
                val rightVal = rightAny as T
                @Suppress("UNCHECKED_CAST")
                val leftVal = smallestChildAny as T
                if (lessThan(rightVal, leftVal)) {
                    smallestChildIndex = right
                    smallestChildAny = rightAny
                }
            }

            @Suppress("UNCHECKED_CAST")
            val smallestChild = smallestChildAny as T
            if (!lessThan(smallestChild, node)) {
                break
            }

            localHeap[index] = smallestChildAny
            index = smallestChildIndex
            child = index shl 1
        }

        localHeap[index] = node
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
