package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class TestIntObjectHashMap : LuceneTestCase() {
    private lateinit var map: IntObjectHashMap<Any?>

    @BeforeTest
    fun setup() {
        map = IntObjectHashMap()
    }

    @Test
    fun testPut() {
        map.put(1, "one")
        assertTrue(map.containsKey(1))
        assertEquals("one", map.get(1))
    }

    @Test
    fun testRemove() {
        map.put(1, "one")
        assertEquals("one", map.remove(1))
        assertNull(map.get(1))
    }

    @Test
    fun testEnsureCapacity() {
        val localMap = IntObjectHashMap<Int>(0)

        val max = if (rarely()) 0 else randomIntBetween(0, 250)
        for (i in 0 until max) {
            localMap.put(i, 0)
        }

        val additions = randomIntBetween(max, max + 5000)
        localMap.ensureCapacity(additions + localMap.size())
        val keysBefore = localMap.keys
        val valuesBefore = localMap.values
        for (i in 0 until additions) {
            localMap.put(i, 0)
        }
        assertTrue(keysBefore === localMap.keys)
        assertTrue(valuesBefore === localMap.values)
    }

    @Test
    fun testCursorIndexIsValid() {
        map.put(0, 1)
        map.put(1, 2)
        map.put(2, 3)

        for (c in map) {
            val cursor = c!!
            assertTrue(map.indexExists(cursor.index))
            assertEquals(cursor.value, map.indexGet(cursor.index))
        }
    }

    @Test
    fun testIndexMethods() {
        map.put(0, 1)
        map.put(1, 2)

        assertTrue(map.indexOf(0) >= 0)
        assertTrue(map.indexOf(1) >= 0)
        assertTrue(map.indexOf(2) < 0)

        assertTrue(map.indexExists(map.indexOf(0)))
        assertTrue(map.indexExists(map.indexOf(1)))
        assertFalse(map.indexExists(map.indexOf(2)))

        assertEquals(1, map.indexGet(map.indexOf(0)))
        assertEquals(2, map.indexGet(map.indexOf(1)))

        assertFailsWith<IllegalArgumentException> { map.indexGet(map.indexOf(2)) }

        assertEquals(1, map.indexReplace(map.indexOf(0), 3))
        assertEquals(2, map.indexReplace(map.indexOf(1), 4))
        assertEquals(3, map.indexGet(map.indexOf(0)))
        assertEquals(4, map.indexGet(map.indexOf(1)))

        map.indexInsert(map.indexOf(2), 2, 1)
        assertEquals(1, map.indexGet(map.indexOf(2)))
        assertEquals(3, map.size())

        assertEquals(3, map.indexRemove(map.indexOf(0)))
        assertEquals(2, map.size())
        assertEquals(1, map.indexRemove(map.indexOf(2)))
        assertEquals(1, map.size())
        assertTrue(map.indexOf(0) < 0)
        assertTrue(map.indexOf(1) >= 0)
        assertTrue(map.indexOf(2) < 0)
    }

    @Test
    fun testCloningConstructor() {
        map.put(1, 1)
        map.put(2, 2)
        map.put(3, 3)

        val clone = IntObjectHashMap<Any?>(map)
        assertSameMap(map, clone)
    }

    @Test
    fun testFromArrays() {
        map.put(1, 1)
        map.put(2, 2)
        map.put(3, 3)

        val keys = intArrayOf(1, 2, 3)
        val values = arrayOf<Any?>(1, 2, 3)
        val map2 = IntObjectHashMap.from(keys, values)
        assertSameMap(map, map2)
    }

    @Test
    fun testGetOrDefault() {
        map.put(2, 2)
        assertTrue(map.containsKey(2))

        map.put(1, 1)
        assertEquals(1, map.getOrDefault(1, 3))
        assertEquals(3, map.getOrDefault(3, 3))
        map.remove(1)
        assertEquals(3, map.getOrDefault(1, 3))
    }

    @Test
    fun testNullValue() {
        map.put(1, null)

        assertTrue(map.containsKey(1))
        assertNull(map.get(1))
    }

    @Test
    fun testPutOverExistingKey() {
        map.put(1, 1)
        assertEquals(1, map.put(1, 3))
        assertEquals(3, map.get(1))

        assertEquals(3, map.put(1, null))
        assertTrue(map.containsKey(1))
        assertNull(map.get(1))

        assertNull(map.put(1, 1))
        assertEquals(1, map.get(1))
    }

    @Test
    fun testPutWithExpansions() {
        val count = 10_000
        val rnd = kotlin.random.Random(random().nextLong())
        val values = hashSetOf<Int>()

        repeat(count) {
            val v = rnd.nextInt()
            val hadKey = values.contains(v)
            values.add(v)

            assertEquals(hadKey, map.containsKey(v))
            map.put(v, v)
            assertEquals(values.size, map.size())
        }
        assertEquals(values.size, map.size())
    }

    @Test
    fun testPutAll() {
        map.put(1, 1)
        map.put(2, 1)

        val map2 = IntObjectHashMap<Any?>()
        map2.put(2, 2)
        map2.put(0, 1)

        assertEquals(1, map.putAll(map2))

        assertEquals(2, map.get(2))
        assertEquals(1, map.get(0))
        assertEquals(3, map.size())
    }

    @Test
    fun testPutIfAbsent() {
        assertTrue(map.putIfAbsent(1, 1))
        assertFalse(map.putIfAbsent(1, 2))
        assertEquals(1, map.get(1))
    }

    @Test
    fun testEmptyKey() {
        val empty = 0

        map.put(empty, 1)
        assertEquals(1, map.size())
        assertFalse(map.isEmpty)
        assertEquals(1, map.get(empty))
        assertEquals(1, map.getOrDefault(empty, 2))
        assertTrue(map.iterator().hasNext())
        val it = map.iterator()
        val entry = it.next()!!
        assertEquals(empty, entry.key)
        assertEquals(1, entry.value)

        map.remove(empty)
        assertNull(map.get(empty))
        assertEquals(0, map.size())

        map.put(empty, null)
        assertEquals(1, map.size())
        assertTrue(map.containsKey(empty))
        assertNull(map.get(empty))

        map.remove(empty)
        assertEquals(0, map.size())
        assertFalse(map.containsKey(empty))
        assertNull(map.get(empty))

        assertNull(map.put(empty, 1))
        assertEquals(1, map.put(empty, 2))
        map.clear()
        assertFalse(map.indexExists(map.indexOf(empty)))
        assertNull(map.put(empty, 1))
        map.clear()
        assertNull(map.remove(empty))
    }

    @Test
    fun testMapKeySet() {
        map.put(1, 3)
        map.put(2, 2)
        map.put(3, 1)

        assertSortedListEquals(map.keys().toArray(), 1, 2, 3)
    }

    @Test
    fun testMapKeySetIterator() {
        map.put(1, 3)
        map.put(2, 2)
        map.put(3, 1)

        var counted = 0
        for (c in map.keys()) {
            val cursor = c!!
            assertEquals(map.keys!![cursor.index], cursor.value)
            counted++
        }
        assertEquals(counted, map.size())
    }

    @Test
    fun testMapValues() {
        map.put(1, 3)
        map.put(2, 2)
        map.put(3, 1)

        val values = IntArray(map.values().size())
        var i = 0
        for (c in map.values()) {
            val cursor = c!!
            values[i++] = cursor.value as Int
        }
        assertEquals(map.size(), i)
        assertSortedListEquals(values, 3, 2, 1)
    }

    @Test
    fun testMapValuesIterator() {
        map.put(1, 3)
        map.put(2, 2)
        map.put(3, 1)

        var counted = 0
        for (c in map.values()) {
            val cursor = c!!
            assertEquals(map.values!![cursor.index], cursor.value)
            counted++
        }
        assertEquals(counted, map.size())
    }

    @Test
    fun testClear() {
        map.put(1, 1)
        map.put(2, 1)
        map.clear()
        assertEquals(0, map.size())

        assertNull(map.put(1, 1))
        assertNull(map.remove(2))
        map.clear()

        testPutWithExpansions()
    }

    @Test
    fun testRelease() {
        map.put(1, 1)
        map.put(2, 1)
        map.release()
        assertEquals(0, map.size())

        testPutWithExpansions()
    }

    @Test
    fun testIterable() {
        map.put(1, 1)
        map.put(2, 2)
        map.put(3, 3)
        map.remove(2)

        var count = 0
        for (cursor in map) {
            val c = cursor!!
            count++
            assertTrue(map.containsKey(c.key))
            assertEquals(c.value, map.get(c.key))

            assertEquals(c.value, map.values!![c.index])
            assertEquals(c.key, map.keys!![c.index])
        }
        assertEquals(count, map.size())

        map.clear()
        assertFalse(map.iterator().hasNext())
    }

    @Test
    fun testBug_HPPC73_FullCapacityGet() {
        val elements = 0x7F
        val localMap = IntObjectHashMap<Any?>(elements)
        for (i in 1..elements) {
            localMap.put(i, 1)
        }

        val outOfSet = elements + 1
        localMap.remove(outOfSet)
        assertFalse(localMap.containsKey(outOfSet))

        localMap.put(1, 2)
        localMap.remove(1)
        localMap.put(1, 2)

        localMap.put(outOfSet, 1)
        assertEquals(elements + 1, localMap.size())
        assertEquals(1, localMap.get(outOfSet))
    }

    @Test
    fun testHashCodeEquals() {
        val l0 = IntObjectHashMap<Any?>()
        assertEquals(0, l0.hashCode())
        assertEquals(l0, IntObjectHashMap<Any?>())

        val l1 = IntObjectHashMap.from(intArrayOf(1, 2, 3), arrayOf<Any?>(1, 2, 3))
        val l2 = IntObjectHashMap.from(intArrayOf(2, 1, 3), arrayOf<Any?>(2, 1, 3))
        val l3 = IntObjectHashMap.from(intArrayOf(1, 2), arrayOf<Any?>(2, 1))

        assertEquals(l1.hashCode(), l2.hashCode())
        assertEquals(l1, l2)
        assertFalse(l1 == l3)
        assertFalse(l2 == l3)
    }

    @Test
    fun testBug_HPPC37() {
        val l1 = IntObjectHashMap.from(intArrayOf(1), arrayOf<Any?>(1))
        val l2 = IntObjectHashMap.from(intArrayOf(2), arrayOf<Any?>(1))

        assertFalse(l1 == l2)
        assertFalse(l2 == l1)
    }

    @Test
    fun testEqualsSameClass() {
        val m1 = IntObjectHashMap<Any?>()
        val m2 = IntObjectHashMap<Any?>()

        m1.put(1, 1)
        m1.put(2, 2)
        m2.put(1, 1)
        m2.put(2, 2)

        assertTrue(m1 == m2)
        assertTrue(m2 == m1)

        m2.put(2, 3)
        assertFalse(m1 == m2)
        assertFalse(m2 == m1)
    }

    @Test
    fun testEqualsSubClass() {
        val m1 = IntObjectHashMap<Any?>()
        val m2 = object : IntObjectHashMap<Any?>() {}

        m1.put(1, 1)
        m2.put(1, 1)

        assertFalse(m1 == m2)
        assertFalse(m2 == m1)
    }

    @Test
    fun testAgainstHashMap() {
        val rnd = random()
        val other = HashMap<Int, Int?>()

        var size = 1000
        while (size < 20000) {
            other.clear()
            map.clear()

            for (round in 0 until size * 20) {
                var key = rnd.nextInt(size)
                if (rnd.nextInt(50) == 0) {
                    key = 0
                }

                val value = rnd.nextInt()

                if (rnd.nextBoolean()) {
                    val previousValue: Int?
                    if (rnd.nextBoolean()) {
                        val index = map.indexOf(key)
                        if (map.indexExists(index)) {
                            previousValue = map.indexReplace(index, value) as Int?
                        } else {
                            map.indexInsert(index, key, value)
                            previousValue = null
                        }
                    } else {
                        previousValue = map.put(key, value) as Int?
                    }
                    assertEquals(other.put(key, value), previousValue)

                    assertEquals(value, map.get(key))
                    assertEquals(value, map.indexGet(map.indexOf(key)))
                    assertTrue(map.containsKey(key))
                    assertTrue(map.indexExists(map.indexOf(key)))
                } else {
                    assertEquals(other.containsKey(key), map.containsKey(key))
                    val previousValue: Int? = if (map.containsKey(key) && rnd.nextBoolean()) {
                        map.indexRemove(map.indexOf(key)) as Int?
                    } else {
                        map.remove(key) as Int?
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
        map.put(1, 1)
        map.put(2, 2)
        map.put(3, 3)

        val cloned = map.clone()
        cloned.remove(1)

        assertSortedListEquals(map.keys().toArray(), 1, 2, 3)
        assertSortedListEquals(cloned.keys().toArray(), 2, 3)
    }

    private fun assertSameMap(c1: IntObjectHashMap<Any?>, c2: IntObjectHashMap<Any?>) {
        assertEquals(c1.size(), c2.size())
        for (entry in c1) {
            val e = entry!!
            assertTrue(c2.containsKey(e.key))
            assertEquals(e.value, c2.get(e.key))
        }
    }

    private fun assertSortedListEquals(array: IntArray, vararg elements: Int) {
        val expected = elements.copyOf()
        array.sort()
        expected.sort()
        assertTrue(array contentEquals expected)
    }

    private fun rarely(): Boolean = org.gnit.lucenekmp.tests.util.TestUtil.rarely(random())

    private fun randomIntBetween(min: Int, max: Int): Int = min + random().nextInt(max + 1 - min)
}
