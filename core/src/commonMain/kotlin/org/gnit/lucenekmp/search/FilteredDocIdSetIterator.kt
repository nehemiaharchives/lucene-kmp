package org.gnit.lucenekmp.search

import okio.IOException


/**
 * Abstract decorator class of a DocIdSetIterator implementation that provides on-demand
 * filter/validation mechanism on an underlying DocIdSetIterator.
 */
abstract class FilteredDocIdSetIterator(innerIter: DocIdSetIterator) : DocIdSetIterator() {
    /** Return the wrapped [DocIdSetIterator].  */
    var delegate: DocIdSetIterator
        protected set
    private var doc: Int

    /**
     * Constructor.
     *
     * @param innerIter Underlying DocIdSetIterator.
     */
    init {
        requireNotNull(innerIter) { "null iterator" }
        this.delegate = innerIter
        doc = -1
    }

    /**
     * Validation method to determine whether a docid should be in the result set.
     *
     * @param doc docid to be tested
     * @return true if input docid should be in the result set, false otherwise.
     * @see .FilteredDocIdSetIterator
     */
    @Throws(IOException::class)
    protected abstract fun match(doc: Int): Boolean

    override fun docID(): Int {
        return doc
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        while ((delegate.nextDoc().also { doc = it }) != NO_MORE_DOCS) {
            if (match(doc)) {
                return doc
            }
        }
        return doc
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        doc = delegate.advance(target)
        if (doc != NO_MORE_DOCS) {
            if (match(doc)) {
                return doc
            } else {
                while ((delegate.nextDoc().also { doc = it }) != NO_MORE_DOCS) {
                    if (match(doc)) {
                        return doc
                    }
                }
                return doc
            }
        }
        return doc
    }

    override fun cost(): Long {
        return delegate.cost()
    }
}
