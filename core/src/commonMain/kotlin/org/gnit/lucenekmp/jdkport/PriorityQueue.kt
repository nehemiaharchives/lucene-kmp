package org.gnit.lucenekmp.jdkport

class PriorityQueue<E : Any> {

    companion object {
        const val DEFAULT_INITIAL_CAPACITY: Int = 11
    }

    private val comparator: Comparator<in E>
    private val heap: MutableList<E>
    private var modCount: Int = 0

    /**
     * Creates a `PriorityQueue` with the specified initial capacity
     * that orders its elements according to the specified comparator.
     *
     * @param  initialCapacity the initial capacity for this priority queue
     * @param  comparator the comparator that will be used to order this
     * priority queue.  If `null`, the [         natural ordering][Comparable] of the elements will be used.
     * @throws IllegalArgumentException if `initialCapacity` is
     * less than 1
     */
    constructor(
        initialCapacity: Int = DEFAULT_INITIAL_CAPACITY,
        comparator: Comparator<in E> = Comparator { a, b -> (a as Comparable<E>).compareTo(b) }
    ) {
        // Note: This restriction of at least one is not actually needed,
        // but continues for 1.5 compatibility
        require(initialCapacity >= 1)
        this.heap = ArrayList<E>(initialCapacity)
        this.comparator = comparator
    }

    fun isEmpty(): Boolean = heap.isEmpty()
    fun size(): Int = heap.size

    /**
     * Inserts the specified element into this priority queue.
     *
     * @return true (as specified by Queue.offer)
     * @throws ClassCastException if the specified element cannot be compared
     *         with elements currently in this priority queue according to the priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    fun offer(element: E?): Boolean {
        if (element == null) throw NullPointerException("Element must not be null")
        modCount++
        heap.add(element)
        siftUp(heap.lastIndex, element)
        return true
    }

    fun poll(): E? {
        if (heap.isEmpty()) return null
        modCount++
        val result = heap[0]
        val last = heap.removeAt(heap.lastIndex)
        if (heap.isNotEmpty()) {
            heap[0] = last
            siftDown(0)
        }
        return result
    }

    fun peek(): E? = heap.firstOrNull()

    private fun siftUp(index: Int, element: E) {
        var child = index
        var e = element
        while (child > 0) {
            val parent = (child - 1) / 2
            if (comparator.compare(e, heap[parent]) >= 0) {
                break
            }
            heap[child] = heap[parent]
            child = parent
        }
        heap[child] = e
    }

    private fun siftDown(index: Int) {
        var parent = index
        val size = heap.size
        val e = heap[parent]
        while (true) {
            val left = 2 * parent + 1
            val right = left + 1
            var candidate = parent
            if (left < size && comparator.compare(heap[left], heap[candidate]) < 0) {
                candidate = left
            }
            if (right < size && comparator.compare(heap[right], heap[candidate]) < 0) {
                candidate = right
            }
            if (candidate == parent) break
            heap[parent] = heap[candidate]
            parent = candidate
        }
        heap[parent] = e
    }

    /**
     * Returns an array containing all of the elements in this priority queue.
     */
    fun toTypedArray(): Array<E> {
        val size = heap.size
        // Create a properly sized array with the correct type information
        @Suppress("UNCHECKED_CAST")
        val result = Array<Any?>(size) { null } as Array<E>
        for (i in 0 until size) {
            result[i] = heap[i]
        }
        return result
    }
}
