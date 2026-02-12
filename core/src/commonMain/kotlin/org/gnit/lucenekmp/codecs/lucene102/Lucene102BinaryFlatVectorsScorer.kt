package org.gnit.lucenekmp.codecs.lucene102

import okio.IOException
import org.gnit.lucenekmp.codecs.lucene102.Lucene102BinaryQuantizedVectorsWriter.OffHeapBinarizedQueryVectorValues
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer
import org.gnit.lucenekmp.util.quantization.OptimizedScalarQuantizer
import org.gnit.lucenekmp.util.quantization.OptimizedScalarQuantizer.QuantizationResult
import kotlin.math.max


/** Vector scorer over binarized vector values  */
class Lucene102BinaryFlatVectorsScorer(private val nonQuantizedDelegate: FlatVectorsScorer) : FlatVectorsScorer {

    @Throws(IOException::class)
    override fun getRandomVectorScorerSupplier(
        similarityFunction: VectorSimilarityFunction, vectorValues: KnnVectorValues
    ): RandomVectorScorerSupplier {
        if (vectorValues is BinarizedByteVectorValues) {
            throw UnsupportedOperationException(
                "getRandomVectorScorerSupplier(VectorSimilarityFunction,RandomAccessVectorValues) not implemented for binarized format"
            )
        }
        return nonQuantizedDelegate.getRandomVectorScorerSupplier(similarityFunction, vectorValues)
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(
        similarityFunction: VectorSimilarityFunction, vectorValues: KnnVectorValues, target: FloatArray
    ): RandomVectorScorer {
        var target = target
        if (vectorValues is BinarizedByteVectorValues) {
            val quantizer: OptimizedScalarQuantizer? = vectorValues.quantizer
            val centroid: FloatArray? = vectorValues.centroid
            // We make a copy as the quantization process mutates the input
            val copy: FloatArray = ArrayUtil.copyOfSubArray(target, 0, target.size)
            if (similarityFunction === VectorSimilarityFunction.COSINE) {
                VectorUtil.l2normalize(copy)
            }
            target = copy
            val quantizationCentroid =
                if (similarityFunction === VectorSimilarityFunction.COSINE) {
                    val centroidCopy = ArrayUtil.copyOfSubArray(centroid!!, 0, centroid.size)
                    var centroidNorm2 = 0f
                    for (value in centroidCopy) {
                        centroidNorm2 += value * value
                    }
                    if (centroidNorm2 > 0f) {
                        VectorUtil.l2normalize(centroidCopy)
                    }
                    centroidCopy
                } else {
                    centroid!!
                }
            val initial = ByteArray(target.size)
            val quantized = ByteArray(Lucene102BinaryQuantizedVectorsFormat.QUERY_BITS * vectorValues.discretizedDimensions() / 8)
            val queryCorrections: QuantizationResult =
                quantizer!!.scalarQuantize(target, initial, 4.toByte(), quantizationCentroid)
            OptimizedScalarQuantizer.transposeHalfByte(initial, quantized)
            return object : RandomVectorScorer.AbstractRandomVectorScorer(vectorValues) {
                @Throws(IOException::class)
                override fun score(node: Int): Float {
                    return quantizedScore(
                        quantized, queryCorrections, vectorValues, node, similarityFunction
                    )
                }
            }
        }
        return nonQuantizedDelegate.getRandomVectorScorer(similarityFunction, vectorValues, target)
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(
        similarityFunction: VectorSimilarityFunction, vectorValues: KnnVectorValues, target: ByteArray
    ): RandomVectorScorer {
        return nonQuantizedDelegate.getRandomVectorScorer(similarityFunction, vectorValues, target)
    }

    fun getRandomVectorScorerSupplier(
        similarityFunction: VectorSimilarityFunction,
        scoringVectors: OffHeapBinarizedQueryVectorValues,
        targetVectors: BinarizedByteVectorValues
    ): RandomVectorScorerSupplier {
        return BinarizedRandomVectorScorerSupplier(
            scoringVectors, targetVectors, similarityFunction
        )
    }

    override fun toString(): String {
        return "Lucene102BinaryFlatVectorsScorer(nonQuantizedDelegate=$nonQuantizedDelegate)"
    }

    /** Vector scorer supplier over binarized vector values  */
    internal class BinarizedRandomVectorScorerSupplier(
        private val queryVectors: OffHeapBinarizedQueryVectorValues,
        private val targetVectors: BinarizedByteVectorValues,
        private val similarityFunction: VectorSimilarityFunction
    ) : RandomVectorScorerSupplier {

        @Throws(IOException::class)
        override fun scorer(): UpdateableRandomVectorScorer {
            val targetVectors: BinarizedByteVectorValues = this.targetVectors.copy()
            val queryVectors: OffHeapBinarizedQueryVectorValues =
                this.queryVectors.copy()
            return object : UpdateableRandomVectorScorer.AbstractUpdateableRandomVectorScorer(targetVectors) {
                private var queryCorrections: QuantizationResult? = null
                private var vector: ByteArray? = null

                @Throws(IOException::class)
                override fun setScoringOrdinal(node: Int) {
                    queryCorrections = queryVectors.getCorrectiveTerms(node)
                    vector = queryVectors.vectorValue(node)
                }

                @Throws(IOException::class)
                override fun score(node: Int): Float {
                    check(!(vector == null || queryCorrections == null)) { "setScoringOrdinal was not called" }
                    return quantizedScore(vector!!, queryCorrections!!, targetVectors, node, similarityFunction)
                }
            }
        }

        @Throws(IOException::class)
        override fun copy(): RandomVectorScorerSupplier {
            return BinarizedRandomVectorScorerSupplier(
                queryVectors.copy(), targetVectors.copy(), similarityFunction
            )
        }
    }

    companion object {
        private const val FOUR_BIT_SCALE = 1f / ((1 shl 4) - 1)

        @Throws(IOException::class)
        fun quantizedScore(
            quantizedQuery: ByteArray,
            queryCorrections: QuantizationResult,
            targetVectors: BinarizedByteVectorValues,
            targetOrd: Int,
            similarityFunction: VectorSimilarityFunction
        ): Float {
            val binaryCode: ByteArray = targetVectors.vectorValue(targetOrd)
            val qcDist = VectorUtil.int4BitDotProduct(quantizedQuery, binaryCode).toFloat()
            val indexCorrections: QuantizationResult =
                targetVectors.getCorrectiveTerms(targetOrd)!!
            val x1 = indexCorrections.quantizedComponentSum.toFloat()
            val ax: Float = indexCorrections.lowerInterval
            // Here we assume `lx` is simply bit vectors, so the scaling isn't necessary
            val lx: Float = indexCorrections.upperInterval - ax
            val ay: Float = queryCorrections.lowerInterval
            val ly: Float = (queryCorrections.upperInterval - ay) * FOUR_BIT_SCALE
            val y1 = queryCorrections.quantizedComponentSum.toFloat()
            var score: Float =
                ax * ay * targetVectors.dimension() + ay * lx * x1 + ax * ly * y1 + lx * ly * qcDist
            // For euclidean, we need to invert the score and apply the additional correction, which is
            // assumed to be the squared l2norm of the centroid centered vectors.
            if (similarityFunction === VectorSimilarityFunction.EUCLIDEAN) {
                score =
                    (queryCorrections.additionalCorrection
                            + indexCorrections.additionalCorrection
                            - 2 * score)
                return max(1 / (1f + score), 0f)
            } else {
                // For cosine and max inner product, we need to apply the additional correction, which is
                // assumed to be the non-centered dot-product between the vector and the centroid
                score +=
                    (queryCorrections.additionalCorrection
                            + indexCorrections.additionalCorrection
                            - targetVectors.centroidDP)
                if (similarityFunction === VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT) {
                    return VectorUtil.scaleMaxInnerProductScore(score)
                }
                return max((1f + score) / 2f, 0f)
            }
        }
    }
}
