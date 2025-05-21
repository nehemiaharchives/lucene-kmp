package org.gnit.lucenekmp.jdkport

import kotlin.test.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class TreeSetTest {
    @Test
    fun testAddAndContains() {
        val set = TreeSet<Int>()
        set.add(1)
        set.add(2)
        assertTrue(set.contains(1))
        assertTrue(set.contains(2))
        assertFalse(set.contains(3))
        logger.debug { "testAddAndContains passed" }
    }

    @Test
    fun testRemove() {
        val set = TreeSet<Int>()
        set.add(1)
        set.remove(1)
        assertFalse(set.contains(1))
        logger.debug { "testRemove passed" }
    }

    @Test
    fun testOrder() {
        val set = TreeSet<Int>()
        set.add(2)
        set.add(1)
        set.add(3)
        assertEquals(listOf(1, 2, 3), set.toList())
        logger.debug { "testOrder passed" }
    }

    @Test
    fun testDescendingIterator() {
        val set = TreeSet<Int>()
        set.addAll(listOf(1, 2, 3))
        val desc = set.descendingIterator().asSequence().toList()
        assertEquals(listOf(3, 2, 1), desc)
    }

    @Test
    fun testDescendingSet() {
        val set = TreeSet<Int>()
        set.addAll(listOf(1, 2, 3))
        val descSet = set.descendingSet()
        assertEquals(listOf(3, 2, 1), descSet.toList())
    }

    @Test
    fun testIsEmptyAndClear() {
        val set = TreeSet<Int>()
        assertTrue(set.isEmpty())
        set.add(1)
        assertFalse(set.isEmpty())
        set.clear()
        assertTrue(set.isEmpty())
    }

    @Test
    fun testAddAll() {
        val set = TreeSet<Int>()
        val changed = set.addAll(listOf(1, 2, 3))
        assertTrue(changed)
        assertEquals(3, set.size)
        val changed2 = set.addAll(listOf(2, 3))
        assertFalse(changed2)
    }

    @Test
    fun testSubSetHeadSetTailSet() {
        val set = TreeSet<Int>()
        set.addAll(listOf(1, 2, 3, 4, 5))
        val sub = set.subSet(2, 4)
        assertEquals(listOf(2, 3), sub.toList())
        val subIncl = set.subSet(2, true, 4, true)
        assertEquals(listOf(2, 3, 4), subIncl.toList())
        val head = set.headSet(3)
        assertEquals(listOf(1, 2), head.toList())
        val headIncl = set.headSet(3, true)
        assertEquals(listOf(1, 2, 3), headIncl.toList())
        val tail = set.tailSet(3)
        assertEquals(listOf(3, 4, 5), tail.toList())
        val tailIncl = set.tailSet(3, false)
        assertEquals(listOf(4, 5), tailIncl.toList())
    }

    @Test
    fun testComparator() {
        val cmp = Comparator<Int> { a, b -> b - a }
        val set = TreeSet(cmp)
        set.addAll(listOf(1, 2, 3))
        assertEquals(cmp, set.comparator())
    }

    @Test
    fun testFirstLast() {
        val set = TreeSet<Int>()
        set.addAll(listOf(2, 3, 1))
        assertEquals(1, set.first())
        assertEquals(3, set.last())
    }

    @Test
    fun testLowerFloorCeilingHigher() {
        val set = TreeSet<Int>()
        set.addAll(listOf(1, 3, 5))
        assertEquals(1, set.lower(3))
        assertEquals(3, set.floor(3))
        assertEquals(3, set.ceiling(3))
        assertEquals(5, set.higher(3))
        assertNull(set.lower(1))
        assertNull(set.floor(0))
        assertNull(set.ceiling(6))
        assertNull(set.higher(5))
    }

    @Test
    fun testPollFirstPollLast() {
        val set = TreeSet<Int>()
        set.addAll(listOf(2, 1, 3))
        assertEquals(1, set.pollFirst())
        assertEquals(3, set.pollLast())
        assertEquals(listOf(2), set.toList())
        set.clear()
        assertNull(set.pollFirst())
        assertNull(set.pollLast())
    }

    @Test
    fun testAddFirstAddLastThrows() {
        val set = TreeSet<Int>()
        assertFailsWith<UnsupportedOperationException> { set.addFirst(1) }
        assertFailsWith<UnsupportedOperationException> { set.addLast(2) }
    }
}
