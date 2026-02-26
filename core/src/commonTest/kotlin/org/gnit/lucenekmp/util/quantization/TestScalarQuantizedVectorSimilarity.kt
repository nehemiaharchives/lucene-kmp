/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.util.quantization

import okio.IOException
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.VectorUtil
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestScalarQuantizedVectorSimilarity : LuceneTestCase() {

    @Test
    fun testNonZeroScores() {
        val quantized = Array(2) { ByteArray(32) }
        for (similarityFunction in VectorSimilarityFunction.entries) {
            var multiplier = random().nextFloat()
            if (random().nextBoolean()) {
                multiplier = -multiplier
            }
            for (bits in byteArrayOf(4, 7)) {
                val quantizedSimilarity =
                    ScalarQuantizedVectorSimilarity.fromVectorSimilarity(
                        similarityFunction,
                        multiplier,
                        bits
                    )
                val negativeOffsetA = -(random().nextFloat() * (random().nextInt(10) + 1))
                val negativeOffsetB = -(random().nextFloat() * (random().nextInt(10) + 1))
                val score =
                    quantizedSimilarity.score(
                        quantized[0],
                        negativeOffsetA,
                        quantized[1],
                        negativeOffsetB
                    )
                assertTrue(score >= 0)
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testToEuclidean() {
        val dims = 128
        val numVecs = 100

        val floats = TestScalarQuantizer.randomFloats(numVecs, dims)
        for (confidenceInterval in floatArrayOf(0.9f, 0.95f, 0.99f, (1 - 1f / (dims + 1)), 1f)) {
            val error = max((100 - confidenceInterval) * 0.01f, 0.01f)
            val floatVectorValues = TestScalarQuantizer.fromFloats(floats)
            val scalarQuantizer =
                ScalarQuantizer.fromVectors(floatVectorValues, confidenceInterval, numVecs, 7)
            val quantized = Array(floats.size) { ByteArray(0) }
            val offsets =
                quantizeVectors(
                    scalarQuantizer,
                    floats,
                    quantized,
                    VectorSimilarityFunction.EUCLIDEAN
                )
            val query = ArrayUtil.copyOfSubArray(floats[0], 0, dims)
            val quantizedSimilarity =
                ScalarQuantizedVectorSimilarity.fromVectorSimilarity(
                    VectorSimilarityFunction.EUCLIDEAN,
                    scalarQuantizer.constantMultiplier,
                    scalarQuantizer.bits
                )
            assertQuantizedScores(
                floats,
                quantized,
                offsets,
                query,
                error,
                VectorSimilarityFunction.EUCLIDEAN,
                quantizedSimilarity,
                scalarQuantizer
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testToCosine() {
        val dims = 128
        val numVecs = 100

        val floats = TestScalarQuantizer.randomFloats(numVecs, dims)

        for (confidenceInterval in floatArrayOf(0.9f, 0.95f, 0.99f, (1 - 1f / (dims + 1)), 1f)) {
            val error = max((100 - confidenceInterval) * 0.01f, 0.01f)
            val floatVectorValues = fromFloatsNormalized(floats, null)
            val scalarQuantizer =
                ScalarQuantizer.fromVectors(floatVectorValues, confidenceInterval, numVecs, 7)
            val quantized = Array(floats.size) { ByteArray(0) }
            val offsets =
                quantizeVectorsNormalized(
                    scalarQuantizer,
                    floats,
                    quantized,
                    VectorSimilarityFunction.COSINE
                )
            val query = ArrayUtil.copyOfSubArray(floats[0], 0, dims)
            VectorUtil.l2normalize(query)
            val quantizedSimilarity =
                ScalarQuantizedVectorSimilarity.fromVectorSimilarity(
                    VectorSimilarityFunction.COSINE,
                    scalarQuantizer.constantMultiplier,
                    scalarQuantizer.bits
                )
            assertQuantizedScores(
                floats,
                quantized,
                offsets,
                query,
                error,
                VectorSimilarityFunction.COSINE,
                quantizedSimilarity,
                scalarQuantizer
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testToDotProduct() {
        val dims = 128
        val numVecs = 100

        val floats = TestScalarQuantizer.randomFloats(numVecs, dims)
        for (fs in floats) {
            VectorUtil.l2normalize(fs)
        }
        for (confidenceInterval in floatArrayOf(0.9f, 0.95f, 0.99f, (1 - 1f / (dims + 1)), 1f)) {
            val error = max((100 - confidenceInterval) * 0.01f, 0.01f)
            val floatVectorValues = TestScalarQuantizer.fromFloats(floats)
            val scalarQuantizer =
                ScalarQuantizer.fromVectors(floatVectorValues, confidenceInterval, numVecs, 7)
            val quantized = Array(floats.size) { ByteArray(0) }
            val offsets =
                quantizeVectors(
                    scalarQuantizer,
                    floats,
                    quantized,
                    VectorSimilarityFunction.DOT_PRODUCT
                )
            val query = TestScalarQuantizer.randomFloatArray(dims)
            VectorUtil.l2normalize(query)
            val quantizedSimilarity =
                ScalarQuantizedVectorSimilarity.fromVectorSimilarity(
                    VectorSimilarityFunction.DOT_PRODUCT,
                    scalarQuantizer.constantMultiplier,
                    scalarQuantizer.bits
                )
            assertQuantizedScores(
                floats,
                quantized,
                offsets,
                query,
                error,
                VectorSimilarityFunction.DOT_PRODUCT,
                quantizedSimilarity,
                scalarQuantizer
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testToMaxInnerProduct() {
        val dims = 128
        val numVecs = 100

        val floats = TestScalarQuantizer.randomFloats(numVecs, dims)
        for (confidenceInterval in floatArrayOf(0.9f, 0.95f, 0.99f, (1 - 1f / (dims + 1)), 1f)) {
            val error = max((100 - confidenceInterval) * 0.5f, 0.5f)
            val floatVectorValues = TestScalarQuantizer.fromFloats(floats)
            val scalarQuantizer =
                ScalarQuantizer.fromVectors(floatVectorValues, confidenceInterval, numVecs, 7)
            val quantized = Array(floats.size) { ByteArray(0) }
            val offsets =
                quantizeVectors(
                    scalarQuantizer,
                    floats,
                    quantized,
                    VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
                )
            val query = TestScalarQuantizer.randomFloatArray(dims)
            val quantizedSimilarity =
                ScalarQuantizedVectorSimilarity.fromVectorSimilarity(
                    VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT,
                    scalarQuantizer.constantMultiplier,
                    scalarQuantizer.bits
                )
            assertQuantizedScores(
                floats,
                quantized,
                offsets,
                query,
                error,
                VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT,
                quantizedSimilarity,
                scalarQuantizer
            )
        }
    }

    private fun assertQuantizedScores(
        floats: Array<FloatArray>,
        quantized: Array<ByteArray>,
        storedOffsets: FloatArray,
        query: FloatArray,
        error: Float,
        similarityFunction: VectorSimilarityFunction,
        quantizedSimilarity: ScalarQuantizedVectorSimilarity,
        scalarQuantizer: ScalarQuantizer
    ) {
        for (i in floats.indices) {
            val storedOffset = storedOffsets[i]
            val quantizedQuery = ByteArray(query.size)
            val queryOffset = scalarQuantizer.quantize(query, quantizedQuery, similarityFunction)
            val original = similarityFunction.compare(query, floats[i])
            val quantizedScore =
                quantizedSimilarity.score(
                    quantizedQuery,
                    queryOffset,
                    quantized[i],
                    storedOffset
                )
            assertEquals(
                original,
                quantizedScore,
                error,
                "Not within acceptable error [$error]"
            )
        }
    }

    private fun quantizeVectors(
        scalarQuantizer: ScalarQuantizer,
        floats: Array<FloatArray>,
        quantized: Array<ByteArray>,
        similarityFunction: VectorSimilarityFunction
    ): FloatArray {
        var i = 0
        val offsets = FloatArray(floats.size)
        for (v in floats) {
            quantized[i] = ByteArray(v.size)
            offsets[i] = scalarQuantizer.quantize(v, quantized[i], similarityFunction)
            ++i
        }
        return offsets
    }

    private fun quantizeVectorsNormalized(
        scalarQuantizer: ScalarQuantizer,
        floats: Array<FloatArray>,
        quantized: Array<ByteArray>,
        similarityFunction: VectorSimilarityFunction
    ): FloatArray {
        var i = 0
        val offsets = FloatArray(floats.size)
        for (f in floats) {
            val v = ArrayUtil.copyArray(f)
            VectorUtil.l2normalize(v)
            quantized[i] = ByteArray(v.size)
            offsets[i] = scalarQuantizer.quantize(v, quantized[i], similarityFunction)
            ++i
        }
        return offsets
    }

    private fun fromFloatsNormalized(
        floats: Array<FloatArray>,
        deletedVectors: Set<Int>?
    ): FloatVectorValues {
        return object : TestScalarQuantizer.TestSimpleFloatVectorValues(floats, deletedVectors) {
            @Throws(IOException::class)
            override fun vectorValue(ord: Int): FloatArray {
                val v = ArrayUtil.copyArray(floats[ordToDoc[ord]])
                VectorUtil.l2normalize(v)
                return v
            }
        }
    }
}
