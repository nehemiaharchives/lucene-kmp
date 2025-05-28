package org.gnit.lucenekmp.codecs.hnsw

import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.index.VectorEncoding.FLOAT32
import org.gnit.lucenekmp.index.VectorEncoding.BYTE
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer.AbstractRandomVectorScorer
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer
import org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer.AbstractUpdateableRandomVectorScorer
import okio.IOException
import org.gnit.lucenekmp.jdkport.System


/**
 * Default implementation of [FlatVectorsScorer].
 *
 * @lucene.experimental
 */
class DefaultFlatVectorScorer : FlatVectorsScorer {
    @Throws(IOException::class)
    override fun getRandomVectorScorerSupplier(
        similarityFunction: VectorSimilarityFunction, vectorValues: KnnVectorValues
    ): RandomVectorScorerSupplier {
        return when (vectorValues.encoding) {
            FLOAT32 -> {
                FloatScoringSupplier(vectorValues as FloatVectorValues, similarityFunction)
            }

            BYTE -> {
                ByteScoringSupplier(vectorValues as ByteVectorValues, similarityFunction)
            }
        }
        /*throw IllegalArgumentException(
            "vectorValues must be an instance of FloatVectorValues or ByteVectorValues, got a "
                    + vectorValues::class.qualifiedName
        )*/
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(
        similarityFunction: VectorSimilarityFunction, vectorValues: KnnVectorValues, target: FloatArray
    ): RandomVectorScorer {
        require(vectorValues is FloatVectorValues)
        require(target.size == vectorValues.dimension()) {
            ("vector query dimension: "
                    + target.size
                    + " differs from field dimension: "
                    + vectorValues.dimension())
        }
        return FloatVectorScorer(vectorValues, target, similarityFunction)
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(
        similarityFunction: VectorSimilarityFunction, vectorValues: KnnVectorValues, target: ByteArray
    ): RandomVectorScorer {
        require(vectorValues is ByteVectorValues)
        require(target.size == vectorValues.dimension()) {
            ("vector query dimension: "
                    + target.size
                    + " differs from field dimension: "
                    + vectorValues.dimension())
        }
        return ByteVectorScorer(vectorValues, target, similarityFunction)
    }

    override fun toString(): String {
        return "DefaultFlatVectorScorer()"
    }

    /** RandomVectorScorerSupplier for bytes vector  */
    private class ByteScoringSupplier(private val vectors: ByteVectorValues,
                                      private val similarityFunction: VectorSimilarityFunction
    ) :
        RandomVectorScorerSupplier {
        private val targetVectors: ByteVectorValues = vectors.copy()

        @Throws(IOException::class)
        override fun scorer(): UpdateableRandomVectorScorer {
            val vector = ByteArray(vectors.dimension())
            return object : AbstractUpdateableRandomVectorScorer(vectors) {
                @Throws(IOException::class)
                override fun setScoringOrdinal(node: Int) {
                    System.arraycopy(targetVectors.vectorValue(node), 0, vector, 0, vector.size)
                }

                @Throws(IOException::class)
                override fun score(node: Int): Float {
                    return similarityFunction.compare(vector, targetVectors.vectorValue(node))
                }
            }
        }

        @Throws(IOException::class)
        override fun copy(): RandomVectorScorerSupplier {
            return ByteScoringSupplier(vectors, similarityFunction)
        }

        override fun toString(): String {
            return "ByteScoringSupplier(similarityFunction=$similarityFunction)"
        }
    }

    /** RandomVectorScorerSupplier for Float vector  */
    private class FloatScoringSupplier(private val vectors: FloatVectorValues,
                                       private val similarityFunction: VectorSimilarityFunction
    ) :
        RandomVectorScorerSupplier {
        private val targetVectors: FloatVectorValues = vectors.copy()

        @Throws(IOException::class)
        override fun scorer(): UpdateableRandomVectorScorer {
            val vector = FloatArray(vectors.dimension())
            return object : AbstractUpdateableRandomVectorScorer(vectors) {
                @Throws(IOException::class)
                override fun score(node: Int): Float {
                    return similarityFunction.compare(vector, targetVectors.vectorValue(node))
                }

                @Throws(IOException::class)
                override fun setScoringOrdinal(node: Int) {
                    System.arraycopy(targetVectors.vectorValue(node), 0, vector, 0, vector.size)
                }
            }
        }

        @Throws(IOException::class)
        override fun copy(): RandomVectorScorerSupplier {
            return FloatScoringSupplier(vectors, similarityFunction)
        }

        override fun toString(): String {
            return "FloatScoringSupplier(similarityFunction=$similarityFunction)"
        }
    }

    /** A [RandomVectorScorer] for float vectors.  */
    private class FloatVectorScorer(
        private val values: FloatVectorValues,
        private val query: FloatArray,
        private val similarityFunction: VectorSimilarityFunction
    ) : AbstractRandomVectorScorer(values) {

        @Throws(IOException::class)
        override fun score(node: Int): Float {
            return similarityFunction.compare(query, values.vectorValue(node))
        }
    }

    /** A [RandomVectorScorer] for byte vectors.  */
    private class ByteVectorScorer(
        private val values: ByteVectorValues,
        private val query: ByteArray,
        private val similarityFunction: VectorSimilarityFunction
    ) : AbstractRandomVectorScorer(values) {

        @Throws(IOException::class)
        override fun score(node: Int): Float {
            return similarityFunction.compare(query, values.vectorValue(node))
        }
    }

    companion object {
        val INSTANCE: DefaultFlatVectorScorer = DefaultFlatVectorScorer()
    }
}
