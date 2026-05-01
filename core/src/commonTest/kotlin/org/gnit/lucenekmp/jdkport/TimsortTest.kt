package org.gnit.lucenekmp.jdkport

import kotlin.test.*

class TimsortTest {

        @Test
    fun testSortIntArray() {
        val arr = arrayOf(5, 3, 1, 4, 2)
        Timsort.sort(arr) { a, b -> a - b }
        assertContentEquals(arrayOf(1, 2, 3, 4, 5), arr)
    }

    @Test
    fun testSortStringArray() {
        val arr = arrayOf("b", "a", "c")
        Timsort.sort(arr) { a, b -> a.compareTo(b) }
        assertContentEquals(arrayOf("a", "b", "c"), arr)
    }
}
