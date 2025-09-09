package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * Port of Lucene's MultiCollector that dispatches collection to multiple collectors.
 */
class MultiCollector private constructor(private val collectors: Array<Collector>) : Collector {

    companion object {
        /** Wraps the given collectors, ignoring nulls and collapsing to a single collector if possible. */
        fun wrap(collectors: Iterable<Collector?>): Collector {
            val filtered = collectors.filterNotNull()
            require(filtered.isNotEmpty()) { "At least 1 collector must not be null" }
            return if (filtered.size == 1) {
                filtered[0]
            } else {
                MultiCollector(filtered.toTypedArray())
            }
        }

        fun wrap(vararg collectors: Collector?): Collector {
            return wrap(collectors.asList())
        }
    }

    override fun scoreMode(): ScoreMode {
        var scoreMode: ScoreMode? = null
        for (c in collectors) {
            scoreMode = when {
                scoreMode == null -> c.scoreMode()
                scoreMode != c.scoreMode() -> {
                    if (scoreMode!!.needsScores() || c.scoreMode().needsScores()) {
                        ScoreMode.COMPLETE
                    } else {
                        ScoreMode.COMPLETE_NO_SCORES
                    }
                }
                else -> scoreMode
            }
        }
        return scoreMode ?: ScoreMode.COMPLETE
    }

    @Throws(IOException::class)
    override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
        val leafCollectors = mutableListOf<LeafCollector>()
        for (collector in collectors) {
            try {
                leafCollectors.add(collector.getLeafCollector(context))
            } catch (_: CollectionTerminatedException) {
                // ignore collectors that do not need this segment
            }
        }
        if (leafCollectors.isEmpty()) {
            throw CollectionTerminatedException()
        }
        val delegates = leafCollectors.toTypedArray()
        return object : LeafCollector {
            override var scorer: Scorable? = null
                set(value) {
                    field = value
                    for (lc in delegates) {
                        lc.scorer = value
                    }
                }

            @Throws(IOException::class)
            override fun collect(doc: Int) {
                for (lc in delegates) {
                    lc.collect(doc)
                }
            }
        }
    }

    override var weight: Weight? = null
        set(value) {
            field = value
            for (collector in collectors) {
                collector.weight = value
            }
        }

    /** Provides access to the wrapped collectors for advanced use-cases. */
    fun getCollectors(): Array<Collector> = collectors
}

