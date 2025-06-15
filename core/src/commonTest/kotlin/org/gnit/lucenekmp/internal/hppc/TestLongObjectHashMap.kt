package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLongObjectHashMap : LuceneTestCase() {
    private val keyE: Long = 0
    private val key1: Long = cast(1)
    private val key2: Long = cast(2)

    private val value0 = vcast(0)
    private val value1 = vcast(1)
    private val value2 = vcast(2)
    private val value3 = vcast(3)

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

    private fun rarely(): Boolean = TestUtil.rarely(random())
    private fun randomIntBetween(min: Int, max: Int): Int = TestUtil.nextInt(random(), min, max)
}
