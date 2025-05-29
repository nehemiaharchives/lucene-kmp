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

    @Test
    fun testConstructorWithZeroSize() {
        val bitSet = BitSet(0)
        assertTrue(bitSet.isEmpty)
        assertEquals(0, bitSet.length())
    }

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
}
