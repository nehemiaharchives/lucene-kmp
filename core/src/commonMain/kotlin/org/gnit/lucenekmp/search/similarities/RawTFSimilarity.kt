package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.TermStatistics

/** Similarity that returns the raw TF as score. */
class RawTFSimilarity : Similarity {
    constructor() : super()

    constructor(discountOverlaps: Boolean) : super(discountOverlaps)

    override fun scorer(
        boost: Float,
        collectionStats: CollectionStatistics,
        vararg termStats: TermStatistics
    ): SimScorer {
        return object : SimScorer() {
            override fun score(freq: Float, norm: Long): Float {
                return boost * freq
            }
        }
    }
}
