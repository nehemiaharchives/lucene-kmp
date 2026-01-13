package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.TermStatistics

/**
 * Provides the ability to use a different [Similarity] for different fields.
 *
 *
 * Subclasses should implement [.get] to return an appropriate Similarity (for
 * example, using field-specific parameter values) for the field.
 *
 * @lucene.experimental
 */
abstract class PerFieldSimilarityWrapper
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
    : Similarity() {
    override fun computeNorm(state: FieldInvertState): Long {
        return get(state.name!!).computeNorm(state)
    }

    override fun scorer(
        boost: Float,
        collectionStats: CollectionStatistics,
        vararg termStats: TermStatistics
    ): SimScorer {
        return get(collectionStats.field!!).scorer(boost, collectionStats, *termStats)
    }

    /** Returns a [Similarity] for scoring a field.  */
    abstract fun get(name: String): Similarity
}
