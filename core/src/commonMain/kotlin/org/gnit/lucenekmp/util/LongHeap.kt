package org.gnit.lucenekmp.util


/**
 * A min heap that stores longs; a primitive priority queue that like all priority queues maintains
 * a partial ordering of its elements such that the least element can always be found in constant
 * time. Put()'s and pop()'s require log(size). This heap provides unbounded growth via [ ][.push], and bounded-size insertion based on its nominal maxSize via [ ][.insertWithOverflow]. The heap is a min heap, meaning that the top element is the lowest
 * value of the heap.
 *
 * @lucene.internal
 */
class LongHeap(maxSize: Int) {
    private val maxSize: Int

    /**
     * This method returns the internal heap array.
     *
     * @lucene.internal
     */
    // pkg-private for testing
    var heapArray: LongArray
        private set
    private var size = 0

    /**
     * Create an empty priority queue of the configured initial size.
     *
     * @param maxSize the maximum size of the heap, or if negative, the initial size of an unbounded
     * heap
     */
    init {
        val heapSize: Int
        require(!(maxSize < 1 || maxSize >= ArrayUtil.MAX_ARRAY_LENGTH)) { "maxSize must be > 0 and < " + (ArrayUtil.MAX_ARRAY_LENGTH - 1) + "; got: " + maxSize }
        // NOTE: we add +1 because all access to heap is 1-based not 0-based.  heap[0] is unused.
        heapSize = maxSize + 1
        this.maxSize = maxSize
        this.heapArray = LongArray(heapSize)
    }

    /**
     * Adds a value in log(size) time. Grows unbounded as needed to accommodate new values.
     *
     * @return the new 'top' element in the queue.
     */
    fun push(element: Long): Long {
        size++
        if (size == heapArray.size) {
            this.heapArray = ArrayUtil.grow(this.heapArray, (size * 3 + 1) / 2)
        }
        this.heapArray[size] = element
        upHeap(size)
        return this.heapArray[1]
    }

    /**
     * Adds a value to an LongHeap in log(size) time. If the number of values would exceed the heap's
     * maxSize, the least value is discarded.
     *
     * @return whether the value was added (unless the heap is full, or the new value is less than the
     * top value)
     */
    fun insertWithOverflow(value: Long): Boolean {
        if (size >= maxSize) {
            if (value < this.heapArray[1]) {
                return false
            }
            updateTop(value)
            return true
        }
        push(value)
        return true
    }

    /**
     * Returns the least element of the LongHeap in constant time. It is up to the caller to verify
     * that the heap is not empty; no checking is done, and if no elements have been added, 0 is
     * returned.
     */
    fun top(): Long {
        return this.heapArray[1]
    }

    /**
     * Removes and returns the least element of the PriorityQueue in log(size) time.
     *
     * @throws IllegalStateException if the LongHeap is empty.
     */
    fun pop(): Long {
        if (size > 0) {
            val result = this.heapArray[1] // save first value
            this.heapArray[1] = this.heapArray[size] // move last to first
            size--
            downHeap(1) // adjust heap
            return result
        } else {
            throw IllegalStateException("The heap is empty")
        }
    }

    /**
     * Replace the top of the pq with `newTop`. Should be called when the top value changes.
     * Still log(n) worst case, but it's at least twice as fast to
     *
     * <pre class="prettyprint">
     * pq.updateTop(value);
    </pre> *
     *
     * instead of
     *
     * <pre class="prettyprint">
     * pq.pop();
     * pq.push(value);
    </pre> *
     *
     * Calling this method on an empty LongHeap has no visible effect.
     *
     * @param value the new element that is less than the current top.
     * @return the new 'top' element after shuffling the heap.
     */
    fun updateTop(value: Long): Long {
        this.heapArray[1] = value
        downHeap(1)
        return this.heapArray[1]
    }

    /** Returns the number of elements currently stored in the PriorityQueue.  */
    fun size(): Int {
        return size
    }

    /** Removes all entries from the PriorityQueue.  */
    fun clear() {
        size = 0
    }

    private fun upHeap(origPos: Int) {
        var i = origPos
        val value = this.heapArray[i] // save bottom value
        var j = i ushr 1
        while (j > 0 && value < this.heapArray[j]) {
            this.heapArray[i] = this.heapArray[j] // shift parents down
            i = j
            j = j ushr 1
        }
        this.heapArray[i] = value // install saved value
    }

    private fun downHeap(i: Int) {
        var i = i
        val value = this.heapArray[i] // save top value
        var j = i shl 1 // find smaller child
        var k = j + 1
        if (k <= size && this.heapArray[k] < this.heapArray[j]) {
            j = k
        }
        while (j <= size && this.heapArray[j] < value) {
            this.heapArray[i] = this.heapArray[j] // shift up child
            i = j
            j = i shl 1
            k = j + 1
            if (k <= size && this.heapArray[k] < this.heapArray[j]) {
                j = k
            }
        }
        this.heapArray[i] = value // install saved value
    }

    fun pushAll(other: LongHeap) {
        for (i in 1..other.size) {
            push(other.heapArray[i])
        }
    }

    /**
     * Return the element at the ith location in the heap array. Use for iterating over elements when
     * the order doesn't matter. Note that the valid arguments range from [1, size].
     */
    fun get(i: Int): Long {
        return this.heapArray[i]
    }
}
