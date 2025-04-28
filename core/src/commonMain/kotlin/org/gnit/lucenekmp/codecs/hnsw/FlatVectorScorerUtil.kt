package org.gnit.lucenekmp.codecs.hnsw

import org.gnit.lucenekmp.internal.vectorization.VectorizationProvider


/**
 * Utilities for [FlatVectorsScorer].
 *
 * @lucene.experimental
 */
object FlatVectorScorerUtil {
    private val IMPL: VectorizationProvider = VectorizationProvider.getInstance()

    /**
     * Returns a FlatVectorsScorer that supports the Lucene99 format. Scorers retrieved through this
     * method may be optimized on certain platforms. Otherwise, a DefaultFlatVectorScorer is returned.
     */
    fun getLucene99FlatVectorsScorer() = IMPL.lucene99FlatVectorsScorer
}
