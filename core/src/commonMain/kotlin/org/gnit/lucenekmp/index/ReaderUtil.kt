package org.gnit.lucenekmp.index


/**
 * Common util methods for dealing with [IndexReader]s and [IndexReaderContext]s.
 *
 * @lucene.internal
 */
object ReaderUtil {
    /**
     * Walks up the reader tree and return the given context's top level reader context, or in other
     * words the reader tree's root context.
     */
    fun getTopLevelContext(context: IndexReaderContext): IndexReaderContext {
        var context = context
        while (context.parent != null) {
            context = context.parent
        }
        return context
    }

    /**
     * Returns index of the searcher/reader for document `n` in the array used to construct
     * this searcher/reader.
     */
    fun subIndex(n: Int, docStarts: IntArray): Int {
        // find searcher/reader for doc n:
        val size = docStarts.size
        var lo = 0 // search starts array
        var hi = size - 1 // for first element less than n, return its index
        while (hi >= lo) {
            var mid = (lo + hi) ushr 1
            val midValue = docStarts[mid]
            if (n < midValue) {
                hi = mid - 1
            } else if (n > midValue) {
                lo = mid + 1
            } else { // found a match
                while (mid + 1 < size && docStarts[mid + 1] == midValue) {
                    mid++ // scan to last match
                }
                return mid
            }
        }
        return hi
    }

    /**
     * Returns index of the searcher/reader for document `n` in the array used to construct
     * this searcher/reader.
     */
    fun subIndex(n: Int, leaves: MutableList<LeafReaderContext?>): Int {
        // find searcher/reader for doc n:
        val size = leaves.size
        var lo = 0 // search starts array
        var hi = size - 1 // for first element less than n, return its index
        while (hi >= lo) {
            var mid = (lo + hi) ushr 1
            val midValue = leaves.get(mid)!!.docBase
            if (n < midValue) {
                hi = mid - 1
            } else if (n > midValue) {
                lo = mid + 1
            } else { // found a match
                while (mid + 1 < size && leaves.get(mid + 1)!!.docBase == midValue) {
                    mid++ // scan to last match
                }
                return mid
            }
        }
        return hi
    }
}
