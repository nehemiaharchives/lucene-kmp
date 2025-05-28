package org.gnit.lucenekmp.search

import okio.IOException

/**
 * A stream of doc IDs. Most methods on [DocIdStream]s are terminal, meaning that the [ ] may not be further used.
 *
 * @see LeafCollector.collect
 * @lucene.experimental
 */
abstract class DocIdStream
/** Sole constructor, for invocation by sub classes.  */
protected constructor() {
    /**
     * Iterate over doc IDs contained in this stream in order, calling the given [ ] on them. This is a terminal operation.
     */
    @Throws(IOException::class)
    abstract fun forEach(consumer: CheckedIntConsumer<IOException>)

    /** Count the number of entries in this stream. This is a terminal operation.  */
    @Throws(IOException::class)
    open fun count(): Int {
        val count = IntArray(1)
        forEach(CheckedIntConsumer { `_` -> count[0]++ })
        return count[0]
    }
}
