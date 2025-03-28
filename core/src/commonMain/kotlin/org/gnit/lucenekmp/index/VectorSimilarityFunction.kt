package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.util.VectorUtil.cosine
import org.gnit.lucenekmp.util.VectorUtil.dotProduct
import org.gnit.lucenekmp.util.VectorUtil.dotProductScore
import org.gnit.lucenekmp.util.VectorUtil.scaleMaxInnerProductScore
import org.gnit.lucenekmp.util.VectorUtil.squareDistance
import kotlin.math.max

/**
 * Vector similarity function; used in search to return top K most similar vectors to a target
 * vector. This is a label describing the method used during indexing and searching of the vectors
 * in order to determine the nearest neighbors.
 */
enum class VectorSimilarityFunction {
    /** Euclidean distance  */
    EUCLIDEAN {
        override fun compare(v1: FloatArray, v2: FloatArray): Float {
            return 1 / (1 + squareDistance(v1, v2))
        }

        override fun compare(v1: ByteArray, v2: ByteArray): Float {
            return 1 / (1f + squareDistance(v1, v2))
        }
    },

    /**
     * Dot product. NOTE: this similarity is intended as an optimized way to perform cosine
     * similarity. In order to use it, all vectors must be normalized, including both document and
     * query vectors. Using dot product with vectors that are not normalized can result in errors or
     * poor search results. Floating point vectors must be normalized to be of unit length, while byte
     * vectors should simply all have the same norm.
     */
    DOT_PRODUCT {
        override fun compare(v1: FloatArray, v2: FloatArray): Float {
            return max((1 + dotProduct(v1, v2)) / 2, 0f)
        }

        override fun compare(v1: ByteArray, v2: ByteArray): Float {
            return dotProductScore(v1, v2)
        }
    },

    /**
     * Cosine similarity. NOTE: the preferred way to perform cosine similarity is to normalize all
     * vectors to unit length, and instead use [VectorSimilarityFunction.DOT_PRODUCT]. You
     * should only use this function if you need to preserve the original vectors and cannot normalize
     * them in advance. The similarity score is normalised to assure it is positive.
     */
    COSINE {
        override fun compare(v1: FloatArray, v2: FloatArray): Float {
            return max((1 + cosine(v1, v2)) / 2, 0f)
        }

        override fun compare(v1: ByteArray, v2: ByteArray): Float {
            return (1 + cosine(v1, v2)) / 2
        }
    },

    /**
     * Maximum inner product. This is like [VectorSimilarityFunction.DOT_PRODUCT], but does not
     * require normalization of the inputs. Should be used when the embedding vectors store useful
     * information within the vector magnitude
     */
    MAXIMUM_INNER_PRODUCT {
        override fun compare(v1: FloatArray, v2: FloatArray): Float {
            return scaleMaxInnerProductScore(dotProduct(v1, v2))
        }

        override fun compare(v1: ByteArray, v2: ByteArray): Float {
            return scaleMaxInnerProductScore(dotProduct(v1, v2).toFloat())
        }
    };

    /**
     * Calculates a similarity score between the two vectors with a specified function. Higher
     * similarity scores correspond to closer vectors.
     *
     * @param v1 a vector
     * @param v2 another vector, of the same dimension
     * @return the value of the similarity function applied to the two vectors
     */
    abstract fun compare(v1: FloatArray, v2: FloatArray): Float

    /**
     * Calculates a similarity score between the two vectors with a specified function. Higher
     * similarity scores correspond to closer vectors. Each (signed) byte represents a vector
     * dimension.
     *
     * @param v1 a vector
     * @param v2 another vector, of the same dimension
     * @return the value of the similarity function applied to the two vectors
     */
    abstract fun compare(v1: ByteArray, v2: ByteArray): Float
}
