package org.gnit.lucenekmp.jdkport

import kotlin.test.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class TreeMapTest {
    @Test
    fun testPutAndGet() {
        val map = TreeMap<Int, String>()
        map[1] = "one"
        map[2] = "two"
        assertEquals("one", map[1])
        assertEquals("two", map[2])
        logger.debug { "testPutAndGet passed" }
    }

    @Test
    fun testRemove() {
        val map = TreeMap<Int, String>()
        map[1] = "one"
        map.remove(1)
        assertNull(map[1])
        logger.debug { "testRemove passed" }
    }

    @Test
    fun testOrder() {
        val map = TreeMap<Int, String>()
        map[2] = "two"
        map[1] = "one"
        map[3] = "three"
        assertEquals(listOf(1, 2, 3), map.keys.toList())
        logger.debug { "testOrder passed" }
    }

    @Test
    fun testEdgeCases() {
        val empty = TreeMap<Int, String>()
        assertTrue(empty.isEmpty())
        assertEquals(0, empty.size)
        assertNull(empty[42])
        assertFalse(empty.containsKey(1))
        assertFalse(empty.containsValue("foo"))
        // Null values are supported, but not null keys (with natural ordering)
        val map = TreeMap<Int, String?>()
        map[1] = null
        assertTrue(map.containsKey(1))
        assertNull(map[1])
        // Duplicate keys: last value wins
        map[1] = "a"
        map[1] = "b"
        assertEquals("b", map[1])
    }

    @Test
    fun testKeyReplacement() {
        val map = TreeMap<Int, String>()
        map[1] = "one"
        val prev = map.put(1, "uno")
        assertEquals("one", prev)
        assertEquals("uno", map[1])
    }

    @Test
    fun testIterationOrder() {
        val map = TreeMap<Int, String>()
        map[3] = "three"
        map[1] = "one"
        map[2] = "two"
        assertEquals(listOf(1, 2, 3), map.keys.toList())
        assertEquals(listOf("one", "two", "three"), map.values.toList())
        assertEquals(listOf(1 to "one", 2 to "two", 3 to "three"), map.entries.map { it.key to it.value })
    }

    @Test
    fun testNavigationalMethods() {
        val map = TreeMap<Int, String>()
        map[10] = "ten"
        map[20] = "twenty"
        map[30] = "thirty"
        assertEquals(10, map.firstKey())
        assertEquals(30, map.lastKey())
        assertEquals(10, map.ceilingKey(5))
        assertEquals(10, map.floorKey(10))
        assertEquals(20, map.higherKey(10))
        assertEquals(10, map.lowerKey(20))
        assertNull(map.ceilingKey(31))
        assertNull(map.floorKey(5))
    }

    @Test
    fun testSubmapViews() {
        val map = TreeMap<Int, String>()
        for (i in 1..5) map[i] = "v$i"
        val head = map.headMap(3)
        assertEquals(setOf(1, 2), head.keys)
        val tail = map.tailMap(3)
        assertEquals(setOf(3, 4, 5), tail.keys)
        val sub = map.subMap(2, 5)
        assertEquals(setOf(2, 3, 4), sub.keys)
    }

    @Test
    fun testFailFastIterator() {
        val map = TreeMap<Int, String>()
        for (i in 1..3) map[i] = "v$i"
        val it = map.entries.iterator()
        assertTrue(it.hasNext())
        map[4] = "v4"
        assertFailsWith<ConcurrentModificationException> { it.next() }
    }

    @Test
    fun testComparatorBasedOrdering() {
        val desc = TreeMap<Int, String>(compareByDescending { it })
        desc[1] = "a"
        desc[3] = "c"
        desc[2] = "b"
        assertEquals(listOf(3, 2, 1), desc.keys.toList())
    }

    @Test
    fun testConcurrentModificationExceptions() {
        val map = TreeMap<Int, String>()
        map[1] = "a"
        val it = map.keys.iterator()
        map[2] = "b"
        assertFailsWith<ConcurrentModificationException> { it.next() }
    }

    @Test
    fun testBulkOperations() {
        val map = TreeMap<Int, String>()
        map.putAll(mapOf(1 to "a", 2 to "b"))
        assertEquals(2, map.size)
        map.clear()
        assertTrue(map.isEmpty())
    }

    @Test
    fun testValueReplacementAndConditionalRemoval() {
        val map = TreeMap<Int, String>()
        map[1] = "a"
        map[2] = "b"
        // replace
        assertEquals("a", map.replace(1, "z"))
        assertEquals("z", map[1])
        // replace with expected value
        assertTrue(map.replace(2, "b", "y"))
        assertEquals("y", map[2])
        assertFalse(map.replace(2, "b", "x"))
        // remove(key, value)
        assertFalse(map.remove(2, "b"))
        assertTrue(map.remove(2, "y"))
        assertNull(map[2])
    }
}
