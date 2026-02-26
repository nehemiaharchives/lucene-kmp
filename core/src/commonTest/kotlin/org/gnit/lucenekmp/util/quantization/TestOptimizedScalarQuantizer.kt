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

import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.VectorUtil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestOptimizedScalarQuantizer : LuceneTestCase() {
    companion object {
        val ALL_BITS: ByteArray = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    }

    @Test
    fun testAbusiveEdgeCases() {
        // large zero array
        for (vectorSimilarityFunction in VectorSimilarityFunction.entries) {
            if (vectorSimilarityFunction == VectorSimilarityFunction.COSINE) {
                continue
            }
            val vector = FloatArray(4096)
            val centroid = FloatArray(4096)
            val osq = OptimizedScalarQuantizer(vectorSimilarityFunction)
            val destinations = Array(OptimizedScalarQuantizer.MINIMUM_MSE_GRID.size) { ByteArray(4096) }
            val results = osq.multiScalarQuantize(vector, destinations, ALL_BITS, centroid)
            assertEquals(OptimizedScalarQuantizer.MINIMUM_MSE_GRID.size, results.size)
            assertValidResults(*results)
            for (destination in destinations) {
                assertContentEquals(ByteArray(4096), destination)
            }
            val destination = ByteArray(4096)
            for (bit in ALL_BITS) {
                val result = osq.scalarQuantize(vector, destination, bit, centroid)
                assertValidResults(result)
                assertContentEquals(ByteArray(4096), destination)
            }
        }

        // single value array
        for (vectorSimilarityFunction in VectorSimilarityFunction.entries) {
            var vector = floatArrayOf(random().nextFloat())
            var centroid = floatArrayOf(random().nextFloat())
            if (vectorSimilarityFunction == VectorSimilarityFunction.COSINE) {
                VectorUtil.l2normalize(vector)
                VectorUtil.l2normalize(centroid)
            }
            val osq = OptimizedScalarQuantizer(vectorSimilarityFunction)
            val destinations = Array(OptimizedScalarQuantizer.MINIMUM_MSE_GRID.size) { ByteArray(1) }
            val results = osq.multiScalarQuantize(vector, destinations, ALL_BITS, centroid)
            assertEquals(OptimizedScalarQuantizer.MINIMUM_MSE_GRID.size, results.size)
            assertValidResults(*results)
            for (i in ALL_BITS.indices) {
                assertValidQuantizedRange(destinations[i], ALL_BITS[i])
            }
            for (bit in ALL_BITS) {
                vector = floatArrayOf(random().nextFloat())
                centroid = floatArrayOf(random().nextFloat())
                if (vectorSimilarityFunction == VectorSimilarityFunction.COSINE) {
                    VectorUtil.l2normalize(vector)
                    VectorUtil.l2normalize(centroid)
                }
                val destination = ByteArray(1)
                val result = osq.scalarQuantize(vector, destination, bit, centroid)
                assertValidResults(result)
                assertValidQuantizedRange(destination, bit)
            }
        }
    }

    @Test
    fun testMathematicalConsistency() {
        val dims = TestUtil.nextInt(random(), 1, 4096)
        val vector = FloatArray(dims)
        for (i in 0 until dims) {
            vector[i] = random().nextFloat()
        }
        val centroid = FloatArray(dims)
        for (i in 0 until dims) {
            centroid[i] = random().nextFloat()
        }
        val copy = FloatArray(dims)
        for (vectorSimilarityFunction in VectorSimilarityFunction.entries) {
            // copy the vector to avoid modifying it
            vector.copyInto(copy, 0, 0, dims)
            if (vectorSimilarityFunction == VectorSimilarityFunction.COSINE) {
                VectorUtil.l2normalize(copy)
                VectorUtil.l2normalize(centroid)
            }
            val osq = OptimizedScalarQuantizer(vectorSimilarityFunction)
            val destinations = Array(OptimizedScalarQuantizer.MINIMUM_MSE_GRID.size) { ByteArray(dims) }
            val results = osq.multiScalarQuantize(copy, destinations, ALL_BITS, centroid)
            assertEquals(OptimizedScalarQuantizer.MINIMUM_MSE_GRID.size, results.size)
            assertValidResults(*results)
            for (i in ALL_BITS.indices) {
                assertValidQuantizedRange(destinations[i], ALL_BITS[i])
            }
            for (bit in ALL_BITS) {
                val destination = ByteArray(dims)
                vector.copyInto(copy, 0, 0, dims)
                if (vectorSimilarityFunction == VectorSimilarityFunction.COSINE) {
                    VectorUtil.l2normalize(copy)
                    VectorUtil.l2normalize(centroid)
                }
                val result = osq.scalarQuantize(copy, destination, bit, centroid)
                assertValidResults(result)
                assertValidQuantizedRange(destination, bit)
            }
        }
    }

    private fun assertValidQuantizedRange(quantized: ByteArray, bits: Byte) {
        for (b in quantized) {
            if (bits < 8) {
                assertTrue(b >= 0)
            }
            assertTrue(b.toInt() < (1 shl bits.toInt()))
        }
    }

    private fun assertValidResults(vararg results: OptimizedScalarQuantizer.QuantizationResult) {
        for (result in results) {
            assertTrue(result.lowerInterval.isFinite())
            assertTrue(result.upperInterval.isFinite())
            assertTrue(result.lowerInterval <= result.upperInterval)
            assertTrue(result.additionalCorrection.isFinite())
            assertTrue(result.quantizedComponentSum >= 0)
        }
    }
}
