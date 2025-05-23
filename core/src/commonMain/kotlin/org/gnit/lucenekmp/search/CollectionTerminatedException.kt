package org.gnit.lucenekmp.search

/**
 * Throw this exception in [LeafCollector.collect] to prematurely terminate collection of
 * the current leaf.
 *
 *
 * Note: IndexSearcher swallows this exception and never re-throws it. As a consequence, you
 * should not catch it when calling the different search methods that [IndexSearcher] exposes
 * as it is unnecessary and might hide misuse of this exception.
 */
class CollectionTerminatedException
/** Sole constructor.  */
    : RuntimeException() {
    fun fillInStackTrace(): Throwable {
        // never re-thrown so we can save the expensive stacktrace
        return this
    }
}
