package org.gnit.lucenekmp.queries.spans

/**
 * A priority queue of DocIdSetIterators that orders by current doc ID. This specialization is
 * needed over `PriorityQueue` because the pluggable comparison function makes the rebalancing
 * quite slow.
 *
 * @lucene.internal
 */
internal class SpanDisiPriorityQueue(maxSize: Int) : Iterable<SpanDisiWrapper> {
    private val heap = arrayOfNulls<SpanDisiWrapper>(maxSize)
    private var size = 0

    fun size(): Int {
        return size
    }

    fun top(): SpanDisiWrapper {
        return heap[0]!!
    }

    /** Get the list of scorers which are on the current doc. */
    fun topList(): SpanDisiWrapper {
        val heap = this.heap
        val size = this.size
        var list = heap[0]!!
        list.next = null
        if (size >= 3) {
            list = topList(list, heap, size, 1)
            list = topList(list, heap, size, 2)
        } else if (size == 2 && heap[1]!!.doc == list.doc) {
            list = prepend(heap[1]!!, list)
        }
        return list
    }

    // prepend w1 (iterator) to w2 (list)
    private fun prepend(w1: SpanDisiWrapper, w2: SpanDisiWrapper): SpanDisiWrapper {
        w1.next = w2
        return w1
    }

    private fun topList(
        list: SpanDisiWrapper,
        heap: Array<SpanDisiWrapper?>,
        size: Int,
        i: Int,
    ): SpanDisiWrapper {
        var currentList = list
        val w = heap[i]!!
        if (w.doc == currentList.doc) {
            currentList = prepend(w, currentList)
            val left = leftNode(i)
            val right = left + 1
            if (right < size) {
                currentList = topList(currentList, heap, size, left)
                currentList = topList(currentList, heap, size, right)
            } else if (left < size && heap[left]!!.doc == currentList.doc) {
                currentList = prepend(heap[left]!!, currentList)
            }
        }
        return currentList
    }

    fun add(entry: SpanDisiWrapper): SpanDisiWrapper {
        val size = this.size
        heap[size] = entry
        upHeap(size)
        this.size = size + 1
        return heap[0]!!
    }

    fun pop(): SpanDisiWrapper {
        val result = heap[0]!!
        val i = --size
        heap[0] = heap[i]
        heap[i] = null
        downHeap(i)
        return result
    }

    fun updateTop(): SpanDisiWrapper {
        downHeap(size)
        return heap[0]!!
    }

    fun updateTop(topReplacement: SpanDisiWrapper): SpanDisiWrapper {
        heap[0] = topReplacement
        return updateTop()
    }

    private fun upHeap(i0: Int) {
        var i = i0
        val node = heap[i]!!
        val nodeDoc = node.doc
        var j = parentNode(i)
        while (j >= 0 && nodeDoc < heap[j]!!.doc) {
            heap[i] = heap[j]
            i = j
            j = parentNode(j)
        }
        heap[i] = node
    }

    private fun downHeap(size: Int) {
        var i = 0
        val node = heap[0] ?: return
        var j = leftNode(i)
        if (j < size) {
            var k = rightNode(j)
            if (k < size && heap[k]!!.doc < heap[j]!!.doc) {
                j = k
            }
            if (heap[j]!!.doc < node.doc) {
                do {
                    heap[i] = heap[j]
                    i = j
                    j = leftNode(i)
                    k = rightNode(j)
                    if (k < size && heap[k]!!.doc < heap[j]!!.doc) {
                        j = k
                    }
                } while (j < size && heap[j]!!.doc < node.doc)
                heap[i] = node
            }
        }
    }

    override fun iterator(): Iterator<SpanDisiWrapper> {
        return heap.take(size).filterNotNull().iterator()
    }

    companion object {
        fun leftNode(node: Int): Int {
            return ((node + 1) shl 1) - 1
        }

        fun rightNode(leftNode: Int): Int {
            return leftNode + 1
        }

        fun parentNode(node: Int): Int {
            return ((node + 1) ushr 1) - 1
        }
    }
}
