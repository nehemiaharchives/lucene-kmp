package org.gnit.lucenekmp.codecs.hnsw


import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier
import kotlinx.io.IOException

/**
 * Provides mechanisms to score vectors that are stored in a flat file The purpose of this class is
 * for providing flexibility to the codec utilizing the vectors
 *
 * @lucene.experimental
 */
interface FlatVectorsScorer {
    /**
     * Returns a [RandomVectorScorerSupplier] that can be used to score vectors
     *
     * @param similarityFunction the similarity function to use
     * @param vectorValues the vector values to score
     * @return a [RandomVectorScorerSupplier] that can be used to score vectors
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    fun getRandomVectorScorerSupplier(
        similarityFunction: VectorSimilarityFunction, vectorValues: KnnVectorValues
    ): RandomVectorScorerSupplier

    /**
     * Returns a [RandomVectorScorer] for the given set of vectors and target vector.
     *
     * @param similarityFunction the similarity function to use
     * @param vectorValues the vector values to score
     * @param target the target vector
     * @return a [RandomVectorScorer] for the given field and target vector.
     * @throws IOException if an I/O error occurs when reading from the index.
     */
    @Throws(IOException::class)
    fun getRandomVectorScorer(
        similarityFunction: VectorSimilarityFunction, vectorValues: KnnVectorValues, target: FloatArray
    ): RandomVectorScorer

    /**
     * Returns a [RandomVectorScorer] for the given set of vectors and target vector.
     *
     * @param similarityFunction the similarity function to use
     * @param vectorValues the vector values to score
     * @param target the target vector
     * @return a [RandomVectorScorer] for the given field and target vector.
     * @throws IOException if an I/O error occurs when reading from the index.
     */
    @Throws(IOException::class)
    fun getRandomVectorScorer(
        similarityFunction: VectorSimilarityFunction, vectorValues: KnnVectorValues, target: ByteArray
    ): RandomVectorScorer
}
