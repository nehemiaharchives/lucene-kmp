package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator

abstract class DocValuesIterator : DocIdSetIterator() {
    /**
     * Advance the iterator to exactly `target` and return whether `target` has a value.
     * `target` must be greater than or equal to the current [doc ID][.docID] and must be
     * a valid doc ID, ie.  0 and &lt; `maxDoc`. After this method returns, [.docID]
     * returns `target`.
     */
    @Throws(IOException::class)
    abstract fun advanceExact(target: Int): Boolean
}
