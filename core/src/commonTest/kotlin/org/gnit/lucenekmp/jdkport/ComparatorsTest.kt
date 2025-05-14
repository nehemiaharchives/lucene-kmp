package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class ComparatorsTest {

    @Test
    fun testReverseOrder() {
        val comparator = reverseOrder<Int>()
        // In reverse order, 2 should be less than 1
        assertEquals(-1, comparator.compare(2, 1))
        // In reverse order, 1 should be greater than 2
        assertEquals(1, comparator.compare(1, 2))
        // Equal values should still be equal
        assertEquals(0, comparator.compare(2, 2))
    }
}
