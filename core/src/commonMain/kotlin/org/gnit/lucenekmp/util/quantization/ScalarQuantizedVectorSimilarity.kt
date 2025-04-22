package org.gnit.lucenekmp.util.quantization

import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.index.VectorSimilarityFunction.EUCLIDEAN
import org.gnit.lucenekmp.index.VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
import org.gnit.lucenekmp.index.VectorSimilarityFunction.COSINE
import org.gnit.lucenekmp.index.VectorSimilarityFunction.DOT_PRODUCT
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.VectorUtil.scaleMaxInnerProductScore
import kotlin.math.max

/**
 * Calculates and adjust the scores correctly for quantized vectors given the scalar quantization
 * parameters
 */
interface ScalarQuantizedVectorSimilarity {
    fun score(queryVector: ByteArray, queryVectorOffset: Float, storedVector: ByteArray, vectorOffset: Float): Float

    /** Calculates euclidean distance on quantized vectors, applying the appropriate corrections  */
    class Euclidean(private val constMultiplier: Float) : ScalarQuantizedVectorSimilarity {
        override fun score(
            queryVector: ByteArray, queryVectorOffset: Float, storedVector: ByteArray, vectorOffset: Float
        ): Float {
            val squareDistance: Int = VectorUtil.squareDistance(storedVector, queryVector)
            val adjustedDistance = squareDistance * constMultiplier
            return 1 / (1f + adjustedDistance)
        }
    }

    /** Calculates dot product on quantized vectors, applying the appropriate corrections  */
    class DotProduct(private val constMultiplier: Float, private val comparator: ByteVectorComparator) :
        ScalarQuantizedVectorSimilarity {
        override fun score(
            queryVector: ByteArray, queryOffset: Float, storedVector: ByteArray, vectorOffset: Float
        ): Float {
            val dotProduct = comparator.compare(storedVector, queryVector)
            // For the current implementation of scalar quantization, all dotproducts should be >= 0;
            require(dotProduct >= 0)
            val adjustedDistance = dotProduct * constMultiplier + queryOffset + vectorOffset
            return max((1 + adjustedDistance) / 2, 0f)
        }
    }

    /** Calculates max inner product on quantized vectors, applying the appropriate corrections  */
    class MaximumInnerProduct(private val constMultiplier: Float, private val comparator: ByteVectorComparator) :
        ScalarQuantizedVectorSimilarity {
        override fun score(
            queryVector: ByteArray, queryOffset: Float, storedVector: ByteArray, vectorOffset: Float
        ): Float {
            val dotProduct = comparator.compare(storedVector, queryVector)
            // For the current implementation of scalar quantization, all dotproducts should be >= 0;
            require(dotProduct >= 0)
            val adjustedDistance = dotProduct * constMultiplier + queryOffset + vectorOffset
            return scaleMaxInnerProductScore(adjustedDistance)
        }
    }

    /** Compares two byte vectors  */
    interface ByteVectorComparator {
        fun compare(v1: ByteArray, v2: ByteArray): Int
    }

    companion object {
        /**
         * Creates a [ScalarQuantizedVectorSimilarity] from a [VectorSimilarityFunction] and
         * the constant multiplier used for quantization.
         *
         * @param sim similarity function
         * @param constMultiplier constant multiplier used for quantization
         * @param bits number of bits used for quantization
         * @return a [ScalarQuantizedVectorSimilarity] that applies the appropriate corrections
         */
        fun fromVectorSimilarity(
            sim: VectorSimilarityFunction, constMultiplier: Float, bits: Byte
        ): ScalarQuantizedVectorSimilarity {
            return when (sim) {
                EUCLIDEAN -> Euclidean(constMultiplier)
                COSINE, DOT_PRODUCT -> DotProduct(
                    constMultiplier,
                    object : ByteVectorComparator {
                        override fun compare(v1: ByteArray, v2: ByteArray): Int {
                            return if (v1 != null && v2 != null) {
                                if (bits <= 4) VectorUtil.int4DotProduct(v1, v2) else VectorUtil.dotProduct(v1, v2)
                            } else 0
                        }
                    }
                )

                MAXIMUM_INNER_PRODUCT -> MaximumInnerProduct(
                    constMultiplier,
                    object : ByteVectorComparator {
                        override fun compare(v1: ByteArray, v2: ByteArray): Int {
                            return if (v1 != null && v2 != null) {
                                if (bits <= 4) VectorUtil.int4DotProduct(v1, v2) else VectorUtil.dotProduct(v1, v2)
                            } else 0
                        }
                    }
                )
            }
        }
    }
}
