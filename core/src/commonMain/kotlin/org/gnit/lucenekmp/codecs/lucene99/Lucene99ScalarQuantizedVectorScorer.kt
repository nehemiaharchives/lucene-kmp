package org.gnit.lucenekmp.codecs.lucene99

import okio.IOException
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.hnsw.ScalarQuantizedVectorScorer
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer
import org.gnit.lucenekmp.util.quantization.QuantizedByteVectorValues
import org.gnit.lucenekmp.util.quantization.ScalarQuantizer
import kotlin.math.max

/**
 * Optimized scalar quantized implementation of [FlatVectorsScorer] for quantized vectors
 * stored in the Lucene99 format.
 *
 * @lucene.experimental
 */
class Lucene99ScalarQuantizedVectorScorer(
    private val nonQuantizedDelegate: FlatVectorsScorer
) : FlatVectorsScorer {

    @Throws(IOException::class)
    override fun getRandomVectorScorerSupplier(
        similarityFunction: VectorSimilarityFunction,
        vectorValues: KnnVectorValues
    ): RandomVectorScorerSupplier {
        return if (vectorValues is QuantizedByteVectorValues) {
            ScalarQuantizedRandomVectorScorerSupplier(vectorValues, similarityFunction)
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
            val offsetCorrection = ScalarQuantizedVectorScorer.quantizeQuery(
                target,
                targetBytes,
                similarityFunction,
                scalarQuantizer
            )
            fromVectorSimilarity(
                targetBytes,
                offsetCorrection,
                similarityFunction,
                scalarQuantizer.constantMultiplier,
                vectorValues
            )
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

    companion object {
        private fun fromVectorSimilarity(
            targetBytes: ByteArray,
            offsetCorrection: Float,
            sim: VectorSimilarityFunction,
            constMultiplier: Float,
            values: QuantizedByteVectorValues
        ): UpdateableRandomVectorScorer {
            checkDimensions(targetBytes.size, values.dimension())
            return when (sim) {
                VectorSimilarityFunction.EUCLIDEAN ->
                    Euclidean(values, constMultiplier, targetBytes)

                VectorSimilarityFunction.COSINE,
                VectorSimilarityFunction.DOT_PRODUCT ->
                    dotProductFactory(
                        targetBytes,
                        offsetCorrection,
                        constMultiplier,
                        values
                    ) { f -> max((1 + f) / 2, 0f) }

                VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT ->
                    dotProductFactory(
                        targetBytes,
                        offsetCorrection,
                        constMultiplier,
                        values,
                        VectorUtil::scaleMaxInnerProductScore
                    )
            }
        }

        private fun checkDimensions(queryLen: Int, fieldLen: Int) {
            require(queryLen == fieldLen) {
                "vector query dimension: $queryLen differs from field dimension: $fieldLen"
            }
        }

        private fun dotProductFactory(
            targetBytes: ByteArray,
            offsetCorrection: Float,
            constMultiplier: Float,
            values: QuantizedByteVectorValues,
            scoreAdjustmentFunction: (Float) -> Float
        ): UpdateableRandomVectorScorer.AbstractUpdateableRandomVectorScorer {
            if (values.scalarQuantizer.bits <= 4) {
                if (values.vectorByteLength != values.dimension() && values.slice != null) {
                    return CompressedInt4DotProduct(
                        values,
                        constMultiplier,
                        targetBytes,
                        offsetCorrection,
                        scoreAdjustmentFunction
                    )
                }
                return Int4DotProduct(
                    values,
                    constMultiplier,
                    targetBytes,
                    offsetCorrection,
                    scoreAdjustmentFunction
                )
            }
            return DotProduct(
                values,
                constMultiplier,
                targetBytes,
                offsetCorrection,
                scoreAdjustmentFunction
            )
        }
    }

    private class Euclidean(
        private val values: QuantizedByteVectorValues,
        private val constMultiplier: Float,
        private val targetBytes: ByteArray
    ) : UpdateableRandomVectorScorer.AbstractUpdateableRandomVectorScorer(values) {
        @Throws(IOException::class)
        override fun score(node: Int): Float {
            val nodeVector = values.vectorValue(node)
            val squareDistance = VectorUtil.squareDistance(nodeVector, targetBytes)
            val adjustedDistance = squareDistance * constMultiplier
            return 1 / (1f + adjustedDistance)
        }

        @Throws(IOException::class)
        override fun setScoringOrdinal(node: Int) {
            System.arraycopy(values.vectorValue(node), 0, targetBytes, 0, targetBytes.size)
        }
    }

    private class DotProduct(
        private val values: QuantizedByteVectorValues,
        private val constMultiplier: Float,
        private val targetBytes: ByteArray,
        private var offsetCorrection: Float,
        private val scoreAdjustmentFunction: (Float) -> Float
    ) : UpdateableRandomVectorScorer.AbstractUpdateableRandomVectorScorer(values) {
        @Throws(IOException::class)
        override fun score(vectorOrdinal: Int): Float {
            val storedVector = values.vectorValue(vectorOrdinal)
            val vectorOffset = values.getScoreCorrectionConstant(vectorOrdinal)
            val dotProduct = VectorUtil.dotProduct(storedVector, targetBytes)
            require(dotProduct >= 0)
            val adjustedDistance = dotProduct * constMultiplier + offsetCorrection + vectorOffset
            return scoreAdjustmentFunction(adjustedDistance)
        }

        @Throws(IOException::class)
        override fun setScoringOrdinal(node: Int) {
            System.arraycopy(values.vectorValue(node), 0, targetBytes, 0, targetBytes.size)
            offsetCorrection = values.getScoreCorrectionConstant(node)
        }
    }

    private class CompressedInt4DotProduct(
        private val values: QuantizedByteVectorValues,
        private val constMultiplier: Float,
        private val targetBytes: ByteArray,
        private var offsetCorrection: Float,
        private val scoreAdjustmentFunction: (Float) -> Float
    ) : UpdateableRandomVectorScorer.AbstractUpdateableRandomVectorScorer(values) {
        private val compressedVector: ByteArray = ByteArray(values.vectorByteLength)

        @Throws(IOException::class)
        override fun score(vectorOrdinal: Int): Float {
            val slice = values.slice ?: throw IllegalStateException("Quantized values slice is null")
            val stride = values.vectorByteLength + Float.SIZE_BYTES
            slice.seek(vectorOrdinal.toLong() * stride)
            slice.readBytes(compressedVector, 0, compressedVector.size)
            val vectorOffset = values.getScoreCorrectionConstant(vectorOrdinal)
            val dotProduct = VectorUtil.int4DotProductPacked(targetBytes, compressedVector)
            require(dotProduct >= 0)
            val adjustedDistance = dotProduct * constMultiplier + offsetCorrection + vectorOffset
            return scoreAdjustmentFunction(adjustedDistance)
        }

        @Throws(IOException::class)
        override fun setScoringOrdinal(node: Int) {
            System.arraycopy(values.vectorValue(node), 0, targetBytes, 0, targetBytes.size)
            offsetCorrection = values.getScoreCorrectionConstant(node)
        }
    }

    private class Int4DotProduct(
        private val values: QuantizedByteVectorValues,
        private val constMultiplier: Float,
        private val targetBytes: ByteArray,
        private var offsetCorrection: Float,
        private val scoreAdjustmentFunction: (Float) -> Float
    ) : UpdateableRandomVectorScorer.AbstractUpdateableRandomVectorScorer(values) {
        @Throws(IOException::class)
        override fun score(vectorOrdinal: Int): Float {
            val storedVector = values.vectorValue(vectorOrdinal)
            val vectorOffset = values.getScoreCorrectionConstant(vectorOrdinal)
            val dotProduct = VectorUtil.int4DotProduct(storedVector, targetBytes)
            require(dotProduct >= 0)
            val adjustedDistance = dotProduct * constMultiplier + offsetCorrection + vectorOffset
            return scoreAdjustmentFunction(adjustedDistance)
        }

        @Throws(IOException::class)
        override fun setScoringOrdinal(node: Int) {
            System.arraycopy(values.vectorValue(node), 0, targetBytes, 0, targetBytes.size)
            offsetCorrection = values.getScoreCorrectionConstant(node)
        }
    }

    private class ScalarQuantizedRandomVectorScorerSupplier(
        private val values: QuantizedByteVectorValues,
        private val vectorSimilarityFunction: VectorSimilarityFunction
    ) : RandomVectorScorerSupplier {
        private val targetVectors: QuantizedByteVectorValues = values.copy()

        @Throws(IOException::class)
        override fun scorer(): UpdateableRandomVectorScorer {
            val vectorValue = ByteArray(values.dimension())
            val offsetCorrection = 0f
            return fromVectorSimilarity(
                vectorValue,
                offsetCorrection,
                vectorSimilarityFunction,
                values.scalarQuantizer.constantMultiplier,
                targetVectors
            )
        }

        @Throws(IOException::class)
        override fun copy(): RandomVectorScorerSupplier {
            return ScalarQuantizedRandomVectorScorerSupplier(values.copy(), vectorSimilarityFunction)
        }

        override fun toString(): String {
            return "ScalarQuantizedRandomVectorScorerSupplier(vectorSimilarityFunction=$vectorSimilarityFunction)"
        }
    }
}
