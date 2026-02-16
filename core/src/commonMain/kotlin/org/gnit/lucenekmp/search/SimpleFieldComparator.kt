package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * Base [FieldComparator] implementation that is used for all contexts.
 *
 * @lucene.experimental
 */
abstract class SimpleFieldComparator<T> : FieldComparator<T>(), LeafFieldComparator {
    /** This method is called before collecting `context`.  */
    @Throws(IOException::class)
    protected abstract fun doSetNextReader(context: LeafReaderContext)

    @Throws(IOException::class)
    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
        doSetNextReader(context)
        return this
    }

    @Throws(IOException::class)
    override fun setScorer(scorer: Scorable) {
    }
}
