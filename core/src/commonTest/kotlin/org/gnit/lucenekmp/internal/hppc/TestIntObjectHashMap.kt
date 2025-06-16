package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

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

    private fun rarely(): Boolean = org.gnit.lucenekmp.tests.util.TestUtil.rarely(random())

    private fun randomIntBetween(min: Int, max: Int): Int = min + random().nextInt(max + 1 - min)
}
