package org.gnit.lucenekmp.util.quantization

import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.HitQueue
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.util.IntroSelector
import org.gnit.lucenekmp.util.Selector
import org.gnit.lucenekmp.util.VectorUtil
import kotlinx.io.IOException
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.isInfinite
import org.gnit.lucenekmp.jdkport.isNaN
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Will scalar quantize float vectors into `int8` byte values. This is a lossy transformation.
 * Scalar quantization works by first calculating the quantiles of the float vector values. The
 * quantiles are calculated using the configured confidence interval. The [minQuantile, maxQuantile]
 * are then used to scale the values into the range [0, 127] and bucketed into the nearest byte
 * values.
 *
 * <h2>How Scalar Quantization Works</h2>
 *
 *
 * The basic mathematical equations behind this are fairly straight forward and based on min/max
 * normalization. Given a float vector `v` and a confidenceInterval `q` we can calculate the
 * quantiles of the vector values [minQuantile, maxQuantile].
 *
 * <pre class="prettyprint">
 * byte = (float - minQuantile) * 127/(maxQuantile - minQuantile)
 * float = (maxQuantile - minQuantile)/127 * byte + minQuantile
</pre> *
 *
 *
 * This then means to multiply two float values together (e.g. dot_product) we can do the
 * following:
 *
 * <pre class="prettyprint">
 * float1 * float2 ~= (byte1 * (maxQuantile - minQuantile)/127 + minQuantile) * (byte2 * (maxQuantile - minQuantile)/127 + minQuantile)
 * float1 * float2 ~= (byte1 * byte2 * (maxQuantile - minQuantile)^2)/(127^2) + (byte1 * minQuantile * (maxQuantile - minQuantile)/127) + (byte2 * minQuantile * (maxQuantile - minQuantile)/127) + minQuantile^2
 * let alpha = (maxQuantile - minQuantile)/127
 * float1 * float2 ~= (byte1 * byte2 * alpha^2) + (byte1 * minQuantile * alpha) + (byte2 * minQuantile * alpha) + minQuantile^2
</pre> *
 *
 *
 * The expansion for square distance is much simpler:
 *
 * <pre class="prettyprint">
 * square_distance = (float1 - float2)^2
 * (float1 - float2)^2 ~= (byte1 * alpha + minQuantile - byte2 * alpha - minQuantile)^2
 * = (alpha*byte1 + minQuantile)^2 + (alpha*byte2 + minQuantile)^2 - 2*(alpha*byte1 + minQuantile)(alpha*byte2 + minQuantile)
 * this can be simplified to:
 * = alpha^2 (byte1 - byte2)^2
</pre> *
 */
class ScalarQuantizer(minQuantile: Float, maxQuantile: Float, bits: Byte) {
    private val alpha: Float
    private val scale: Float
    val bits: Byte
    val lowerQuantile: Float
    val upperQuantile: Float

    /**
     * Quantize a float vector into a byte vector
     *
     * @param src the source vector
     * @param dest the destination vector
     * @param similarityFunction the similarity function used to calculate the quantile
     * @return the corrective offset that needs to be applied to the score
     */
    fun quantize(src: FloatArray, dest: ByteArray, similarityFunction: VectorSimilarityFunction): Float {
        require(src.size == dest.size)
        require(similarityFunction !== VectorSimilarityFunction.COSINE || VectorUtil.isUnitVector(src))
        var correction = 0f
        for (i in src.indices) {
            correction += quantizeFloat(src[i], dest, i)
        }
        if (similarityFunction == VectorSimilarityFunction.EUCLIDEAN) {
            return 0f
        }
        return correction
    }

    private fun quantizeFloat(v: Float, dest: ByteArray?, destIndex: Int): Float {
        require(dest == null || destIndex < dest.size)
        // Make sure the value is within the quantile range, cutting off the tails
        // see first parenthesis in equation: byte = (float - minQuantile) * 127/(maxQuantile -
        // minQuantile)
        val dx = v - this.lowerQuantile
        val dxc = max(this.lowerQuantile, min(this.upperQuantile, v)) - this.lowerQuantile
        // Scale the value to the range [0, 127], this is our quantized value
        // scale = 127/(maxQuantile - minQuantile)
        val dxs = scale * dxc
        // We multiply by `alpha` here to get the quantized value back into the original range
        // to aid in calculating the corrective offset
        val dxq: Float = Math.round(dxs) * alpha
        if (dest != null) {
            dest[destIndex] = Math.round(dxs).toByte()
        }
        // Calculate the corrective offset that needs to be applied to the score
        // in addition to the `byte * minQuantile * alpha` term in the equation
        // we add the `(dx - dxq) * dxq` term to account for the fact that the quantized value
        // will be rounded to the nearest whole number and lose some accuracy
        // Additionally, we account for the global correction of `minQuantile^2` in the equation
        return this.lowerQuantile * (v - this.lowerQuantile / 2.0f) + (dx - dxq) * dxq
    }

    /**
     * Recalculate the old score corrective value given new current quantiles
     *
     * @param quantizedVector the old vector
     * @param oldQuantizer the old quantizer
     * @param similarityFunction the similarity function used to calculate the quantile
     * @return the new offset
     */
    fun recalculateCorrectiveOffset(
        quantizedVector: ByteArray,
        oldQuantizer: ScalarQuantizer,
        similarityFunction: VectorSimilarityFunction
    ): Float {
        if (similarityFunction == VectorSimilarityFunction.EUCLIDEAN) {
            return 0f
        }
        var correctiveOffset = 0f
        for (i in quantizedVector.indices) {
            // dequantize the old value in order to recalculate the corrective offset
            val v = (oldQuantizer.alpha * quantizedVector[i]) + oldQuantizer.lowerQuantile
            correctiveOffset += quantizeFloat(v, null, 0)
        }
        return correctiveOffset
    }

    /**
     * Dequantize a byte vector into a float vector
     *
     * @param src the source vector
     * @param dest the destination vector
     */
    fun deQuantize(src: ByteArray, dest: FloatArray) {
        require(src.size == dest.size)
        for (i in src.indices) {
            dest[i] = (alpha * src[i]) + this.lowerQuantile
        }
    }

    val constantMultiplier: Float
        get() = alpha * alpha

    override fun toString(): String {
        return ("ScalarQuantizer{"
                + "minQuantile="
                + this.lowerQuantile
                + ", maxQuantile="
                + this.upperQuantile
                + ", bits="
                + bits
                + '}')
    }

    /**
     * @param minQuantile the lower quantile of the distribution
     * @param maxQuantile the upper quantile of the distribution
     * @param bits the number of bits to use for quantization
     */
    init {
        check(
            !(Float.isNaN(minQuantile)
                    || Float.isInfinite(minQuantile)
                    || Float.isNaN(maxQuantile)
                    || Float.isInfinite(maxQuantile))
        ) { "Scalar quantizer does not support infinite or NaN values" }
        require(maxQuantile >= minQuantile)
        require(bits > 0 && bits <= 8)
        this.bits = bits
        val divisor = ((1 shl bits.toInt()) - 1).toFloat()
        if (minQuantile == maxQuantile) {
            // avoid divide-by-zero with an arbitrary but plausible choice (leads to alpha = scale = 1)
            this.lowerQuantile = minQuantile - divisor
            this.upperQuantile = maxQuantile + divisor
        } else {
            this.lowerQuantile = minQuantile
            this.upperQuantile = maxQuantile
        }
        this.scale = divisor / (this.upperQuantile - this.lowerQuantile)
        this.alpha = (this.upperQuantile - this.lowerQuantile) / divisor
    }

    private class FloatSelector(private val arr: FloatArray) : IntroSelector() {
        var pivot: Float = Float.Companion.NaN

        override fun setPivot(i: Int) {
            pivot = arr[i]
        }

        override fun comparePivot(j: Int): Int {
            return Float.compare(pivot, arr[j])
        }

        override fun swap(i: Int, j: Int) {
            val tmp = arr[i]
            arr[i] = arr[j]
            arr[j] = tmp
        }
    }

    private class ScoreDocsAndScoreVariance(val scoreDocs: Array<ScoreDoc>, val scoreVariance: Float)

    private class OnlineMeanAndVar {
        var mean = 0.0
        private var `var` = 0.0
        private var n = 0

        fun reset() {
            mean = 0.0
            `var` = 0.0
            n = 0
        }

        fun add(x: Double) {
            n++
            val delta = x - mean
            mean += delta / n
            `var` += delta * (x - mean)
        }

        fun `var`(): Float {
            return (`var` / (n - 1)).toFloat()
        }
    }

    /**
     * This class is used to correlate the scores of the nearest neighbors with the errors in the
     * scores. This is used to find the best quantile pair for the scalar quantizer.
     */
    private class ScoreErrorCorrelator(
        private val function: VectorSimilarityFunction,
        private val nearestNeighbors: MutableList<ScoreDocsAndScoreVariance>,
        private val vectors: MutableList<FloatArray?>,
        private val bits: Byte
    ) {
        private val corr = OnlineMeanAndVar()
        private val errors = OnlineMeanAndVar()
        private val query: ByteArray = ByteArray(vectors[0]!!.size)
        private val vector: ByteArray = ByteArray(vectors[0]!!.size)

        fun scoreErrorCorrelation(lowerQuantile: Float, upperQuantile: Float): Double {
            corr.reset()
            val quantizer = ScalarQuantizer(lowerQuantile, upperQuantile, bits)
            val scalarQuantizedVectorSimilarity: ScalarQuantizedVectorSimilarity =
                ScalarQuantizedVectorSimilarity.fromVectorSimilarity(
                    function, quantizer.constantMultiplier, quantizer.bits
                )
            for (i in nearestNeighbors.indices) {
                val queryCorrection = quantizer.quantize(vectors[i]!!, query, function)
                val scoreDocsAndScoreVariance = nearestNeighbors[i]
                val scoreDocs: Array<ScoreDoc> = scoreDocsAndScoreVariance.scoreDocs
                val scoreVariance = scoreDocsAndScoreVariance.scoreVariance
                // calculate the score for the vector against its nearest neighbors but with quantized
                // scores now
                errors.reset()
                for (scoreDoc in scoreDocs) {
                    val vectorCorrection = quantizer.quantize(vectors[scoreDoc.doc]!!, vector, function)
                    val qScore: Float =
                        scalarQuantizedVectorSimilarity.score(
                            query, queryCorrection, vector, vectorCorrection
                        )
                    errors.add((qScore - scoreDoc.score).toDouble())
                }
                corr.add((1 - errors.`var`() / scoreVariance).toDouble())
            }
            return if (Double.isNaN(corr.mean)) 0.0 else corr.mean
        }
    }

    companion object {
        const val SCALAR_QUANTIZATION_SAMPLE_SIZE: Int = 25000

        // 20*dimension provides protection from extreme confidence intervals
        // and also prevents humongous allocations
        const val SCRATCH_SIZE: Int = 20

        private val random: Random = Random(42)

        private fun reservoirSampleIndices(numFloatVecs: Int, sampleSize: Int): IntArray {
            val vectorsToTake = IntArray(sampleSize) { it }
            for (i in sampleSize..<numFloatVecs) {
                val j: Int = random.nextInt(i + 1)
                if (j < sampleSize) {
                    vectorsToTake[j] = i
                }
            }
            Arrays.sort(vectorsToTake)
            return vectorsToTake
        }

        /**
         * This will read the float vector values and calculate the quantiles. If the number of float
         * vectors is less than [.SCALAR_QUANTIZATION_SAMPLE_SIZE] then all the values will be read
         * and the quantiles calculated. If the number of float vectors is greater than [ ][.SCALAR_QUANTIZATION_SAMPLE_SIZE] then a random sample of [ ][.SCALAR_QUANTIZATION_SAMPLE_SIZE] will be read and the quantiles calculated.
         *
         * @param floatVectorValues the float vector values from which to calculate the quantiles
         * @param confidenceInterval the confidence interval used to calculate the quantiles
         * @param totalVectorCount the total number of live float vectors in the index. This is vital for
         * accounting for deleted documents when calculating the quantiles.
         * @param bits the number of bits to use for quantization
         * @return A new [ScalarQuantizer] instance
         * @throws IOException if there is an error reading the float vector values
         */
        @Throws(IOException::class)
        fun fromVectors(
            floatVectorValues: FloatVectorValues,
            confidenceInterval: Float,
            totalVectorCount: Int,
            bits: Byte
        ): ScalarQuantizer {
            return fromVectors(
                floatVectorValues,
                confidenceInterval,
                totalVectorCount,
                bits,
                SCALAR_QUANTIZATION_SAMPLE_SIZE
            )
        }

        @Throws(IOException::class)
        fun fromVectors(
            floatVectorValues: FloatVectorValues,
            confidenceInterval: Float,
            totalVectorCount: Int,
            bits: Byte,
            quantizationSampleSize: Int
        ): ScalarQuantizer {
            require(0.9f <= confidenceInterval && confidenceInterval <= 1f)
            require(quantizationSampleSize > SCRATCH_SIZE)
            if (totalVectorCount == 0) {
                return ScalarQuantizer(0f, 0f, bits)
            }
            val iterator: KnnVectorValues.DocIndexIterator = floatVectorValues.iterator()
            if (confidenceInterval == 1f) {
                var min = Float.Companion.POSITIVE_INFINITY
                var max = Float.Companion.NEGATIVE_INFINITY
                while (iterator.nextDoc() != NO_MORE_DOCS) {
                    for (v in floatVectorValues.vectorValue(iterator.index())) {
                        min = min(min, v)
                        max = max(max, v)
                    }
                }
                return ScalarQuantizer(min, max, bits)
            }
            val quantileGatheringScratch =
                FloatArray(floatVectorValues.dimension() * min(SCRATCH_SIZE, totalVectorCount))
            var count = 0
            val upperSum = DoubleArray(1)
            val lowerSum = DoubleArray(1)
            val confidenceIntervals = floatArrayOf(confidenceInterval)
            if (totalVectorCount <= quantizationSampleSize) {
                val scratchSize = min(SCRATCH_SIZE, totalVectorCount)
                var i = 0
                while (iterator.nextDoc() != NO_MORE_DOCS) {
                    val vectorValue: FloatArray = floatVectorValues.vectorValue(iterator.index())
                    System.arraycopy(
                        vectorValue, 0, quantileGatheringScratch, i * vectorValue.size, vectorValue.size
                    )
                    i++
                    if (i == scratchSize) {
                        extractQuantiles(confidenceIntervals, quantileGatheringScratch, upperSum, lowerSum)
                        i = 0
                        count++
                    }
                }
                // Note, we purposefully don't use the rest of the scratch state if we have fewer than
                // `SCRATCH_SIZE` vectors, mainly because if we are sampling so few vectors then we don't
                // want to be adversely affected by the extreme confidence intervals over small sample sizes
                return ScalarQuantizer(lowerSum[0].toFloat() / count, upperSum[0].toFloat() / count, bits)
            }
            val vectorsToTake = reservoirSampleIndices(totalVectorCount, quantizationSampleSize)
            var index = 0
            var idx = 0
            for (i in vectorsToTake) {
                while (index <= i) {
                    // We cannot use `advance(docId)` as MergedVectorValues does not support it
                    iterator.nextDoc()
                    index++
                }
                require(iterator.docID() != NO_MORE_DOCS)
                val vectorValue: FloatArray = floatVectorValues.vectorValue(iterator.index())
                System.arraycopy(
                    vectorValue, 0, quantileGatheringScratch, idx * vectorValue.size, vectorValue.size
                )
                idx++
                if (idx == SCRATCH_SIZE) {
                    extractQuantiles(confidenceIntervals, quantileGatheringScratch, upperSum, lowerSum)
                    count++
                    idx = 0
                }
            }
            return ScalarQuantizer(lowerSum[0].toFloat() / count, upperSum[0].toFloat() / count, bits)
        }

        @Throws(IOException::class)
        fun fromVectorsAutoInterval(
            floatVectorValues: FloatVectorValues,
            function: VectorSimilarityFunction,
            totalVectorCount: Int,
            bits: Byte
        ): ScalarQuantizer {
            require(function !== VectorSimilarityFunction.COSINE)
            if (totalVectorCount == 0) {
                return ScalarQuantizer(0f, 0f, bits)
            }

            val sampleSize = min(totalVectorCount, 1000)
            val quantileGatheringScratch =
                FloatArray(floatVectorValues.dimension() * min(SCRATCH_SIZE, totalVectorCount))
            var count = 0
            val upperSum = DoubleArray(2)
            val lowerSum = DoubleArray(2)
            val sampledDocs: MutableList<FloatArray?> = ArrayList(sampleSize)
            val confidenceIntervals =
                floatArrayOf(
                    1
                            - min(32f, floatVectorValues.dimension() / 10f)
                            / (floatVectorValues.dimension() + 1),
                    1 - 1f / (floatVectorValues.dimension() + 1)
                )
            val iterator: KnnVectorValues.DocIndexIterator = floatVectorValues.iterator()
            if (totalVectorCount <= sampleSize) {
                val scratchSize = min(SCRATCH_SIZE, totalVectorCount)
                var i = 0
                while (iterator.nextDoc() != NO_MORE_DOCS) {
                    gatherSample(
                        floatVectorValues.vectorValue(iterator.index()),
                        quantileGatheringScratch,
                        sampledDocs,
                        i
                    )
                    i++
                    if (i == scratchSize) {
                        extractQuantiles(confidenceIntervals, quantileGatheringScratch, upperSum, lowerSum)
                        i = 0
                        count++
                    }
                }
            } else {
                // Reservoir sample the vector ordinals we want to read
                val vectorsToTake = reservoirSampleIndices(totalVectorCount, 1000)
                // TODO make this faster by .advance()ing & dual iterator
                var index = 0
                var idx = 0
                for (i in vectorsToTake) {
                    while (index <= i) {
                        // We cannot use `advance(docId)` as MergedVectorValues does not support it
                        iterator.nextDoc()
                        index++
                    }
                    require(iterator.docID() != NO_MORE_DOCS)
                    gatherSample(
                        floatVectorValues.vectorValue(iterator.index()),
                        quantileGatheringScratch,
                        sampledDocs,
                        idx
                    )
                    idx++
                    if (idx == SCRATCH_SIZE) {
                        extractQuantiles(confidenceIntervals, quantileGatheringScratch, upperSum, lowerSum)
                        count++
                        idx = 0
                    }
                }
            }

            // Here we gather the upper and lower bounds for the quantile grid search
            val al = lowerSum[1].toFloat() / count
            val bu = upperSum[1].toFloat() / count
            val au = lowerSum[0].toFloat() / count
            val bl = upperSum[0].toFloat() / count
            check(
                !(Float.isNaN(al)
                        || Float.isInfinite(al)
                        || Float.isNaN(au)
                        || Float.isInfinite(au)
                        || Float.isNaN(bl)
                        || Float.isInfinite(bl)
                        || Float.isNaN(bu)
                        || Float.isInfinite(bu))
            ) { "Quantile calculation resulted in NaN or infinite values" }
            val lowerCandidates = FloatArray(16)
            val upperCandidates = FloatArray(16)
            var idx = 0
            var i = 0f
            while (i < 32f) {
                lowerCandidates[idx] = al + i * (au - al) / 32f
                upperCandidates[idx] = bl + i * (bu - bl) / 32f
                idx++
                i += 2f
            }
            // Now we need to find the best candidate pair by correlating the true quantized nearest
            // neighbor scores
            // with the float vector scores
            val nearestNeighbors = findNearestNeighbors(sampledDocs, function)
            val bestPair =
                candidateGridSearch(
                    nearestNeighbors, sampledDocs, lowerCandidates, upperCandidates, function, bits
                )
            return ScalarQuantizer(bestPair[0], bestPair[1], bits)
        }

        private fun extractQuantiles(
            confidenceIntervals: FloatArray,
            quantileGatheringScratch: FloatArray,
            upperSum: DoubleArray,
            lowerSum: DoubleArray
        ) {
            require(
                confidenceIntervals.size == upperSum.size
                        && confidenceIntervals.size == lowerSum.size
            )
            for (i in confidenceIntervals.indices) {
                val upperAndLower =
                    getUpperAndLowerQuantile(quantileGatheringScratch, confidenceIntervals[i])
                upperSum[i] += upperAndLower[1].toDouble()
                lowerSum[i] += upperAndLower[0].toDouble()
            }
        }

        private fun gatherSample(
            vectorValue: FloatArray, quantileGatheringScratch: FloatArray, sampledDocs: MutableList<FloatArray?>, i: Int
        ) {
            val copy = FloatArray(vectorValue.size)
            System.arraycopy(vectorValue, 0, copy, 0, vectorValue.size)
            sampledDocs.add(copy)
            System.arraycopy(
                vectorValue, 0, quantileGatheringScratch, i * vectorValue.size, vectorValue.size
            )
        }

        private fun candidateGridSearch(
            nearestNeighbors: MutableList<ScoreDocsAndScoreVariance>,
            vectors: MutableList<FloatArray?>,
            lowerCandidates: FloatArray,
            upperCandidates: FloatArray,
            function: VectorSimilarityFunction,
            bits: Byte
        ): FloatArray {
            var maxCorr = Double.Companion.NEGATIVE_INFINITY
            var bestLower = 0f
            var bestUpper = 0f
            val scoreErrorCorrelator =
                ScoreErrorCorrelator(function, nearestNeighbors, vectors, bits)
            // first do a coarse grained search to find the initial best candidate pair
            var bestQuandrantLower = 0
            var bestQuandrantUpper = 0
            run {
                var i = 0
                while (i < lowerCandidates.size) {
                    val lower = lowerCandidates[i]
                    if (Float.isNaN(lower) || Float.isInfinite(lower)) {
                        require(false) { "Lower candidate is NaN or infinite" }
                        i += 4
                        continue
                    }
                    var j = 0
                    while (j < upperCandidates.size) {
                        val upper = upperCandidates[j]
                        if (Float.isNaN(upper) || Float.isInfinite(upper)) {
                            require(false) { "Upper candidate is NaN or infinite" }
                            j += 4
                            continue
                        }
                        if (upper <= lower) {
                            j += 4
                            continue
                        }
                        val mean = scoreErrorCorrelator.scoreErrorCorrelation(lower, upper)
                        if (mean > maxCorr) {
                            maxCorr = mean
                            bestLower = lower
                            bestUpper = upper
                            bestQuandrantLower = i
                            bestQuandrantUpper = j
                        }
                        j += 4
                    }
                    i += 4
                }
            }
            // Now search within the best quadrant
            for (i in bestQuandrantLower + 1..<bestQuandrantLower + 4) {
                for (j in bestQuandrantUpper + 1..<bestQuandrantUpper + 4) {
                    val lower = lowerCandidates[i]
                    val upper = upperCandidates[j]
                    if (Float.isNaN(lower)
                        || Float.isInfinite(lower)
                        || Float.isNaN(upper)
                        || Float.isInfinite(upper)
                    ) {
                        require(false) { "Lower or upper candidate is NaN or infinite" }
                        continue
                    }
                    if (upper <= lower) {
                        continue
                    }
                    val mean = scoreErrorCorrelator.scoreErrorCorrelation(lower, upper)
                    if (mean > maxCorr) {
                        maxCorr = mean
                        bestLower = lower
                        bestUpper = upper
                    }
                }
            }
            return floatArrayOf(bestLower, bestUpper)
        }

        /**
         * @param vectors The vectors to find the nearest neighbors for each other
         * @param similarityFunction The similarity function to use
         * @return The top 10 nearest neighbors for each vector from the vectors list
         */
        private fun findNearestNeighbors(
            vectors: MutableList<FloatArray?>, similarityFunction: VectorSimilarityFunction
        ): MutableList<ScoreDocsAndScoreVariance> {
            val queues: MutableList<HitQueue> = ArrayList(vectors.size)
            queues.add(HitQueue(10, false))
            for (i in vectors.indices) {
                val vector = vectors[i]
                for (j in i + 1..<vectors.size) {
                    val otherVector = vectors[j]
                    val score: Float = similarityFunction.compare(vector!!, otherVector!!)
                    // initialize the rest of the queues
                    if (queues.size <= j) {
                        queues.add(HitQueue(10, false))
                    }
                    queues[i].insertWithOverflow(ScoreDoc(j, score))
                    queues[j].insertWithOverflow(ScoreDoc(i, score))
                }
            }
            // Extract the top 10 from each queue
            val result: MutableList<ScoreDocsAndScoreVariance> =
                ArrayList(vectors.size)
            val meanAndVar = OnlineMeanAndVar()
            for (i in vectors.indices) {
                val queue: HitQueue = queues[i]
                val scoreDocs: Array<ScoreDoc?> = kotlin.arrayOfNulls(queue.size())
                for (j in queue.size() - 1 downTo 0) {
                    scoreDocs[j] = queue.pop()
                    checkNotNull(scoreDocs[j])
                    meanAndVar.add(scoreDocs[j]!!.score.toDouble())
                }
                result.add(ScoreDocsAndScoreVariance(scoreDocs as Array<ScoreDoc>, meanAndVar.`var`()))
                meanAndVar.reset()
            }
            return result
        }

        /**
         * Takes an array of floats, sorted or not, and returns a minimum and maximum value. These values
         * are such that they reside on the `(1 - confidenceInterval)/2` and `confidenceInterval/2`
         * percentiles. Example: providing floats `[0..100]` and asking for `90` quantiles will return `5`
         * and `95`.
         *
         * @param arr array of floats
         * @param confidenceInterval the configured confidence interval
         * @return lower and upper quantile values
         */
        fun getUpperAndLowerQuantile(arr: FloatArray, confidenceInterval: Float): FloatArray {
            require(arr.isNotEmpty())
            // If we have 1 or 2 values, we can't calculate the quantiles, simply return the min and max
            if (arr.size <= 2) {
                Arrays.sort(arr)
                return floatArrayOf(arr[0], arr[arr.size - 1])
            }
            val selectorIndex = (arr.size * (1f - confidenceInterval) / 2f + 0.5f).toInt()
            if (selectorIndex > 0) {
                val selector: Selector = FloatSelector(arr)
                selector.select(0, arr.size, arr.size - selectorIndex)
                selector.select(0, arr.size - selectorIndex, selectorIndex)
            }
            var min = Float.Companion.POSITIVE_INFINITY
            var max = Float.Companion.NEGATIVE_INFINITY
            for (i in selectorIndex..<arr.size - selectorIndex) {
                min = min(arr[i], min)
                max = max(arr[i], max)
            }
            return floatArrayOf(min, max)
        }
    }
}
