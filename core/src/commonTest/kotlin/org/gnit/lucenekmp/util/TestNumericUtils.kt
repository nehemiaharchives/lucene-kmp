package org.gnit.lucenekmp.util

import com.ionspin.kotlin.bignum.integer.BigInteger
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.valueOf
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
}
