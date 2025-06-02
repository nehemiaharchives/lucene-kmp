package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BitSetTest {

    @Test
    fun testToByteArray() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(8)

        // Bit 0 and Bit 8 are set, expecting: [1, 1]
        val byteArray = bitSet.toByteArray()!!

        assertEquals(2, byteArray.size)
        assertEquals(1, byteArray[0])
        assertEquals(1, byteArray[1])
    }

    @Test
    fun testToLongArray() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(64)
        val longArray = bitSet.toLongArray()
        assertEquals(2, longArray.size)
        assertEquals(1L, longArray[0])
        assertEquals(1L, longArray[1])
    }

    @Test
    fun testFlip() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.flip(0)
        assertFalse(bitSet.get(0))
        bitSet.flip(0)
        assertTrue(bitSet.get(0))
    }

    @Test
    fun testSet() {
        val bitSet = BitSet()
        bitSet.set(0)
        assertTrue(bitSet.get(0))
        bitSet.set(0, false)
        assertFalse(bitSet.get(0))
    }

    @Test
    fun testClear() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.clear(0)
        assertFalse(bitSet.get(0))
    }

    @Test
    fun testGet() {
        val bitSet = BitSet()
        bitSet.set(0)
        assertTrue(bitSet.get(0))
        assertFalse(bitSet.get(1))
    }

    @Test
    fun testNextSetBit() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(2)
        assertEquals(0, bitSet.nextSetBit(0))
        assertEquals(2, bitSet.nextSetBit(1))
    }

    @Test
    fun testNextClearBit() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(2)
        assertEquals(1, bitSet.nextClearBit(0))
        assertEquals(3, bitSet.nextClearBit(2))
    }

    @Test
    fun testPreviousSetBit() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(2)
        assertEquals(2, bitSet.previousSetBit(2))
        assertEquals(0, bitSet.previousSetBit(1))
    }

    @Test
    fun testPreviousClearBit() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(2)
        assertEquals(1, bitSet.previousClearBit(2))
        assertEquals(-1, bitSet.previousClearBit(0))
    }

    @Test
    fun testLength() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(2)
        assertEquals(3, bitSet.length())
    }

    @Test
    fun testIsEmpty() {
        val bitSet = BitSet()
        assertTrue(bitSet.isEmpty)
        bitSet.set(0)
        assertFalse(bitSet.isEmpty)
    }

    @Test
    fun testIntersects() {
        val bitSet1 = BitSet()
        bitSet1.set(0)
        val bitSet2 = BitSet()
        bitSet2.set(1)
        assertFalse(bitSet1.intersects(bitSet2))
        bitSet2.set(0)
        assertTrue(bitSet1.intersects(bitSet2))
    }

    @Test
    fun testCardinality() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(2)
        assertEquals(2, bitSet.cardinality())
    }

    @Test
    fun testAnd() {
        val bitSet1 = BitSet()
        bitSet1.set(0)
        val bitSet2 = BitSet()
        bitSet2.set(0)
        bitSet2.set(1)
        bitSet1.and(bitSet2)
        assertTrue(bitSet1.get(0))
        assertFalse(bitSet1.get(1))
    }

    @Test
    fun testOr() {
        val bitSet1 = BitSet()
        bitSet1.set(0)
        val bitSet2 = BitSet()
        bitSet2.set(1)
        bitSet1.or(bitSet2)
        assertTrue(bitSet1.get(0))
        assertTrue(bitSet1.get(1))
    }

    @Test
    fun testXor() {
        val bitSet1 = BitSet()
        bitSet1.set(0)
        val bitSet2 = BitSet()
        bitSet2.set(0)
        bitSet2.set(1)
        bitSet1.xor(bitSet2)
        assertFalse(bitSet1.get(0))
        assertTrue(bitSet1.get(1))
    }

    @Test
    fun testAndNot() {
        val bitSet1 = BitSet()
        bitSet1.set(0)
        bitSet1.set(1)
        val bitSet2 = BitSet()
        bitSet2.set(1)
        bitSet1.andNot(bitSet2)
        assertTrue(bitSet1.get(0))
        assertFalse(bitSet1.get(1))
    }

    @Test
    fun testHashCode() {
        val bitSet = BitSet()
        bitSet.set(0)
        assertEquals(1235, bitSet.hashCode())
    }

    @Test
    fun testSize() {
        val bitSet = BitSet()
        assertEquals(64, bitSet.size())
    }

    @Test
    fun testEquals() {
        val bitSet1 = BitSet()
        bitSet1.set(0)
        val bitSet2 = BitSet()
        bitSet2.set(0)
        assertTrue(bitSet1.equals(bitSet2))
        bitSet2.set(1)
        assertFalse(bitSet1.equals(bitSet2))
    }

    @Test
    fun testClone() {
        val bitSet1 = BitSet()
        bitSet1.set(0)
        val bitSet2 = bitSet1.clone()
        assertTrue(bitSet1.equals(bitSet2))
        bitSet2.set(1)
        assertFalse(bitSet1.equals(bitSet2))
    }

    @Test
    fun testToString() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(2)
        assertEquals("{0, 2}", bitSet.toString())
    }

    @Test
    fun testValueOfLongArray() {
        val longArray = longArrayOf(1L, 2L)
        val bitSet = BitSet.valueOf(longArray)
        assertTrue(bitSet.get(0))
        assertTrue(bitSet.get(65))
    }

    // ---------------- BitSet.valueOf(LongBuffer) Tests -------------------------------------

    @Test
    fun testValueOfLongBuffer_emptyBuffer() {
        val buffer = LongBuffer.wrap(longArrayOf())
        val bitSet = BitSet.valueOf(buffer)
        assertTrue(bitSet.isEmpty)
        assertEquals(0, bitSet.length())
        assertEquals(0, bitSet.cardinality())
    }

    @Test
    fun testValueOfLongBuffer_withData() {
        // (1L shl 1) = 2 (binary 10) -> bit 1
        // (1L shl 5) = 32 (binary 100000) -> bit 5
        // (1L shl 63) -> bit 63
        // (1L shl 64) effectively 1L -> bit 0 of the next long (bit 64 overall)
        // (1L shl 70) effectively (1L shl 6) -> bit 6 of the next long (bit 70 overall)
        val longs = longArrayOf( (1L shl 1) or (1L shl 5) or (1L shl 63), (1L shl (70-64)) )
        val buffer = LongBuffer.wrap(longs)
        val bitSet = BitSet.valueOf(buffer)

        assertFalse(bitSet.isEmpty)
        assertEquals(71, bitSet.length()) // Highest bit set is 70, so length is 70 + 1
        assertEquals(4, bitSet.cardinality())

        assertFalse(bitSet.get(0))
        assertTrue(bitSet.get(1))
        assertFalse(bitSet.get(2))
        assertTrue(bitSet.get(5))
        assertFalse(bitSet.get(62))
        assertTrue(bitSet.get(63))
        assertFalse(bitSet.get(64)) // this is bit 0 of the second long, which is not set by (1L shl (70-64)) by itself
                                    // The second long is (1L shl 6) = 64. So bit 6 of the second long is set.
        assertTrue(bitSet.get(70)) // bit 6 of second long (64 + 6)
        assertFalse(bitSet.get(71))
    }

    @Test
    fun testValueOfLongBuffer_slicedBuffer() {
        // Data: long0 (bits 0-63), long1 (bits 64-127), long2 (bits 128-191)
        // We will slice to use only long1
        val underlyingArray = longArrayOf(
            (1L shl 0) or (1L shl 63), // value for first long (index 0)
            (1L shl (5-0)) or (1L shl (10-0)),          // value for second long (index 1) -> bits 64+5 and 64+10
            (1L shl 0)                 // value for third long (index 2)
        )
        val fullBuffer: org.gnit.lucenekmp.jdkport.LongBuffer = LongBuffer.wrap(underlyingArray)
        fullBuffer.position = 1 // Start from the second long
        fullBuffer.limit = 2    // End before the third long (exclusive limit)

        val bitSet = BitSet.valueOf(fullBuffer) // Should process only the second long

        assertFalse(bitSet.isEmpty)
        // Relative to the start of the *BitSet*, not the underlying array.
        // The buffer passed starts effectively at "bit 0" for the BitSet.
        // The second long has bits 5 and 10 set (relative to itself).
        // So, bitSet should have bits 5 and 10 set.
        // Max bit is 10, so length is 11.
        assertEquals(11, bitSet.length())
        assertEquals(2, bitSet.cardinality())

        assertFalse(bitSet.get(0))
        assertTrue(bitSet.get(5))  // Corresponds to bit 5 of the *processed slice*
        assertTrue(bitSet.get(10)) // Corresponds to bit 10 of the *processed slice*
        assertFalse(bitSet.get(63)) // Should not see data from the first long
        assertFalse(bitSet.get(64)) // Should not see data from the first long
        assertFalse(bitSet.get(64+5)) // This would be an absolute index if it used the original buffer's full view
        assertFalse(bitSet.get(128))// Should not see data from the third long

        // Verify original buffer position is consumed (or rather, NOT consumed by this implementation due to slice())
        assertEquals(1, fullBuffer.position, "Original buffer's position should NOT be advanced by this valueOf due to slice()")
    }

    // ---------------- BitSet.valueOf(ByteBuffer) Tests ---------------------------------------

    @Test
    fun testValueOfByteBuffer_emptyBuffer() {
        val buffer = ByteBuffer.wrap(byteArrayOf())
        val bitSet = BitSet.valueOf(buffer)
        assertTrue(bitSet.isEmpty)
        assertEquals(0, bitSet.length())
        assertEquals(0, bitSet.cardinality())
        assertEquals(0, buffer.position, "Buffer position should not change")
    }

    @Test
    fun testValueOfByteBuffer_withDataAndPartialLastLong() {
        // 10 bytes: 1 full long (8 bytes) and 2 bytes for the next long.
        // Byte 0: 0x01 (bit 0)
        // Byte 1: 0x02 (bit 9)
        // Byte 7: 0x80 (bit 63)
        // Byte 8: 0x04 (bit 66) (0x04 is 00000100, so bit 2 of this byte, overall bit 64+2=66)
        // Byte 9: 0x08 (bit 75) (0x08 is 00001000, so bit 3 of this byte, overall bit 72+3=75)
        val bytes = byteArrayOf(
            0x01.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x80.toByte(), // First 8 bytes (long 0)
            0x04.toByte(), 0x08.toByte()                               // Next 2 bytes (part of long 1)
        )
        val buffer = ByteBuffer.wrap(bytes)
        val bitSet = BitSet.valueOf(buffer)

        assertFalse(bitSet.isEmpty)
        assertEquals(5, bitSet.cardinality())
        assertEquals(76, bitSet.length()) // Highest bit is 75 (0-indexed), so length is 75+1

        // Check bits from byte 0
        assertTrue(bitSet.get(0)) // 0x01
        assertFalse(bitSet.get(1))
        // Check bits from byte 1
        assertFalse(bitSet.get(8))
        assertTrue(bitSet.get(9)) // 0x02
        assertFalse(bitSet.get(10))
        // Check bits from byte 7
        assertFalse(bitSet.get(62))
        assertTrue(bitSet.get(63)) // 0x80 (bit 7 of byte 7)
        // Check bits from byte 8 (starts new long)
        assertFalse(bitSet.get(64)) // bit 0 of byte 8
        assertFalse(bitSet.get(65)) // bit 1 of byte 8
        assertTrue(bitSet.get(66))  // bit 2 of byte 8 (0x04)
        assertFalse(bitSet.get(67)) // bit 3 of byte 8
        // Check bits from byte 9
        assertFalse(bitSet.get(72)) // bit 0 of byte 9
        assertFalse(bitSet.get(73)) // bit 1 of byte 9
        assertFalse(bitSet.get(74)) // bit 2 of byte 9
        assertTrue(bitSet.get(75))  // bit 3 of byte 9 (0x08)
        assertFalse(bitSet.get(76))

        assertEquals(0, buffer.position, "Buffer position should not change")
    }

    @Test
    fun testValueOfByteBuffer_slicedBuffer() {
        val underlyingArray = byteArrayOf(
            0x01.toByte(), // byte 0 (bit 0)
            0x0F.toByte(), // byte 1 (bits 8-11) - to be sliced out
            0xFF.toByte(), // byte 2 (bits 16-23) - to be sliced out
            0x02.toByte()  // byte 3 (bit 25)
        )
        val fullBuffer = ByteBuffer.wrap(underlyingArray)
        fullBuffer.position(1) // Start from byte 1
        fullBuffer.limit(3)    // Limit to byte 2 (exclusive end is byte 3)
                               // So, the slice is [0x0F, 0xFF]

        val initialPosition = fullBuffer.position
        val bitSet = BitSet.valueOf(fullBuffer)

        assertFalse(bitSet.isEmpty)
        // The BitSet should be created from bytes [0x0F, 0xFF]
        // 0x0F -> bits 0,1,2,3 of the first byte of the BitSet's view.
        // 0xFF -> bits 0,1,2,3,4,5,6,7 of the second byte of the BitSet's view (bits 8-15 of BitSet view).
        assertEquals(4 + 8, bitSet.cardinality())
        assertEquals(16, bitSet.length()) // Highest bit is 15, so length is 16.

        assertTrue(bitSet.get(0))  // from 0x0F
        assertTrue(bitSet.get(1))  // from 0x0F
        assertTrue(bitSet.get(2))  // from 0x0F
        assertTrue(bitSet.get(3))  // from 0x0F
        assertFalse(bitSet.get(4)) // from 0x0F (higher bits are 0)

        assertTrue(bitSet.get(8))  // from 0xFF (bit 0 of second byte)
        assertTrue(bitSet.get(15)) // from 0xFF (bit 7 of second byte)
        assertFalse(bitSet.get(16))

        // Verify original buffer position is NOT changed by this specific implementation
        assertEquals(initialPosition, fullBuffer.position, "Buffer position should not change")
    }

    @Test
    fun testValueOfByteBuffer_trailingZeroBytes() {
        val bytes = byteArrayOf(0x01.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte())
        val buffer = ByteBuffer.wrap(bytes)
        val bitSet = BitSet.valueOf(buffer)

        assertFalse(bitSet.isEmpty)
        // Byte 0: 0x01 (bit 0) -> 1 bit
        // Byte 1: 0x02 (bit 9) -> 1 bit
        // Trailing zeros should not extend length beyond highest set bit.
        assertEquals(2, bitSet.cardinality())
        assertEquals(10, bitSet.length()) // Bit 9 is highest, length is 10.

        assertTrue(bitSet.get(0))
        assertTrue(bitSet.get(9))
        assertFalse(bitSet.get(16)) // from zero bytes
        assertEquals(0, buffer.position, "Buffer position should not change")
    }

    @Test
    fun testValueOfByteBuffer_spanMultipleInternalLongs() {
        // 10 bytes: 0x01, 0, 0, 0, 0, 0, 0, 0, 0x01, 0x01
        // bit 0 is set from first byte.
        // bit 64 is set from 9th byte (0x01).
        // bit 72 is set from 10th byte (0x01).
        val bytes = byteArrayOf(
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // First 8 bytes
            0x01.toByte(), // 9th byte
            0x01.toByte()  // 10th byte
        )
        val buffer = ByteBuffer.wrap(bytes)
        val bitSet = BitSet.valueOf(buffer)

        assertFalse(bitSet.isEmpty)
        assertEquals(3, bitSet.cardinality())
        assertEquals(73, bitSet.length()) // Bit 72 is highest, length is 73.

        assertTrue(bitSet.get(0))
        assertFalse(bitSet.get(1))
        assertFalse(bitSet.get(63))
        assertTrue(bitSet.get(64)) // From 9th byte
        assertFalse(bitSet.get(65))
        assertTrue(bitSet.get(72)) // From 10th byte
        assertFalse(bitSet.get(73))
        assertEquals(0, buffer.position, "Buffer position should not change")
    }

    @Test
    fun testValueOfByteBuffer_variousBitPositionsInBytes() {
        // Byte 0: 10000001b (0x81) -> bits 0 and 7
        // Byte 1: 01000010b (0x42) -> bits 9 (1+8) and 14 (6+8)
        // Byte 2: 00000000b (0x00)
        // Byte 3: 11111111b (0xFF) -> bits 24-31
        val bytes = byteArrayOf(0x81.toByte(), 0x42.toByte(), 0x00.toByte(), 0xFF.toByte())
        val buffer = ByteBuffer.wrap(bytes)
        val bitSet = BitSet.valueOf(buffer)

        assertFalse(bitSet.isEmpty)
        assertEquals(2 + 2 + 8, bitSet.cardinality())
        assertEquals(32, bitSet.length()) // Highest bit is 31, length is 32

        // Byte 0 (0x81)
        assertTrue(bitSet.get(0))
        assertFalse(bitSet.get(1))
        assertFalse(bitSet.get(6))
        assertTrue(bitSet.get(7))

        // Byte 1 (0x42)
        assertFalse(bitSet.get(8))  // bit 0 of byte 1
        assertTrue(bitSet.get(9))   // bit 1 of byte 1
        assertFalse(bitSet.get(10))
        assertFalse(bitSet.get(11))
        assertFalse(bitSet.get(12))
        assertFalse(bitSet.get(13))
        assertTrue(bitSet.get(14))  // bit 6 of byte 1
        assertFalse(bitSet.get(15)) // bit 7 of byte 1

        // Byte 2 (0x00)
        for (i in 16..23) {
            assertFalse(bitSet.get(i))
        }

        // Byte 3 (0xFF)
        for (i in 24..31) {
            assertTrue(bitSet.get(i))
        }
        assertFalse(bitSet.get(32))
        assertEquals(0, buffer.position, "Buffer position should not change")
    }


    @Test
    fun testValueOfLongBuffer_trailingZeroLongs() {
        val longs = longArrayOf( (1L shl 2) or (1L shl 10), 0L, 0L ) // Data in first long, then two zero longs
        val buffer = LongBuffer.wrap(longs)
        val bitSet = BitSet.valueOf(buffer)

        assertFalse(bitSet.isEmpty)
        // Max bit set is 10, so length should be 11. Trailing zeros should not extend length.
        assertEquals(11, bitSet.length())
        assertEquals(2, bitSet.cardinality())

        assertTrue(bitSet.get(2))
        assertTrue(bitSet.get(10))
        assertFalse(bitSet.get(64)) // Bit from the first zero long
        assertFalse(bitSet.get(128)) // Bit from the second zero long
    }

    @Test
    fun testValueOfLongBuffer_bitsSpanMultipleLongs() {
        // Bit 63 (last bit of first long) and Bit 64 (first bit of second long)
        val longs = longArrayOf( (1L shl 63), (1L shl 0) )
        val buffer = LongBuffer.wrap(longs)
        val bitSet = BitSet.valueOf(buffer)

        assertFalse(bitSet.isEmpty)
        assertEquals(65, bitSet.length()) // Max bit is 64, so length is 65
        assertEquals(2, bitSet.cardinality())

        assertTrue(bitSet.get(63))
        assertTrue(bitSet.get(64))
        assertFalse(bitSet.get(62))
        assertFalse(bitSet.get(65))
    }


    @Test
    fun testRawLongBitSet() {
        val bs = BitSet.valueOf(longArrayOf(257L)) // 257 = 1 + (1 << 8)
        assertTrue(bs.get(0))  // bit 0
        assertTrue(bs.get(8))  // bit 8
        assertFalse(bs.get(1))
    }

    @Test
    fun testValueOfByteArray() {
        val byteArray = byteArrayOf(1, 2)
        val bitSet = BitSet.valueOf(byteArray)
        assertTrue(bitSet.get(0))
        // Original test for valueOf(byteArray) had bit 9 for 0x02 in second byte.
        // Byte 0: 00000001 (1) -> bit 0
        // Byte 1: 00000010 (2) -> bit 1 of this byte, which is bit 8+1 = 9.
        assertTrue(bitSet.get(9))
    }

    // ========================= EDGE CASE TESTS =========================

    @Test
    fun testConstructorWithNegativeSize() {
        try {
            BitSet(-1)
            assert(false) { "Expected exception for negative size" }
        } catch (e: Exception) {
            // Expected
        }
    }

    // --- Enhanced BitSet(nbits) constructor tests ---
    @Test
    fun testConstructorWith_nbits_0() {
        val nbits = 0
        val bitSet = BitSet(nbits)
        assertTrue(bitSet.isEmpty, "BitSet(0) should be empty")
        assertEquals(0, bitSet.length(), "BitSet(0) length should be 0")
        // wordIndex(0-1) + 1 = wordIndex(-1) + 1 = -1 + 1 = 0 words. size = 0 * 64 = 0
        assertEquals(0, bitSet.size(), "BitSet(0) size should be 0")
    }

    @Test
    fun testConstructorWith_nbits_1() {
        val nbits = 1
        val bitSet = BitSet(nbits)
        assertTrue(bitSet.isEmpty, "BitSet(1) should be empty")
        assertEquals(0, bitSet.length(), "BitSet(1) length should be 0")
        // wordIndex(1-1)+1 = wordIndex(0)+1 = 0+1 = 1 word. size = 1 * 64 = 64
        assertEquals(64, bitSet.size(), "BitSet(1) size should be BITS_PER_WORD (64)")
        assertFalse(bitSet.get(0), "BitSet(1) bit 0 should be false")
    }

    @Test
    fun testConstructorWith_nbits_63() {
        val nbits = 63
        val bitSet = BitSet(nbits)
        assertTrue(bitSet.isEmpty, "BitSet(63) should be empty")
        assertEquals(0, bitSet.length(), "BitSet(63) length should be 0")
        // wordIndex(63-1)+1 = wordIndex(62)+1 = 0+1 = 1 word. size = 1 * 64 = 64
        assertEquals(64, bitSet.size(), "BitSet(63) size should be BITS_PER_WORD (64)")
        assertFalse(bitSet.get(0), "BitSet(63) bit 0 should be false")
        assertFalse(bitSet.get(62), "BitSet(63) bit 62 should be false")
    }

    @Test
    fun testConstructorWith_nbits_64() {
        val nbits = 64
        val bitSet = BitSet(nbits)
        assertTrue(bitSet.isEmpty, "BitSet(64) should be empty")
        assertEquals(0, bitSet.length(), "BitSet(64) length should be 0")
        // wordIndex(64-1)+1 = wordIndex(63)+1 = 0+1 = 1 word. size = 1 * 64 = 64
        assertEquals(64, bitSet.size(), "BitSet(64) size should be BITS_PER_WORD (64)")
        assertFalse(bitSet.get(0), "BitSet(64) bit 0 should be false")
        assertFalse(bitSet.get(63), "BitSet(64) bit 63 should be false")
    }

    @Test
    fun testConstructorWith_nbits_65() {
        val nbits = 65
        val bitSet = BitSet(nbits)
        assertTrue(bitSet.isEmpty, "BitSet(65) should be empty")
        assertEquals(0, bitSet.length(), "BitSet(65) length should be 0")
        // wordIndex(65-1)+1 = wordIndex(64)+1 = 1+1 = 2 words. size = 2 * 64 = 128
        assertEquals(128, bitSet.size(), "BitSet(65) size should be 2 * BITS_PER_WORD (128)")
        assertFalse(bitSet.get(0), "BitSet(65) bit 0 should be false")
        assertFalse(bitSet.get(64), "BitSet(65) bit 64 should be false")
    }

    @Test
    fun testConstructorWith_nbits_1000() {
        val nbits = 1000
        val bitSet = BitSet(nbits)
        assertTrue(bitSet.isEmpty, "BitSet(1000) should be empty")
        assertEquals(0, bitSet.length(), "BitSet(1000) length should be 0")
        // wordIndex(1000-1)+1 = wordIndex(999)+1 = (999/64)+1 = 15+1 = 16 words.
        // size = 16 * 64 = 1024
        val expectedWords = ( (nbits - 1) shr 6 ) + 1 // Equivalent to wordIndex(nbits-1) + 1
        assertEquals(expectedWords * 64, bitSet.size(), "BitSet(1000) size should be ceil(1000/64)*64")
        assertFalse(bitSet.get(0), "BitSet(1000) bit 0 should be false")
        assertFalse(bitSet.get(nbits - 1), "BitSet(1000) bit 999 should be false")
        if (nbits < bitSet.size()) { // Check up to allocated capacity if nbits is not a multiple of 64
             assertFalse(bitSet.get(expectedWords * 64 - 1), "BitSet(1000) last alloc bit should be false")
        }
    }
    // --- End of enhanced BitSet(nbits) constructor tests ---

    @Test
    fun testNegativeIndicesThrowExceptions() {
        val bitSet = BitSet()
        
        // Test get with negative index
        try {
            bitSet.get(-1)
            assert(false) { "Expected exception for negative index in get" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
        
        // Test set with negative index
        try {
            bitSet.set(-1)
            assert(false) { "Expected exception for negative index in set" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
        
        // Test clear with negative index
        try {
            bitSet.clear(-1)
            assert(false) { "Expected exception for negative index in clear" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
        
        // Test flip with negative index
        try {
            bitSet.flip(-1)
            assert(false) { "Expected exception for negative index in flip" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
        
        // Test nextSetBit with negative index
        try {
            bitSet.nextSetBit(-1)
            assert(false) { "Expected exception for negative index in nextSetBit" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
        
        // Test nextClearBit with negative index
        try {
            bitSet.nextClearBit(-1)
            assert(false) { "Expected exception for negative index in nextClearBit" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
        
        // Test previousSetBit with invalid negative index (less than -1)
        try {
            bitSet.previousSetBit(-2)
            assert(false) { "Expected exception for index < -1 in previousSetBit" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
        
        // Test previousClearBit with invalid negative index (less than -1)
        try {
            bitSet.previousClearBit(-2)
            assert(false) { "Expected exception for index < -1 in previousClearBit" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
    }

    @Test
    fun testPreviousBitWithMinusOne() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(5)
        
        // previousSetBit(-1) should return -1
        assertEquals(-1, bitSet.previousSetBit(-1))
        
        // previousClearBit(-1) should return -1
        assertEquals(-1, bitSet.previousClearBit(-1))
    }

    @Test
    fun testRangeOperations() {
        val bitSet = BitSet()
        
        // Test set range
        bitSet.set(5, 10)
        for (i in 5 until 10) {
            assertTrue(bitSet.get(i), "Bit $i should be set")
        }
        assertFalse(bitSet.get(4))
        assertFalse(bitSet.get(10))
        
        // Test clear range
        bitSet.clear(7, 9)
        assertTrue(bitSet.get(5))
        assertTrue(bitSet.get(6))
        assertFalse(bitSet.get(7))
        assertFalse(bitSet.get(8))
        assertTrue(bitSet.get(9))
        
        // Test flip range
        bitSet.flip(5, 12)
        assertFalse(bitSet.get(5)) // was true, now false
        assertFalse(bitSet.get(6)) // was true, now false
        assertTrue(bitSet.get(7))  // was false, now true
        assertTrue(bitSet.get(8))  // was false, now true
        assertFalse(bitSet.get(9)) // was true, now false
        assertTrue(bitSet.get(10)) // was false, now true
        assertTrue(bitSet.get(11)) // was false, now true
        assertFalse(bitSet.get(12)) // outside range
        
        // Test get range
        val subSet = bitSet.get(7, 11)
        assertTrue(subSet.get(0)) // bit 7 in original
        assertTrue(subSet.get(1)) // bit 8 in original
        assertFalse(subSet.get(2)) // bit 9 in original
        assertTrue(subSet.get(3)) // bit 10 in original
        assertEquals(4, subSet.length()) // bits 0-3 in subset
    }

    @Test
    fun testRangeOperationsEdgeCases() {
        val bitSet = BitSet()
        
        // Test empty range (fromIndex == toIndex)
        bitSet.set(10, 10) // Should do nothing
        assertFalse(bitSet.get(10))
        
        bitSet.set(5)
        bitSet.clear(5, 5) // Should do nothing
        assertTrue(bitSet.get(5))
        
        bitSet.flip(5, 5) // Should do nothing
        assertTrue(bitSet.get(5))
        
        // Test invalid ranges
        try {
            bitSet.set(10, 5) // fromIndex > toIndex
            assert(false) { "Expected exception for invalid range" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
        
        try {
            bitSet.clear(-1, 5) // negative fromIndex
            assert(false) { "Expected exception for negative fromIndex" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
        
        try {
            bitSet.flip(5, -1) // negative toIndex
            assert(false) { "Expected exception for negative toIndex" }
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }
    }

    @Test
    fun testWordBoundaryOperations() {
        val bitSet = BitSet()
        
        // Test operations at word boundaries (64-bit boundaries)
        bitSet.set(63) // Last bit of first word
        bitSet.set(64) // First bit of second word
        bitSet.set(127) // Last bit of second word
        bitSet.set(128) // First bit of third word
        
        assertTrue(bitSet.get(63))
        assertTrue(bitSet.get(64))
        assertTrue(bitSet.get(127))
        assertTrue(bitSet.get(128))
        
        assertEquals(63, bitSet.nextSetBit(63))
        assertEquals(64, bitSet.nextSetBit(64))
        assertEquals(127, bitSet.nextSetBit(65))
        assertEquals(128, bitSet.nextSetBit(128))
        
        assertEquals(63, bitSet.previousSetBit(63))
        assertEquals(64, bitSet.previousSetBit(64))
        assertEquals(64, bitSet.previousSetBit(126))
        assertEquals(127, bitSet.previousSetBit(127))
        assertEquals(128, bitSet.previousSetBit(128))
        
        // Test range operations across word boundaries
        bitSet.clear()
        bitSet.set(60, 68) // Spans across word boundary
        for (i in 60 until 68) {
            assertTrue(bitSet.get(i), "Bit $i should be set")
        }
        assertFalse(bitSet.get(59))
        assertFalse(bitSet.get(68))
    }

    @Test
    fun testEmptyBitSetOperations() {
        val empty1 = BitSet()
        val empty2 = BitSet()
        val nonEmpty = BitSet()
        nonEmpty.set(5)
        
        // Operations with empty BitSets
        assertTrue(empty1.isEmpty)
        assertEquals(0, empty1.cardinality())
        assertEquals(0, empty1.length())
        assertEquals(-1, empty1.nextSetBit(0))
        assertEquals(0, empty1.nextClearBit(0))
        assertEquals(-1, empty1.previousSetBit(10))
        assertEquals(10, empty1.previousClearBit(10))
        assertFalse(empty1.intersects(empty2))
        assertFalse(empty1.intersects(nonEmpty))
        
        // Logical operations with empty BitSets
        val result1 = BitSet()
        result1.and(nonEmpty)
        assertTrue(result1.isEmpty)
        
        val result2 = BitSet()
        result2.or(nonEmpty)
        assertEquals(1, result2.cardinality())
        assertTrue(result2.get(5))
        
        val result3 = BitSet()
        result3.xor(nonEmpty)
        assertEquals(1, result3.cardinality())
        assertTrue(result3.get(5))
        
        val result4 = nonEmpty.clone()
        result4.andNot(empty1)
        assertEquals(1, result4.cardinality())
        assertTrue(result4.get(5))
        
        // toString of empty BitSet
        assertEquals("{}", empty1.toString())
    }

    @Test
    fun testSelfOperations() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(5)
        bitSet.set(10)
        
        // Self AND operation
        val originalCardinality = bitSet.cardinality()
        bitSet.and(bitSet)
        assertEquals(originalCardinality, bitSet.cardinality())
        assertTrue(bitSet.get(0))
        assertTrue(bitSet.get(5))
        assertTrue(bitSet.get(10))
        
        // Self OR operation  
        bitSet.or(bitSet)
        assertEquals(originalCardinality, bitSet.cardinality())
        assertTrue(bitSet.get(0))
        assertTrue(bitSet.get(5))
        assertTrue(bitSet.get(10))
        
        // Self XOR operation should clear all bits
        bitSet.xor(bitSet)
        assertEquals(0, bitSet.cardinality())
        assertTrue(bitSet.isEmpty)
        
        // Restore bits for andNot test
        bitSet.set(0)
        bitSet.set(5)
        bitSet.set(10)
        
        // Self ANDNOT operation should clear all bits
        bitSet.andNot(bitSet)
        assertEquals(0, bitSet.cardinality())
        assertTrue(bitSet.isEmpty)
    }

    @Test
    fun testDifferentSizedBitSets() {
        val small = BitSet()
        small.set(0)
        small.set(2)
        
        val large = BitSet()
        large.set(0)
        large.set(100)
        large.set(200)
        
        // AND with different sizes
        val result1 = small.clone()
        result1.and(large)
        assertEquals(1, result1.cardinality())
        assertTrue(result1.get(0))
        assertFalse(result1.get(2))
        
        // OR with different sizes
        val result2 = small.clone()
        result2.or(large)
        assertEquals(4, result2.cardinality())
        assertTrue(result2.get(0))
        assertTrue(result2.get(2))
        assertTrue(result2.get(100))
        assertTrue(result2.get(200))
        
        // XOR with different sizes
        val result3 = small.clone()
        result3.xor(large)
        assertEquals(3, result3.cardinality())
        assertFalse(result3.get(0)) // common bit cancelled out
        assertTrue(result3.get(2))
        assertTrue(result3.get(100))
        assertTrue(result3.get(200))
    }

    @Test
    fun testNoMoreBitsEdgeCases() {
        val bitSet = BitSet()
        bitSet.set(5)
        bitSet.set(10)
        
        // nextSetBit beyond last set bit
        assertEquals(-1, bitSet.nextSetBit(11))
        assertEquals(-1, bitSet.nextSetBit(100))
        
        // nextClearBit should find clear bits
        assertEquals(0, bitSet.nextClearBit(0))
        assertEquals(6, bitSet.nextClearBit(6))
        assertEquals(11, bitSet.nextClearBit(11))
        
        // previousSetBit beyond range
        assertEquals(10, bitSet.previousSetBit(100))
        assertEquals(-1, bitSet.previousSetBit(4))
    }

    @Test
    fun testToArraysWithEmptyBitSet() {
        val empty = BitSet()
        
        val byteArray = empty.toByteArray()
        assertEquals(0, byteArray.size)
        
        val longArray = empty.toLongArray()
        assertEquals(0, longArray.size)
    }

    @Test
    fun testValueOfWithEmptyArrays() {
        // Empty long array
        val emptyLongs = longArrayOf()
        val bitSet1 = BitSet.valueOf(emptyLongs)
        assertTrue(bitSet1.isEmpty)
        
        // Empty byte array
        val emptyBytes = byteArrayOf()
        val bitSet2 = BitSet.valueOf(emptyBytes)
        assertTrue(bitSet2.isEmpty)
        
        // Array with only zeros
        val zerosLong = longArrayOf(0L, 0L, 0L)
        val bitSet3 = BitSet.valueOf(zerosLong)
        assertTrue(bitSet3.isEmpty)
        
        val zerosBytes = byteArrayOf(0, 0, 0, 0)
        val bitSet4 = BitSet.valueOf(zerosBytes)
        assertTrue(bitSet4.isEmpty)
    }

    @Test
    fun testValueOfWithTrailingZeros() {
        // Long array with trailing zeros
        val longsWithZeros = longArrayOf(5L, 0L, 0L)
        val bitSet1 = BitSet.valueOf(longsWithZeros)
        assertTrue(bitSet1.get(0)) // bit 0 from 5L
        assertTrue(bitSet1.get(2)) // bit 2 from 5L
        assertFalse(bitSet1.get(64)) // should not have bits beyond necessary
        
        // Byte array with trailing zeros  
        val bytesWithZeros = byteArrayOf(1, 0, 0, 0)
        val bitSet2 = BitSet.valueOf(bytesWithZeros)
        assertTrue(bitSet2.get(0))
        assertFalse(bitSet2.get(8))
        assertEquals(1, bitSet2.length())
    }

    @Test
    fun testLargeToStringOperation() {
        val bitSet = BitSet()
        
        // Set a few bits spread out
        bitSet.set(0)
        bitSet.set(100)
        bitSet.set(1000)
        
        val str = bitSet.toString()
        assertEquals("{0, 100, 1000}", str)
        
        // Test with consecutive bits
        bitSet.clear()
        bitSet.set(5, 8) // sets bits 5, 6, 7
        val str2 = bitSet.toString()
        assertEquals("{5, 6, 7}", str2)
    }

    @Test
    fun testSetWithBooleanValue() {
        val bitSet = BitSet()
        
        // Test set(index, true)
        bitSet.set(5, true)
        assertTrue(bitSet.get(5))
        
        // Test set(index, false) 
        bitSet.set(5, false)
        assertFalse(bitSet.get(5))
        
        // Test range set with boolean values
        bitSet.set(10, 15, true)
        for (i in 10 until 15) {
            assertTrue(bitSet.get(i))
        }
        
        bitSet.set(12, 14, false)
        assertTrue(bitSet.get(10))
        assertTrue(bitSet.get(11))
        assertFalse(bitSet.get(12))
        assertFalse(bitSet.get(13))
        assertTrue(bitSet.get(14))
    }

    @Test
    fun testClearAllBits() {
        val bitSet = BitSet()
        bitSet.set(0)
        bitSet.set(50)
        bitSet.set(100)
        
        assertFalse(bitSet.isEmpty)
        assertEquals(3, bitSet.cardinality())
        
        bitSet.clear() // Clear all bits
        assertTrue(bitSet.isEmpty)
        assertEquals(0, bitSet.cardinality())
        assertEquals(0, bitSet.length())
        
        // Verify specific bits are cleared
        assertFalse(bitSet.get(0))
        assertFalse(bitSet.get(50))
        assertFalse(bitSet.get(100))
    }

    @Test
    fun testEqualsWithDifferentSizes() {
        val bitSet1 = BitSet()
        val bitSet2 = BitSet(128)
        
        // Both empty, should be equal regardless of initial size
        assertTrue(bitSet1.equals(bitSet2))
        
        bitSet1.set(5)
        bitSet2.set(5)
        assertTrue(bitSet1.equals(bitSet2))
        
        bitSet1.set(100)
        bitSet2.set(100)
        assertTrue(bitSet1.equals(bitSet2))
        
        bitSet1.set(50)
        assertFalse(bitSet1.equals(bitSet2))
        
        // Test equals with non-BitSet object
        assertFalse(bitSet1.equals("not a bitset"))
        assertFalse(bitSet1.equals(null))
    }

    @Test
    fun testHashCodeConsistency() {
        val bitSet1 = BitSet()
        val bitSet2 = BitSet()
        
        // Equal BitSets should have equal hash codes
        assertEquals(bitSet1.hashCode(), bitSet2.hashCode())
        
        bitSet1.set(5)
        bitSet2.set(5)
        assertEquals(bitSet1.hashCode(), bitSet2.hashCode())
        
        bitSet1.set(10)
        // Hash codes should be different now
        assertFalse(bitSet1.hashCode() == bitSet2.hashCode())
        
        bitSet2.set(10)
        assertEquals(bitSet1.hashCode(), bitSet2.hashCode())
    }

    @Test
    fun testCloneIndependence() {
        val original = BitSet()
        original.set(0)
        original.set(5)
        original.set(10)
        
        val clone = original.clone()
        assertTrue(original.equals(clone))
        
        // Modify clone, should not affect original
        clone.set(15)
        assertFalse(original.equals(clone))
        assertFalse(original.get(15))
        assertTrue(clone.get(15))
        
        // Modify original, should not affect clone
        original.clear(5)
        assertFalse(original.get(5))
        assertTrue(clone.get(5))
    }

    @Test
    fun testTrimToSize_largerThanNecessary() {
        val bitSet = BitSet(256) // Initial capacity for 256 bits (4 words)
        val initialSize = bitSet.size()
        assertEquals(256, initialSize) // Should be 4 words * 64 bits/word

        bitSet.set(5)
        bitSet.set(60)
        assertEquals(2, bitSet.cardinality())
        assertEquals(61, bitSet.length()) // Highest bit is 60, length is 61 (needs 1 word)

        bitSet.trimToSize()

        // After trim, wordsInUse should be 1. So size() should be 1*64=64
        assertEquals(64, bitSet.size(), "Size should be reduced to fit used words")
        assertEquals(2, bitSet.cardinality(), "Cardinality should remain the same")
        assertEquals(61, bitSet.length(), "Length should remain the same")
        assertTrue(bitSet.get(5), "Bit 5 should still be set")
        assertTrue(bitSet.get(60), "Bit 60 should still be set")
        assertFalse(bitSet.get(61), "Bit 61 should be false")

        // Further operations
        bitSet.set(70) // This might cause re-expansion
        assertTrue(bitSet.get(70))
        assertEquals(71, bitSet.length())
        assertEquals(3, bitSet.cardinality())
    }

    @Test
    fun testTrimToSize_wordsInUseEqualsWordsSize() {
        val bitSet = BitSet() // Default initial capacity (1 word = 64 bits)
        bitSet.set(63) // Set a high bit in the first word
        assertEquals(64, bitSet.size())
        assertEquals(64, bitSet.length())
        assertEquals(1, bitSet.cardinality())

        val originalSize = bitSet.size()
        bitSet.trimToSize() // Should do nothing as wordsInUse is likely 1, and words.size is 1

        assertEquals(originalSize, bitSet.size(), "Size should not change")
        assertEquals(1, bitSet.cardinality())
        assertTrue(bitSet.get(63))
    }

    @Test
    fun testTrimToSize_emptyBitSet() {
        val bitSet = BitSet()
        assertTrue(bitSet.isEmpty)
        val originalSize = bitSet.size() // Default is 64 (1 word)
        assertEquals(64, originalSize)
        assertEquals(0, bitSet.length())

        bitSet.trimToSize() // wordsInUse is 0, words.size is 1. Should trim to 0 words.
                                // The implementation words = words.copyOf(wordsInUse)
                                // would make words an empty array. size() would be 0.

        assertEquals(0, bitSet.size(), "Size should be 0 for an empty trimmed BitSet")
        assertTrue(bitSet.isEmpty)
        assertEquals(0, bitSet.length())
        assertEquals(0, bitSet.cardinality())

        // Test adding a bit after trimming an empty set
        bitSet.set(0)
        assertTrue(bitSet.get(0))
        assertEquals(1, bitSet.length())
        assertEquals(1, bitSet.cardinality())
        assertEquals(64, bitSet.size(), "Size should expand to 1 word (64 bits) after setting a bit")
    }

    @Test
    fun testTrimToSize_defaultConstructorThenAddBits() {
        val bitSet = BitSet() // Default: 1 word (64 bits)
        assertEquals(64, bitSet.size())
        bitSet.set(5)
        bitSet.set(10)
        assertEquals(11, bitSet.length())
        assertEquals(2, bitSet.cardinality())
        val sizeBeforeTrim = bitSet.size() // Still 64

        bitSet.trimToSize() // wordsInUse is 1, words.size is 1. No change expected.

        assertEquals(sizeBeforeTrim, bitSet.size(), "Size should not change if already minimal for content")
        assertTrue(bitSet.get(5))
        assertTrue(bitSet.get(10))
        assertEquals(11, bitSet.length())
        assertEquals(2, bitSet.cardinality())
    }

    @Test
    fun testTrimToSize_nbitsConstructorThenAddFewerBits() {
        val bitSet = BitSet(192) // 3 words (192 bits)
        assertEquals(192, bitSet.size())

        bitSet.set(0)
        bitSet.set(65) // Uses 2 words
        assertEquals(66, bitSet.length())
        assertEquals(2, bitSet.cardinality())

        bitSet.trimToSize() // wordsInUse is 2, words.size is 3. Should trim to 2 words.

        assertEquals(128, bitSet.size(), "Size should be reduced to 2 words (128 bits)")
        assertTrue(bitSet.get(0))
        assertTrue(bitSet.get(65))
        assertFalse(bitSet.get(1))
        assertFalse(bitSet.get(64))
        assertEquals(66, bitSet.length())
        assertEquals(2, bitSet.cardinality())

        // Set a bit that requires expansion beyond current trimmed capacity
        bitSet.set(130) // Needs 3rd word
        assertTrue(bitSet.get(130))
        assertEquals(131, bitSet.length())
        assertEquals(3, bitSet.cardinality())
        // Size might grow in larger chunks than strictly necessary depending on ensureCapacity logic
        assertTrue(bitSet.size() >= 192, "Size should expand to accommodate new bit")
    }

    @Test
    fun testTrimToSize_operationsAfterTrim() {
        val bitSet = BitSet(128) // 2 words
        bitSet.set(10)
        bitSet.set(70)
        assertEquals(128, bitSet.size())
        assertEquals(71, bitSet.length())
        assertEquals(2, bitSet.cardinality())

        bitSet.trimToSize() // Should trim to 2 words (wordsInUse = 2, words.size = 2 initially because 70 is in word 1)
                                // Actually, bit 70 is in words[1]. wordsInUse will be 2.
                                // If BitSet(128) allocates exactly 2 words, no change in size here.
                                // Let's make it more explicit: BitSet(200) -> 4 words. set bit 70 -> wordsInUse = 2.
        val bs200 = BitSet(200) // 4 words (256 bits)
        bs200.set(10) // word 0
        bs200.set(70) // word 1
        assertEquals(256, bs200.size())
        assertEquals(71, bs200.length())
        assertEquals(2, bs200.cardinality())

        bs200.trimToSize() // wordsInUse = 2. words.size should become 2. Size = 128.
        assertEquals(128, bs200.size(), "Size should be trimmed to 2 words (128 bits)")
        assertTrue(bs200.get(10))
        assertTrue(bs200.get(70))

        // Test get
        assertTrue(bs200.get(10))
        assertFalse(bs200.get(11))
        assertTrue(bs200.get(70))
        assertFalse(bs200.get(127)) // Edge of current allocation
        assertFalse(bs200.get(128)) // Outside current allocation

        // Test set within current trimmed capacity
        bs200.set(100)
        assertTrue(bs200.get(100))
        assertEquals(101, bs200.length())
        assertEquals(3, bs200.cardinality())
        assertEquals(128, bs200.size()) // Size should not change yet

        // Test set that forces expansion
        bs200.set(150) // Needs word index 2 (150 / 64 = 2)
        assertTrue(bs200.get(150))
        assertEquals(151, bs200.length())
        assertEquals(4, bs200.cardinality())
        assertTrue(bs200.size() >= 192, "Size should expand") // New size depends on growth strategy

        // Test clear
        bs200.clear(70)
        assertFalse(bs200.get(70))
        assertEquals(3, bs200.cardinality())
        // Length might change if 70 was the highest bit, but 150 is now.
        assertEquals(151, bs200.length())

        // Test logical operations
        val other = BitSet()
        other.set(10)
        other.set(150)

        val temp = bs200.clone()
        temp.and(other)
        assertTrue(temp.get(10))
        assertTrue(temp.get(150))
        assertFalse(temp.get(100))
        assertEquals(2, temp.cardinality())

        val temp2 = bs200.clone()
        temp2.or(other) // other has 10, 150. bs200 has 10, 100, 150
        assertTrue(temp2.get(10))
        assertTrue(temp2.get(100))
        assertTrue(temp2.get(150))
        assertEquals(3, temp2.cardinality()) // No change as bs200 already contained these

        bs200.clear()
        bs200.set(1)
        bs200.trimToSize() // size should be 64
        assertEquals(64, bs200.size())
        other.clear()
        other.set(65) // size 128
        bs200.or(other)
        assertTrue(bs200.get(1))
        assertTrue(bs200.get(65))
        assertEquals(128, bs200.size()) // or operation expands the smaller set
    }

    // --- Tests for BitSet.get(fromIndex, toIndex) ---

    @Test
    fun testGetRange_toIndexGreaterThanLength() {
        val bs = BitSet()
        bs.set(0)
        bs.set(10) // length is 11
        bs.set(60) // length is 61
        assertEquals(61, bs.length())

        // Range [5, 200), original length is 61. Effective toIndex should be 61.
        val sub = bs.get(5, 200)
        assertEquals(61 - 5, sub.length(), "Length of sub BitSet incorrect") // Bits 5..60 -> 56 bits. Length = 56.
                                                                              // Highest bit in sub is (60-5)=55. So length is 56.
        assertFalse(sub.get(0)) // Original bit 5 was false
        assertTrue(sub.get(10 - 5)) // Original bit 10 was true
        assertTrue(sub.get(60 - 5)) // Original bit 60 was true
        assertFalse(sub.get(61 - 5)) // Original bit 61 did not exist / was false
    }

    @Test
    fun testGetRange_toIndexGreaterThanLength_allBitsWithinRangeSet() {
        val bs = BitSet()
        bs.set(5)
        bs.set(6)
        bs.set(7) // length is 8
        assertEquals(8, bs.length())

        // Range is [5, 20). Effective toIndex is 8.
        // Sub-BitSet should contain bits for original indices 5, 6, 7.
        // These map to indices 0, 1, 2 in the sub-BitSet.
        val sub = bs.get(5, 20)
        assertEquals(3, sub.cardinality())
        assertEquals(3, sub.length()) // Highest bit is 2 (from original bit 7), so length is 3.
        assertTrue(sub.get(0)) // Original bs.get(5)
        assertTrue(sub.get(1)) // Original bs.get(6)
        assertTrue(sub.get(2)) // Original bs.get(7)
        assertFalse(sub.get(3))
    }


    @Test
    fun testGetRange_entireRangeOutsideLength() {
        val bs = BitSet()
        bs.set(0)
        bs.set(10) // length is 11
        assertEquals(11, bs.length())

        val sub = bs.get(15, 20)
        assertTrue(sub.isEmpty, "Resulting BitSet should be empty")
        assertEquals(0, sub.length(), "Length of empty BitSet should be 0")
        assertEquals(0, sub.cardinality(), "Cardinality of empty BitSet should be 0")
    }

    @Test
    fun testGetRange_fromIndexEqualsToIndex() {
        val bs = BitSet()
        bs.set(0)
        bs.set(5)
        bs.set(10)

        val sub = bs.get(5, 5)
        assertTrue(sub.isEmpty, "Resulting BitSet should be empty for fromIndex == toIndex")
        assertEquals(0, sub.length(), "Length should be 0")
    }

    @Test
    fun testGetRange_fromIndexGreaterThanToIndex_throwsException() {
        val bs = BitSet()
        bs.set(0)
        var thrown = false
        try {
            bs.get(5, 0)
        } catch (e: IndexOutOfBoundsException) {
            thrown = true
        }
        assertTrue(thrown, "Expected IndexOutOfBoundsException for fromIndex > toIndex")
    }

    @Test
    fun testGetRange_fromIndexEqualToLength() {
        val bs = BitSet()
        bs.set(0)
        bs.set(10) // length is 11
        assertEquals(11, bs.length())

        val sub = bs.get(11, 15) // fromIndex is equal to length
        assertTrue(sub.isEmpty, "Resulting BitSet should be empty if fromIndex is length")
        assertEquals(0, sub.length())
    }
}
