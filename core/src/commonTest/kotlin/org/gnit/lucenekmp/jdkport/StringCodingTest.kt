package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class StringCodingTest {

    @Test
    fun testCountPositives_allPositive() {
        val arr = byteArrayOf(1, 2, 3, 127, 0)
        assertEquals(5, StringCoding.countPositives(arr, 0, arr.size))
    }

    @Test
    fun testCountPositives_negativeAtStart() {
        val arr = byteArrayOf(-1, 2, 3)
        assertEquals(0, StringCoding.countPositives(arr, 0, arr.size))
    }

    @Test
    fun testCountPositives_negativeInMiddle() {
        val arr = byteArrayOf(1, 2, -3, 4)
        assertEquals(2, StringCoding.countPositives(arr, 0, arr.size))
    }

    @Test
    fun testCountPositives_negativeAtEnd() {
        val arr = byteArrayOf(1, 2, 3, -1)
        assertEquals(3, StringCoding.countPositives(arr, 0, arr.size))
    }

    @Test
    fun testCountPositives_emptyArray() {
        val arr = byteArrayOf()
        assertEquals(0, StringCoding.countPositives(arr, 0, arr.size))
    }

    @Test
    fun testCountPositives_partialRange() {
        val arr = byteArrayOf(1, 2, -1, 4, 5)
        assertEquals(2, StringCoding.countPositives(arr, 0, 3))
        assertEquals(1, StringCoding.countPositives(arr, 1, 3))
    }
}
