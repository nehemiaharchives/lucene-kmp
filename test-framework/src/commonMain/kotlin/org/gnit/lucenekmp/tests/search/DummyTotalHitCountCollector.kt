package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.Collector
import org.gnit.lucenekmp.search.CollectorManager
import org.gnit.lucenekmp.search.LeafCollector
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.search.Weight

/**
 * A dummy version of [org.gnit.lucenekmp.search.TotalHitCountCollector] that doesn't shortcut
 * using [Weight.count].
 */
class DummyTotalHitCountCollector : Collector {
    private var totalHits = 0

    /** Get the number of hits. */
    fun getTotalHits(): Int {
        return totalHits
    }

    override fun scoreMode(): ScoreMode {
        return ScoreMode.COMPLETE_NO_SCORES
    }

    @Throws(IOException::class)
    override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
        return object : LeafCollector {
            override var scorer: Scorable? = null

            @Throws(IOException::class)
            override fun collect(doc: Int) {
                totalHits++
            }
        }
    }

    override var weight: Weight? = null

    companion object {
        /** Create a collector manager. */
        fun createManager(): CollectorManager<DummyTotalHitCountCollector, Int> {
            return object : CollectorManager<DummyTotalHitCountCollector, Int> {
                @Throws(IOException::class)
                override fun newCollector(): DummyTotalHitCountCollector {
                    return DummyTotalHitCountCollector()
                }

                @Throws(IOException::class)
                override fun reduce(collectors: MutableCollection<DummyTotalHitCountCollector>): Int {
                    var sum = 0
                    for (coll in collectors) {
                        sum += coll.totalHits
                    }
                    return sum
                }
            }
        }
    }
}
