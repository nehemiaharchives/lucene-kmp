package org.gnit.lucenekmp.util

import kotlin.jvm.JvmOverloads


/**
 * A priority queue maintains a partial ordering of its elements such that the least element can
 * always be found in constant time. Put()'s and pop()'s require log(size) time but the remove()
 * cost implemented here is linear.
 *
 *
 * **NOTE**: This class pre-allocates an array of length `maxSize+1` and pre-fills it
 * with elements if instantiated via the [.PriorityQueue] constructor.
 *
 *
 * **NOTE**: Iteration order is not specified.
 *
 * @lucene.internal
 */
abstract class PriorityQueue<T> @JvmOverloads constructor(
    maxSize: Int,
    sentinelObjectSupplier: () -> T? = { null }
) : Iterable<T> {
    private var size = 0
    private val maxSize: Int
    private val heap: Array<T?>

    /**
     * Create a priority queue that is pre-filled with sentinel objects, so that the code which uses
     * that queue can always assume it's full and only change the top without attempting to insert any
     * new object.
     *
     *
     * Those sentinel values should always compare worse than any non-sentinel value (i.e., [ ][.lessThan] should always favor the non-sentinel values).
     *
     *
     * By default, the supplier returns null, which means the queue will not be filled with
     * sentinel values. Otherwise, the value returned will be used to pre-populate the queue.
     *
     *
     * If this method is extended to return a non-null value, then the following usage pattern is
     * recommended:
     *
     * <pre class="prettyprint">
     * PriorityQueue&lt;MyObject&gt; pq = new MyQueue&lt;MyObject&gt;(numHits);
     * // save the 'top' element, which is guaranteed to not be null.
     * MyObject pqTop = pq.top();
     * &lt;...&gt;
     * // now in order to add a new element, which is 'better' than top (after
     * // you've verified it is better), it is as simple as:
     * pqTop.change().
     * pqTop = pq.updateTop();
    </pre> *
     *
     * **NOTE:** the given supplier will be called `maxSize` times, relying on a new object
     * to be returned and will not check if it's null again. Therefore you should ensure any call to
     * this method creates a new instance and behaves consistently, e.g., it cannot return null if it
     * previously returned non-null and all returned instances must [compare equal][.lessThan].
     */
    /** Create an empty priority queue of the configured size.  */
    init {
        val heapSize: Int

        if (0 == maxSize) {
            // We allocate 1 extra to avoid if statement in top()
            heapSize = 2
        } else {
            require(!((maxSize < 0) || (maxSize >= ArrayUtil.MAX_ARRAY_LENGTH))) { "maxSize must be >= 0 and < " + (ArrayUtil.MAX_ARRAY_LENGTH) + "; got: " + maxSize }

            // NOTE: we add +1 because all access to heap is
            // 1-based not 0-based.  heap[0] is unused.
            heapSize = maxSize + 1
        }

        // T is an unbounded type, so this unchecked cast works always.
        val h = kotlin.arrayOfNulls<Any>(heapSize) as Array<T?>
        this.heap = h
        this.maxSize = maxSize

        // If sentinel objects are supported, populate the queue with them
        val sentinel: T? = sentinelObjectSupplier()
        if (sentinel != null) {
            heap[1] = sentinel
            for (i in 2..<heap.size) {
                heap[i] = sentinelObjectSupplier()!!
            }
            size = maxSize
        }
    }

    /**
     * Adds all elements of the collection into the queue. This method should be preferred over
     * calling [.add] in loop if all elements are known in advance as it builds queue
     * faster.
     *
     *
     * If one tries to add more objects than the maxSize passed in the constructor, an [ ] is thrown.
     */
    fun addAll(elements: MutableCollection<T>) {
        if (this.size + elements.size > this.maxSize) {
            throw IndexOutOfBoundsException(
                ("Cannot add "
                        + elements.size
                        + " elements to a queue with remaining capacity: "
                        + (maxSize - size))
            )
        }

        // Heap with size S always takes first S elements of the array,
        // and thus it's safe to fill array further - no actual non-sentinel value will be overwritten.
        val iterator = elements.iterator()
        while (iterator.hasNext()) {
            this.heap[size + 1] = iterator.next()
            this.size++
        }

        // The loop goes down to 1 as heap is 1-based not 0-based.
        for (i in (size ushr 1) downTo 1) {
            downHeap(i)
        }
    }

    /**
     * Determines the ordering of objects in this priority queue. Subclasses must define this one
     * method.
     *
     * @return `true` iff parameter `a` is less than parameter `b`.
     */
    abstract fun lessThan(a: T, b: T): Boolean

    /**
     * Adds an Object to a PriorityQueue in log(size) time. If one tries to add more objects than
     * maxSize from initialize an [ArrayIndexOutOfBoundsException] is thrown.
     *
     * @return the new 'top' element in the queue.
     */
    fun add(element: T): T? {
        // don't modify size until we know heap access didn't throw AIOOB.
        val index = size + 1
        heap[index] = element
        size = index
        upHeap(index)
        return heap[1]
    }

    /**
     * Adds an Object to a PriorityQueue in log(size) time. It returns the object (if any) that was
     * dropped off the heap because it was full. This can be the given parameter (in case it is
     * smaller than the full heap's minimum, and couldn't be added), or another object that was
     * previously the smallest value in the heap and now has been replaced by a larger one, or null if
     * the queue wasn't yet full with maxSize elements.
     */
    fun insertWithOverflow(element: T): T? {
        if (size < maxSize) {
            add(element)
            return null
        } else if (size > 0 && lessThan(heap[1]!!, element)) {
            val ret = heap[1]
            heap[1] = element
            updateTop()
            return ret
        } else {
            return element
        }
    }

    /** Returns the least element of the PriorityQueue in constant time.  */
    fun top(): T {
        // We don't need to check size here: if maxSize is 0,
        // then heap is length 2 array with both entries null.
        // If size is 0 then heap[1] is already null.
        return heap[1]!!
    }

    /** Removes and returns the least element of the PriorityQueue in log(size) time.  */
    fun pop(): T? {
        if (size > 0) {
            val result = heap[1] // save first value
            heap[1] = heap[size] // move last to first
            heap[size] = null // permit GC of objects
            size--
            downHeap(1) // adjust heap
            return result
        } else {
            return null
        }
    }

    /**
     * Should be called when the Object at top changes values. Still log(n) worst case, but it's at
     * least twice as fast to
     *
     * <pre class="prettyprint">
     * pq.top().change();
     * pq.updateTop();
    </pre> *
     *
     * instead of
     *
     * <pre class="prettyprint">
     * o = pq.pop();
     * o.change();
     * pq.push(o);
    </pre> *
     *
     * @return the new 'top' element.
     */
    fun updateTop(): T {
        downHeap(1)
        return heap[1]!!
    }

    /** Replace the top of the pq with `newTop` and run [.updateTop].  */
    fun updateTop(newTop: T): T {
        heap[1] = newTop
        return updateTop()
    }

    /** Returns the number of elements currently stored in the PriorityQueue.  */
    fun size(): Int {
        return size
    }

    /** Removes all entries from the PriorityQueue.  */
    fun clear() {
        for (i in 0..size) {
            heap[i] = null
        }
        size = 0
    }

    /**
     * Removes an existing element currently stored in the PriorityQueue. Cost is linear with the size
     * of the queue. (A specialization of PriorityQueue which tracks element positions would provide a
     * constant remove time but the trade-off would be extra cost to all additions/insertions)
     */
    fun remove(element: T): Boolean {
        for (i in 1..size) {
            if (heap[i] === element) {
                heap[i] = heap[size]
                heap[size] = null // permit GC of objects
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
        val node = heap[i]!! // save bottom node
        var j = i ushr 1
        while (j > 0 && lessThan(node, heap[j]!!)) {
            heap[i] = heap[j] // shift parents down
            i = j
            j = j ushr 1
        }
        heap[i] = node // install saved node
        return i != origPos
    }

    private fun downHeap(i: Int) {
        var i = i
        val node = heap[i] ?: return // queue is empty
        var j = i shl 1 // find smaller child
        var k = j + 1
        if (k <= size && lessThan(heap[k]!!, heap[j]!!)) {
            j = k
        }
        while (j <= size && lessThan(heap[j]!!, node)) {
            heap[i] = heap[j] // shift up child
            i = j
            j = i shl 1
            k = j + 1
            if (k <= size && lessThan(heap[k]!!, heap[j]!!)) {
                j = k
            }
        }
        heap[i] = node // install saved node
    }

    protected val heapArray: Array<T?>
        /**
         * This method returns the internal heap array as Object[].
         *
         * @lucene.internal
         */
        get() = heap

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            var i: Int = 1

            override fun hasNext(): Boolean {
                return i <= size
            }

            override fun next(): T {
                if (hasNext() == false) {
                    throw NoSuchElementException()
                }
                return heap[i++]!!
            }
        }
    }
}
