package org.gnit.lucenekmp.util.hnsw

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A blocking bounded min heap that stores floats. The top element is the lowest value of the heap.
 *
 *
 * A primitive priority queue that maintains a partial ordering of its elements such that the
 * least element can always be found in constant time. Implementation is based on [org.gnit.lucenekmp.util.LongHeap]
 *
 * @lucene.internal
 */
class BlockingFloatHeap(private val maxSize: Int) {
    private val heap: FloatArray = FloatArray(maxSize + 1)
    private val lock = Mutex()
    private var size = 0

    /**
     * Inserts a value into this heap.
     *
     *
     * If the number of values would exceed the heap's maxSize, the least value is discarded
     *
     * @param value the value to add
     * @return the new 'top' element in the queue.
     */
    suspend fun offer(value: Float): Float {
        return lock.withLock {
            if (size < maxSize) {
                push(value)
                heap[1]
            } else {
                if (value >= heap[1]) {
                    updateTop(value)
                }
                heap[1]
            }
        }
    }

    /**
     * Inserts array of values into this heap.
     *
     *
     * Values must be sorted in ascending order.
     *
     * @param values a set of values to insert, must be sorted in ascending order
     * @param len number of values from the `values` array to insert
     * @return the new 'top' element in the queue.
     */
    suspend fun offer(values: FloatArray, len: Int): Float {
        require(len >= 0) { "len cannot be negative" }
        require(len <= values.size) { "len cannot be greater than values.size" }

        return lock.withLock {
            for (i in len - 1 downTo 0) {
                if (size < maxSize) {
                    push(values[i])
                } else {
                    if (values[i] >= heap[1]) {
                        updateTop(values[i])
                    } else {
                        break
                    }
                }
            }
            heap[1]
        }
    }

    /**
     * Removes and returns the head of the heap
     *
     * @return the head of the heap, the smallest value
     * @throws IllegalStateException if the heap is empty
     */
    suspend fun poll(): Float {
        return lock.withLock {
            if (size > 0) {
                var result: Float = heap[1] // save first value
                heap[1] = heap[size] // move last to first
                size--

                if (size > 0){
                    downHeap(1) // adjust heap
                }
                result
            } else {
                throw IllegalStateException("The heap is empty")
            }
        }
    }

    /**
     * Retrieves, but does not remove, the head of this heap.
     *
     * @return the head of the heap, the smallest value
     */
    suspend fun peek(): Float {
        return lock.withLock {
            if (size > 0) {
                heap[1]
            } else {
                throw IllegalStateException("The heap is empty")
            }
        }
    }

    /**
     * Returns the number of elements in this heap.
     *
     * @return the number of elements in this heap
     */
    suspend fun size(): Int {
        return lock.withLock {
            size
        }
    }

    private fun push(element: Float) {
        size++
        heap[size] = element
        upHeap(size)
    }

    private fun updateTop(value: Float): Float {
        heap[1] = value
        downHeap(1)
        return heap[1]
    }

    private fun downHeap(i: Int) {
        var i = i
        val value = heap[i] // save top value
        var j = i shl 1 // find smaller child
        var k = j + 1
        if (k <= size && heap[k] < heap[j]) {
            j = k
        }
        while (j <= size && heap[j] < value) {
            heap[i] = heap[j] // shift up child
            i = j
            j = i shl 1
            k = j + 1
            if (k <= size && heap[k] < heap[j]) {
                j = k
            }
        }
        heap[i] = value // install saved value
    }

    private fun upHeap(origPos: Int) {
        var i = origPos
        val value = heap[i] // save bottom value
        var j = i ushr 1
        while (j > 0 && value < heap[j]) {
            heap[i] = heap[j] // shift parents down
            i = j
            j = j ushr 1
        }
        heap[i] = value // install saved value
    }
}
