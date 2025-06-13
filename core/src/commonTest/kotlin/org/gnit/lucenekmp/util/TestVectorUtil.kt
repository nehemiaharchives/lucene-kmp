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
package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestVectorUtil : LuceneTestCase() {

    companion object {
        const val DELTA: Double = 1e-4
    }

    @Test
    fun testBasicDotProduct() {
        assertEquals(5f, VectorUtil.dotProduct(floatArrayOf(1f, 2f, 3f), floatArrayOf(-10f, 0f, 5f)), 0f)
    }

    @Test
    fun testSelfDotProduct() {
        val v = randomVector()
        assertEquals(l2(v), VectorUtil.dotProduct(v, v).toDouble(), DELTA)
    }

    @Test
    fun testOrthogonalDotProduct() {
        val v = FloatArray(2)
        v[0] = random().nextInt(100).toFloat()
        v[1] = random().nextInt(100).toFloat()
        val u = floatArrayOf(v[1], -v[0])
        assertEquals(0f, VectorUtil.dotProduct(u, v), 0f)
    }

    @Test
    fun testDotProductThrowsForDimensionMismatch() {
        val u = floatArrayOf(0f, 1f)
        val v = floatArrayOf(1f, 0f, 0f)
        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) { VectorUtil.dotProduct(u, v) }
    }

    @Test
    fun testSelfSquareDistance() {
        val v = randomVector()
        assertEquals(0f, VectorUtil.squareDistance(v, v), 0f)
    }

    @Test
    fun testBasicSquareDistance() {
        assertEquals(12f, VectorUtil.squareDistance(floatArrayOf(1f, 2f, 3f), floatArrayOf(-1f, 0f, 5f)), 0f)
    }

    @Test
    fun testSquareDistanceThrowsForDimensionMismatch() {
        val u = floatArrayOf(0f, 1f)
        val v = floatArrayOf(1f, 0f, 0f)
        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) { VectorUtil.squareDistance(u, v) }
    }

    @Test
    fun testRandomSquareDistance() {
        val v = randomVector()
        val u = negative(v)
        assertEquals(4 * l2(v), VectorUtil.squareDistance(u, v).toDouble(), DELTA)
    }

    @Test
    fun testBasicCosine() {
        assertEquals(0.11952f, VectorUtil.cosine(floatArrayOf(1f, 2f, 3f), floatArrayOf(-10f, 0f, 5f)), DELTA.toFloat())
    }

    @Test
    fun testSelfCosine() {
        val v = randomVector()
        v[0] = random().nextFloat() + 0.01f
        assertEquals(1f, VectorUtil.cosine(v, v), DELTA.toFloat())
    }

    @Test
    fun testOrthogonalCosine() {
        val v = FloatArray(2)
        v[0] = random().nextInt(100).toFloat()
        v[1] = random().nextInt(1, 100).toFloat()
        val u = floatArrayOf(v[1], -v[0])
        assertEquals(0f, VectorUtil.cosine(u, v), 0f)
    }

    @Test
    fun testCosineThrowsForDimensionMismatch() {
        val u = floatArrayOf(0f, 1f)
        val v = floatArrayOf(1f, 0f, 0f)
        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) { VectorUtil.cosine(u, v) }
    }

    @Test
    fun testNormalize() {
        val v = randomVector()
        v[random().nextInt(v.size)] = 1f
        VectorUtil.l2normalize(v)
        assertEquals(1.0, l2(v), DELTA)
    }

    @Test
    fun testNormalizeZeroThrows() {
        val v = floatArrayOf(0f, 0f, 0f)
        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) { VectorUtil.l2normalize(v) }
    }

    @Test
    fun testExtremeNumerics() {
        val v1 = FloatArray(1536) { 0.888888f }
        val v2 = FloatArray(1536) { -0.777777f }
        for (sim in VectorSimilarityFunction.entries) {
            val score = sim.compare(v1, v2)
            assertTrue(score >= 0f, "${sim} expected >=0 got:$score")
        }
    }

    @Test
    fun testBasicDotProductBytes() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(-10, 0, 5)
        assertEquals(5, VectorUtil.dotProduct(a, b))
        val denom = (a.size * (1 shl 15)).toFloat()
        assertEquals(0.5f + 5 / denom, VectorUtil.dotProductScore(a, b), DELTA.toFloat())
        val zero = byteArrayOf(0, 0, 0)
        assertEquals(0.5f, VectorUtil.dotProductScore(a, zero), DELTA.toFloat())
        val min = byteArrayOf(-128, -128)
        val max = byteArrayOf(127, 127)
        assertEquals(0.0039f, VectorUtil.dotProductScore(min, max), DELTA.toFloat())
        assertEquals(1f, VectorUtil.dotProductScore(min, min), DELTA.toFloat())
    }

    @Test
    fun testSelfDotProductBytes() {
        val v = randomVectorBytes()
        assertEquals(l2(v), VectorUtil.dotProduct(v, v).toDouble(), DELTA)
    }

    @Test
    fun testOrthogonalDotProductBytes() {
        val a = ByteArray(2)
        a[0] = random().nextInt(100).toByte()
        a[1] = random().nextInt(100).toByte()
        val b = byteArrayOf(a[1], (-a[0]).toByte())
        assertEquals(0, VectorUtil.dotProduct(a, b))
    }

    @Test
    fun testSelfSquareDistanceBytes() {
        val v = randomVectorBytes()
        assertEquals(0f, VectorUtil.squareDistance(v, v).toFloat(), 0f)
    }

    @Test
    fun testBasicSquareDistanceBytes() {
        assertEquals(12, VectorUtil.squareDistance(byteArrayOf(1, 2, 3), byteArrayOf(-1, 0, 5)))
    }

    @Test
    fun testRandomSquareDistanceBytes() {
        val v = randomVectorBytes()
        val u = negative(v)
        assertEquals(4 * l2(v), VectorUtil.squareDistance(u, v).toDouble(), DELTA)
    }

    @Test
    fun testBasicCosineBytes() {
        assertEquals(0.11952f, VectorUtil.cosine(byteArrayOf(1, 2, 3), byteArrayOf(-10, 0, 5)), DELTA.toFloat())
    }

    @Test
    fun testSelfCosineBytes() {
        val v = randomVectorBytes()
        v[0] = (random().nextInt(126) + 1).toByte()
        assertEquals(1f, VectorUtil.cosine(v, v), DELTA.toFloat())
    }

    @Test
    fun testOrthogonalCosineBytes() {
        val v = FloatArray(2)
        v[0] = random().nextInt(100).toFloat()
        v[1] = random().nextInt(1, 100).toFloat()
        val u = floatArrayOf(v[1], -v[0])
        assertEquals(0f, VectorUtil.cosine(u, v), 0f)
    }

    fun interface ToIntBiFunction {
        fun apply(a: ByteArray, b: ByteArray): Int
    }

    @Test
    fun testBasicXorBitCount() {
        testBasicXorBitCountImpl { a, b -> VectorUtil.xorBitCount(a, b) }
        testBasicXorBitCountImpl { a, b -> VectorUtil.xorBitCountInt(a, b) }
        testBasicXorBitCountImpl { a, b -> VectorUtil.xorBitCountLong(a, b) }
        testBasicXorBitCountImpl { a, b -> xorBitCount(a, b) }
    }

    private fun testBasicXorBitCountImpl(xor: ToIntBiFunction) {
        assertEquals(0, xor.apply(byteArrayOf(1), byteArrayOf(1)))
        assertEquals(0, xor.apply(byteArrayOf(1,2,3), byteArrayOf(1,2,3)))
        assertEquals(1, xor.apply(byteArrayOf(1,2,3), byteArrayOf(0,2,3)))
        assertEquals(2, xor.apply(byteArrayOf(1,2,3), byteArrayOf(0,6,3)))
        assertEquals(3, xor.apply(byteArrayOf(1,2,3), byteArrayOf(0,6,7)))
        assertEquals(4, xor.apply(byteArrayOf(1,2,3), byteArrayOf(2,6,7)))
        assertEquals(0, xor.apply(byteArrayOf(1,2,3,4), byteArrayOf(1,2,3,4)))
        assertEquals(1, xor.apply(byteArrayOf(1,2,3,4), byteArrayOf(0,2,3,4)))
        assertEquals(0, xor.apply(byteArrayOf(1,2,3,4,5), byteArrayOf(1,2,3,4,5)))
        assertEquals(1, xor.apply(byteArrayOf(1,2,3,4,5), byteArrayOf(0,2,3,4,5)))
        assertEquals(0, xor.apply(byteArrayOf(1,2,3,4,5,6,7,8), byteArrayOf(1,2,3,4,5,6,7,8)))
        assertEquals(1, xor.apply(byteArrayOf(1,2,3,4,5,6,7,8), byteArrayOf(0,2,3,4,5,6,7,8)))
        assertEquals(0, xor.apply(byteArrayOf(1,2,3,4,5,6,7,8,9), byteArrayOf(1,2,3,4,5,6,7,8,9)))
        assertEquals(1, xor.apply(byteArrayOf(1,2,3,4,5,6,7,8,9), byteArrayOf(0,2,3,4,5,6,7,8,9)))
    }

    @Test
    fun testXorBitCount() {
        val iterations = atLeast(100)
        for (i in 0 until iterations) {
            val size = random().nextInt(1024)
            val a = ByteArray(size)
            val b = ByteArray(size)
            random().nextBytes(a)
            random().nextBytes(b)
            val expected = xorBitCount(a, b)
            assertEquals(expected, VectorUtil.xorBitCount(a, b))
            assertEquals(expected, VectorUtil.xorBitCountInt(a, b))
            assertEquals(expected, VectorUtil.xorBitCountLong(a, b))
        }
    }

    @Test
    fun testFindNextGEQ() {
        val padding = TestUtil.nextInt(random(), 0, 5)
        val values = IntArray(128 + padding)
        var v = 0
        for (i in 0 until 128) {
            v += TestUtil.nextInt(random(), 1, 1000)
            values[i] = v
        }
        for (iter in 0 until 1000) {
            val from = TestUtil.nextInt(random(), 0, 127)
            val target = TestUtil.nextInt(random(), values[from], maxOf(values[from], values[127])) + random().nextInt(10) - 5
            assertEquals(slowFindNextGEQ(values, 128, target, from), VectorUtil.findNextGEQ(values, target, from, 128))
        }
    }

    @Test
    fun testInt4BitDotProductInvariants() {
        val iterations = atLeast(10)
        for (i in 0 until iterations) {
            val size = TestUtil.nextInt(random(), 1, 10)
            val d = ByteArray(size)
            val q = ByteArray(size * 4 - 1)
            expectThrows<IllegalArgumentException>(IllegalArgumentException::class) { VectorUtil.int4BitDotProduct(q, d) }
        }
    }

    @Test
    fun testBasicInt4BitDotProduct() {
        testBasicInt4BitDotProductImpl { q, d -> VectorUtil.int4BitDotProduct(q, d) }
    }

    fun interface Int4BitDotProduct {
        fun apply(q: ByteArray, d: ByteArray): Long
    }

    private fun testBasicInt4BitDotProductImpl(f: Int4BitDotProduct) {
        assertEquals(15L, f.apply(byteArrayOf(1,1,1,1), byteArrayOf(1)))
        assertEquals(30L, f.apply(byteArrayOf(1,2,1,2,1,2,1,2), byteArrayOf(1,2)))
        var d = byteArrayOf(1,2,3)
        var q = byteArrayOf(1,2,3,1,2,3,1,2,3,1,2,3)
        assertEquals(60L, f.apply(q,d))
        d = byteArrayOf(1,2,3,4)
        q = byteArrayOf(1,2,3,4,1,2,3,4,1,2,3,4,1,2,3,4)
        assertEquals(75L, f.apply(q,d))
        d = byteArrayOf(1,2,3,4,5)
        q = byteArrayOf(1,2,3,4,5,1,2,3,4,5,1,2,3,4,5,1,2,3,4,5)
        assertEquals(105L, f.apply(q,d))
        d = byteArrayOf(1,2,3,4,5,6)
        q = byteArrayOf(1,2,3,4,5,6,1,2,3,4,5,6,1,2,3,4,5,6,1,2,3,4,5,6)
        assertEquals(135L, f.apply(q,d))
        d = byteArrayOf(1,2,3,4,5,6,7)
        q = byteArrayOf(1,2,3,4,5,6,7,1,2,3,4,5,6,7,1,2,3,4,5,6,7,1,2,3,4,5,6,7)
        assertEquals(180L, f.apply(q,d))
        d = byteArrayOf(1,2,3,4,5,6,7,8)
        q = byteArrayOf(1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8)
        assertEquals(195L, f.apply(q,d))
        d = byteArrayOf(1,2,3,4,5,6,7,8,9)
        q = byteArrayOf(1,2,3,4,5,6,7,8,9,1,2,3,4,5,6,7,8,9,1,2,3,4,5,6,7,8,9,1,2,3,4,5,6,7,8,9)
        assertEquals(225L, f.apply(q,d))
    }

    @Test
    fun testInt4BitDotProduct() {
        testInt4BitDotProductImpl { q, d -> VectorUtil.int4BitDotProduct(q, d) }
    }

    private fun testInt4BitDotProductImpl(f: Int4BitDotProduct) {
        val iterations = atLeast(50)
        for (i in 0 until iterations) {
            val size = random().nextInt(5000)
            val d = ByteArray(size)
            val q = ByteArray(size * 4)
            random().nextBytes(d)
            random().nextBytes(q)
            assertEquals(scalarInt4BitDotProduct(q,d).toLong(), f.apply(q,d))
            d.fill(Byte.MAX_VALUE)
            q.fill(Byte.MAX_VALUE)
            assertEquals(scalarInt4BitDotProduct(q,d).toLong(), f.apply(q,d))
            d.fill(Byte.MIN_VALUE)
            q.fill(Byte.MIN_VALUE)
            assertEquals(scalarInt4BitDotProduct(q,d).toLong(), f.apply(q,d))
        }
    }

    private fun xorBitCount(a: ByteArray, b: ByteArray): Int {
        var res = 0
        for (i in a.indices) {
            var x = a[i]
            var y = b[i]
            for (j in 0 until Byte.SIZE_BITS) {
                if (x.toInt() == y.toInt()) break
                if ((x.toInt() and 0x01) != (y.toInt() and 0x01)) res++
                x = ((x.toInt() and 0xFF) shr 1).toByte()
                y = ((y.toInt() and 0xFF) shr 1).toByte()
            }
        }
        return res
    }

    private fun slowFindNextGEQ(buffer: IntArray, length: Int, target: Int, from: Int): Int {
        for (i in from until length) {
            if (buffer[i] >= target) return i
        }
        return length
    }

    private fun scalarInt4BitDotProduct(q: ByteArray, d: ByteArray): Int {
        var res = 0
        for (i in 0 until 4) {
            res += popcount(q, i * d.size, d, d.size) shl i
        }
        return res
    }

    private fun popcount(a: ByteArray, aOffset: Int, b: ByteArray, length: Int): Int {
        var res = 0
        for (j in 0 until length) {
            var value = (a[aOffset + j].toInt() and b[j].toInt()) and 0xFF
            for (k in 0 until Byte.SIZE_BITS) {
                if (value and (1 shl k) != 0) ++res
            }
        }
        return res
    }

    private fun l2(v: FloatArray): Double {
        var l2 = 0.0
        for (x in v) {
            l2 += x * x
        }
        return l2
    }

    private fun l2(v: ByteArray): Double {
        var l2 = 0.0
        for (i in v.indices) {
            l2 += v[i] * v[i]
        }
        return l2
    }

    private fun randomVector(dim: Int = random().nextInt(100) + 1): FloatArray {
        val v = FloatArray(dim)
        val r: Random = random()
        for (i in 0 until dim) {
            v[i] = r.nextFloat()
        }
        return v
    }

    private fun randomVectorBytes(dim: Int = TestUtil.nextInt(random(), 1, 100)): ByteArray {
        val bytes = ByteArray(dim)
        random().nextBytes(bytes)
        for (i in bytes.indices) {
            if (bytes[i] == (-128).toByte()) bytes[i] = (-127).toByte()
        }
        return bytes
    }

    private fun negative(v: FloatArray): FloatArray {
        val u = FloatArray(v.size)
        for (i in v.indices) {
            u[i] = -v[i]
        }
        return u
    }

    private fun negative(v: ByteArray): ByteArray {
        val u = ByteArray(v.size)
        for (i in v.indices) {
            u[i] = (-v[i]).toByte()
        }
        return u
    }
}

