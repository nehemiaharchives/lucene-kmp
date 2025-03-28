package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.Accountable


/**
 * A DocIdSet contains a set of doc ids. Implementing classes must only implement [.iterator]
 * to provide access to the set.
 */
abstract class DocIdSet : Accountable {
    /**
     * Provides a [DocIdSetIterator] to access the set. This implementation can return `
     * null` if there are no docs that match.
     */
    abstract fun iterator(): DocIdSetIterator

    companion object {
        /** An empty `DocIdSet` instance  */
        val EMPTY: DocIdSet = object : DocIdSet() {
            override fun iterator(): DocIdSetIterator {
                return DocIdSetIterator.empty()
            }

            override fun ramBytesUsed(): Long {
                return 0L
            }
        }
    }
}
