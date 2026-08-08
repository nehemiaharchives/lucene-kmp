/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.queries.intervals

import org.gnit.lucenekmp.util.PriorityQueue

/**
 * A priority queue of DocIdSetIterators that orders by current doc ID. This specialization is
 * needed over [PriorityQueue] because the pluggable comparison function makes the rebalancing
 * quite slow.
 *
 * @lucene.internal
 */
internal class DisiPriorityQueue(maxSize: Int) : Iterable<DisiWrapper> {

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

    private val heap: Array<DisiWrapper?> = arrayOfNulls(maxSize)
    private var size: Int = 0

    fun size(): Int {
        return size
    }

    fun top(): DisiWrapper {
        return heap[0]!!
    }

    /** Get the list of scorers which are on the current doc. */
    fun topList(): DisiWrapper {
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
    private fun prepend(w1: DisiWrapper, w2: DisiWrapper): DisiWrapper {
        w1.next = w2
        return w1
    }

    private fun topList(
        list: DisiWrapper,
        heap: Array<DisiWrapper?>,
        size: Int,
        i: Int
    ): DisiWrapper {
        var list = list
        val w = heap[i]!!
        if (w.doc == list.doc) {
            list = prepend(w, list)
            val left = leftNode(i)
            val right = left + 1
            if (right < size) {
                list = topList(list, heap, size, left)
                list = topList(list, heap, size, right)
            } else if (left < size && heap[left]!!.doc == list.doc) {
                list = prepend(heap[left]!!, list)
            }
        }
        return list
    }

    fun add(entry: DisiWrapper): DisiWrapper {
        val heap = this.heap
        val size = this.size
        heap[size] = entry
        upHeap(size)
        this.size = size + 1
        return heap[0]!!
    }

    fun pop(): DisiWrapper {
        val heap = this.heap
        val result = heap[0]!!
        val i = --size
        heap[0] = heap[i]
        heap[i] = null
        downHeap(i)
        return result
    }

    fun updateTop(): DisiWrapper {
        downHeap(size)
        return heap[0]!!
    }

    fun updateTop(topReplacement: DisiWrapper): DisiWrapper {
        heap[0] = topReplacement
        return updateTop()
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
        val node = heap[0]
        var j = leftNode(i)
        if (j < size) {
            var k = rightNode(j)
            if (k < size && heap[k]!!.doc < heap[j]!!.doc) {
                j = k
            }
            if (heap[j]!!.doc < node!!.doc) {
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

    override fun iterator(): Iterator<DisiWrapper> {
        return object : Iterator<DisiWrapper> {
            private var index = 0

            override fun hasNext(): Boolean {
                return index < size
            }

            override fun next(): DisiWrapper {
                return heap[index++]!!
            }
        }
    }
}
