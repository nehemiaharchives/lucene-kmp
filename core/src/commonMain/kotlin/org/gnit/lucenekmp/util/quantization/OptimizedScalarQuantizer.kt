package org.gnit.lucenekmp.util.quantization

import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.util.VectorUtil
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * This is a scalar quantizer that optimizes the quantization intervals for a given vector. This is
 * done by optimizing the quantiles of the vector centered on a provided centroid. The optimization
 * is done by minimizing the quantization loss via coordinate descent.
 *
 *
 * Local vector quantization parameters was originally proposed with LVQ in [Similarity search in the blink of an eye with compressed
 * indices](https://arxiv.org/abs/2304.04759) This technique builds on LVQ, but instead of taking the min/max values, a grid search
 * over the centered vector is done to find the optimal quantization intervals, taking into account
 * anisotropic loss.
 *
 *
 * Anisotropic loss is first discussed in depth by [Accelerating Large-Scale Inference with Anisotropic
 * Vector Quantization](https://arxiv.org/abs/1908.10396) by Ruiqi Guo, et al.
 *
 * @lucene.experimental
 */
class OptimizedScalarQuantizer(
    private val similarityFunction: VectorSimilarityFunction, // This determines how much emphasis we place on quantization errors perpendicular to the
    // embedding
    // as opposed to parallel to it.
    // The smaller the value the more we will allow the overall error to increase if it allows us to
    // reduce error parallel to the vector.
    // Parallel errors are important for nearest neighbor queries because the closest document vectors
    // tend to be parallel to the query
    private val lambda: Float = DEFAULT_LAMBDA, // the number of iterations to optimize the quantization intervals
    private val iters: Int = DEFAULT_ITERS
) {

    /**
     * Create a new scalar quantizer with the given similarity function, lambda, and number of
     * iterations.
     *
     * @param similarityFunction similarity function to use
     * @param lambda lambda value to use
     * @param iters number of iterations to use
     */

    /**
     * Quantization result containing the lower and upper interval bounds, the additional correction
     *
     * @param lowerInterval the lower interval bound
     * @param upperInterval the upper interval bound
     * @param additionalCorrection the additional correction
     * @param quantizedComponentSum the sum of the quantized components
     */
    data class QuantizationResult(
        val lowerInterval: Float,
        val upperInterval: Float,
        val additionalCorrection: Float,
        val quantizedComponentSum: Int
    )

    /**
     * Quantize the vector to the multiple bit levels.
     *
     * @param vector raw vector
     * @param destinations array of destinations to store the quantized vector
     * @param bits array of bits to quantize the vector
     * @param centroid centroid to center the vector
     * @return array of quantization results
     */
    fun multiScalarQuantize(
        vector: FloatArray, destinations: Array<ByteArray>, bits: ByteArray, centroid: FloatArray
    ): Array<QuantizationResult> {
        assert(similarityFunction !== VectorSimilarityFunction.COSINE || VectorUtil.isUnitVector(vector))
        assert(similarityFunction !== VectorSimilarityFunction.COSINE || VectorUtil.isUnitVector(centroid))
        assert(bits.size == destinations.size)
        val intervalScratch = FloatArray(2)
        var vecMean = 0.0
        var vecVar = 0.0
        var norm2 = 0f
        var centroidDot = 0f
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (i in vector.indices) {
            if (similarityFunction !== VectorSimilarityFunction.EUCLIDEAN) {
                centroidDot += vector[i] * centroid[i]
            }
            vector[i] = vector[i] - centroid[i]
            min = min(min, vector[i])
            max = max(max, vector[i])
            norm2 += (vector[i] * vector[i])
            val delta = vector[i] - vecMean
            vecMean += delta / (i + 1)
            vecVar += delta * (vector[i] - vecMean)
        }
        vecVar /= vector.size.toDouble()
        val vecStd = sqrt(vecVar)
        val results = Array(bits.size)
        /*for (i in bits.indices)*/ { i ->
            assert(bits[i] in 1..8)
            val points = (1 shl bits[i].toInt())
            // Linearly scale the interval to the standard deviation of the vector, ensuring we are within
            // the min/max bounds
            intervalScratch[0] = clamp((MINIMUM_MSE_GRID[bits[i] - 1][0] + vecMean) * vecStd, min.toDouble(), max.toDouble()).toFloat()
            intervalScratch[1] = clamp((MINIMUM_MSE_GRID[bits[i] - 1][1] + vecMean) * vecStd, min.toDouble(), max.toDouble()).toFloat()
            optimizeIntervals(intervalScratch, vector, norm2, points)
            val nSteps = ((1 shl bits[i].toInt()) - 1).toFloat()
            val a = intervalScratch[0]
            val b = intervalScratch[1]
            val step = (b - a) / nSteps
            var sumQuery = 0
            // Now we have the optimized intervals, quantize the vector
            for (h in vector.indices) {
                val xi = clamp(vector[h].toDouble(), a.toDouble(), b.toDouble()).toFloat()
                val assignment: Int = Math.round((xi - a) / step)
                sumQuery += assignment
                destinations[i][h] = assignment.toByte()
            }
            /*results[i] =*/
            QuantizationResult(
                intervalScratch[0],
                intervalScratch[1],
                if (similarityFunction === VectorSimilarityFunction.EUCLIDEAN) norm2 else centroidDot,
                sumQuery
            )
        }
        return results
    }

    /**
     * Quantize the vector to the given bit level.
     *
     * @param vector raw vector
     * @param destination destination to store the quantized vector
     * @param bits number of bits to quantize the vector
     * @param centroid centroid to center the vector
     * @return quantization result
     */
    fun scalarQuantize(
        vector: FloatArray, destination: ByteArray, bits: Byte, centroid: FloatArray
    ): QuantizationResult {
        assert(similarityFunction !== VectorSimilarityFunction.COSINE || VectorUtil.isUnitVector(vector))
        assert(similarityFunction !== VectorSimilarityFunction.COSINE || VectorUtil.isUnitVector(centroid))
        assert(vector.size <= destination.size)
        assert(bits in 1..8)
        val intervalScratch = FloatArray(2)
        val points = 1 shl bits.toInt()
        var vecMean = 0.0
        var vecVar = 0.0
        var norm2 = 0f
        var centroidDot = 0f
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (i in vector.indices) {
            if (similarityFunction !== VectorSimilarityFunction.EUCLIDEAN) {
                centroidDot += vector[i] * centroid[i]
            }
            vector[i] = vector[i] - centroid[i]
            min = min(min, vector[i])
            max = max(max, vector[i])
            norm2 += (vector[i] * vector[i])
            val delta = vector[i] - vecMean
            vecMean += delta / (i + 1)
            vecVar += delta * (vector[i] - vecMean)
        }
        vecVar /= vector.size.toDouble()
        val vecStd = sqrt(vecVar)
        // Linearly scale the interval to the standard deviation of the vector, ensuring we are within
        // the min/max bounds
        intervalScratch[0] = clamp((MINIMUM_MSE_GRID[bits - 1][0] + vecMean) * vecStd, min.toDouble(), max.toDouble()).toFloat()
        intervalScratch[1] = clamp((MINIMUM_MSE_GRID[bits - 1][1] + vecMean) * vecStd, min.toDouble(), max.toDouble()).toFloat()
        optimizeIntervals(intervalScratch, vector, norm2, points)
        val nSteps = ((1 shl bits.toInt()) - 1).toFloat()
        // Now we have the optimized intervals, quantize the vector
        val a = intervalScratch[0]
        val b = intervalScratch[1]
        val step = (b - a) / nSteps
        var sumQuery = 0
        for (h in vector.indices) {
            val xi = clamp(vector[h].toDouble(), a.toDouble(), b.toDouble()).toFloat()
            val assignment: Int = Math.round((xi - a) / step)
            sumQuery += assignment
            destination[h] = assignment.toByte()
        }
        return QuantizationResult(
            intervalScratch[0],
            intervalScratch[1],
            if (similarityFunction === VectorSimilarityFunction.EUCLIDEAN) norm2 else centroidDot,
            sumQuery
        )
    }

    /**
     * Compute the loss of the vector given the interval. Effectively, we are computing the MSE of a
     * dequantized vector with the raw vector.
     *
     * @param vector raw vector
     * @param interval interval to quantize the vector
     * @param points number of quantization points
     * @param norm2 squared norm of the vector
     * @return the loss
     */
    private fun loss(vector: FloatArray, interval: FloatArray, points: Int, norm2: Float): Double {
        val a = interval[0].toDouble()
        val b = interval[1].toDouble()
        val step = ((b - a) / (points - 1.0f))
        val stepInv = 1.0 / step
        var xe = 0.0
        var e = 0.0
        for (xi in vector) {
            // this is quantizing and then dequantizing the vector
            val xiq: Double = (a + step * Math.round((clamp(xi.toDouble(), a, b) - a) * stepInv))
            // how much does the de-quantized value differ from the original value
            xe += xi * (xi - xiq)
            e += (xi - xiq) * (xi - xiq)
        }
        return (1.0 - lambda) * xe * xe / norm2 + lambda * e
    }

    /**
     * Optimize the quantization interval for the given vector. This is done via a coordinate descent
     * trying to minimize the quantization loss. Note, the loss is not always guaranteed to decrease,
     * so we have a maximum number of iterations and will exit early if the loss increases.
     *
     * @param initInterval initial interval, the optimized interval will be stored here
     * @param vector raw vector
     * @param norm2 squared norm of the vector
     * @param points number of quantization points
     */
    private fun optimizeIntervals(initInterval: FloatArray, vector: FloatArray, norm2: Float, points: Int) {
        var initialLoss = loss(vector, initInterval, points, norm2)
        val scale = (1.0f - lambda) / norm2
        if (Float.isFinite(scale) == false) {
            return
        }
        for (i in 0..<iters) {
            val a = initInterval[0]
            val b = initInterval[1]
            val stepInv = (points - 1.0f) / (b - a)
            // calculate the grid points for coordinate descent
            var daa = 0.0
            var dab = 0.0
            var dbb = 0.0
            var dax = 0.0
            var dbx = 0.0
            for (xi in vector) {
                val k = Math.round((clamp(xi.toDouble(), a.toDouble(), b.toDouble()) - a) * stepInv).toFloat()
                val s = k / (points - 1)
                daa += (1.0 - s) * (1.0 - s)
                dab += (1.0 - s) * s
                dbb += (s * s).toDouble()
                dax += xi * (1.0 - s)
                dbx += (xi * s).toDouble()
            }
            val m0 = scale * dax * dax + lambda * daa
            val m1 = scale * dax * dbx + lambda * dab
            val m2 = scale * dbx * dbx + lambda * dbb
            // its possible that the determinant is 0, in which case we can't update the interval
            val det = m0 * m2 - m1 * m1
            if (det == 0.0) {
                return
            }
            val aOpt = ((m2 * dax - m1 * dbx) / det).toFloat()
            val bOpt = ((m0 * dbx - m1 * dax) / det).toFloat()
            // If there is no change in the interval, we can stop
            if ((abs(initInterval[0] - aOpt) < 1e-8 && abs(initInterval[1] - bOpt) < 1e-8)) {
                return
            }
            val newLoss = loss(vector, floatArrayOf(aOpt, bOpt), points, norm2)
            // If the new loss is worse, don't update the interval and exit
            // This optimization, unlike kMeans, does not always converge to better loss
            // So exit if we are getting worse
            if (newLoss > initialLoss) {
                return
            }
            // Update the interval and go again
            initInterval[0] = aOpt
            initInterval[1] = bOpt
            initialLoss = newLoss
        }
    }

    companion object {
        // The initial interval is set to the minimum MSE grid for each number of bits
        // these starting points are derived from the optimal MSE grid for a uniform distribution
        val MINIMUM_MSE_GRID: Array<FloatArray> = arrayOf(
            floatArrayOf(-0.798f, 0.798f),
            floatArrayOf(-1.493f, 1.493f),
            floatArrayOf(-2.051f, 2.051f),
            floatArrayOf(-2.514f, 2.514f),
            floatArrayOf(-2.916f, 2.916f),
            floatArrayOf(-3.278f, 3.278f),
            floatArrayOf(-3.611f, 3.611f),
            floatArrayOf(-3.922f, 3.922f)
        )

        // the default lambda value
        private const val DEFAULT_LAMBDA = 0.1f

        // the default optimization iterations allowed
        private const val DEFAULT_ITERS = 5
        fun discretize(value: Int, bucket: Int): Int {
            return ((value + (bucket - 1)) / bucket) * bucket
        }

        /**
         * Transpose the query vector into a byte array allowing for efficient bitwise operations with the
         * index bit vectors. The idea here is to organize the query vector bits such that the first bit
         * of every dimension is in the first set dimensions bits, or (dimensions/8) bytes. The second,
         * third, and fourth bits are in the second, third, and fourth set of dimensions bits,
         * respectively. This allows for direct bitwise comparisons with the stored index vectors through
         * summing the bitwise results with the relative required bit shifts.
         *
         *
         * This bit decomposition for fast bitwise SIMD operations was first proposed in:
         *
         * <pre class="prettyprint">
         * Gao, Jianyang, and Cheng Long. "RaBitQ: Quantizing High-
         * Dimensional Vectors with a Theoretical Error Bound for Approximate Nearest Neighbor Search."
         * Proceedings of the ACM on Management of Data 2, no. 3 (2024): 1-27.
        </pre> *
         *
         * @param q the query vector, assumed to be half-byte quantized with values between 0 and 15
         * @param quantQueryByte the byte array to store the transposed query vector
         */
        fun transposeHalfByte(q: ByteArray, quantQueryByte: ByteArray) {
            var i = 0
            while (i < q.size) {
                assert(q[i] in 0..15)
                var lowerByte = 0
                var lowerMiddleByte = 0
                var upperMiddleByte = 0
                var upperByte = 0
                var j = 7
                while (j >= 0 && i < q.size) {
                    lowerByte = lowerByte or ((q[i].toInt() and 1) shl j)
                    lowerMiddleByte = lowerMiddleByte or (((q[i].toInt() shr 1) and 1) shl j)
                    upperMiddleByte = upperMiddleByte or (((q[i].toInt() shr 2) and 1) shl j)
                    upperByte = upperByte or (((q[i].toInt() shr 3) and 1) shl j)
                    i++
                    j--
                }
                val index = ((i + 7) / 8) - 1
                quantQueryByte[index] = lowerByte.toByte()
                quantQueryByte[index + quantQueryByte.size / 4] = lowerMiddleByte.toByte()
                quantQueryByte[index + quantQueryByte.size / 2] = upperMiddleByte.toByte()
                quantQueryByte[index + 3 * quantQueryByte.size / 4] = upperByte.toByte()
            }
        }

        /**
         * Pack the vector as a binary array.
         *
         * @param vector the vector to pack
         * @param packed the packed vector
         */
        fun packAsBinary(vector: ByteArray, packed: ByteArray) {
            var i = 0
            while (i < vector.size) {
                var result: Byte = 0
                var j = 7
                while (j >= 0 && i < vector.size) {
                    assert(vector[i].toInt() == 0 || vector[i].toInt() == 1)
                    result = result or ((vector[i] and 1).toInt() shl j).toByte()
                    ++i
                    j--
                }
                val index = ((i + 7) / 8) - 1
                assert(index < packed.size)
                packed[index] = result
            }
        }

        private fun clamp(x: Double, a: Double, b: Double): Double {
            return min(max(x, a), b)
        }
    }
}
