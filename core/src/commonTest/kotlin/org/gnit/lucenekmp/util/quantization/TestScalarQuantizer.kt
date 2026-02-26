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
import org.gnit.lucenekmp.util.VectorUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestScalarQuantizer : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testTinyVectors() {
        for (function in VectorSimilarityFunction.entries) {
            val dims = random().nextInt(9) + 1
            val numVecs = random().nextInt(9) + 10
            val floats = randomFloats(numVecs, dims)
            if (function == VectorSimilarityFunction.COSINE) {
                for (v in floats) {
                    VectorUtil.l2normalize(v)
                }
            }
            for (bits in byteArrayOf(4, 7)) {
                val floatVectorValues = fromFloats(floats)
                val actualFunction =
                    if (function == VectorSimilarityFunction.COSINE) {
                        VectorSimilarityFunction.DOT_PRODUCT
                    } else {
                        function
                    }
                val scalarQuantizer =
                    if (random().nextBoolean()) {
                        ScalarQuantizer.fromVectors(floatVectorValues, 0.9f, numVecs, bits)
                    } else {
                        ScalarQuantizer.fromVectorsAutoInterval(
                            floatVectorValues,
                            actualFunction,
                            numVecs,
                            bits
                        )
                    }
                // We simply assert that we created a scalar quantizer and didn't trip any assertions
                // the quality of the quantization might be poor, but this is expected as sampling size is
                // tiny
                assertNotNull(scalarQuantizer)
            }
        }
    }

    @Test
    fun testNanAndInfValueFailure() {
        for (function in VectorSimilarityFunction.entries) {
            val dims = random().nextInt(9) + 1
            val numVecs = random().nextInt(9) + 10
            val floats = Array(numVecs) { FloatArray(dims) }
            for (i in 0 until numVecs) {
                for (j in 0 until dims) {
                    floats[i][j] =
                        if (random().nextBoolean()) Float.NaN else Float.POSITIVE_INFINITY
                }
            }
            for (bits in byteArrayOf(4, 7)) {
                val floatVectorValues = fromFloats(floats)
                expectThrows(IllegalStateException::class) {
                    ScalarQuantizer.fromVectors(floatVectorValues, 0.9f, numVecs, bits)
                }
                val actualFunction =
                    if (function == VectorSimilarityFunction.COSINE) {
                        VectorSimilarityFunction.DOT_PRODUCT
                    } else {
                        function
                    }
                expectThrows(IllegalStateException::class) {
                    ScalarQuantizer.fromVectorsAutoInterval(
                        floatVectorValues,
                        actualFunction,
                        numVecs,
                        bits
                    )
                }
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testQuantizeAndDeQuantize7Bit() {
        val dims = 128
        val numVecs = 100
        val similarityFunction = VectorSimilarityFunction.DOT_PRODUCT

        val floats = randomFloats(numVecs, dims)
        val floatVectorValues = fromFloats(floats)
        val scalarQuantizer =
            ScalarQuantizer.fromVectors(floatVectorValues, 1f, numVecs, 7)
        val dequantized = FloatArray(dims)
        val quantized = ByteArray(dims)
        val requantized = ByteArray(dims)
        var maxDimValue: Byte = -128
        var minDimValue: Byte = 127
        for (i in 0 until numVecs) {
            scalarQuantizer.quantize(floats[i], quantized, similarityFunction)
            scalarQuantizer.deQuantize(quantized, dequantized)
            scalarQuantizer.quantize(dequantized, requantized, similarityFunction)
            for (j in 0 until dims) {
                if (quantized[j] > maxDimValue) {
                    maxDimValue = quantized[j]
                }
                if (quantized[j] < minDimValue) {
                    minDimValue = quantized[j]
                }
                assertEquals(dequantized[j], floats[i][j], 0.02f)
                assertEquals(quantized[j], requantized[j])
            }
        }
        // int7 should always quantize to 0-127
        assertTrue(minDimValue >= 0)
    }

    @Test
    fun testQuantiles() {
        val percs = FloatArray(1000)
        for (i in 0 until 1000) {
            percs[i] = i.toFloat()
        }
        shuffleArray(percs)
        var upperAndLower = ScalarQuantizer.getUpperAndLowerQuantile(percs, 0.9f)
        assertEquals(50f, upperAndLower[0], 1e-7f)
        assertEquals(949f, upperAndLower[1], 1e-7f)
        shuffleArray(percs)
        upperAndLower = ScalarQuantizer.getUpperAndLowerQuantile(percs, 0.95f)
        assertEquals(25f, upperAndLower[0], 1e-7f)
        assertEquals(974f, upperAndLower[1], 1e-7f)
        shuffleArray(percs)
        upperAndLower = ScalarQuantizer.getUpperAndLowerQuantile(percs, 0.99f)
        assertEquals(5f, upperAndLower[0], 1e-7f)
        assertEquals(994f, upperAndLower[1], 1e-7f)
    }

    @Test
    fun testEdgeCase() {
        val upperAndLower =
            ScalarQuantizer.getUpperAndLowerQuantile(
                floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f),
                0.9f
            )
        assertEquals(1f, upperAndLower[0], 1e-7f)
        assertEquals(1f, upperAndLower[1], 1e-7f)
    }

    @Test
    @Throws(IOException::class)
    fun testScalarWithSampling() {
        val numVecs = random().nextInt(128) + 5
        val dims = 64
        val floats = randomFloats(numVecs, dims)
        // Should not throw
        run {
            val floatVectorValues =
                fromFloatsWithRandomDeletions(floats, random().nextInt(numVecs - 1) + 1)
            ScalarQuantizer.fromVectors(
                floatVectorValues,
                0.99f,
                floatVectorValues.numLiveVectors,
                7,
                kotlin.math.max(floatVectorValues.numLiveVectors - 1, ScalarQuantizer.SCRATCH_SIZE + 1)
            )
        }
        run {
            val floatVectorValues =
                fromFloatsWithRandomDeletions(floats, random().nextInt(numVecs - 1) + 1)
            ScalarQuantizer.fromVectors(
                floatVectorValues,
                0.99f,
                floatVectorValues.numLiveVectors,
                7,
                kotlin.math.max(floatVectorValues.numLiveVectors - 1, ScalarQuantizer.SCRATCH_SIZE + 1)
            )
        }
        run {
            val floatVectorValues =
                fromFloatsWithRandomDeletions(floats, random().nextInt(numVecs - 1) + 1)
            ScalarQuantizer.fromVectors(
                floatVectorValues,
                0.99f,
                floatVectorValues.numLiveVectors,
                7,
                kotlin.math.max(floatVectorValues.numLiveVectors - 1, ScalarQuantizer.SCRATCH_SIZE + 1)
            )
        }
        run {
            val floatVectorValues =
                fromFloatsWithRandomDeletions(floats, random().nextInt(numVecs - 1) + 1)
            ScalarQuantizer.fromVectors(
                floatVectorValues,
                0.99f,
                floatVectorValues.numLiveVectors,
                7,
                kotlin.math.max(random().nextInt(floatVectorValues.floats.size - 1) + 1, ScalarQuantizer.SCRATCH_SIZE + 1)
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testFromVectorsAutoInterval4Bit() {
        val dims = 128
        val numVecs = 100
        val similarityFunction = VectorSimilarityFunction.DOT_PRODUCT

        val floats = randomFloats(numVecs, dims)
        for (v in floats) {
            VectorUtil.l2normalize(v)
        }
        val floatVectorValues = fromFloats(floats)
        val scalarQuantizer =
            ScalarQuantizer.fromVectorsAutoInterval(
                floatVectorValues,
                similarityFunction,
                numVecs,
                4
            )
        assertNotNull(scalarQuantizer)
        val dequantized = FloatArray(dims)
        val quantized = ByteArray(dims)
        val requantized = ByteArray(dims)
        var maxDimValue: Byte = -128
        var minDimValue: Byte = 127
        for (i in 0 until numVecs) {
            scalarQuantizer.quantize(floats[i], quantized, similarityFunction)
            scalarQuantizer.deQuantize(quantized, dequantized)
            scalarQuantizer.quantize(dequantized, requantized, similarityFunction)
            for (j in 0 until dims) {
                if (quantized[j] > maxDimValue) {
                    maxDimValue = quantized[j]
                }
                if (quantized[j] < minDimValue) {
                    minDimValue = quantized[j]
                }
                assertEquals(dequantized[j], floats[i][j], 0.2f)
                assertEquals(quantized[j], requantized[j])
            }
        }
        // int4 should always quantize to 0-15
        assertTrue(minDimValue >= 0)
        assertTrue(maxDimValue <= 15)
    }

    companion object {
        fun shuffleArray(ar: FloatArray) {
            for (i in ar.size - 1 downTo 1) {
                val index = random().nextInt(i + 1)
                val a = ar[index]
                ar[index] = ar[i]
                ar[i] = a
            }
        }

        fun randomFloatArray(dims: Int): FloatArray {
            val arr = FloatArray(dims)
            for (j in 0 until dims) {
                arr[j] = random().nextFloat() * 2f - 1f
            }
            return arr
        }

        fun randomFloats(num: Int, dims: Int): Array<FloatArray> {
            val floats = Array(num) { FloatArray(0) }
            for (i in 0 until num) {
                floats[i] = randomFloatArray(dims)
            }
            return floats
        }

        fun fromFloats(floats: Array<FloatArray>): FloatVectorValues {
            return TestSimpleFloatVectorValues(floats, null)
        }

        fun fromFloatsWithRandomDeletions(
            floats: Array<FloatArray>,
            numDeleted: Int
        ): TestSimpleFloatVectorValues {
            val deletedVectors = hashSetOf<Int>()
            for (i in 0 until numDeleted) {
                deletedVectors.add(random().nextInt(floats.size))
            }
            return TestSimpleFloatVectorValues(floats, deletedVectors)
        }
    }

    open class TestSimpleFloatVectorValues(
        val floats: Array<FloatArray>,
        protected val deletedVectors: Set<Int>?
    ) : FloatVectorValues() {
        protected val ordToDoc: IntArray
        val numLiveVectors: Int

        init {
            numLiveVectors =
                if (deletedVectors == null) floats.size else floats.size - deletedVectors.size
            ordToDoc = IntArray(numLiveVectors)
            if (deletedVectors == null) {
                for (i in 0 until numLiveVectors) {
                    ordToDoc[i] = i
                }
            } else {
                var ord = 0
                for (doc in floats.indices) {
                    if (!deletedVectors.contains(doc)) {
                        ordToDoc[ord++] = doc
                    }
                }
            }
        }

        override fun dimension(): Int {
            return floats[0].size
        }

        override fun size(): Int {
            return floats.size
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): FloatArray {
            return floats[ordToDoc(ord)]
        }

        override fun ordToDoc(ord: Int): Int {
            return ordToDoc[ord]
        }

        override fun iterator(): DocIndexIterator {
            return object : DocIndexIterator() {
                var ord = -1
                var doc = -1

                override fun docID(): Int {
                    return doc
                }

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    while (doc < floats.size - 1) {
                        ++doc
                        if (deletedVectors == null || !deletedVectors.contains(doc)) {
                            ++ord
                            return doc
                        }
                    }
                    doc = NO_MORE_DOCS
                    return doc
                }

                override fun index(): Int {
                    return ord
                }

                override fun cost(): Long {
                    return (floats.size - (deletedVectors?.size ?: 0)).toLong()
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    throw UnsupportedOperationException()
                }
            }
        }

        override fun copy(): TestSimpleFloatVectorValues {
            return this
        }
    }
}
