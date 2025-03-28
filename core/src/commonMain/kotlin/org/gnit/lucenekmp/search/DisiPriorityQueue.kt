package org.gnit.lucenekmp.search


/**
 * A priority queue of DocIdSetIterators that orders by current doc ID. This specialization is
 * needed over [PriorityQueue] because the pluggable comparison function makes the rebalancing
 * quite slow.
 *
 * @lucene.internal
 */
abstract class DisiPriorityQueue : Iterable<DisiWrapper?> {
    /** Return the number of entries in this heap.  */
    abstract fun size(): Int

    /** Return top value in this heap, or null if the heap is empty.  */
    abstract fun top(): DisiWrapper?

    /** Return the 2nd least value in this heap, or null if the heap contains less than 2 values.  */
    abstract fun top2(): DisiWrapper?

    /** Get the list of scorers which are on the current doc.  */
    abstract fun topList(): DisiWrapper?

    /** Add a [DisiWrapper] to this queue and return the top entry.  */
    abstract fun add(entry: DisiWrapper): DisiWrapper

    /** Bulk add.  */
    open fun addAll(entries: Array<DisiWrapper>, offset: Int, len: Int) {
        for (i in 0..<len) {
            add(entries[offset + i])
        }
    }

    /** Remove the top entry and return it.  */
    abstract fun pop(): DisiWrapper?

    /** Rebalance this heap and return the top entry.  */
    abstract fun updateTop(): DisiWrapper

    /**
     * Replace the top entry with the given entry, rebalance the heap, and return the new top entry.
     */
    abstract fun updateTop(topReplacement: DisiWrapper): DisiWrapper

    /** Clear the heap.  */
    abstract fun clear()

    companion object {
        /** Create a [DisiPriorityQueue] of the given maximum size.  */
        fun ofMaxSize(maxSize: Int): DisiPriorityQueue {
            if (maxSize <= 2) {
                return DisiPriorityQueue2()
            } else {
                return DisiPriorityQueueN(maxSize)
            }
        }
    }
}
