package org.gnit.lucenekmp.index

import kotlinx.io.IOException


/**
 * Utility class to help merging documents from sub-readers according to either simple concatenated
 * (unsorted) order, or by a specified index-time sort, skipping deleted documents and remapping
 * non-deleted documents.
 */
abstract class DocIDMerger<T : DocIDMerger.Sub> private constructor() {
    /** Represents one sub-reader being merged  */
    abstract class Sub protected constructor(docMap: MergeState.DocMap) {
        /** Mapped doc ID  */
        var mappedDocID: Int = 0

        /** Map from old to new doc IDs  */
        val docMap: MergeState.DocMap

        /** Sole constructor  */
        init {
            this.docMap = docMap
        }

        /**
         * Returns the next document ID from this sub reader, and [DocIdSetIterator.NO_MORE_DOCS]
         * when done
         */
        @Throws(IOException::class)
        abstract fun nextDoc(): Int

        /**
         * Like [.nextDoc] but skips over unmapped docs and returns the next mapped doc ID, or
         * [DocIdSetIterator.NO_MORE_DOCS] when exhausted. This method sets [.mappedDocID]
         * as a side effect.
         */
        @Throws(IOException::class)
        fun nextMappedDoc(): Int {
            while (true) {
                val doc = nextDoc()
                if (doc == NO_MORE_DOCS) {
                    return NO_MORE_DOCS.also { this.mappedDocID = it }
                }
                val mappedDoc: Int = docMap.get(doc)!!
                if (mappedDoc != -1) {
                    return mappedDoc.also { this.mappedDocID = it }
                }
            }
        }
    }

    /** Reuse API, currently only used by postings during merge  */
    @Throws(IOException::class)
    abstract fun reset()

    /**
     * Returns null when done. **NOTE:** after the iterator has exhausted you should not call this
     * method, as it may result in unpredicted behavior.
     */
    @Throws(IOException::class)
    abstract fun next(): T

    private class SequentialDocIDMerger<T : Sub>(private val subs: MutableList<T>) : DocIDMerger<T>() {
        private var current: T = null
        private var nextIndex = 0

        init {
            reset()
        }

        @Throws(IOException::class)
        override fun reset() {
            if (subs.size > 0) {
                current = subs.get(0)
                nextIndex = 1
            } else {
                current = null
                nextIndex = 0
            }
        }

        @Throws(IOException::class)
        override fun next(): T {
            while (current!!.nextMappedDoc() == NO_MORE_DOCS) {
                if (nextIndex == subs.size) {
                    current = null
                    return null
                }
                current = subs.get(nextIndex)
                nextIndex++
            }
            return current
        }
    }

    private class SortedDocIDMerger<T : Sub>(subs: MutableList<T>, maxCount: Int) : DocIDMerger<T>() {
        private val subs: MutableList<T>
        private var current: T = null
        private val queue: PriorityQueue<T>
        private var queueMinDocID = 0

        init {
            require(maxCount > 1)
            this.subs = subs
            queue =
                object : PriorityQueue<T>(maxCount - 1) {
                    protected override fun lessThan(a: Sub, b: Sub): Boolean {
                        assert(a.mappedDocID != b.mappedDocID)
                        return a.mappedDocID < b.mappedDocID
                    }
                }
            reset()
        }

        fun setQueueMinDocID() {
            if (queue.size() > 0) {
                queueMinDocID = queue.top().mappedDocID
            } else {
                queueMinDocID = DocIdSetIterator.NO_MORE_DOCS
            }
        }

        @Throws(IOException::class)
        override fun reset() {
            // caller may not have fully consumed the queue:
            queue.clear()
            current = null
            var first = true
            for (sub in subs) {
                if (first) {
                    // by setting mappedDocID = -1, this entry is guaranteed to be the top of the queue
                    // so the first call to next() will advance it
                    sub!!.mappedDocID = -1
                    current = sub
                    first = false
                } else if (sub!!.nextMappedDoc() != NO_MORE_DOCS) {
                    queue.add(sub)
                } // else all docs in this sub were deleted; do not add it to the queue!
            }
            setQueueMinDocID()
        }

        @Throws(IOException::class)
        override fun next(): T {
            val nextDoc = current!!.nextMappedDoc()
            if (nextDoc < queueMinDocID) {
                // This should be the common case when index sorting is either disabled, or enabled on a
                // low-cardinality field, or enabled on a field that correlates with index order.
                return current
            }

            if (nextDoc == NO_MORE_DOCS) {
                if (queue.size() === 0) {
                    current = null
                } else {
                    current = queue.pop()
                }
            } else if (queue.size() > 0) {
                assert(queueMinDocID == queue.top().mappedDocID)
                assert(nextDoc > queueMinDocID)
                val newCurrent: T = queue.top()
                queue.updateTop(current)
                current = newCurrent
            }

            setQueueMinDocID()
            return current
        }
    }

    companion object {
        /** Construct this from the provided subs, specifying the maximum sub count  */
        @Throws(IOException::class)
        fun <T : Sub> of(
            subs: MutableList<T>, maxCount: Int, indexIsSorted: Boolean
        ): DocIDMerger<T> {
            if (indexIsSorted && maxCount > 1) {
                return SortedDocIDMerger<T>(subs, maxCount)
            } else {
                return SequentialDocIDMerger<T>(subs)
            }
        }

        /** Construct this from the provided subs  */
        @Throws(IOException::class)
        fun <T : Sub> of(subs: MutableList<T>, indexIsSorted: Boolean): DocIDMerger<T> {
            return of<T>(subs, subs.size, indexIsSorted)
        }
    }
}
