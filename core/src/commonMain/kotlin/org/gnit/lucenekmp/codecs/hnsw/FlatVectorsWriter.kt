package org.gnit.lucenekmp.codecs.hnsw


import org.gnit.lucenekmp.codecs.KnnVectorsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.util.hnsw.CloseableRandomVectorScorerSupplier
import okio.IOException

/**
 * Vectors' writer for a field that allows additional indexing logic to be implemented by the caller
 *
 * @lucene.experimental
 */
abstract class FlatVectorsWriter
/** Sole constructor  */ protected constructor(
    /** Scorer for flat vectors  */
    val vectorScorer: FlatVectorsScorer
) : KnnVectorsWriter() {
    /**
     * @return the [FlatVectorsScorer] for this reader.
     */

    fun getFlatVectorScorer(): FlatVectorsScorer {
        return vectorScorer
    }

    /**
     * Add a new field for indexing
     *
     * @param fieldInfo fieldInfo of the field to add
     * @return a writer for the field
     * @throws IOException if an I/O error occurs when adding the field
     */
    @Throws(IOException::class)
    abstract override fun addField(fieldInfo: FieldInfo): FlatFieldVectorsWriter<*>

    /**
     * Write the field for merging, providing a scorer over the newly merged flat vectors. This way
     * any additional merging logic can be implemented by the user of this class.
     *
     * @param fieldInfo fieldInfo of the field to merge
     * @param mergeState mergeState of the segments to merge
     * @return a scorer over the newly merged flat vectors, which should be closed as it holds a
     * temporary file handle to read over the newly merged vectors
     * @throws IOException if an I/O error occurs when merging
     */
    @Throws(IOException::class)
    abstract fun mergeOneFieldToIndex(
        fieldInfo: FieldInfo, mergeState: MergeState
    ): CloseableRandomVectorScorerSupplier
}
