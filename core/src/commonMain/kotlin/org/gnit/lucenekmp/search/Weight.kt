package org.gnit.lucenekmp.search

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.util.Bits
import kotlin.math.min

private val logger = KotlinLogging.logger {}


/**
 * Expert: Calculate query weights and build query scorers.
 *
 *
 * The purpose of [Weight] is to ensure searching does not modify a [Query], so that
 * a [Query] instance can be reused.
 *
 *
 * [IndexSearcher] dependent state of the query should reside in the [Weight].
 *
 *
 * [org.apache.lucene.index.LeafReader] dependent state should reside in the [ ].
 *
 *
 * Since [Weight] creates [Scorer] instances for a given [ ] ([ ][.scorer]) callers must maintain the relationship
 * between the searcher's top-level [IndexReaderContext] and the context used to create a
 * [Scorer].
 *
 *
 * A `Weight` is used in the following way:
 *
 *
 *  1. A `Weight` is constructed by a top-level query, given a `IndexSearcher
` *  ([Query.createWeight]).
 *  1. A `Scorer` is constructed by [       ][.scorer].
 *
 *
 * @since 2.9
 */
abstract class Weight
/**
 * Sole constructor, typically invoked by sub-classes.
 *
 * @param query the parent query
 */ protected constructor(
    /** The query that this concerns.  */
    open val query: Query
) : SegmentCacheable {
    /**
     * Returns [Matches] for a specific document, or `null` if the document does not match
     * the parent query
     *
     *
     * A query match that contains no position information (for example, a Point or DocValues
     * query) will return [MatchesUtils.MATCH_WITH_NO_TERMS]
     *
     * @param context the reader's context to create the [Matches] for
     * @param doc the document's id relative to the given context's reader
     * @lucene.experimental
     */
    @Throws(IOException::class)
    open fun matches(context: LeafReaderContext, doc: Int): Matches? {
        val scorerSupplier: ScorerSupplier? = scorerSupplier(context)
        if (scorerSupplier == null) {
            return null
        }
        val scorer: Scorer = scorerSupplier.get(1)
        val twoPhase = scorer.twoPhaseIterator()
        if (twoPhase == null) {
            if (scorer.iterator().advance(doc) != doc) {
                return null
            }
        } else {
            if (twoPhase.approximation().advance(doc) != doc || twoPhase.matches() == false) {
                return null
            }
        }
        return MatchesUtils.MATCH_WITH_NO_TERMS
    }

    /**
     * An explanation of the score computation for the named document.
     *
     * @param context the readers context to create the [Explanation] for.
     * @param doc the document's id relative to the given context's reader
     * @return an Explanation for the score
     * @throws IOException if an [IOException] occurs
     */
    @Throws(IOException::class)
    abstract fun explain(context: LeafReaderContext, doc: Int): Explanation

    /**
     * Optional method that delegates to scorerSupplier.
     *
     *
     * Returns a [Scorer] which can iterate in order over all matching documents and assign
     * them a score. A scorer for the same [LeafReaderContext] instance may be requested
     * multiple times as part of a single search call.
     *
     *
     * **NOTE:** null can be returned if no documents will be scored by this query.
     *
     *
     * **NOTE**: The returned [Scorer] does not have [LeafReader.getLiveDocs]
     * applied, they need to be checked on top.
     *
     * @param context the [org.apache.lucene.index.LeafReaderContext] for which to return the
     * [Scorer].
     * @return a [Scorer] which scores documents in/out-of order.
     * @throws IOException if there is a low-level I/O error
     */
    @Throws(IOException::class)
    fun scorer(context: LeafReaderContext): Scorer? {
        val scorerSupplier: ScorerSupplier? = scorerSupplier(context)
        if (scorerSupplier == null) {
            return null
        }
        return scorerSupplier.get(Long.Companion.MAX_VALUE)
    }

    /**
     * Get a [ScorerSupplier], which allows knowing the cost of the [Scorer] before
     * building it. A scorer supplier for the same [LeafReaderContext] instance may be requested
     * multiple times as part of a single search call.
     *
     *
     * **Note:** It must return null if the scorer is null.
     *
     * @param context the leaf reader context
     * @return a [ScorerSupplier] providing the scorer, or null if scorer is null
     * @throws IOException if an IOException occurs
     * @see Scorer
     *
     * @see DefaultScorerSupplier
     */
    @Throws(IOException::class)
    abstract fun scorerSupplier(context: LeafReaderContext): ScorerSupplier?

    /**
     * Helper method that delegates to [.scorerSupplier]. It is implemented
     * as
     *
     * <pre class="prettyprint">
     * ScorerSupplier scorerSupplier = scorerSupplier(context);
     * if (scorerSupplier == null) {
     * // No docs match
     * return null;
     * }
     *
     * scorerSupplier.setTopLevelScoringClause();
     * return scorerSupplier.bulkScorer();
    </pre> *
     *
     * A bulk scorer for the same [LeafReaderContext] instance may be requested multiple times
     * as part of a single search call.
     */
    @Throws(IOException::class)
    fun bulkScorer(context: LeafReaderContext): BulkScorer? {
        val scorerSupplier: ScorerSupplier? = scorerSupplier(context)
        if (scorerSupplier == null) {
            // No docs match
            return null
        }

        scorerSupplier.setTopLevelScoringClause()
        return scorerSupplier.bulkScorer()
    }

    /**
     * Counts the number of live documents that match a given [this.query] in a leaf.
     *
     *
     * The default implementation returns -1 for every query. This indicates that the count could
     * not be computed in sub-linear time.
     *
     *
     * Specific query classes should override it to provide other accurate sub-linear
     * implementations (that actually return the count). Look at [ ][MatchAllDocsQuery.createWeight] for an example
     *
     *
     * We use this property of the function to count hits in [IndexSearcher.count].
     *
     * @param context the [org.apache.lucene.index.LeafReaderContext] for which to return the
     * count.
     * @return integer count of the number of matches
     * @throws IOException if there is a low-level I/O error
     */
    @Throws(IOException::class)
    open fun count(context: LeafReaderContext): Int {
        return -1
    }

    /**
     * A wrap for default scorer supplier.
     *
     * @lucene.internal
     */
    protected class DefaultScorerSupplier(scorer: Scorer) : ScorerSupplier() {
        private val scorer: Scorer

        init {
            this.scorer = requireNotNull<Scorer>(scorer){"Scorer must not be null"}
        }

        @Throws(IOException::class)
        override fun get(leadCost: Long): Scorer {
            return scorer
        }

        override fun cost(): Long {
            return scorer.iterator().cost()
        }
    }

    /**
     * Just wraps a Scorer and performs top scoring using it.
     *
     * @lucene.internal
     */
    class DefaultBulkScorer(scorer: Scorer) : BulkScorer() {
        private val scorer: Scorer
        private var iterator: DocIdSetIterator
        private val twoPhase: TwoPhaseIterator?

        /** Sole constructor.  */
        init {
            this.scorer = requireNotNull<Scorer>(scorer)
            this.twoPhase = scorer.twoPhaseIterator()
            if (twoPhase == null) {
                this.iterator = scorer.iterator()
            } else {
                this.iterator = twoPhase.approximation()
            }
        }

        override fun cost(): Long {
            return iterator!!.cost()
        }

        @Throws(IOException::class)
        override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
            var min = min
            collector.scorer = scorer
            val competitiveIterator: DocIdSetIterator? = collector.competitiveIterator()
            logger.debug {
                "[DefaultBulkScorer.score] start min=$min max=$max " +
                    "iterator=${iterator::class.simpleName} twoPhase=${twoPhase!!::class.simpleName} " +
                    "competitive=${competitiveIterator!!::class.simpleName} acceptDocs=${acceptDocs != null}"
            }

            if (competitiveIterator != null) {
                if (competitiveIterator.docID() > min) {
                    min = competitiveIterator.docID()
                    // The competitive iterator may not match any docs in the range.
                    min = min(min, max)
                }
            }

            if (iterator.docID() < min) {
                if (iterator.docID() == min - 1) {
                    iterator.nextDoc()
                } else {
                    iterator.advance(min)
                }
            }

            // These various specializations help save some null checks in a hot loop, but as importantly
            // if not more importantly, they help reduce the polymorphism of calls sites to nextDoc() and
            // collect() because only a subset of collectors produce a competitive iterator, and the set
            // of implementing classes for two-phase approximations is smaller than the set of doc id set
            // iterator implementations.
            if (twoPhase == null && competitiveIterator == null) {
                // Optimize simple iterators with collectors that can't skip
                scoreIterator(collector, acceptDocs, iterator, max)
            } else if (competitiveIterator == null) {
                scoreTwoPhaseIterator(collector, acceptDocs, iterator, twoPhase!!, max)
            } else if (twoPhase == null) {
                scoreCompetitiveIterator(collector, acceptDocs, iterator, competitiveIterator, max)
            } else {
                scoreTwoPhaseOrCompetitiveIterator(
                    collector, acceptDocs, iterator, twoPhase, competitiveIterator, max
                )
            }

            return iterator.docID()
        }

        companion object {
            private const val LOG_INTERVAL = 1000
            private const val STALL_THRESHOLD = 1000
            private const val ITERATION_HARD_LIMIT = 1_000_000

            @Throws(IOException::class)
            private fun scoreIterator(
                collector: LeafCollector, acceptDocs: Bits?, iterator: DocIdSetIterator, max: Int
            ) {
                var doc = iterator.docID()
                var iterations = 0
                var stallCount = 0
                while (doc < max) {
                    if (iterations % LOG_INTERVAL == 0) {
                        logger.debug { "[DefaultBulkScorer.scoreIterator] doc=$doc max=$max iter=$iterations" }
                    }
                    if (iterations > ITERATION_HARD_LIMIT) {
                        throw IllegalStateException(
                            "DefaultBulkScorer.scoreIterator exceeded iteration limit doc=$doc max=$max iterator=${iterator::class.simpleName}"
                        )
                    }
                    if (iterations < 5) {
                        logger.debug { "[DefaultBulkScorer.scoreIterator] pre-collect doc=$doc" }
                    }
                    if (acceptDocs == null || acceptDocs.get(doc)) {
                        collector.collect(doc)
                    }
                    if (iterations < 5) {
                        logger.debug { "[DefaultBulkScorer.scoreIterator] pre-nextDoc doc=$doc" }
                    }
                    val nextDoc = iterator.nextDoc()
                    if (iterations < 5) {
                        logger.debug { "[DefaultBulkScorer.scoreIterator] post-nextDoc doc=$doc nextDoc=$nextDoc" }
                    }
                    if (nextDoc == doc) {
                        stallCount++
                        if (stallCount >= STALL_THRESHOLD) {
                            throw IllegalStateException(
                                "DefaultBulkScorer.scoreIterator stuck doc=$doc max=$max iterator=${iterator::class.simpleName}"
                            )
                        }
                    } else {
                        stallCount = 0
                    }
                    doc = nextDoc
                    iterations++
                }
            }

            @Throws(IOException::class)
            private fun scoreTwoPhaseIterator(
                collector: LeafCollector,
                acceptDocs: Bits?,
                iterator: DocIdSetIterator,
                twoPhase: TwoPhaseIterator,
                max: Int
            ) {
                var doc = iterator.docID()
                var iterations = 0
                var stallCount = 0
                while (doc < max) {
                    if (iterations % LOG_INTERVAL == 0) {
                        logger.debug { "[DefaultBulkScorer.scoreTwoPhaseIterator] doc=$doc max=$max iter=$iterations" }
                    }
                    if ((acceptDocs == null || acceptDocs.get(doc)) && twoPhase.matches()) {
                        collector.collect(doc)
                    }
                    val nextDoc = iterator.nextDoc()
                    if (nextDoc == doc) {
                        stallCount++
                        if (stallCount >= STALL_THRESHOLD) {
                            throw IllegalStateException(
                                "DefaultBulkScorer.scoreTwoPhaseIterator stuck doc=$doc max=$max iterator=${iterator::class.simpleName}"
                            )
                        }
                    } else {
                        stallCount = 0
                    }
                    doc = nextDoc
                    iterations++
                }
            }

            @Throws(IOException::class)
            private fun scoreCompetitiveIterator(
                collector: LeafCollector,
                acceptDocs: Bits?,
                iterator: DocIdSetIterator,
                competitiveIterator: DocIdSetIterator,
                max: Int
            ) {
                var doc = iterator.docID()
                var iterations = 0
                var stallCount = 0
                while (doc < max) {
                    if (iterations % LOG_INTERVAL == 0) {
                        logger.debug {
                            "[DefaultBulkScorer.scoreCompetitiveIterator] doc=$doc max=$max iter=$iterations " +
                                "competitive=${competitiveIterator.docID()}"
                        }
                    }
                    val prevDoc = doc
                    require(
                        competitiveIterator.docID() <= doc // invariant
                    )
                    if (competitiveIterator.docID() < doc) {
                        val competitiveNext = competitiveIterator.advance(doc)
                        if (competitiveNext != doc) {
                            doc = iterator.advance(competitiveNext)
                            if (doc == prevDoc) {
                                stallCount++
                                if (stallCount >= STALL_THRESHOLD) {
                                    throw IllegalStateException(
                                        "DefaultBulkScorer.scoreCompetitiveIterator advance stuck " +
                                            "doc=$doc prevDoc=$prevDoc competitiveNext=$competitiveNext " +
                                            "iterator=${iterator::class.simpleName} competitive=${competitiveIterator::class.simpleName}"
                                    )
                                }
                            } else {
                                stallCount = 0
                            }
                            continue
                        }
                    }

                    if ((acceptDocs == null || acceptDocs.get(doc))) {
                        collector.collect(doc)
                    }

                    val nextDoc = iterator.nextDoc()
                    if (nextDoc == doc) {
                        stallCount++
                        if (stallCount >= STALL_THRESHOLD) {
                            throw IllegalStateException(
                                "DefaultBulkScorer.scoreCompetitiveIterator stuck doc=$doc max=$max iterator=${iterator::class.simpleName}"
                            )
                        }
                    } else {
                        stallCount = 0
                    }
                    doc = nextDoc
                    iterations++
                }
            }

            @Throws(IOException::class)
            private fun scoreTwoPhaseOrCompetitiveIterator(
                collector: LeafCollector,
                acceptDocs: Bits?,
                iterator: DocIdSetIterator,
                twoPhase: TwoPhaseIterator,
                competitiveIterator: DocIdSetIterator,
                max: Int
            ) {
                var doc = iterator.docID()
                var iterations = 0
                var stallCount = 0
                while (doc < max) {
                    if (iterations % LOG_INTERVAL == 0) {
                        logger.debug {
                            "[DefaultBulkScorer.scoreTwoPhaseOrCompetitiveIterator] doc=$doc max=$max iter=$iterations " +
                                "competitive=${competitiveIterator.docID()}"
                        }
                    }
                    val prevDoc = doc
                    require(
                        competitiveIterator.docID() <= doc // invariant
                    )
                    if (competitiveIterator.docID() < doc) {
                        val competitiveNext = competitiveIterator.advance(doc)
                        if (competitiveNext != doc) {
                            doc = iterator.advance(competitiveNext)
                            if (doc == prevDoc) {
                                stallCount++
                                if (stallCount >= STALL_THRESHOLD) {
                                    throw IllegalStateException(
                                        "DefaultBulkScorer.scoreTwoPhaseOrCompetitiveIterator advance stuck " +
                                            "doc=$doc prevDoc=$prevDoc competitiveNext=$competitiveNext " +
                                            "iterator=${iterator::class.simpleName} competitive=${competitiveIterator::class.simpleName}"
                                    )
                                }
                            } else {
                                stallCount = 0
                            }
                            continue
                        }
                    }

                    if ((acceptDocs == null || acceptDocs.get(doc)) && twoPhase.matches()) {
                        collector.collect(doc)
                    }

                    val nextDoc = iterator.nextDoc()
                    if (nextDoc == doc) {
                        stallCount++
                        if (stallCount >= STALL_THRESHOLD) {
                            throw IllegalStateException(
                                "DefaultBulkScorer.scoreTwoPhaseOrCompetitiveIterator stuck doc=$doc max=$max iterator=${iterator::class.simpleName}"
                            )
                        }
                    } else {
                        stallCount = 0
                    }
                    doc = nextDoc
                    iterations++
                }
            }
        }
    }
}
