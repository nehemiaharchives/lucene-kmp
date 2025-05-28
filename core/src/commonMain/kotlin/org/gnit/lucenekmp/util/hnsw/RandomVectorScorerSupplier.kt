package org.gnit.lucenekmp.util.hnsw

import okio.IOException


/** A supplier that creates [RandomVectorScorer] from an ordinal.  */
interface RandomVectorScorerSupplier {
    /**
     * This creates a [UpdateableRandomVectorScorer] for scoring random nodes in batches against
     * an ordinal.
     *
     * @return a new [UpdateableRandomVectorScorer]
     */
    @Throws(IOException::class)
    fun scorer(): UpdateableRandomVectorScorer

    /**
     * Make a copy of the supplier, which will copy the underlying vectorValues so the copy is safe to
     * be used in other threads.
     */
    @Throws(IOException::class)
    fun copy(): RandomVectorScorerSupplier
}
