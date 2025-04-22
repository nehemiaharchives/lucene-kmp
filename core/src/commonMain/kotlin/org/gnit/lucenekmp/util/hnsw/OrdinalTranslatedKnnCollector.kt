package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TotalHits

/**
 * Wraps a provided KnnCollector object, translating the provided vectorId ordinal to a documentId
 */
class OrdinalTranslatedKnnCollector(collector: KnnCollector, private val vectorOrdinalToDocId: IntToIntFunction) :
    KnnCollector.Decorator(collector) {

    override fun collect(vectorId: Int, similarity: Float): Boolean {
        return super.collect(vectorOrdinalToDocId.apply(vectorId), similarity)
    }

    override fun topDocs(): TopDocs {
        val td: TopDocs = super.topDocs()
        return TopDocs(
            TotalHits(
                visitedCount(),
                if (this.earlyTerminated())
                    TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
                else
                    TotalHits.Relation.EQUAL_TO
            ),
            td.scoreDocs
        )
    }
}
