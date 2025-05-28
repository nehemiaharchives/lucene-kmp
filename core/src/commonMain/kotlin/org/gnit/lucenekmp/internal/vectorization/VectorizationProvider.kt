package org.gnit.lucenekmp.internal.vectorization

import okio.IOException
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.store.IndexInput

abstract class VectorizationProvider {
    companion object {
        fun getInstance(): VectorizationProvider = DefaultVectorizationProvider()
    }

    /**
     * Returns a singleton (stateless) [VectorUtilSupport] to support SIMD usage in [ ].
     */
    abstract val vectorUtilSupport: VectorUtilSupport

    /** Returns a FlatVectorsScorer that supports the Lucene99 format.  */
    abstract val lucene99FlatVectorsScorer: FlatVectorsScorer

    /** Create a new [PostingDecodingUtil] for the given [IndexInput].  */
    @Throws(IOException::class)
    abstract fun newPostingDecodingUtil(input: IndexInput): PostingDecodingUtil

}
