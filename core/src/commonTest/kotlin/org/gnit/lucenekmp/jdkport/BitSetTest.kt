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
        assertTrue(bitSet.get(9))
    }
}
