package org.gnit.lucenekmp.util.hnsw


/**
 * A bounded min heap that stores floats. The top element is the lowest value of the heap.
 *
 *
 * A primitive priority queue that maintains a partial ordering of its elements such that the
 * least element can always be found in constant time. Implementation is based on [ ]
 *
 * @lucene.internal
 */
class FloatHeap(private val maxSize: Int) {
    private val heap: FloatArray = FloatArray(maxSize + 1)
    private var size = 0

    /**
     * Inserts a value into this heap.
     *
     *
     * If the number of values would exceed the heap's maxSize, the least value is discarded
     *
     * @param value the value to add
     * @return whether the value was added (unless the heap is full, or the new value is less than the
     * top value)
     */
    fun offer(value: Float): Boolean {
        if (size >= maxSize) {
            if (value < heap[1]) {
                return false
            }
            updateTop(value)
            return true
        }
        push(value)
        return true
    }

    fun getHeap(): FloatArray {
        val result = FloatArray(size)
        /*java.lang.System.arraycopy(this.heap, 1, result, 0, size)*/
        this.heap.copyInto(
            destination = result,
            destinationOffset = 0,
            startIndex = 1,
            endIndex = size + 1
        )
        return result
    }

    /**
     * Removes and returns the head of the heap
     *
     * @return the head of the heap, the smallest value
     * @throws IllegalStateException if the heap is empty
     */
    fun poll(): Float {
        if (size > 0) {
            val result: Float = heap[1] // save first value
            heap[1] = heap[size] // move last to first
            size--
            downHeap(1) // adjust heap
            return result
        } else {
            throw IllegalStateException("The heap is empty")
        }
    }

    /**
     * Retrieves, but does not remove, the head of this heap.
     *
     * @return the head of the heap, the smallest value
     */
    fun peek(): Float {
        return heap[1]
    }

    /**
     * Returns the number of elements in this heap.
     *
     * @return the number of elements in this heap
     */
    fun size(): Int {
        return size
    }

    fun clear() {
        size = 0
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
