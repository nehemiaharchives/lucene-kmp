package org.gnit.lucenekmp.internal.vectorization

import okio.IOException
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.store.IndexInput

abstract class VectorizationProvider {
    companion object {
        private val VALID_CALLERS: Set<String> = setOf(
            "org.gnit.lucenekmp.codecs.hnsw.FlatVectorScorerUtil",
            "org.gnit.lucenekmp.util.VectorUtil",
            "org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsReader",
            "org.gnit.lucenekmp.codecs.lucene101.PostingIndexInput",
            "org.gnit.lucenekmp.internal.vectorization.BaseVectorizationTestCase",
            "org.gnit.lucenekmp.internal.vectorization.TestPostingDecodingUtil",
            "org.gnit.lucenekmp.internal.vectorization.TestVectorScorer"
        )

        fun getInstance(): VectorizationProvider {
            ensureCaller()
            return DefaultVectorizationProvider()
        }

        private fun ensureCaller() {
            val validCaller = hasValidVectorizationCallerPlatform(VALID_CALLERS)
            if (!validCaller) {
                throw UnsupportedOperationException(
                    "VectorizationProvider is internal and can only be used by known Lucene classes."
                )
            }
        }
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
