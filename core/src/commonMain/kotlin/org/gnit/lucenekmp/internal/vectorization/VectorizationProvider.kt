package org.gnit.lucenekmp.internal.vectorization

import kotlinx.io.IOException
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.store.IndexInput

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

    /** Create a new [PostingDecodingUtil] for the given [IndexInput].  */
    @Throws(IOException::class)
    abstract fun newPostingDecodingUtil(input: IndexInput): PostingDecodingUtil

}
