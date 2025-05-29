package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpliteratorsTest {

    /*@Test
    fun testSpliterator() {
        val array = arrayOf(1, 2, 3, 4, 5)
        val spliterator = Spliterators.ArraySpliterator<Int>(array, Spliterator.ORDERED)

        val list = mutableListOf<Int>()
        while (spliterator.tryAdvance { list.add(it) }) {
        }

        assertEquals(listOf(1, 2, 3, 4, 5), list)
    }*/

    @Test
    fun testSpliteratorUnknownSize() {
        val iterator = listOf(1, 2, 3, 4, 5).iterator()
        val spliterator = Spliterators.IteratorSpliterator<Int>(iterator, Spliterator.ORDERED)

        val list = mutableListOf<Int>()
        while (spliterator.tryAdvance { list.add(it) }) {
        }

        assertEquals(listOf(1, 2, 3, 4, 5), list)
    }

    @Test
    fun testSpliteratorTrySplit() {
        val array = arrayOf(1, 2, 3, 4, 5)
        val spliterator = Spliterators.ArraySpliterator<Int>(array as Array<Int?>, Spliterator.ORDERED)

        val split = spliterator.trySplit()
        assertTrue(split != null)

        val list1 = mutableListOf<Int>()
        while (spliterator.tryAdvance { list1.add(it) }) {
        }

        val list2 = mutableListOf<Int>()
        while (split!!.tryAdvance { list2.add(it) }) {
        }

        assertEquals(listOf(3, 4, 5), list1)
        assertEquals(listOf(1, 2), list2)
    }

    @Test
    fun testSpliteratorUnknownSizeTrySplit() {
        val iterator = listOf(1, 2, 3, 4, 5).iterator()
        val spliterator = Spliterators.IteratorSpliterator<Int>(iterator, Spliterator.ORDERED)

        val split = spliterator.trySplit()
        assertTrue(split != null)

        val list1 = mutableListOf<Int>()
        while (spliterator.tryAdvance { list1.add(it) }) {
        }

        val list2 = mutableListOf<Int>()
        while (split!!.tryAdvance { list2.add(it) }) {
        }

        // At least one of the lists should have elements
        assertTrue(list1.isNotEmpty() || list2.isNotEmpty())

        // The total number of elements should be 5
        assertEquals(5, list1.size + list2.size)
    }

    @Test
    fun testSpliteratorCharacteristics() {
        val array = arrayOf(1, 2, 3, 4, 5)
        val spliterator = Spliterators.ArraySpliterator<Int>(array as Array<Int?>, Spliterator.ORDERED or Spliterator.SIZED)

        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED))
        assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED))
        assertFalse(spliterator.hasCharacteristics(Spliterator.SORTED))
    }

    @Test
    fun testSpliteratorEstimateSize() {
        val array = arrayOf(1, 2, 3, 4, 5)
        val spliterator = Spliterators.ArraySpliterator<Int>(array as Array<Int?>, Spliterator.ORDERED or Spliterator.SIZED)

        assertEquals(5, spliterator.estimateSize())
    }

    @Test
    fun testSpliteratorUnknownSizeEstimateSize() {
        val iterator = listOf(1, 2, 3, 4, 5).iterator()
        val spliterator = Spliterators.IteratorSpliterator<Int>(iterator, Spliterator.ORDERED)

        assertEquals(Long.MAX_VALUE, spliterator.estimateSize())
    }
}
