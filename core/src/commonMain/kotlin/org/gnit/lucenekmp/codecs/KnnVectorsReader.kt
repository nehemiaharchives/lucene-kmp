package org.gnit.lucenekmp.codecs

import okio.IOException
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TotalHits
import org.gnit.lucenekmp.util.Bits

/** Reads vectors from an index.  */
abstract class KnnVectorsReader
/** Sole constructor  */
protected constructor() : AutoCloseable {
    /**
     * Checks consistency of this reader.
     *
     *
     * Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
     * against large data files.
     *
     * @lucene.internal
     */
    @Throws(IOException::class)
    abstract fun checkIntegrity()

    /**
     * Returns the [FloatVectorValues] for the given `field`. The behavior is undefined if
     * the given field doesn't have KNN vectors enabled on its [FieldInfo]. The return value is
     * never `null`.
     */
    @Throws(IOException::class)
    abstract fun getFloatVectorValues(field: String): FloatVectorValues?

    /**
     * Returns the [ByteVectorValues] for the given `field`. The behavior is undefined if
     * the given field doesn't have KNN vectors enabled on its [FieldInfo]. The return value is
     * never `null`.
     */
    @Throws(IOException::class)
    abstract fun getByteVectorValues(field: String): ByteVectorValues?

    /**
     * Return the k nearest neighbor documents as determined by comparison of their vector values for
     * this field, to the given vector, by the field's similarity function. The score of each document
     * is derived from the vector similarity in a way that ensures scores are positive and that a
     * larger score corresponds to a higher ranking.
     *
     *
     * The search is allowed to be approximate, meaning the results are not guaranteed to be the
     * true k closest neighbors. For large values of k (for example when k is close to the total
     * number of documents), the search may also retrieve fewer than k documents.
     *
     *
     * The returned [TopDocs] will contain a [ScoreDoc] for each nearest neighbor, in
     * order of their similarity to the query vector (decreasing scores). The [TotalHits]
     * contains the number of documents visited during the search. If the search stopped early because
     * it hit `visitedLimit`, it is indicated through the relation `TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO`.
     *
     *
     * The behavior is undefined if the given field doesn't have KNN vectors enabled on its [ ].
     *
     * @param field the vector field to search
     * @param target the vector-valued query
     * @param knnCollector a KnnResults collector and relevant settings for gathering vector results
     * @param acceptDocs [Bits] that represents the allowed documents to match, or `null`
     * if they are all allowed to match.
     */
    @Throws(IOException::class)
    abstract fun search(
        field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits?
    )

    /**
     * Return the k nearest neighbor documents as determined by comparison of their vector values for
     * this field, to the given vector, by the field's similarity function. The score of each document
     * is derived from the vector similarity in a way that ensures scores are positive and that a
     * larger score corresponds to a higher ranking.
     *
     *
     * The search is allowed to be approximate, meaning the results are not guaranteed to be the
     * true k closest neighbors. For large values of k (for example when k is close to the total
     * number of documents), the search may also retrieve fewer than k documents.
     *
     *
     * The returned [TopDocs] will contain a [ScoreDoc] for each nearest neighbor, in
     * order of their similarity to the query vector (decreasing scores). The [TotalHits]
     * contains the number of documents visited during the search. If the search stopped early because
     * it hit `visitedLimit`, it is indicated through the relation `TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO`.
     *
     *
     * The behavior is undefined if the given field doesn't have KNN vectors enabled on its [ ].
     *
     * @param field the vector field to search
     * @param target the vector-valued query
     * @param knnCollector a KnnResults collector and relevant settings for gathering vector results
     * @param acceptDocs [Bits] that represents the allowed documents to match, or `null`
     * if they are all allowed to match.
     */
    @Throws(IOException::class)
    abstract fun search(
        field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits?
    )

    open val mergeInstance: KnnVectorsReader
        /**
         * Returns an instance optimized for merging. This instance may only be consumed in the thread
         * that called [.getMergeInstance].
         *
         *
         * The default implementation returns `this`
         */
        get() = this

    /**
     * Optional: reset or close merge resources used in the reader
     *
     *
     * The default implementation is empty
     */
    @Throws(IOException::class)
    open fun finishMerge() {
    }
}
