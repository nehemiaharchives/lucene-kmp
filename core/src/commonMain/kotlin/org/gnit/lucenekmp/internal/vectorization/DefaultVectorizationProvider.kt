package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil
import org.gnit.lucenekmp.store.IndexInput

/** Default provider returning scalar implementations.  */
internal class DefaultVectorizationProvider : VectorizationProvider() {
    val vectorUtilSupport: VectorUtilSupport

    init {
        vectorUtilSupport = DefaultVectorUtilSupport()
    }

    /*val lucene99FlatVectorsScorer: FlatVectorsScorer
        get() = DefaultFlatVectorScorer.INSTANCE*/

    fun newPostingDecodingUtil(input: IndexInput): PostingDecodingUtil? {
        return PostingDecodingUtil(input)
    }

    override fun getVectorUtilSupport(): VectorUtilSupport {
        return vectorUtilSupport
    }
}
