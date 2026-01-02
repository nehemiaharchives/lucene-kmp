package org.gnit.lucenekmp.codecs.hnsw

import okio.IOException
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer
import org.gnit.lucenekmp.util.quantization.QuantizedByteVectorValues
import org.gnit.lucenekmp.util.quantization.ScalarQuantizedVectorSimilarity
import org.gnit.lucenekmp.util.quantization.ScalarQuantizer

/**
 * Default scalar quantized implementation of [FlatVectorsScorer].
 *
 * @lucene.experimental
 */
class ScalarQuantizedVectorScorer(
    private val nonQuantizedDelegate: FlatVectorsScorer
) : FlatVectorsScorer {

    companion object {
        fun quantizeQuery(
            query: FloatArray,
            quantizedQuery: ByteArray,
            similarityFunction: VectorSimilarityFunction,
            scalarQuantizer: ScalarQuantizer
        ): Float {
            val processedQuery = when (similarityFunction) {
                VectorSimilarityFunction.EUCLIDEAN,
                VectorSimilarityFunction.DOT_PRODUCT,
                VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT -> query

                VectorSimilarityFunction.COSINE -> {
                    val queryCopy = ArrayUtil.copyArray(query)
                    VectorUtil.l2normalize(queryCopy)
                    queryCopy
                }
            }
            return scalarQuantizer.quantize(processedQuery, quantizedQuery, similarityFunction)
        }
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorerSupplier(
        similarityFunction: VectorSimilarityFunction,
        vectorValues: KnnVectorValues
    ): RandomVectorScorerSupplier {
        return if (vectorValues is QuantizedByteVectorValues) {
            ScalarQuantizedRandomVectorScorerSupplier(
                similarityFunction,
                vectorValues.scalarQuantizer,
                vectorValues
            )
        } else {
            // It is possible to get to this branch during initial indexing and flush
            nonQuantizedDelegate.getRandomVectorScorerSupplier(similarityFunction, vectorValues)
        }
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(
        similarityFunction: VectorSimilarityFunction,
        vectorValues: KnnVectorValues,
        target: FloatArray
    ): RandomVectorScorer {
        return if (vectorValues is QuantizedByteVectorValues) {
            val scalarQuantizer = vectorValues.scalarQuantizer
            val targetBytes = ByteArray(target.size)
            val offsetCorrection = quantizeQuery(target, targetBytes, similarityFunction, scalarQuantizer)
            val scalarQuantizedVectorSimilarity =
                ScalarQuantizedVectorSimilarity.fromVectorSimilarity(
                    similarityFunction,
                    scalarQuantizer.constantMultiplier,
                    scalarQuantizer.bits
                )
            object : RandomVectorScorer.AbstractRandomVectorScorer(vectorValues) {
                @Throws(IOException::class)
                override fun score(node: Int): Float {
                    val nodeVector = vectorValues.vectorValue(node)
                    val nodeOffset = vectorValues.getScoreCorrectionConstant(node)
                    return scalarQuantizedVectorSimilarity.score(
                        targetBytes,
                        offsetCorrection,
                        nodeVector,
                        nodeOffset
                    )
                }
            }
        } else {
            // It is possible to get to this branch during initial indexing and flush
            nonQuantizedDelegate.getRandomVectorScorer(similarityFunction, vectorValues, target)
        }
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(
        similarityFunction: VectorSimilarityFunction,
        vectorValues: KnnVectorValues,
        target: ByteArray
    ): RandomVectorScorer {
        return nonQuantizedDelegate.getRandomVectorScorer(similarityFunction, vectorValues, target)
    }

    override fun toString(): String {
        return "ScalarQuantizedVectorScorer(nonQuantizedDelegate=$nonQuantizedDelegate)"
    }

    /**
     * Quantized vector scorer supplier
     *
     * @lucene.experimental
     */
    class ScalarQuantizedRandomVectorScorerSupplier private constructor(
        private val similarity: ScalarQuantizedVectorSimilarity,
        private val vectorSimilarityFunction: VectorSimilarityFunction,
        private val values: QuantizedByteVectorValues
    ) : RandomVectorScorerSupplier {

        constructor(
            similarityFunction: VectorSimilarityFunction,
            scalarQuantizer: ScalarQuantizer,
            values: QuantizedByteVectorValues
        ) : this(
            ScalarQuantizedVectorSimilarity.fromVectorSimilarity(
                similarityFunction,
                scalarQuantizer.constantMultiplier,
                scalarQuantizer.bits
            ),
            similarityFunction,
            values
        )

        @Throws(IOException::class)
        override fun scorer(): UpdateableRandomVectorScorer {
            val vectorsCopy = values.copy()
            val queryVector = ByteArray(values.dimension())
            return object : UpdateableRandomVectorScorer.AbstractUpdateableRandomVectorScorer(vectorsCopy) {
                private var queryOffset = 0f

                @Throws(IOException::class)
                override fun setScoringOrdinal(node: Int) {
                    System.arraycopy(vectorsCopy.vectorValue(node), 0, queryVector, 0, queryVector.size)
                    queryOffset = vectorsCopy.getScoreCorrectionConstant(node)
                }

                @Throws(IOException::class)
                override fun score(node: Int): Float {
                    val nodeVector = vectorsCopy.vectorValue(node)
                    val nodeOffset = vectorsCopy.getScoreCorrectionConstant(node)
                    return similarity.score(queryVector, queryOffset, nodeVector, nodeOffset)
                }
            }
        }

        @Throws(IOException::class)
        override fun copy(): RandomVectorScorerSupplier {
            return ScalarQuantizedRandomVectorScorerSupplier(similarity, vectorSimilarityFunction, values.copy())
        }

        override fun toString(): String {
            return "ScalarQuantizedRandomVectorScorerSupplier(vectorSimilarityFunction=$vectorSimilarityFunction)"
        }
    }
}
