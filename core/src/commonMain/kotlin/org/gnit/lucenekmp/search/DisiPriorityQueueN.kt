package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.jdkport.Arrays


internal class DisiPriorityQueueN(maxSize: Int) : DisiPriorityQueue() {
    private val heap: Array<DisiWrapper?> = kotlin.arrayOfNulls<DisiWrapper>(maxSize)
    private var size = 0

    public override fun size(): Int {
        return size
    }

    public override fun top(): DisiWrapper? {
        return heap[0]
    }

    public override fun top2(): DisiWrapper? {
        when (size()) {
            0, 1 -> return null
            2 -> return heap[1]
            else -> if (heap[1]!!.doc <= heap[2]!!.doc) {
                return heap[1]
            } else {
                return heap[2]
            }
        }
    }

    public override fun topList(): DisiWrapper {
        val heap = this.heap
        val size = this.size
        var list = heap[0]
        list!!.next = null
        if (size >= 3) {
            list = topList(list, heap, size, 1)
            list = topList(list, heap, size, 2)
        } else if (size == 2 && heap[1]!!.doc == list.doc) {
            list = prepend(heap[1], list)
        }
        return list
    }

    // prepend w1 (iterator) to w2 (list)
    private fun prepend(w1: DisiWrapper?, w2: DisiWrapper?): DisiWrapper {
        w1!!.next = w2
        return w1
    }

    private fun topList(list: DisiWrapper, heap: Array<DisiWrapper?>, size: Int, i: Int): DisiWrapper {
        var list = list
        val w = heap[i]
        if (w!!.doc == list.doc) {
            list = prepend(w, list)
            val left = leftNode(i)
            val right = rightNode(left)
            if (right < size) {
                list = topList(list, heap, size, left)
                list = topList(list, heap, size, right)
            } else if (left < size && heap[left]!!.doc == list.doc) {
                list = prepend(heap[left], list)
            }
        }
        return list
    }

    public override fun add(entry: DisiWrapper): DisiWrapper {
        val heap = this.heap
        val size = this.size
        heap[size] = entry
        upHeap(size)
        this.size = size + 1
        return heap[0]!!
    }

    public override fun addAll(entries: Array<DisiWrapper>, offset: Int, len: Int) {
        // Nothing to do if empty:
        if (len == 0) {
            return
        }

        // Fail early if we're going to over-fill:
        if (size + len > heap.size) {
            throw IndexOutOfBoundsException(
                ("Cannot add "
                        + len
                        + " elements to a queue with remaining capacity "
                        + (heap.size - size))
            )
        }

        // Copy the entries over to our heap array:
        /*java.lang.System.arraycopy(entries, offset, heap, size, len)*/
        entries.copyInto(
            destination = heap,
            destinationOffset = size,
            startIndex = offset,
            endIndex = offset + len
        )
        size += len

        // Heapify in bulk:
        val firstLeafIndex = size ushr 1
        for (rootIndex in firstLeafIndex - 1 downTo 0) {
            var parentIndex = rootIndex
            val parent = heap[parentIndex]!!
            while (parentIndex < firstLeafIndex) {
                var childIndex = leftNode(parentIndex)
                val rightChildIndex = rightNode(childIndex)
                var child = heap[childIndex]!!
                if (rightChildIndex < size && heap[rightChildIndex]!!.doc < child.doc) {
                    child = heap[rightChildIndex]!!
                    childIndex = rightChildIndex
                }
                if (child.doc >= parent.doc) {
                    break
                }
                heap[parentIndex] = child
                parentIndex = childIndex
            }
            heap[parentIndex] = parent
        }
    }

    public override fun pop(): DisiWrapper? {
        val heap = this.heap
        val result: DisiWrapper? = heap[0]
        val i = --size
        heap[0] = heap[i]
        heap[i] = null
        if (i > 0) {
            downHeap(i)
        }
        return result
    }

    public override fun updateTop(): DisiWrapper {
        downHeap(size)
        return heap[0]!!
    }

    public override fun updateTop(topReplacement: DisiWrapper): DisiWrapper {
        heap[0] = topReplacement
        return updateTop()
    }

    public override fun clear() {
        Arrays.fill(heap, null)
        size = 0
    }

    fun upHeap(i: Int) {
        var i = i
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

    fun downHeap(size: Int) {
        var i = 0
        val node = heap[0]!!
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

    public override fun iterator(): MutableIterator<DisiWrapper?> {
        return heap.toMutableList().subList(0, size).iterator()
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
