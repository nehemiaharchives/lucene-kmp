package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.store.IndexInput

/** Default provider returning scalar implementations.  */
class DefaultVectorizationProvider : VectorizationProvider() {
    override val vectorUtilSupport: VectorUtilSupport = DefaultVectorUtilSupport()

    override val lucene99FlatVectorsScorer: FlatVectorsScorer
        get() = DefaultFlatVectorScorer.INSTANCE

    override fun newPostingDecodingUtil(input: IndexInput): PostingDecodingUtil {
        return PostingDecodingUtil(input)
    }
}
