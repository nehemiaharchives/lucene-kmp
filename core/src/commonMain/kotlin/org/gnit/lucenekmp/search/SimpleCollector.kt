package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * Base [Collector] implementation that is used to collect all contexts.
 *
 * @lucene.experimental
 */
abstract class SimpleCollector : Collector, LeafCollector {
    override var scorer: Scorable? = null
    override var weight: Weight? = null

    override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
        doSetNextReader(context)
        return this
    }

    /** This method is called before collecting [context]. */
    @Throws(IOException::class)
    protected open fun doSetNextReader(context: LeafReaderContext) {}

    @Throws(IOException::class)
    abstract override fun collect(doc: Int)

    override fun scoreMode(): ScoreMode = ScoreMode.COMPLETE
}
