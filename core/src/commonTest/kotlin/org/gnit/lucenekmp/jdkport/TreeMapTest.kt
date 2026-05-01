package org.gnit.lucenekmp.jdkport

import kotlin.test.*

class TreeMapTest {
    private fun newMap(vararg entries: Pair<Int, String>): TreeMap<Int, String> {
        val map = TreeMap<Int, String>()
        for ((key, value) in entries) {
            map[key] = value
        }
        return map
    }

    @Test
    fun testPutAndGet() {
        val map = TreeMap<Int, String>()
        map[1] = "one"
        map[2] = "two"
        assertEquals("one", map[1])
        assertEquals("two", map[2])
    }

    @Test
    fun testRemove() {
        val map = TreeMap<Int, String>()
        map[1] = "one"
        map.remove(1)
        assertNull(map[1])
    }

    @Test
    fun testOrder() {
        val map = TreeMap<Int, String>()
        map[2] = "two"
        map[1] = "one"
        map[3] = "three"
        assertEquals(listOf(1, 2, 3), map.keys.toList())
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
    fun testNavigationalEntries() {
        val map = newMap(10 to "ten", 20 to "twenty", 30 to "thirty")
        assertEquals(10, map.firstEntry()?.key)
        assertEquals("ten", map.firstEntry()?.value)
        assertEquals(30, map.lastEntry()?.key)
        assertEquals("thirty", map.lastEntry()?.value)
        assertEquals(20, map.ceilingEntry(15)?.key)
        assertEquals(20, map.higherEntry(10)?.key)
        assertEquals(20, map.floorEntry(20)?.key)
        assertEquals(10, map.lowerEntry(20)?.key)
        assertNull(map.lowerEntry(10))
        assertNull(map.higherEntry(30))
    }

    @Test
    fun testExportedEntriesAreImmutableSnapshots() {
        val map = newMap(1 to "one", 2 to "two")
        val first = map.firstEntry()
        assertEquals(1, first?.key)
        assertEquals("one", first?.value)
        map[1] = "uno"
        assertEquals("one", first?.value)
        map.pollFirstEntry()
        assertEquals("one", first?.value)
    }

    @Test
    fun testFirstAndLastKeyOnEmptyMap() {
        val map = TreeMap<Int, String>()
        assertFailsWith<NoSuchElementException> { map.firstKey() }
        assertFailsWith<NoSuchElementException> { map.lastKey() }
        assertNull(map.firstEntry())
        assertNull(map.lastEntry())
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
    fun testSubmapIsLiveView() {
        val map = newMap(1 to "v1", 2 to "v2", 3 to "v3", 4 to "v4", 5 to "v5")
        val sub = map.subMap(2, 5)
        sub[3] = "changed"
        assertEquals("changed", map[3])
        map[4] = "updated"
        assertEquals("updated", sub[4])
        sub.remove(2)
        assertFalse(map.containsKey(2))
    }

    @Test
    fun testSubmapEntrySetIsLiveView() {
        val map = newMap(1 to "v1", 2 to "v2", 3 to "v3", 4 to "v4", 5 to "v5")
        val sub = map.subMap(2, 5)
        val entries = sub.entries
        assertTrue(entries.any { it.key == 2 && it.value == "v2" })
        map[3] = "changed"
        assertTrue(entries.any { it.key == 3 && it.value == "changed" })
        entries.remove(entries.first { it.key == 4 })
        assertFalse(map.containsKey(4))
    }

    @Test
    fun testSubmapRangeChecks() {
        val map = newMap(1 to "v1", 2 to "v2", 3 to "v3", 4 to "v4", 5 to "v5")
        val sub = map.subMap(2, 5)
        assertFailsWith<IllegalArgumentException> { sub[1] = "x" }
        assertFailsWith<IllegalArgumentException> { sub[5] = "x" }
        assertFailsWith<IllegalArgumentException> { sub.putAll(mapOf(1 to "x")) }
    }

    @Test
    fun testSubmapClearOnlyClearsRange() {
        val map = newMap(1 to "v1", 2 to "v2", 3 to "v3", 4 to "v4", 5 to "v5")
        val sub = map.subMap(2, 5)
        sub.clear()
        assertEquals(listOf(1, 5), map.keys.toList())
        assertEquals("v1", map[1])
        assertEquals("v5", map[5])
    }

    @Test
    fun testSubmapPollFirstAndLastEntryAffectBackingMap() {
        val map = newMap(1 to "v1", 2 to "v2", 3 to "v3", 4 to "v4", 5 to "v5")
        val sub = map.subMap(2, 5)
        assertEquals(2, sub.pollFirstEntry()?.key)
        assertEquals(4, sub.pollLastEntry()?.key)
        assertFalse(map.containsKey(2))
        assertFalse(map.containsKey(4))
        assertEquals(listOf(1, 3, 5), map.keys.toList())
    }

    @Test
    fun testInclusiveExclusiveSubmaps() {
        val map = newMap(1 to "v1", 2 to "v2", 3 to "v3", 4 to "v4", 5 to "v5")
        assertEquals(listOf(2, 3, 4), map.subMap(2, true, 5, false).keys.toList())
        assertEquals(listOf(3, 4, 5), map.subMap(2, false, 5, true).keys.toList())
        assertEquals(listOf(1, 2, 3), map.headMap(3, true).keys.toList())
        assertEquals(listOf(1, 2), map.headMap(3, false).keys.toList())
        assertEquals(listOf(3, 4, 5), map.tailMap(3, true).keys.toList())
        assertEquals(listOf(4, 5), map.tailMap(3, false).keys.toList())
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
    fun testComparatorBasedRangeViews() {
        val desc = TreeMap<Int, String>(compareByDescending { it })
        desc[1] = "a"
        desc[2] = "b"
        desc[3] = "c"
        desc[4] = "d"
        assertEquals(listOf(4), desc.headMap(3).keys.toList())
        assertEquals(listOf(3, 2, 1), desc.tailMap(3).keys.toList())
        assertEquals(listOf(4, 3), desc.subMap(4, 2).keys.toList())
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
    fun testIteratorRemoveOnEntrySet() {
        val map = newMap(1 to "a", 2 to "b", 3 to "c")
        val it = map.entries.iterator()
        assertEquals(1, it.next().key)
        it.remove()
        assertFalse(map.containsKey(1))
        assertEquals(listOf(2, 3), map.keys.toList())
        assertFailsWith<IllegalStateException> { it.remove() }
    }

    @Test
    fun testIteratorRemoveOnKeySet() {
        val map = newMap(1 to "a", 2 to "b", 3 to "c")
        val it = map.keys.iterator()
        assertEquals(1, it.next())
        it.remove()
        assertFalse(map.containsKey(1))
        assertEquals(listOf(2, 3), map.keys.toList())
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

    @Test
    fun testPutIfAbsent() {
        val map = TreeMap<Int, String>()
        map[1] = "one"
        val prevExisting = map.putIfAbsent(1, "uno")
        assertEquals("one", prevExisting)
        assertEquals("one", map[1])
        val prevMissing = map.putIfAbsent(2, "two")
        assertNull(prevMissing)
        assertEquals("two", map[2])
    }

    @Test
    fun testPollFirstAndLastEntry() {
        val map = TreeMap<Int, String>()
        map[2] = "two"
        map[1] = "one"
        map[3] = "three"
        val first = map.pollFirstEntry()
        assertEquals(1, first?.key)
        assertEquals("one", first?.value)
        val last = map.pollLastEntry()
        assertEquals(3, last?.key)
        assertEquals("three", last?.value)
        assertEquals(setOf(2), map.keys)
        map.clear()
        assertNull(map.pollFirstEntry())
        assertNull(map.pollLastEntry())
    }

    @Test
    fun testComputeIfAbsent() {
        val map = TreeMap<Int, String?>()
        map[1] = "a"
        var called = false
        val existing = map.computeIfAbsent(1) { called = true; "b" }
        assertEquals("a", existing)
        assertFalse(called)
        val added = map.computeIfAbsent(2) { "b" }
        assertEquals("b", added)
        assertEquals("b", map[2])
        val none = map.computeIfAbsent(3) { null }
        assertNull(none)
        assertFalse(map.containsKey(3))
    }

    @Test
    fun testComputeIfPresent() {
        val map = TreeMap<Int, String?>()
        map[1] = "a"
        map[2] = null
        val updated = map.computeIfPresent(1) { _, v -> v + "1" }
        assertEquals("a1", updated)
        assertEquals("a1", map[1])
        val nullResult = map.computeIfPresent(2) { _, _ -> "x" }
        assertNull(nullResult)
        val absent = map.computeIfPresent(3) { _, _ -> "y" }
        assertNull(absent)
        val removed = map.computeIfPresent(1) { _, _ -> null }
        assertNull(removed)
        assertFalse(map.containsKey(1))
    }

    @Test
    fun testCompute() {
        val map = TreeMap<Int, String?>()
        map[1] = "a"
        val res1 = map.compute(1) { _, v -> v + "x" }
        assertEquals("ax", res1)
        assertEquals("ax", map[1])
        val res2 = map.compute(2) { _, _ -> "b" }
        assertEquals("b", res2)
        assertEquals("b", map[2])
        val res3 = map.compute(3) { _, _ -> null }
        assertNull(res3)
        assertFalse(map.containsKey(3))
        val res4 = map.compute(1) { _, _ -> null }
        assertNull(res4)
        assertFalse(map.containsKey(1))
    }

    @Test
    fun testMerge() {
        val map = TreeMap<Int, String?>()
        map[1] = "a"
        val merged = map.merge(1, "b") { o, n -> o + n }
        assertEquals("ab", merged)
        assertEquals("ab", map[1])
        val mergedNew = map.merge(2, "c") { o, n -> o + n }
        assertEquals("c", mergedNew)
        assertEquals("c", map[2])
        val removed = map.merge(1, "x") { _, _ -> null }
        assertNull(removed)
        assertFalse(map.containsKey(1))
    }

    @Test
    fun testDescendingMapView() {
        val map = TreeMap<Int, String>()
        for (i in 1..3) map[i] = "v$i"
        val desc = map.descendingMap()
        assertEquals(listOf(3, 2, 1), desc.keys.toList())
        desc[0] = "v0"
        assertTrue(map.containsKey(0))
        map[4] = "v4"
        assertTrue(desc.containsKey(4))
        desc.remove(2)
        assertFalse(map.containsKey(2))
    }

    @Test
    fun testDescendingMapDescendingMapRoundTrip() {
        val map = newMap(1 to "v1", 2 to "v2", 3 to "v3")
        val roundTrip = map.descendingMap().descendingMap()
        assertEquals(listOf(1, 2, 3), roundTrip.keys.toList())
        roundTrip[4] = "v4"
        assertTrue(map.containsKey(4))
    }

    @Test
    fun testDescendingMapOrderAndNavigation() {
        val map = newMap(1 to "v1", 2 to "v2", 3 to "v3", 4 to "v4")
        val desc = map.descendingMap()
        assertEquals(listOf(4, 3, 2, 1), desc.keys.toList())
        assertEquals(4, desc.firstKey())
        assertEquals(1, desc.lastKey())
        assertEquals(3, desc.higherKey(4))
        assertEquals(4, desc.lowerKey(3))
    }

    @Test
    fun testDescendingMapIteratorRemove() {
        val map = newMap(1 to "v1", 2 to "v2", 3 to "v3")
        val it = map.descendingMap().entries.iterator()
        assertEquals(3, it.next().key)
        it.remove()
        assertFalse(map.containsKey(3))
        assertEquals(listOf(2, 1), map.descendingMap().keys.toList())
    }

    @Test
    fun testEntrySetSetValueWritesThrough() {
        val map = newMap(1 to "a", 2 to "b")
        val entry = map.entries.first { it.key == 1 }
        val oldValue = entry.setValue("z")
        assertEquals("a", oldValue)
        assertEquals("z", map[1])
    }

    @Test
    fun testEntrySetRemoveRequiresMatchingKeyAndValue() {
        val map = newMap(1 to "a", 2 to "b")
        assertFalse(map.entries.remove(object : MutableMap.MutableEntry<Int, String> {
            override val key: Int = 1
            override val value: String = "wrong"
            override fun setValue(newValue: String): String = value
        }))
        assertTrue(map.containsKey(1))
    }

    @Test
    fun testKeySetAndValuesViewsAreBacked() {
        val map = newMap(1 to "a", 2 to "b", 3 to "c")
        val keys = map.keys
        val values = map.values
        keys.remove(2)
        assertFalse(map.containsKey(2))
        values.remove("c")
        assertFalse(map.containsKey(3))
        map[4] = "d"
        assertTrue(keys.contains(4))
        assertTrue(values.contains("d"))
    }

    @Test
    fun testNaturalOrderingRejectsNullKey() {
        val map = TreeMap<Int, String>()
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<NullPointerException> { map.put(null as Int, "x") }
    }

    @Test
    fun testPutAllFromCompatibleTreeMapPreservesOrderingAndCopiesValues() {
        val source = newMap(3 to "c", 1 to "a", 2 to "b")
        val copy = TreeMap(source)
        assertEquals(listOf(1, 2, 3), copy.keys.toList())
        source[2] = "changed"
        assertEquals("b", copy[2])
    }

    @Test
    fun testSubmapPublicRangeBehavior() {
        val map = newMap(1 to "a", 2 to "b", 3 to "c", 4 to "d")
        val sub = map.subMap(2, 4)
        assertEquals("b", sub[2])
        assertEquals("c", sub[3])
        assertNull(sub[1])
        assertNull(sub[4])
        sub[3] = "cx"
        assertEquals("cx", map[3])
        assertEquals("cx", sub[3])
        assertNull(sub.remove(1))
        assertFailsWith<IllegalArgumentException> { sub[1] = "z" }
        assertFailsWith<IllegalArgumentException> { sub[4] = "z" }
    }

}
