package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
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
        var value = -100000L
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
}
