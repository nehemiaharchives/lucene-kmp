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
    fun testPutAndGet() {
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

    private fun assertSameMap(c1: IntObjectHashMap<Any?>, c2: IntObjectHashMap<Any?>) {
        assertEquals(c1.size(), c2.size())
        for (entry in c1) {
            val e = entry!!
            assertTrue(c2.containsKey(e.key))
            assertEquals(e.value, c2.get(e.key))
        }
    }

    private fun rarely(): Boolean = org.gnit.lucenekmp.tests.util.TestUtil.rarely(random())

    private fun randomIntBetween(min: Int, max: Int): Int = min + random().nextInt(max + 1 - min)
}
