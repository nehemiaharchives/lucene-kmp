package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext


/**
 * Just counts the total number of hits. This is the collector behind [IndexSearcher.count].
 * When the [Weight] implements [Weight.count], this collector will skip collecting
 * segments.
 */
open class TotalHitCountCollector : Collector {
    override var weight: Weight? = null

    /** Returns how many hits matched the search.  */
    var totalHits: Int = 0
        private set

    override fun scoreMode(): ScoreMode {
        return ScoreMode.COMPLETE_NO_SCORES
    }

    /*override fun setWeight(weight: Weight?) {
        this.weight = weight
    }*/

    @Throws(IOException::class)
    override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
        val leafCount = if (weight == null) -1 else weight!!.count(context)
        if (leafCount != -1) {
            totalHits += leafCount
            throw CollectionTerminatedException()
        }
        return createLeafCollector()
    }

    protected fun createLeafCollector(): LeafCollector {
        return object : LeafCollector {
            override var scorer: Scorable?
                get() {
                    throw UnsupportedOperationException("Scoring is not supported by TotalHitCountCollector")
                }
                set(scorer) {}

            override fun collect(doc: Int) {
                totalHits++
            }

            @Throws(IOException::class)
            override fun collect(stream: DocIdStream) {
                totalHits += stream.count()
            }
        }
    }
}
