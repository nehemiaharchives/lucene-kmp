package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.collections.HashSet
import kotlin.test.assertContentEquals

class TestLongObjectHashMap : LuceneTestCase() {
    private val keyE: Long = 0
    private val key1: Long = cast(1)
    private val key4: Long = cast(4)
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

    @Test
    fun testClear() {
        map = LongObjectHashMap()
        map.put(key1, value1)
        map.put(key2, value1)
        map.clear()
        assertEquals(0, map.size())

        assertEquals(null, map.put(key1, value1))
        assertEquals(null, map.remove(key2))
        map.clear()

        testPutWithExpansions()
    }

    @Test
    fun testRelease() {
        map = LongObjectHashMap()
        map.put(key1, value1)
        map.put(key2, value1)
        map.release()
        assertEquals(0, map.size())

        testPutWithExpansions()
    }

    @Test
    fun testIterable() {
        map = LongObjectHashMap()
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value3)
        map.remove(key2)

        var count = 0
        for (cursor in map) {
            count++
            assertTrue(map.containsKey(cursor.key))
            assertEquals(cursor.value, map.get(cursor.key))
            assertEquals(cursor.value, map.values!![cursor.index])
            assertEquals(cursor.key, map.keys!![cursor.index])
        }
        assertEquals(count, map.size())

        map.clear()
        assertFalse(map.iterator().hasNext())
    }

    @Test
    fun testBug_HPPC73_FullCapacityGet() {
        val elements = 0x7F
        map = LongObjectHashMap(elements - 1, 0.99)
        val beforeKeys = map.keys
        val beforeValues = map.values

        for (i in 1..elements) {
            map.put(cast(i), value1)
        }

        val outOfSet = cast(elements + 1)
        map.remove(outOfSet)
        assertFalse(map.containsKey(outOfSet))
        assertTrue(beforeKeys === map.keys)
        assertTrue(beforeValues === map.values)

        map.put(key1, value2)
        assertTrue(beforeKeys === map.keys)
        assertTrue(beforeValues === map.values)

        map.remove(key1)
        assertTrue(beforeKeys === map.keys)
        assertTrue(beforeValues === map.values)
        map.put(key1, value2)

        map.put(outOfSet, value1)
        assertTrue(beforeKeys !== map.keys)
        assertTrue(beforeValues !== map.values)
    }

    @Test
    fun testHashCodeEquals() {
        val l0 = LongObjectHashMap<Int>()
        assertEquals(0, l0.hashCode())
        assertEquals(l0, LongObjectHashMap<Int>())

        val l1 = LongObjectHashMap.from(
            longArrayOf(key1, key2, key3),
            arrayOf(value1, value2, value3)
        )

        val l2 = LongObjectHashMap.from(
            longArrayOf(key2, key1, key3),
            arrayOf(value2, value1, value3)
        )

        val l3 = LongObjectHashMap.from(
            longArrayOf(key1, key2),
            arrayOf(value2, value1)
        )

        assertEquals(l1.hashCode(), l2.hashCode())
        assertEquals(l1, l2)

        assertNotEquals(l1, l3)
        assertNotEquals(l2, l3)
    }

    @Test
    fun testBug_HPPC37() {
        val l1 = LongObjectHashMap.from(longArrayOf(key1), arrayOf(value1))
        val l2 = LongObjectHashMap.from(longArrayOf(key2), arrayOf(value1))

        assertNotEquals(l1, l2)
        assertNotEquals(l2, l1)
    }


    @Test
    fun testAgainstHashMap() {
        map = LongObjectHashMap()
        val rnd = random()
        val other = HashMap<Long, Int?>()

        var size = 1000
        while (size < 20000) {
            other.clear()
            map.clear()
            for (round in 0 until size * 20) {
                var key = cast(rnd.nextInt(size))
                if (rnd.nextInt(50) == 0) {
                    key = 0
                }

                val value = vcast(rnd.nextInt())

                if (rnd.nextBoolean()) {
                    val previousValue: Int?
                    if (rnd.nextBoolean()) {
                        val index = map.indexOf(key)
                        previousValue = if (map.indexExists(index)) {
                            map.indexReplace(index, value)
                        } else {
                            map.indexInsert(index, key, value)
                            null
                        }
                    } else {
                        previousValue = map.put(key, value)
                    }

                    assertEquals(other.put(key, value), previousValue)
                    assertEquals(value, map.get(key))
                    assertEquals(value, map.indexGet(map.indexOf(key)))
                    assertTrue(map.containsKey(key))
                    assertTrue(map.indexExists(map.indexOf(key)))
                } else {
                    assertEquals(other.containsKey(key), map.containsKey(key))
                    val previousValue = if (map.containsKey(key) && rnd.nextBoolean()) {
                        map.indexRemove(map.indexOf(key))
                    } else {
                        map.remove(key)
                    }
                    assertEquals(other.remove(key), previousValue)
                }

                assertEquals(other.size, map.size())
            }

            size += 4000
        }
    }

    @Test
    fun testClone() {
        map = LongObjectHashMap()
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value3)

        val cloned = map.clone()
        cloned.remove(key1)

        assertSortedListEquals(map.keys().toArray(), asArray(key1, key2, key3))
        assertSortedListEquals(cloned.keys().toArray(), asArray(key2, key3))
    }

    @Test
    fun testMapValues() {
        map = LongObjectHashMap()
        map.put(key1, value3)
        map.put(key2, value2)
        map.put(key3, value1)
        assertContentEquals(listOf(value1, value2, value3).sorted(), toList(map.values()).map { it!! }.sorted())

        map.clear()
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value2)
        assertContentEquals(listOf(value1, value2, value2).sorted(), toList(map.values()).map { it!! }.sorted())
    }

    @Test
    fun testMapValuesIterator() {
        map = LongObjectHashMap()
        map.put(key1, value3)
        map.put(key2, value2)
        map.put(key3, value1)

        var counted = 0
        for (c in map.values()) {
            assertEquals(map.values!![c.index], c.value)
            counted++
        }
        assertEquals(counted, map.size())
    }

    @Test
    fun testEqualsSameClass() {
        val l1 = LongObjectHashMap<Int>()
        l1.put(key1, value0)
        l1.put(key2, value1)
        l1.put(key3, value2)

        val l2 = LongObjectHashMap(l1)
        l2.putAll(l1)

        val l3 = LongObjectHashMap(l2)
        l3.putAll(l2)
        l3.put(key4, value0)

        assertEquals(l2, l1)
        assertEquals(l2.hashCode(), l1.hashCode())
        assertNotEquals(l1, l3)
    }

    @Test
    fun testEqualsSubClass() {
        class Sub : LongObjectHashMap<Int>()

        val l1 = LongObjectHashMap<Int>()
        l1.put(key1, value0)
        l1.put(key2, value1)
        l1.put(key3, value2)

        val l2 = Sub()
        l2.putAll(l1)
        l2.put(key4, value3)

        val l3 = Sub()
        l3.putAll(l2)

        assertNotEquals(l1, l2)
        assertEquals(l3.hashCode(), l2.hashCode())
        assertEquals(l3, l2)
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

    private fun <T> toList(values: Iterable<ObjectCursor<T>>): List<T?> {
        val result = mutableListOf<T?>()
        for (c in values) {
            result.add(c.value)
        }
        return result
    }


    private fun rarely(): Boolean = TestUtil.rarely(random())
    private fun randomIntBetween(min: Int, max: Int): Int = TestUtil.nextInt(random(), min, max)
}
