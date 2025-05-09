package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionsTest {

    @Test
    fun testSwap() {
        val list = mutableListOf(1, 2, 3, 4)
        Collections.swap(list, 1, 3)
        assertEquals(listOf(1, 4, 3, 2), list)

        assertFailsWith<IndexOutOfBoundsException> {
            Collections.swap(list, -1, 2)
        }

        assertFailsWith<IndexOutOfBoundsException> {
            Collections.swap(list, 1, 4)
        }
    }

    @Test
    fun testReverseOrder() {
        val comparator = Collections.reverseOrder<Int>(null)
        assertEquals(1, comparator.compare(2, 1))
        assertEquals(-1, comparator.compare(1, 2))
        assertEquals(0, comparator.compare(2, 2))
    }

    @Test
    fun testReverse() {
        val list = mutableListOf(1, 2, 3, 4)
        Collections.reverse(list)
        assertEquals(listOf(4, 3, 2, 1), list)
    }
}
