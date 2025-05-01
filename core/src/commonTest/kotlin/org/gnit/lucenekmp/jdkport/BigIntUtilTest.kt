package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals
import space.kscience.kmath.operations.toBigInt

class BigIntUtilTest {

    @Test
    fun testValueOf() {
        val value = 123456789L
        val bigInt = BigInt.valueOf(value)
        assertEquals(value.toBigInt(), bigInt)
    }

    @Test
    fun testToByteArray() {
        val bigInt = 123456789.toBigInt()
        val byteArray = bigInt.toByteArray()
        val expectedByteArray = byteArrayOf(7, 91, -51, 21)
        assertContentEquals(expectedByteArray, byteArray)
    }

    @Test
    fun testFromByteArray() {
        val byteArray = byteArrayOf(7, 91, -51, 21)
        val bigInt = BigInt.fromByteArray(byteArray)
        val expectedBigInt = 123456789.toBigInt()
        assertEquals(expectedBigInt, bigInt)
    }

    @Test
    fun testToByteArrayNegative() {
        val bigInt = (-123456789).toBigInt()
        val byteArray = bigInt.toByteArray()
        val expectedByteArray = byteArrayOf(-8, -92, 52, -21)
        assertContentEquals(expectedByteArray, byteArray)
    }

    @Test
    fun testFromByteArrayNegative() {
        val byteArray = byteArrayOf(-8, -92, 52, -21)
        val bigInt = BigInt.fromByteArray(byteArray)
        val expectedBigInt = (-123456789).toBigInt()
        assertEquals(expectedBigInt, bigInt)
    }

    @Test
    fun testToByteArrayZero() {
        val bigInt = BigInt.ZERO
        val byteArray = bigInt.toByteArray()
        val expectedByteArray = byteArrayOf(0)
        assertContentEquals(expectedByteArray, byteArray)
    }

    @Test
    fun testFromByteArrayZero() {
        val byteArray = byteArrayOf(0)
        val bigInt = BigInt.fromByteArray(byteArray)
        val expectedBigInt = BigInt.ZERO
        assertEquals(expectedBigInt, bigInt)
    }
}
