package org.gnit.lucenekmp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.test.Ignore
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.jdkport.floatToRawIntBits
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.Arrays

class TestSmallFloat : LuceneTestCase() {

    private fun orig_byteToFloat(b: Byte): Float {
        if (b.toInt() == 0) return 0.0f
        val mantissa = b.toInt() and 7
        val exponent = (b.toInt() shr 3) and 31
        val bits = ((exponent + (63 - 15)) shl 24) or (mantissa shl 21)
        return Float.intBitsToFloat(bits)
    }

    private fun orig_floatToByte_v13(f: Float): Byte {
        var v = f
        if (v < 0.0f) v = 0.0f
        if (v == 0.0f) return 0
        val bits = Float.floatToIntBits(v)
        var mantissa = (bits and 0xffffff) ushr 21
        var exponent = ((bits ushr 24) and 0x7f) - 63 + 15
        if (exponent > 31) {
            exponent = 31
            mantissa = 7
        }
        if (exponent < 0) {
            exponent = 0
            mantissa = 1
        }
        return ((exponent shl 3) or mantissa).toByte()
    }

    private fun orig_floatToByte(f: Float): Byte {
        var v = f
        if (v < 0.0f) v = 0.0f
        if (v == 0.0f) return 0
        val bits = Float.floatToIntBits(v)
        var mantissa = (bits and 0xffffff) ushr 21
        var exponent = ((bits ushr 24) and 0x7f) - 63 + 15
        if (exponent > 31) {
            exponent = 31
            mantissa = 7
        }
        if (exponent < 0 || (exponent == 0 && mantissa == 0)) {
            exponent = 0
            mantissa = 1
        }
        return ((exponent shl 3) or mantissa).toByte()
    }

    @Test
    fun testByteToFloat() {
        for (i in 0 until 256) {
            val f1 = orig_byteToFloat(i.toByte())
            val f2 = SmallFloat.byteToFloat(i.toByte(), 3, 15)
            val f3 = SmallFloat.byte315ToFloat(i.toByte())
            assertEquals(f1, f2)
            assertEquals(f2, f3)
        }
    }

    @Test
    fun testFloatToByte() {
        assertEquals(0, (orig_floatToByte_v13(5.8123817E-10f).toInt() and 0xff))
        assertEquals(1, (orig_floatToByte(5.8123817E-10f).toInt() and 0xff))
        assertEquals(1, (SmallFloat.floatToByte315(5.8123817E-10f).toInt() and 0xff))

        assertEquals(0, (SmallFloat.floatToByte315(0f).toInt() and 0xff))
        assertEquals(1, (SmallFloat.floatToByte315(Float.MIN_VALUE).toInt() and 0xff))
        assertEquals(255, SmallFloat.floatToByte315(Float.MAX_VALUE).toInt() and 0xff)
        assertEquals(255, SmallFloat.floatToByte315(Float.POSITIVE_INFINITY).toInt() and 0xff)

        assertEquals(0, (SmallFloat.floatToByte315(-Float.MIN_VALUE).toInt() and 0xff))
        assertEquals(0, (SmallFloat.floatToByte315(-Float.MAX_VALUE).toInt() and 0xff))
        assertEquals(0, (SmallFloat.floatToByte315(Float.NEGATIVE_INFINITY).toInt() and 0xff))

        val num = atLeast(100000)
        for (i in 0 until num) {
            val f = Float.intBitsToFloat(random().nextInt())
            if (f.isNaN()) continue
            val b1 = orig_floatToByte(f)
            val b2 = SmallFloat.floatToByte(f, 3, 15)
            val b3 = SmallFloat.floatToByte315(f)
            assertEquals(b1, b2)
            assertEquals(b2, b3)
        }
    }

    @Test
    fun testInt4() {
        for (i in 0..16) {
            assertEquals(i.toLong(), SmallFloat.int4ToLong(SmallFloat.longToInt4(i.toLong())))
        }
        val maxEncoded = SmallFloat.longToInt4(Long.MAX_VALUE)
        for (i in 1 until maxEncoded) {
            assertTrue(SmallFloat.int4ToLong(i) > SmallFloat.int4ToLong(i - 1))
        }
        val iters = atLeast(1000)
        for (iter in 0 until iters) {
            val limit = 1L shl TestUtil.nextInt(random(), 5, 61)
            val l = random().nextLong(0, limit)
            val numBits = 64 - Long.numberOfLeadingZeros(l)
            var expected = l
            if (numBits > 4) {
                val mask = -1L shl (numBits - 4)
                expected = expected and mask
            }
            val l2 = SmallFloat.int4ToLong(SmallFloat.longToInt4(l))
            assertEquals(expected, l2)
        }
    }

    @Test
    fun testByte4() {
        val decoded = IntArray(256)
        for (b in 0 until 256) {
            decoded[b] = SmallFloat.byte4ToInt(b.toByte())
            assertEquals(b.toByte(), SmallFloat.intToByte4(decoded[b]))
        }
        for (i in 1 until 256) {
            assertTrue(decoded[i] > decoded[i - 1])
        }
        assertEquals(255.toByte(), SmallFloat.intToByte4(Int.MAX_VALUE))
        val iters = atLeast(1000)
        for (iter in 0 until iters) {
            val i = random().nextInt(1 shl TestUtil.nextInt(random(), 5, 30))
            var idx = Arrays.binarySearch(decoded, i)
            if (idx < 0) {
                idx = -2 - idx
            }
            assertTrue(decoded[idx] <= i)
            assertEquals(idx.toByte(), SmallFloat.intToByte4(i))
        }
    }

    @Ignore
    @Test
    fun testAllFloats() {
        var i = Int.MIN_VALUE
        while (true) {
            val f = Float.intBitsToFloat(i)
            if (!f.isNaN()) {
                val b1 = orig_floatToByte(f)
                val b2 = SmallFloat.floatToByte315(f)
                if (b1 != b2 || (b2.toInt() == 0 && f > 0)) {
                    fail("Failed floatToByte315 for float $f source bits=" + i.toUInt().toString(16) + " float raw bits=" + Float.floatToRawIntBits(f).toUInt().toString(16))
                }
            }
            if (i == Int.MAX_VALUE) break
            i++
        }
    }
}

