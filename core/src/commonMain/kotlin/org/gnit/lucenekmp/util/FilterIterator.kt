package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.assert

/**
 * An [Iterator] implementation that filters elements with a boolean predicate.
 *
 * @param <T> generic parameter for this iterator instance: this iterator implements [     Iterator&amp;lt;T&amp;gt;][Iterator]
 * @param <InnerT> generic parameter of the wrapped iterator, must be `T` or extend
 * `T`
 * @see .predicateFunction
 *
 * @lucene.internal
</InnerT></T> */
abstract class FilterIterator<T, InnerT : T>(private val iterator: MutableIterator<InnerT>) :
    MutableIterator<T> {
    private var next: T? = null
    private var nextIsSet = false

    /** returns true, if this element should be returned by [.next].  */
    protected abstract fun predicateFunction(`object`: InnerT): Boolean

    override fun hasNext(): Boolean {
        return nextIsSet || setNext()
    }

    override fun next(): T {
        if (!hasNext()) {
            throw NoSuchElementException()
        }
        assert(nextIsSet)
        try {
            return next as T
        } finally {
            nextIsSet = false
            next = null
        }
    }

    override fun remove() {
        throw UnsupportedOperationException()
    }

    private fun setNext(): Boolean {
        while (iterator.hasNext()) {
            val `object` = iterator.next()
            if (predicateFunction(`object`)) {
                next = `object`
                nextIsSet = true
                return true
            }
        }
        return false
    }
}
