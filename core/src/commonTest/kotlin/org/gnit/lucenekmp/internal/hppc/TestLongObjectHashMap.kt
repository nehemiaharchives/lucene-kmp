package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.collections.HashSet
import kotlin.test.assertContentEquals

class TestLongObjectHashMap : LuceneTestCase() {
    private val keyE: Long = 0
    private val key1: Long = cast(1)
    private val key2: Long = cast(2)
    private val key3: Long = cast(3)

    private val value0 = vcast(0)
    private val value1 = vcast(1)
    private val value2 = vcast(2)
    private val value3 = vcast(3)
    private val value4 = vcast(4)

    private lateinit var map: LongObjectHashMap<Int>

    private fun cast(v: Int): Long = v.toLong()
    private fun vcast(v: Int): Int = v

    @Test
    fun testEnsureCapacity() {
        map = LongObjectHashMap(0)
        val max = if (rarely()) 0 else randomIntBetween(0, 250)
        for (i in 0 until max) {
            map.put(cast(i), value0)
        }
        val additions = randomIntBetween(max, max + 5000)
        map.ensureCapacity(additions + map.size())
        val beforeKeys = map.keys
        val beforeValues = map.values
        for (i in 0 until additions) {
            map.put(cast(i), value0)
        }
        assertTrue(beforeKeys === map.keys)
        assertTrue(beforeValues === map.values)
    }

    @Test
    fun testCursorIndexIsValid() {
        map = LongObjectHashMap()
        map.put(keyE, value1)
        map.put(key1, value2)
        map.put(key2, value3)
        for (c in map) {
            assertTrue(map.indexExists(c.index))
            assertEquals(c.value, map.indexGet(c.index))
        }
    }

    @Test
    fun testIndexMethods() {
        map = LongObjectHashMap()
        map.put(keyE, value1)
        map.put(key1, value2)

        assertTrue(map.indexOf(keyE) >= 0)
        assertTrue(map.indexOf(key1) >= 0)
        assertTrue(map.indexOf(key2) < 0)

        assertTrue(map.indexExists(map.indexOf(keyE)))
        assertTrue(map.indexExists(map.indexOf(key1)))
        assertFalse(map.indexExists(map.indexOf(key2)))

        assertEquals(value1, map.indexGet(map.indexOf(keyE)))
        assertEquals(value2, map.indexGet(map.indexOf(key1)))

        expectThrows<AssertionError>(AssertionError::class) {
            map.indexGet(map.indexOf(key2))
        }

        assertEquals(value1, map.indexReplace(map.indexOf(keyE), value3))
        assertEquals(value2, map.indexReplace(map.indexOf(key1), value4))
        assertEquals(value3, map.indexGet(map.indexOf(keyE)))
        assertEquals(value4, map.indexGet(map.indexOf(key1)))

        map.indexInsert(map.indexOf(key2), key2, value1)
        assertEquals(value1, map.indexGet(map.indexOf(key2)))
        assertEquals(3, map.size())

        assertEquals(value3, map.indexRemove(map.indexOf(keyE)))
        assertEquals(2, map.size())
        assertEquals(value1, map.indexRemove(map.indexOf(key2)))
        assertEquals(1, map.size())
        assertTrue(map.indexOf(keyE) < 0)
        assertTrue(map.indexOf(key1) >= 0)
        assertTrue(map.indexOf(key2) < 0)
    }

    @Test
    fun testCloningConstructor() {
        map = LongObjectHashMap()
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value3)

        val cloned = LongObjectHashMap(map)
        assertSameMap(map, cloned)
    }

    @Test
    fun testFromArrays() {
        map = LongObjectHashMap()
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value3)

        val map2 = LongObjectHashMap.from(
            longArrayOf(key1, key2, key3),
            arrayOf(value1, value2, value3)
        )

        assertSameMap(map, map2)
    }

    @Test
    fun testGetOrDefault() {
        map = LongObjectHashMap()
        map.put(key2, value2)
        assertTrue(map.containsKey(key2))

        map.put(key1, value1)
        assertEquals(value1, map.getOrDefault(key1, value3))
        assertEquals(value3, map.getOrDefault(key3, value3))
        map.remove(key1)
        assertEquals(value3, map.getOrDefault(key1, value3))
    }

    @Test
    fun testPut() {
        map = LongObjectHashMap()
        map.put(key1, value1)

        assertTrue(map.containsKey(key1))
        assertEquals(value1, map.get(key1))
    }

    @Test
    fun testNullValue() {
        map = LongObjectHashMap()
        map.put(key1, null)

        assertTrue(map.containsKey(key1))
        assertEquals(null, map.get(key1))
    }

    @Test
    fun testPutOverExistingKey() {
        map = LongObjectHashMap()
        map.put(key1, value1)

        assertEquals(value1, map.put(key1, value3))
        assertEquals(value3, map.get(key1))

        assertEquals(value3, map.put(key1, null))
        assertTrue(map.containsKey(key1))
        assertEquals(null, map.get(key1))

        assertEquals(null, map.put(key1, value1))
        assertEquals(value1, map.get(key1))
    }

    @Test
    fun testPutWithExpansions() {
        map = LongObjectHashMap()
        val COUNT = 10000
        val rnd = kotlin.random.Random(random().nextLong())
        val values = HashSet<Long>()

        for (i in 0 until COUNT) {
            val v = rnd.nextInt()
            val key = cast(v)
            val hadKey = values.contains(key)
            values.add(key)

            assertEquals(hadKey, map.containsKey(key))
            map.put(key, vcast(v))
            assertEquals(values.size, map.size())
        }
        assertEquals(values.size, map.size())
    }

    @Test
    fun testPutAll() {
        map = LongObjectHashMap()
        map.put(key1, value1)
        map.put(key2, value1)

        val map2 = LongObjectHashMap<Int>()
        map2.put(key2, value2)
        map2.put(keyE, value1)

        assertEquals(1, map.putAll(map2))
        assertEquals(value2, map.get(key2))
        assertEquals(value1, map.get(keyE))
        assertEquals(3, map.size())
    }

    @Test
    fun testPutIfAbsent() {
        map = LongObjectHashMap()

        assertTrue(map.putIfAbsent(key1, value1))
        assertFalse(map.putIfAbsent(key1, value2))
        assertEquals(value1, map.get(key1))
    }

    @Test
    fun testRemove() {
        map = LongObjectHashMap()
        map.put(key1, value1)
        assertEquals(value1, map.remove(key1))
        assertEquals(null, map.remove(key1))
        assertEquals(0, map.size())
    }

    @Test
    fun testEmptyKey() {
        map = LongObjectHashMap()
        val empty = keyE

        map.put(empty, value1)
        assertEquals(1, map.size())
        assertFalse(map.isEmpty)
        assertEquals(value1, map.get(empty))
        assertEquals(value1, map.getOrDefault(empty, value2))
        assertTrue(map.iterator().hasNext())
        val c = map.iterator().next()
        assertEquals(empty, c.key)
        assertEquals(value1, c.value)

        map.remove(empty)
        assertEquals(null, map.get(empty))
        assertEquals(0, map.size())

        map.put(empty, null)
        assertEquals(1, map.size())
        assertTrue(map.containsKey(empty))
        assertEquals(null, map.get(empty))

        map.remove(empty)
        assertEquals(0, map.size())
        assertFalse(map.containsKey(empty))
        assertEquals(null, map.get(empty))

        assertEquals(null, map.put(empty, value1))
        assertEquals(value1, map.put(empty, value2))
        map.clear()
        assertFalse(map.indexExists(map.indexOf(empty)))
        assertEquals(null, map.put(empty, value1))
        map.clear()
        assertEquals(null, map.remove(empty))
    }

    @Test
    fun testMapKeySet() {
        map = LongObjectHashMap()
        map.put(key1, value3)
        map.put(key2, value2)
        map.put(key3, value1)

        assertSortedListEquals(map.keys().toArray(), asArray(key1, key2, key3))
    }

    @Test
    fun testMapKeySetIterator() {
        map = LongObjectHashMap()
        map.put(key1, value3)
        map.put(key2, value2)
        map.put(key3, value1)

        var counted = 0
        for (c in map.keys()) {
            assertEquals(map.keys!![c.index], c.value)
            counted++
        }
        assertEquals(counted, map.size())
    }

    private fun assertSameMap(c1: LongObjectHashMap<Int>, c2: LongObjectHashMap<Int>) {
        assertEquals(c1.size(), c2.size())
        for (entry in c1) {
            assertTrue(c2.containsKey(entry.key))
            assertEquals(entry.value, c2.get(entry.key))
        }
    }

    private fun asArray(vararg elements: Long): LongArray = elements

    private fun assertSortedListEquals(array: LongArray, elements: LongArray) {
        assertEquals(elements.size, array.size)
        val sortedArray = array.copyOf()
        val sortedElements = elements.copyOf()
        org.gnit.lucenekmp.jdkport.Arrays.sort(sortedArray)
        org.gnit.lucenekmp.jdkport.Arrays.sort(sortedElements)
        assertContentEquals(sortedElements, sortedArray)
    }

    private fun rarely(): Boolean = TestUtil.rarely(random())
    private fun randomIntBetween(min: Int, max: Int): Int = TestUtil.nextInt(random(), min, max)
}
