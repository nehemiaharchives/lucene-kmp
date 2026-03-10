package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * A [Collector] which allows running a search with several [Collector]s. It offers a
 * static [wrap] method which accepts a list of collectors and wraps them with [
 * MultiCollector], while filtering out the `null` ones.
 *
 * <p><b>NOTE:</b>When mixing collectors that want to skip low-scoring hits ([
 * ScoreMode.TOP_SCORES]) with ones that require to see all hits, such as mixing [
 * TopScoreDocCollector] and [TotalHitCountCollector], it should be faster to run the query
 * twice, once for each collector, rather than using this wrapper on a single search.
 */
class MultiCollector private constructor(private val collectors: Array<Collector>) : Collector {
    private class MultiLeafCollector(
        collectors: MutableList<LeafCollector>,
        private val skipNonCompetitiveScores: Boolean
    ) : LeafCollector {
        private var collectors: Array<LeafCollector?> = arrayOf()
        private var minScores: FloatArray? = null

        init {
            this.collectors = collectors.toTypedArray()
            this.minScores = if (this.skipNonCompetitiveScores) FloatArray(this.collectors.size) else null
        }

        override var scorer: Scorable?
            get() = throw UnsupportedOperationException()
            set(value) {
                var scorer = value!!
                if (skipNonCompetitiveScores) {
                    for (i in collectors.indices) {
                        val c: LeafCollector? = collectors[i]
                        if (c != null) {
                            c.scorer = MinCompetitiveScoreAwareScorable(scorer, i, minScores!!)
                        }
                    }
                } else {
                    scorer =
                        object : FilterScorable(scorer) {
                            override var minCompetitiveScore: Float
                                get() = super.minCompetitiveScore
                                set(minScore) {
                                    // Ignore calls to setMinCompetitiveScore so that if we wrap two
                                    // collectors and one of them wants to skip low-scoring hits, then
                                    // the other collector still sees all hits.
                                }
                        }
                    for (i in collectors.indices) {
                        val c: LeafCollector? = collectors[i]
                        if (c != null) {
                            c.scorer = scorer
                        }
                    }
                }
            }

        // NOTE: not propagating collect(DocIdStream) since DocIdStreams may only be consumed once.
        @Throws(IOException::class)
        override fun collect(doc: Int) {
            for (i in collectors.indices) {
                val collector = collectors[i]
                if (collector != null) {
                    try {
                        collector.collect(doc)
                    } catch (_: CollectionTerminatedException) {
                        collectors[i]!!.finish()
                        collectors[i] = null
                        if (allCollectorsTerminated()) {
                            throw CollectionTerminatedException()
                        }
                    }
                }
            }
        }

        @Throws(IOException::class)
        override fun finish() {
            for (collector in collectors) {
                if (collector != null) {
                    collector.finish()
                }
            }
        }

        private fun allCollectorsTerminated(): Boolean {
            for (i in collectors.indices) {
                if (collectors[i] != null) {
                    return false
                }
            }
            return true
        }
    }

    class MinCompetitiveScoreAwareScorable(`in`: Scorable, private val idx: Int, private val minScores: FloatArray) : FilterScorable(`in`) {
        override var minCompetitiveScore: Float
            get() = super.minCompetitiveScore
            set(minScore) {
                if (minScore > minScores[idx]) {
                    minScores[idx] = minScore
                    `in`.minCompetitiveScore = minScore()
                }
            }

        private fun minScore(): Float {
            var min = Float.MAX_VALUE
            for (i in minScores.indices) {
                if (minScores[i] < min) {
                    min = minScores[i]
                }
            }
            return min
        }
    }

    private val cacheScores: Boolean

    init {
        var numNeedsScores = 0
        for (collector in collectors) {
            if (collector.scoreMode().needsScores()) {
                numNeedsScores += 1
            }
        }
        this.cacheScores = numNeedsScores >= 2
    }

    override fun scoreMode(): ScoreMode {
        var scoreMode: ScoreMode? = null
        for (collector in collectors) {
            if (scoreMode == null) {
                scoreMode = collector.scoreMode()
            } else if (scoreMode != collector.scoreMode()) {
                // If score modes disagree, we don't try to be smart and just use one of the COMPLETE score
                // modes depending on whether scores are needed or not.
                scoreMode = if (scoreMode.needsScores() || collector.scoreMode().needsScores()) {
                    ScoreMode.COMPLETE
                } else {
                    ScoreMode.COMPLETE_NO_SCORES
                }
            }
        }
        return scoreMode!!
    }

    @Throws(IOException::class)
    override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
        val leafCollectors = mutableListOf<LeafCollector>()
        var leafScoreMode: ScoreMode? = null
        for (collector in collectors) {
            val leafCollector: LeafCollector = try {
                collector.getLeafCollector(context)
            } catch (_: CollectionTerminatedException) {
                // this leaf collector does not need this segment
                continue
            }
            if (leafScoreMode == null) {
                leafScoreMode = collector.scoreMode()
            } else if (leafScoreMode != collector.scoreMode()) {
                leafScoreMode = ScoreMode.COMPLETE
            }
            leafCollectors.add(leafCollector)
        }
        if (leafCollectors.isEmpty()) {
            throw CollectionTerminatedException()
        } else {
            // Wraps single leaf collector that wants to skip low-scoring hits (ScoreMode.TOP_SCORES)
            // but the global score mode doesn't allow it.
            if (leafCollectors.size == 1 &&
                (scoreMode() == ScoreMode.TOP_SCORES || leafScoreMode != ScoreMode.TOP_SCORES)
            ) {
                return leafCollectors[0]
            }
            var collector: LeafCollector = MultiLeafCollector(leafCollectors, scoreMode() == ScoreMode.TOP_SCORES)
            if (cacheScores) {
                collector = ScoreCachingWrappingScorer.wrap(collector)
            }
            return collector
        }
    }

    override var weight: Weight? = null
        set(value) {
            field = value
            for (collector in collectors) {
                collector.weight = value
            }
        }

    /** Provides access to the wrapped `Collector`s for advanced use-cases */
    fun getCollectors(): Array<Collector> {
        return collectors
    }

    companion object {
        /** See [wrap]. */
        fun wrap(vararg collectors: Collector?): Collector {
            return wrap(collectors.asList())
        }

        /**
         * Wraps a list of [Collector]s with a [MultiCollector]. This method works as follows:
         *
         * <ul>
         *   <li>Filters out the `null` collectors, so they are not used during search time.
         *   <li>If the input contains 1 real collector (i.e. non-`null` ), it is returned.
         *   <li>Otherwise the method returns a [MultiCollector] which wraps the non-`null`
         *       ones.
         * </ul>
         *
         * @throws IllegalArgumentException if either 0 collectors were input, or all collectors are
         *     `null`.
         */
        fun wrap(collectors: Iterable<Collector?>): Collector {
            // For the user's convenience, we allow null collectors to be passed.
            // However, to improve performance, these null collectors are found
            // and dropped from the array we save for actual collection time.
            var n = 0
            for (c in collectors) {
                if (c != null) {
                    n++
                }
            }

            if (n == 0) {
                throw IllegalArgumentException("At least 1 collector must not be null")
            } else if (n == 1) {
                // only 1 Collector - return it.
                var col: Collector? = null
                for (c in collectors) {
                    if (c != null) {
                        col = c
                        break
                    }
                }
                return col!!
            } else {
                val colls = arrayOfNulls<Collector>(n)
                n = 0
                for (c in collectors) {
                    if (c != null) {
                        colls[n++] = c
                    }
                }
                @Suppress("UNCHECKED_CAST")
                return MultiCollector(colls as Array<Collector>)
            }
        }
    }
}
