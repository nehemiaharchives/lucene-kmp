package org.gnit.lucenekmp.jdkport

import kotlin.test.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class TimsortTest {
    @Test
    fun testSortIntArray() {
        val arr = arrayOf(5, 3, 1, 4, 2)
        Timsort.sort(arr) { a, b -> a - b }
        assertContentEquals(arrayOf(1, 2, 3, 4, 5), arr)
        logger.debug { "testSortIntArray passed" }
    }

    @Test
    fun testSortStringArray() {
        val arr = arrayOf("b", "a", "c")
        Timsort.sort(arr) { a, b -> a.compareTo(b) }
        assertContentEquals(arrayOf("a", "b", "c"), arr)
        logger.debug { "testSortStringArray passed" }
    }
}
