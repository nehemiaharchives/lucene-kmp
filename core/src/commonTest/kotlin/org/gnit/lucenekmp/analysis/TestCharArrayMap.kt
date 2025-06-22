package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestCharArrayMap : LuceneTestCase() {

    private fun doRandom(iter: Int, ignoreCase: Boolean) {
        val map = CharArrayMap<Int>(1, ignoreCase)
        val hmap = HashMap<String, Int>()
        var key: CharArray
        for (i in 0 until iter) {
            val len = random().nextInt(5)
            key = CharArray(len)
            for (j in key.indices) {
                key[j] = random().nextInt(127).toChar()
            }
            val keyStr = key.concatToString()
            val hmapKey = if (ignoreCase) keyStr.lowercase() else keyStr
            val value = random().nextInt()

            val o1 = map.put(key, value)
            val o2 = hmap.put(hmapKey, value)
            assertEquals(o1, o2)

            // add it again with the string method
            assertEquals(value, map.put(keyStr, value))

            assertEquals(value, map.get(key, 0, key.size))
            assertEquals(value, map.get(key))
            assertEquals(value, map.get(keyStr))

            assertEquals(hmap.size, map.size)
        }
    }

    @Test
    fun testCharArrayMap() {
        val num = 5 * RANDOM_MULTIPLIER
        for (i in 0 until num) {
            doRandom(1000, false)
            doRandom(1000, true)
        }
    }

    @Test
    fun testMethods() {
        val cm = CharArrayMap<Int>(2, false)
        val hm = HashMap<String, Int>()
        hm["foo"] = 1
        hm["bar"] = 2
        cm.putAll(hm as Map<Any, Int>)
        assertEquals(hm.size, cm.size)
        hm["baz"] = 3
        cm.putAll(hm as Map<Any, Int>)
        assertEquals(hm.size, cm.size)

        val cs = cm.keySet
        var n = 0
        for (o in cs) {
            assertTrue(cm.containsKey(o))
            val co = o as CharArray
            assertTrue(cm.containsKey(co, 0, co.size))
            n++
        }
        assertEquals(hm.size, n)
        assertEquals(hm.size, cs.size)
        assertEquals(cm.size, cs.size)
        cs.clear()
        assertEquals(0, cs.size)
        assertEquals(0, cm.size)
        expectThrows(UnsupportedOperationException::class) { cs.add("test") }

        cm.putAll(hm as Map<Any, Int>)
        assertEquals(hm.size, cs.size)
        assertEquals(cm.size, cs.size)

        val iter1 = cm.entries.iterator()
        n = 0
        while (iter1.hasNext()) {
            val entry = iter1.next()
            val key = entry.key
            val value = entry.value
            assertEquals(cm.get(key), value)
            entry.setValue(value * 100)
            assertEquals(value * 100, cm.get(key))
            n++
        }
        assertEquals(hm.size, n)
        cm.clear()
        cm.putAll(hm as Map<Any, Int>)
        assertEquals(cm.size, n)

        val iter2 = cm.entries.iterator() as CharArrayMap<Int>.EntryIterator
        n = 0
        while (iter2.hasNext()) {
            val keyc = iter2.nextKey()!!
            val value = iter2.currentValue()!!
            assertEquals(hm[keyc.concatToString()], value)
            iter2.setValue(value * 100)
            assertEquals(value * 100, cm.get(keyc))
            n++
        }
        assertEquals(hm.size, n)

        cm.entries.clear()
        assertEquals(0, cm.size)
        assertEquals(0, cm.entries.size)
        assertTrue(cm.isEmpty())
    }

    @Test
    @Suppress("UnnecessaryStringBuilder")
    fun testModifyOnUnmodifiable() {
        val map = CharArrayMap<Int>(2, false)
        map.put("foo", 1)
        map.put("bar", 2)
        val size = map.size
        assertEquals(2, size)
        assertTrue(map.containsKey("foo"))
        assertEquals(1, map.get("foo"))
        assertTrue(map.containsKey("bar"))
        assertEquals(2, map.get("bar"))

        val unmodifiableMap = CharArrayMap.unmodifiableMap(map)
        assertEquals(size, unmodifiableMap.size, "Map size changed due to unmodifiableMap call")
        val NOT_IN_MAP = "SirGallahad"
        assertFalse(unmodifiableMap.containsKey(NOT_IN_MAP))
        assertNull(unmodifiableMap.get(NOT_IN_MAP))

        expectThrows(UnsupportedOperationException::class) { unmodifiableMap.put(NOT_IN_MAP.toCharArray(), 3) }
        assertFalse(unmodifiableMap.containsKey(NOT_IN_MAP))
        assertNull(unmodifiableMap.get(NOT_IN_MAP))
        assertEquals(size, unmodifiableMap.size)

        expectThrows(UnsupportedOperationException::class) { unmodifiableMap.put(NOT_IN_MAP, 3) }
        assertFalse(unmodifiableMap.containsKey(NOT_IN_MAP))
        assertNull(unmodifiableMap.get(NOT_IN_MAP))
        assertEquals(size, unmodifiableMap.size)

        expectThrows(UnsupportedOperationException::class) { unmodifiableMap.put(StringBuilder(NOT_IN_MAP), 3) }
        assertFalse(unmodifiableMap.containsKey(NOT_IN_MAP))
        assertNull(unmodifiableMap.get(NOT_IN_MAP))
        assertEquals(size, unmodifiableMap.size)

        expectThrows(UnsupportedOperationException::class) { unmodifiableMap.clear() }
        assertEquals(size, unmodifiableMap.size)

        expectThrows(UnsupportedOperationException::class) { unmodifiableMap.entries.clear() }
        assertEquals(size, unmodifiableMap.size)

        expectThrows(UnsupportedOperationException::class) { unmodifiableMap.keys.clear() }
        assertEquals(size, unmodifiableMap.size)

        expectThrows(UnsupportedOperationException::class) { unmodifiableMap.put(NOT_IN_MAP as Any, 3) }
        assertFalse(unmodifiableMap.containsKey(NOT_IN_MAP))
        assertNull(unmodifiableMap.get(NOT_IN_MAP))
        assertEquals(size, unmodifiableMap.size)

        expectThrows(UnsupportedOperationException::class) { unmodifiableMap.putAll(mapOf(NOT_IN_MAP to 3)) }
        assertFalse(unmodifiableMap.containsKey(NOT_IN_MAP))
        assertNull(unmodifiableMap.get(NOT_IN_MAP))
        assertEquals(size, unmodifiableMap.size)

        assertTrue(unmodifiableMap.containsKey("foo"))
        assertEquals(1, unmodifiableMap.get("foo"))
        assertTrue(unmodifiableMap.containsKey("bar"))
        assertEquals(2, unmodifiableMap.get("bar"))
    }

    @Test
    fun testToString() {
        val cm = CharArrayMap(mutableMapOf<Any, Int>("test" to 1), false)
        assertEquals("[test]", cm.keySet.toString())
        assertEquals("[1]", cm.values.toString())
        assertEquals("[test=1]", cm.entries.toString())
        assertEquals("{test=1}", cm.toString())
        cm.put("test2", 2)
        assertTrue(cm.keySet.toString().contains(", "))
        assertTrue(cm.values.toString().contains(", "))
        assertTrue(cm.entries.toString().contains(", "))
        assertTrue(cm.toString().contains(", "))
    }
}
