package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.store.IndexInput

/** Default provider returning scalar implementations.  */
internal class DefaultVectorizationProvider : VectorizationProvider() {
    val vectorUtilSupport: VectorUtilSupport = DefaultVectorUtilSupport()

    val lucene99FlatVectorsScorer: FlatVectorsScorer
        get() = DefaultFlatVectorScorer.INSTANCE

    override fun newPostingDecodingUtil(input: IndexInput): PostingDecodingUtil {
        return PostingDecodingUtil(input)
    }

    override fun getVectorUtilSupport(): VectorUtilSupport {
        return vectorUtilSupport
    }

    override fun getLucene99FlatVectorsScorer(): FlatVectorsScorer {
        return lucene99FlatVectorsScorer
    }
}
