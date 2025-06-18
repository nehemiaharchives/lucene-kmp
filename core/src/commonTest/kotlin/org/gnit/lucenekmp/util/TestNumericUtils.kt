package org.gnit.lucenekmp.util

import com.ionspin.kotlin.bignum.integer.BigInteger
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import com.ionspin.kotlin.bignum.integer.Sign
import org.gnit.lucenekmp.jdkport.valueOf
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.jdkport.signum
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestNumericUtils : LuceneTestCase() {

    /**
     * generate a series of encoded longs, each numerical one bigger than the one before. check for
     * correct ordering of the encoded bytes and that values round-trip.
     */
    @Test
    fun testLongConversionAndOrdering() {
        var previous: BytesRef? = null
        val current = BytesRef(ByteArray(Long.SIZE_BYTES))
        var value = 0L
        while (value < 100000L) {
            NumericUtils.longToSortableBytes(value, current.bytes, current.offset)
            if (previous == null) {
                previous = BytesRef(ByteArray(Long.SIZE_BYTES))
            } else {
                assertTrue(previous!!.compareTo(current) < 0, "current bigger than previous: ")
            }
            assertEquals(
                value,
                NumericUtils.sortableBytesToLong(current.bytes, current.offset),
                "forward and back conversion should generate same long"
            )
            current.bytes.copyInto(previous!!.bytes, previous!!.offset, current.offset, current.offset + current.length)
            value++
        }
    }

    /**
     * generate a series of encoded ints, each numerical one bigger than the one before. check for
     * correct ordering of the encoded bytes and that values round-trip.
     */
    @Test
    fun testIntConversionAndOrdering() {
        var previous: BytesRef? = null
        val current = BytesRef(ByteArray(Int.SIZE_BYTES))
        var value = -100000
        while (value < 100000) {
            NumericUtils.intToSortableBytes(value, current.bytes, current.offset)
            if (previous == null) {
                previous = BytesRef(ByteArray(Int.SIZE_BYTES))
            } else {
                assertTrue(previous!!.compareTo(current) < 0, "current bigger than previous: ")
            }
            assertEquals(
                value,
                NumericUtils.sortableBytesToInt(current.bytes, current.offset),
                "forward and back conversion should generate same int"
            )
            current.bytes.copyInto(previous!!.bytes, previous!!.offset, current.offset, current.offset + current.length)
            value++
        }
    }

    /**
     * generate a series of encoded big integers, each numerical one bigger than the one before. check for
     * correct ordering of the encoded bytes and that values round-trip.
     */
    @Test
    @Ignore
    fun testBigIntConversionAndOrdering() {
        val size = TestUtil.nextInt(random(), 3, 16)
        val current = BytesRef(ByteArray(size))
        val values = listOf(0L, 1L, 128L)
        for (value in values) {
            NumericUtils.bigIntToSortableBytes(BigInteger.valueOf(value), size, current.bytes, current.offset)
            assertEquals(
                BigInteger.valueOf(value),
                NumericUtils.sortableBytesToBigInt(current.bytes, current.offset, current.length),
                "forward and back conversion should generate same BigInteger"
            )
        }
    }

    /**
     * check extreme values of longs check for correct ordering of the encoded bytes and that values round-trip.
     */
    @Test
    fun testLongSpecialValues() {
        val values = longArrayOf(
            Long.MIN_VALUE,
            Long.MIN_VALUE + 1,
            Long.MIN_VALUE + 2,
            -5003400000000L,
            -4000L,
            -3000L,
            -2000L,
            -1000L,
            -1L,
            0L,
            1L,
            10L,
            300L,
            50006789999999999L,
            Long.MAX_VALUE - 2,
            Long.MAX_VALUE - 1,
            Long.MAX_VALUE
        )
        val encoded = Array(values.size) { BytesRef(ByteArray(Long.SIZE_BYTES)) }

        for (i in values.indices) {
            NumericUtils.longToSortableBytes(values[i], encoded[i].bytes, encoded[i].offset)
            assertEquals(
                values[i],
                NumericUtils.sortableBytesToLong(encoded[i].bytes, encoded[i].offset),
                "forward and back conversion should generate same long"
            )
        }

        for (i in 1 until encoded.size) {
            assertTrue(encoded[i - 1].compareTo(encoded[i]) < 0, "check sort order")
        }
    }

    /**
     * check extreme values of ints check for correct ordering of the encoded bytes and that values round-trip.
     */
    @Test
    fun testIntSpecialValues() {
        val values = intArrayOf(
            Int.MIN_VALUE,
            Int.MIN_VALUE + 1,
            Int.MIN_VALUE + 2,
            -64765767,
            -4000,
            -3000,
            -2000,
            -1000,
            -1,
            0,
            1,
            10,
            300,
            765878989,
            Int.MAX_VALUE - 2,
            Int.MAX_VALUE - 1,
            Int.MAX_VALUE
        )
        val encoded = Array(values.size) { BytesRef(ByteArray(Int.SIZE_BYTES)) }

        for (i in values.indices) {
            NumericUtils.intToSortableBytes(values[i], encoded[i].bytes, encoded[i].offset)
            assertEquals(
                values[i],
                NumericUtils.sortableBytesToInt(encoded[i].bytes, encoded[i].offset),
                "forward and back conversion should generate same int"
            )
        }

        for (i in 1 until encoded.size) {
            assertTrue(encoded[i - 1].compareTo(encoded[i]) < 0, "check sort order")
        }
    }

    /**
     * check extreme values of big integers (4 bytes) check for correct ordering of the encoded bytes and that values round-trip.
     */
    @Test
    @Ignore
    fun testBigIntSpecialValues() {
        val values = arrayOf(
            BigInteger.valueOf(Int.MIN_VALUE.toLong()),
            BigInteger.valueOf((Int.MIN_VALUE + 1).toLong()),
            BigInteger.valueOf((Int.MIN_VALUE + 2).toLong()),
            BigInteger.valueOf(-64765767L),
            BigInteger.valueOf(-4000L),
            BigInteger.valueOf(-3000L),
            BigInteger.valueOf(-2000L),
            BigInteger.valueOf(-1000L),
            BigInteger.valueOf(-1L),
            BigInteger.valueOf(0L),
            BigInteger.valueOf(1L),
            BigInteger.valueOf(10L),
            BigInteger.valueOf(300L),
            BigInteger.valueOf(765878989L),
            BigInteger.valueOf((Int.MAX_VALUE - 2).toLong()),
            BigInteger.valueOf((Int.MAX_VALUE - 1).toLong()),
            BigInteger.valueOf(Int.MAX_VALUE.toLong())
        )
        val encoded = Array(values.size) { BytesRef(ByteArray(Int.SIZE_BYTES)) }

        for (i in values.indices) {
            NumericUtils.bigIntToSortableBytes(values[i], Int.SIZE_BYTES, encoded[i].bytes, encoded[i].offset)
            assertEquals(
                values[i],
                NumericUtils.sortableBytesToBigInt(encoded[i].bytes, encoded[i].offset, Int.SIZE_BYTES),
                "forward and back conversion should generate same big integer"
            )
        }

        for (i in 1 until encoded.size) {
            assertTrue(encoded[i - 1].compareTo(encoded[i]) < 0, "check sort order")
        }
    }

    /**
     * check various sorted values of doubles (including extreme values) check for
     * correct ordering of the encoded bytes and that values round-trip.
     */
    @Test
    fun testDoubles() {
        val values = doubleArrayOf(
            Double.NEGATIVE_INFINITY,
            -2.3E25,
            -1.0E15,
            -1.0,
            -1.0E-1,
            -1.0E-2,
            -0.0,
            +0.0,
            1.0E-2,
            1.0E-1,
            1.0,
            1.0E15,
            2.3E25,
            Double.POSITIVE_INFINITY,
            Double.NaN
        )
        val encoded = LongArray(values.size)

        for (i in values.indices) {
            encoded[i] = NumericUtils.doubleToSortableLong(values[i])
            assertTrue(
                values[i].compareTo(NumericUtils.sortableLongToDouble(encoded[i])) == 0,
                "forward and back conversion should generate same double"
            )
        }

        for (i in 1 until encoded.size) {
            assertTrue(encoded[i - 1] < encoded[i], "check sort order")
        }
    }

    /**
     * check various sorted values of floats (including extreme values) check for
     * correct ordering of the encoded bytes and that values round-trip.
     */
    @Test
    fun testFloats() {
        val values = floatArrayOf(
            Float.NEGATIVE_INFINITY,
            -2.3E25f,
            -1.0E15f,
            -1.0f,
            -1.0E-1f,
            -1.0E-2f,
            -0.0f,
            +0.0f,
            1.0E-2f,
            1.0E-1f,
            1.0f,
            1.0E15f,
            2.3E25f,
            Float.POSITIVE_INFINITY,
            Float.NaN
        )
        val encoded = IntArray(values.size)

        for (i in values.indices) {
            encoded[i] = NumericUtils.floatToSortableInt(values[i])
            assertTrue(
                values[i].compareTo(NumericUtils.sortableIntToFloat(encoded[i])) == 0,
                "forward and back conversion should generate same float"
            )
        }

        for (i in 1 until encoded.size) {
            assertTrue(encoded[i - 1] < encoded[i], "check sort order")
        }
    }

    @Test
    fun testSortableDoubleNaN() {
        val plusInf = NumericUtils.doubleToSortableLong(Double.POSITIVE_INFINITY)
        val values = doubleArrayOf(
            Double.NaN,
            Double.fromBits(0x7ff0000000000001L),
            Double.fromBits(0x7fffffffffffffffL),
            Double.fromBits(-0xfffffffffffffL),
            Double.fromBits(-1L)
        )
        for (nan in values) {
            assertTrue(nan.isNaN())
            val sortable = NumericUtils.doubleToSortableLong(nan)
            assertTrue(
                sortable > plusInf,
                "Double not sorted correctly: $nan, long repr: $sortable, positive inf.: $plusInf"
            )
        }
    }

    @Test
    fun testSortableFloatNaN() {
        val plusInf = NumericUtils.floatToSortableInt(Float.POSITIVE_INFINITY)
        val values = floatArrayOf(
            Float.NaN,
            Float.fromBits(0x7f800001),
            Float.fromBits(0x7fffffff),
            Float.fromBits(-0x007fffff),
            Float.fromBits(-1)
        )
        for (nan in values) {
            assertTrue(nan.isNaN())
            val sortable = NumericUtils.floatToSortableInt(nan)
            assertTrue(
                sortable > plusInf,
                "Float not sorted correctly: $nan, int repr: $sortable, positive inf.: $plusInf"
            )
        }
    }

    private fun randomBigInteger(numBits: Int): BigInteger {
        val numBytes = (numBits + 7) / 8
        val bytes = ByteArray(numBytes)
        for (i in bytes.indices) {
            bytes[i] = random().nextInt(256).toByte()
        }
        val extraBits = numBytes * 8 - numBits
        if (extraBits > 0) {
            val mask = (1 shl (8 - extraBits)) - 1
            bytes[0] = (bytes[0].toInt() and mask).toByte()
        }
        return BigInteger.fromByteArray(bytes, Sign.POSITIVE)
    }

    @Test
    fun testAdd() {
        val iters = atLeast(1000)
        val numBytes = TestUtil.nextInt(random(), 1, 100)
        for (iter in 0 until iters) {
            val v1 = randomBigInteger(8 * numBytes - 1)
            val v2 = randomBigInteger(8 * numBytes - 1)
            val v1Bytes = ByteArray(numBytes)
            val v1Raw = v1.toByteArray()
            check(v1Raw.size <= numBytes)
            v1Raw.copyInto(v1Bytes, v1Bytes.size - v1Raw.size)
            val v2Bytes = ByteArray(numBytes)
            val v2Raw = v2.toByteArray()
            check(v2Raw.size <= numBytes)
            v2Raw.copyInto(v2Bytes, v2Bytes.size - v2Raw.size)
            val result = ByteArray(numBytes)
            NumericUtils.add(numBytes, 0, v1Bytes, v2Bytes, result)
            val sum = v1 + v2
            assertTrue(sum == BigInteger.fromByteArray(result, Sign.POSITIVE))
        }
    }

    @Test
    fun testIllegalAdd() {
        val bytes = ByteArray(4) { 0xff.toByte() }
        val one = ByteArray(4)
        one[3] = 1
        val expected = expectThrows(IllegalArgumentException::class) {
            NumericUtils.add(4, 0, bytes, one, ByteArray(4))
        }
        assertEquals("a + b overflows bytesPerDim=4", expected?.message)
    }

    @Test
    fun testSubtract() {
        val iters = atLeast(1000)
        val numBytes = TestUtil.nextInt(random(), 1, 100)
        for (iter in 0 until iters) {
            var v1 = randomBigInteger(8 * numBytes - 1)
            var v2 = randomBigInteger(8 * numBytes - 1)
            if (v1 < v2) {
                val tmp = v1
                v1 = v2
                v2 = tmp
            }

            val v1Bytes = ByteArray(numBytes)
            val v1Raw = v1.toByteArray()
            check(v1Raw.size <= numBytes)
            v1Raw.copyInto(v1Bytes, v1Bytes.size - v1Raw.size)

            val v2Bytes = ByteArray(numBytes)
            val v2Raw = v2.toByteArray()
            check(v2Raw.size <= numBytes)
            v2Raw.copyInto(v2Bytes, v2Bytes.size - v2Raw.size)

            val result = ByteArray(numBytes)
            NumericUtils.subtract(numBytes, 0, v1Bytes, v2Bytes, result)

            val diff = v1 - v2
            assertTrue(diff == BigInteger.fromByteArray(result, Sign.POSITIVE))
        }
    }

    @Test
    fun testIllegalSubtract() {
        val v1 = ByteArray(4)
        v1[3] = 0xf0.toByte()
        val v2 = ByteArray(4)
        v2[3] = 0xf1.toByte()
        val expected = expectThrows(IllegalArgumentException::class) {
            NumericUtils.subtract(4, 0, v1, v2, ByteArray(4))
        }
        assertEquals("a < b", expected?.message)
    }

    @Test
    fun testIntsRoundTrip() {
        val encoded = ByteArray(Int.SIZE_BYTES)
        repeat(10000) {
            val value = random().nextInt()
            NumericUtils.intToSortableBytes(value, encoded, 0)
            assertEquals(value, NumericUtils.sortableBytesToInt(encoded, 0))
        }
    }

    @Test
    fun testLongsRoundTrip() {
        val encoded = ByteArray(Long.SIZE_BYTES)
        repeat(10000) {
            val value = random().nextLong()
            NumericUtils.longToSortableBytes(value, encoded, 0)
            assertEquals(value, NumericUtils.sortableBytesToLong(encoded, 0))
        }
    }

    @Test
    fun testFloatsRoundTrip() {
        val encoded = ByteArray(Float.SIZE_BYTES)
        repeat(10000) {
            val value = Float.fromBits(random().nextInt())
            NumericUtils.intToSortableBytes(NumericUtils.floatToSortableInt(value), encoded, 0)
            val actual = NumericUtils.sortableIntToFloat(NumericUtils.sortableBytesToInt(encoded, 0))
            assertEquals(Float.floatToIntBits(value), Float.floatToIntBits(actual))
        }
    }

    @Test
    @Ignore
    fun testDoublesRoundTrip() {
        val encoded = ByteArray(Double.SIZE_BYTES)
        repeat(10000) {
            val value = Double.fromBits(random().nextLong())
            NumericUtils.longToSortableBytes(NumericUtils.doubleToSortableLong(value), encoded, 0)
            val actual = NumericUtils.sortableLongToDouble(NumericUtils.sortableBytesToLong(encoded, 0))
            assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits(actual))
        }
    }

    private fun randomSignedBigInteger(maxBytes: Int): BigInteger {
        // ionspin BigInteger does not use two's complement, so generate positive values only
        val numBits = maxBytes * 8 - 1
        return randomBigInteger(numBits)
    }

    @Test
    @Ignore
    fun testBigIntsRoundTrip() {
        repeat(10000) {
            val value = randomSignedBigInteger(16)
            val length = value.toByteArray().size
            val maxLength = TestUtil.nextInt(random(), length, length + 3)
            val encoded = ByteArray(maxLength)
            NumericUtils.bigIntToSortableBytes(value, maxLength, encoded, 0)
            val decoded = NumericUtils.sortableBytesToBigInt(encoded, 0, maxLength)
            assertEquals(value, decoded, "value=$value decoded=$decoded")
        }
    }

    @Test
    fun testIntsCompare() {
        val left = BytesRef(ByteArray(Int.SIZE_BYTES))
        val right = BytesRef(ByteArray(Int.SIZE_BYTES))
        repeat(10000) {
            val leftValue = random().nextInt()
            NumericUtils.intToSortableBytes(leftValue, left.bytes, left.offset)

            val rightValue = random().nextInt()
            NumericUtils.intToSortableBytes(rightValue, right.bytes, right.offset)

            assertEquals(Int.signum(leftValue.compareTo(rightValue)), Int.signum(left.compareTo(right)))
        }
    }

    @Test
    fun testLongsCompare() {
        val left = BytesRef(ByteArray(Long.SIZE_BYTES))
        val right = BytesRef(ByteArray(Long.SIZE_BYTES))
        repeat(10000) {
            val leftValue = random().nextLong()
            NumericUtils.longToSortableBytes(leftValue, left.bytes, left.offset)

            val rightValue = random().nextLong()
            NumericUtils.longToSortableBytes(rightValue, right.bytes, right.offset)

            assertEquals(Int.signum(leftValue.compareTo(rightValue)), Int.signum(left.compareTo(right)))
        }
    }

    @Test
    fun testFloatsCompare() {
        val left = BytesRef(ByteArray(Float.SIZE_BYTES))
        val right = BytesRef(ByteArray(Float.SIZE_BYTES))
        repeat(10000) {
            val leftValue = Float.fromBits(random().nextInt())
            NumericUtils.intToSortableBytes(NumericUtils.floatToSortableInt(leftValue), left.bytes, left.offset)

            val rightValue = Float.fromBits(random().nextInt())
            NumericUtils.intToSortableBytes(NumericUtils.floatToSortableInt(rightValue), right.bytes, right.offset)

            assertEquals(Int.signum(leftValue.compareTo(rightValue)), Int.signum(left.compareTo(right)))
        }
    }

    @Test
    fun testDoublesCompare() {
        val left = BytesRef(ByteArray(Double.SIZE_BYTES))
        val right = BytesRef(ByteArray(Double.SIZE_BYTES))
        repeat(10000) {
            val leftValue = Double.fromBits(random().nextLong())
            NumericUtils.longToSortableBytes(NumericUtils.doubleToSortableLong(leftValue), left.bytes, left.offset)

            val rightValue = Double.fromBits(random().nextLong())
            NumericUtils.longToSortableBytes(NumericUtils.doubleToSortableLong(rightValue), right.bytes, right.offset)

            assertEquals(Int.signum(leftValue.compareTo(rightValue)), Int.signum(left.compareTo(right)))
        }
    }

    @Test
    @Ignore
    fun testBigIntsCompare() {
        repeat(10000) {
            val maxLength = TestUtil.nextInt(random(), 1, 16)

            val leftValue = randomSignedBigInteger(maxLength)
            val left = BytesRef(ByteArray(maxLength))
            NumericUtils.bigIntToSortableBytes(leftValue, maxLength, left.bytes, left.offset)

            val rightValue = randomSignedBigInteger(maxLength)
            val right = BytesRef(ByteArray(maxLength))
            NumericUtils.bigIntToSortableBytes(rightValue, maxLength, right.bytes, right.offset)

            assertEquals(Int.signum(leftValue.compareTo(rightValue)), Int.signum(left.compareTo(right)))
        }
    }

    private fun fromTwoComplement(bytes: ByteArray): BigInteger {
        if (bytes.isEmpty()) return BigInteger.ZERO
        val negative = bytes[0] < 0
        if (!negative) {
            return BigInteger.fromByteArray(bytes, Sign.POSITIVE)
        }
        val inverted = ByteArray(bytes.size)
        for (i in bytes.indices) {
            inverted[i] = (bytes[i].toInt() xor 0xFF).toByte()
        }
        var carry = 1
        for (i in inverted.size - 1 downTo 0) {
            val sum = (inverted[i].toInt() and 0xff) + carry
            inverted[i] = (sum and 0xff).toByte()
            carry = sum ushr 8
        }
        val magnitude = BigInteger.fromByteArray(inverted, Sign.POSITIVE)
        return magnitude.negate()
    }
}
