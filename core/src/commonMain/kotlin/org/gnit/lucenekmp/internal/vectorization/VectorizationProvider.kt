package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer

abstract class VectorizationProvider {
    companion object {
        fun getInstance(): VectorizationProvider = DefaultVectorizationProvider()
    }

    /**
     * Returns a singleton (stateless) [VectorUtilSupport] to support SIMD usage in [ ].
     */
    abstract fun getVectorUtilSupport(): VectorUtilSupport

    /** Returns a FlatVectorsScorer that supports the Lucene99 format.  */
    abstract fun getLucene99FlatVectorsScorer(): FlatVectorsScorer
}
