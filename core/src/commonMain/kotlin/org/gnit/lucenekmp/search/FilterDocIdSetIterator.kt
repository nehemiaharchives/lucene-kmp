package org.gnit.lucenekmp.search

import kotlinx.io.IOException

/** Wrapper around a [DocIdSetIterator].  */
class FilterDocIdSetIterator
/** Sole constructor.  */(
    /** Wrapped instance.  */
    protected val `in`: DocIdSetIterator
) : DocIdSetIterator() {
    override fun docID(): Int {
        return `in`.docID()
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        return `in`.nextDoc()
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        return `in`.advance(target)
    }

    override fun cost(): Long {
        return `in`.cost()
    }
}
