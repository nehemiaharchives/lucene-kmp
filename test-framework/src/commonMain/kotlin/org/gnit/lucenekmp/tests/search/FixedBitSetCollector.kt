package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.CollectorManager
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.SimpleCollector
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.FixedBitSet

/** Collector that accumulates matching docs in a [FixedBitSet]  */
class FixedBitSetCollector internal constructor(maxDoc: Int) : SimpleCollector() {
    private val bitSet: FixedBitSet = FixedBitSet(maxDoc)

    private var docBase = 0

    @Throws(IOException::class)
    override fun doSetNextReader(context: LeafReaderContext) {
        docBase = context.docBase
    }

    @Throws(IOException::class)
    override fun collect(doc: Int) {
        bitSet.set(docBase + doc)
    }

    override fun scoreMode(): ScoreMode {
        return ScoreMode.COMPLETE_NO_SCORES
    }

    override var weight: Weight? = null

    companion object {
        /**
         * Creates a [CollectorManager] that can concurrently collect matching docs in a [ ]
         */
        fun createManager(maxDoc: Int): CollectorManager<FixedBitSetCollector, FixedBitSet> {
            return object :
                CollectorManager<FixedBitSetCollector, FixedBitSet> {
                override fun newCollector(): FixedBitSetCollector {
                    return FixedBitSetCollector(maxDoc)
                }

                override fun reduce(collectors: MutableCollection<FixedBitSetCollector>): FixedBitSet {
                    val reduced = FixedBitSet(maxDoc)
                    for (collector in collectors) {
                        reduced.or(collector.bitSet)
                    }
                    return reduced
                }
            }
        }
    }
}
