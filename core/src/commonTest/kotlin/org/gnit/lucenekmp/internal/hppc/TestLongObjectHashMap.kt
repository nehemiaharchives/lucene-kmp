package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.collections.HashSet

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

    private fun assertSameMap(c1: LongObjectHashMap<Int>, c2: LongObjectHashMap<Int>) {
        assertEquals(c1.size(), c2.size())
        for (entry in c1) {
            assertTrue(c2.containsKey(entry.key))
            assertEquals(entry.value, c2.get(entry.key))
        }
    }

    private fun rarely(): Boolean = TestUtil.rarely(random())
    private fun randomIntBetween(min: Int, max: Int): Int = TestUtil.nextInt(random(), min, max)
}
