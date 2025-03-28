package org.gnit.lucenekmp.internal.hppc


/**
 * Simplifies the implementation of iterators a bit. Modeled loosely after Google Guava's API.
 *
 *
 * Forked from com.carrotsearch.hppc.AbstractIterator
 *
 * @lucene.internal
 */
abstract class AbstractIterator<E> : MutableIterator<E?> {
    /** Current iterator state.  */
    private var state: Int = NOT_CACHED

    /** The next element to be returned from [.next] if fetched.  */
    private var nextElement: E? = null

    override fun hasNext(): Boolean {
        if (state == NOT_CACHED) {
            state = CACHED
            nextElement = fetch()
        }
        return state == CACHED
    }

    override fun next(): E? {
        if (!hasNext()) {
            throw NoSuchElementException()
        }

        state = NOT_CACHED
        return nextElement
    }

    /** Default implementation throws [UnsupportedOperationException].  */
    override fun remove() {
        throw UnsupportedOperationException()
    }

    /**
     * Fetch next element. The implementation must return [.done] when all elements have been
     * fetched.
     *
     * @return Returns the next value for the iterator or chain-calls [.done].
     */
    protected abstract fun fetch(): E

    /**
     * Call when done.
     *
     * @return Returns a unique sentinel value to indicate end-of-iteration.
     */
    protected fun done(): E? {
        state = AT_END
        return null
    }

    companion object {
        private const val NOT_CACHED = 0
        private const val CACHED = 1
        private const val AT_END = 2
    }
}
