package org.gnit.lucenekmp.codecs.hnsw


import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import okio.IOException

/**
 * Reads vectors from an index. When searching this reader, it iterates every vector in the index
 * and scores them
 *
 *
 * This class is useful when:
 *
 *
 *  * the number of vectors is small
 *  * when used along side some additional indexing structure that can be used to better search
 * the vectors (like HNSW).
 *
 *
 * @lucene.experimental
 */
abstract class FlatVectorsReader
/** Sole constructor  */ protected constructor(
    /** Scorer for flat vectors  */
    val vectorScorer: FlatVectorsScorer
) : KnnVectorsReader(), Accountable {
    /**
     * @return the [FlatVectorsScorer] for this reader.
     */
    fun getFlatVectorScorer(): FlatVectorsScorer {
        return vectorScorer
    }

    @Throws(IOException::class)
    override fun search(field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits) {
        // don't scan stored field data. If we didn't index it, produce no search results
    }

    @Throws(IOException::class)
    override fun search(field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits) {
        // don't scan stored field data. If we didn't index it, produce no search results
    }

    /**
     * Returns a [RandomVectorScorer] for the given field and target vector.
     *
     * @param field the field to search
     * @param target the target vector
     * @return a [RandomVectorScorer] for the given field and target vector.
     * @throws IOException if an I/O error occurs when reading from the index.
     */
    @Throws(IOException::class)
    abstract fun getRandomVectorScorer(field: String, target: FloatArray): RandomVectorScorer

    /**
     * Returns a [RandomVectorScorer] for the given field and target vector.
     *
     * @param field the field to search
     * @param target the target vector
     * @return a [RandomVectorScorer] for the given field and target vector.
     * @throws IOException if an I/O error occurs when reading from the index.
     */
    @Throws(IOException::class)
    abstract fun getRandomVectorScorer(field: String, target: ByteArray): RandomVectorScorer

    /**
     * Returns an instance optimized for merging. This instance may only be consumed in the thread
     * that called [.getMergeInstance].
     *
     *
     * The default implementation returns `this`
     */
    override val mergeInstance: FlatVectorsReader = this
}
