package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.jdkport.Arrays
import kotlin.test.Test
import kotlin.test.assertEquals

class TestVectorUtilSupport : BaseVectorizationTestCase() {

    @Test
    fun testFloatVectors() {
        for (size in VECTOR_SIZES) {
            val a = FloatArray(size)
            val b = FloatArray(size)
            for (i in 0..<size) {
                a[i] = random().nextFloat()
                b[i] = random().nextFloat()
            }
            assertFloatReturningProviders { p -> p.dotProduct(a, b).toDouble() }
            assertFloatReturningProviders { p -> p.squareDistance(a, b).toDouble() }
            assertFloatReturningProviders { p -> p.cosine(a, b).toDouble() }
        }
    }

    @Test
    fun testBinaryVectors() {
        for (size in VECTOR_SIZES) {
            val a = ByteArray(size)
            val b = ByteArray(size)
            random().nextBytes(a)
            random().nextBytes(b)
            assertIntReturningProviders { p -> p.dotProduct(a, b) }
            assertIntReturningProviders { p -> p.squareDistance(a, b) }
            assertFloatReturningProviders { p -> p.cosine(a, b).toDouble() }
        }
    }

    @Test
    fun testBinaryVectorsBoundaries() {
        for (size in VECTOR_SIZES) {
            val a = ByteArray(size)
            val b = ByteArray(size)

            Arrays.fill(a, Byte.MIN_VALUE)
            Arrays.fill(b, Byte.MIN_VALUE)
            assertIntReturningProviders { p -> p.dotProduct(a, b) }
            assertIntReturningProviders { p -> p.squareDistance(a, b) }
            assertFloatReturningProviders { p -> p.cosine(a, b).toDouble() }

            Arrays.fill(a, Byte.MAX_VALUE)
            Arrays.fill(b, Byte.MAX_VALUE)
            assertIntReturningProviders { p -> p.dotProduct(a, b) }
            assertIntReturningProviders { p -> p.squareDistance(a, b) }
            assertFloatReturningProviders { p -> p.cosine(a, b).toDouble() }

            Arrays.fill(a, Byte.MIN_VALUE)
            Arrays.fill(b, Byte.MAX_VALUE)
            assertIntReturningProviders { p -> p.dotProduct(a, b) }
            assertIntReturningProviders { p -> p.squareDistance(a, b) }
            assertFloatReturningProviders { p -> p.cosine(a, b).toDouble() }

            Arrays.fill(a, Byte.MAX_VALUE)
            Arrays.fill(b, Byte.MIN_VALUE)
            assertIntReturningProviders { p -> p.dotProduct(a, b) }
            assertIntReturningProviders { p -> p.squareDistance(a, b) }
            assertFloatReturningProviders { p -> p.cosine(a, b).toDouble() }
        }
    }

    @Test
    fun testInt4DotProduct() {
        for (size in VECTOR_SIZES) {
            if (size % 2 != 0) {
                continue
            }
            val a = ByteArray(size)
            val b = ByteArray(size)
            for (i in 0..<size) {
                a[i] = random().nextInt(16).toByte()
                b[i] = random().nextInt(16).toByte()
            }

            assertIntReturningProviders { p -> p.int4DotProduct(a, false, pack(b), true) }
            assertIntReturningProviders { p -> p.int4DotProduct(pack(a), true, b, false) }
            assertEquals(
                LUCENE_PROVIDER.vectorUtilSupport.dotProduct(a, b),
                PANAMA_PROVIDER.vectorUtilSupport.int4DotProduct(a, false, pack(b), true)
            )
        }
    }

    @Test
    fun testInt4DotProductBoundaries() {
        for (size in VECTOR_SIZES) {
            if (size % 2 != 0) {
                continue
            }
            val MAX_VALUE: Byte = 15
            val a = ByteArray(size)
            val b = ByteArray(size)

            Arrays.fill(a, MAX_VALUE)
            Arrays.fill(b, MAX_VALUE)
            assertIntReturningProviders { p -> p.int4DotProduct(a, false, pack(b), true) }
            assertIntReturningProviders { p -> p.int4DotProduct(pack(a), true, b, false) }
            assertEquals(
                LUCENE_PROVIDER.vectorUtilSupport.dotProduct(a, b),
                PANAMA_PROVIDER.vectorUtilSupport.int4DotProduct(a, false, pack(b), true)
            )

            val MIN_VALUE: Byte = 0
            Arrays.fill(a, MIN_VALUE)
            Arrays.fill(b, MIN_VALUE)
            assertIntReturningProviders { p -> p.int4DotProduct(a, false, pack(b), true) }
            assertIntReturningProviders { p -> p.int4DotProduct(pack(a), true, b, false) }
            assertEquals(
                LUCENE_PROVIDER.vectorUtilSupport.dotProduct(a, b),
                PANAMA_PROVIDER.vectorUtilSupport.int4DotProduct(a, false, pack(b), true)
            )
        }
    }

    @Test
    fun testInt4BitDotProduct() {
        for (size in VECTOR_SIZES) {
            val binaryQuantized = ByteArray(size)
            val int4Quantized = ByteArray(size * 4)
            random().nextBytes(binaryQuantized)
            random().nextBytes(int4Quantized)
            assertLongReturningProviders { p -> p.int4BitDotProduct(int4Quantized, binaryQuantized) }
        }
    }

    @Test
    fun testInt4BitDotProductBoundaries() {
        for (size in VECTOR_SIZES) {
            val binaryQuantized = ByteArray(size)
            val int4Quantized = ByteArray(size * 4)

            Arrays.fill(binaryQuantized, Byte.MAX_VALUE)
            Arrays.fill(int4Quantized, Byte.MAX_VALUE)
            assertLongReturningProviders { p -> p.int4BitDotProduct(int4Quantized, binaryQuantized) }

            Arrays.fill(binaryQuantized, Byte.MIN_VALUE)
            Arrays.fill(int4Quantized, Byte.MIN_VALUE)
            assertLongReturningProviders { p -> p.int4BitDotProduct(int4Quantized, binaryQuantized) }
        }
    }

    private fun assertFloatReturningProviders(func: (VectorUtilSupport) -> Double) {
        assertEquals(
            func(LUCENE_PROVIDER.vectorUtilSupport),
            func(PANAMA_PROVIDER.vectorUtilSupport),
            DELTA
        )
    }

    private fun assertIntReturningProviders(func: (VectorUtilSupport) -> Int) {
        assertEquals(
            func(LUCENE_PROVIDER.vectorUtilSupport),
            func(PANAMA_PROVIDER.vectorUtilSupport)
        )
    }

    private fun assertLongReturningProviders(func: (VectorUtilSupport) -> Long) {
        assertEquals(
            func(LUCENE_PROVIDER.vectorUtilSupport),
            func(PANAMA_PROVIDER.vectorUtilSupport)
        )
    }

    companion object {
        private const val DELTA = 1e-3

        private val VECTOR_SIZES = intArrayOf(
            1,
            4,
            6,
            8,
            13,
            16,
            25,
            32,
            64,
            100,
            128,
            207,
            256,
            300,
            512,
            702,
            1024,
            1536,
            2046,
            2048,
            4096,
            4098
        )

        fun pack(unpacked: ByteArray): ByteArray {
            val len = (unpacked.size + 1) / 2
            val packed = ByteArray(len)
            for (i in 0..<len) {
                packed[i] = ((unpacked[i].toInt() shl 4) or unpacked[packed.size + i].toInt()).toByte()
            }
            return packed
        }
    }
}
