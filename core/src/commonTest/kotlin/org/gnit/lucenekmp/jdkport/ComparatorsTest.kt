package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class ComparatorsTest {

    @Test
    fun testReverseOrder() {
        val comparator = reverseOrder<Int>()
        assertEquals(1, comparator.compare(2, 1))
        assertEquals(-1, comparator.compare(1, 2))
        assertEquals(0, comparator.compare(2, 2))
    }
}
