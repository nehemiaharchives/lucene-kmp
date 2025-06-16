/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnit.lucenekmp.internal.hppc

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.random.Random
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil

class TestIntIntHashMap : LuceneTestCase() {
    private val keyE = 0
    private val key1 = cast(1)
    private val key2 = cast(2)
    private val key3 = cast(3)
    private val key4 = cast(4)

    private val value0 = vcast(0)
    private val value1 = vcast(1)
    private val value2 = vcast(2)
    private val value3 = vcast(3)
    private val value4 = vcast(4)

    private lateinit var map: IntIntHashMap

    private fun cast(v: Int): Int = v
    private fun newArray(vararg elements: Int): IntArray = elements
    private fun vcast(v: Int): Int = v
    private fun newvArray(vararg elements: Int): IntArray = elements
    private fun newInstance() = IntIntHashMap()

    @BeforeTest
    fun initialize() {
        map = newInstance()
    }

    private fun assertSameMap(c1: IntIntHashMap, c2: IntIntHashMap) {
        assertEquals(c1.size(), c2.size())
        for (entry in c1) {
            assertTrue(c2.containsKey(entry.key))
            assertEquals(entry.value, c2.get(entry.key))
        }
    }

    private fun randomIntBetween(min: Int, max: Int): Int =
        min + random().nextInt(max + 1 - min)

    private fun rarely(): Boolean = TestUtil.rarely(random())

    @Test
    fun testEnsureCapacity() {
        val map = IntIntHashMap(0)
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
        map.put(keyE, value1)
        map.put(key1, value2)
        map.put(key2, value3)

        val idx0 = map.indexOf(keyE)
        val idx1 = map.indexOf(key1)
        val idx2 = map.indexOf(key2)

        assertTrue(map.indexExists(idx0))
        assertTrue(map.indexExists(idx1))
        assertTrue(map.indexExists(idx2))

        assertEquals(value1, map.indexGet(idx0))
        assertEquals(value2, map.indexGet(idx1))
        assertEquals(value3, map.indexGet(idx2))
    }

    @Test
    fun testIndexMethods() {
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

        expectThrows(IllegalArgumentException::class) {
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
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value3)

        val clone = IntIntHashMap()
        clone.put(key1, value1)
        clone.put(key2, value2)
        clone.put(key3, value3)

        assertEquals(map.size(), clone.size())
        assertEquals(map.get(key1), clone.get(key1))
        assertEquals(map.get(key2), clone.get(key2))
        assertEquals(map.get(key3), clone.get(key3))
    }

    @Test
    fun testFromArrays() {
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value3)

        val map2 = IntIntHashMap.from(newArray(key1, key2, key3), newvArray(value1, value2, value3))

        assertEquals(map.size(), map2.size())
        assertEquals(map.get(key1), map2.get(key1))
        assertEquals(map.get(key2), map2.get(key2))
        assertEquals(map.get(key3), map2.get(key3))
    }

    @Test
    fun testGetOrDefault() {
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
        map.put(key1, value1)

        assertTrue(map.containsKey(key1))
        assertEquals(value1, map.get(key1))
    }

    @Test
    fun testPutOverExistingKey() {
        map.put(key1, value1)
        assertEquals(value1, map.put(key1, value3))
        assertEquals(value3, map.get(key1))
    }

    @Test
    fun testPutWithExpansions() {
        val count = 10000
        val rnd = Random(random().nextLong())
        val values = HashSet<Int>()
        for (i in 0 until count) {
            val v = rnd.nextInt()
            val hadKey = values.contains(cast(v))
            values.add(cast(v))

            assertEquals(hadKey, map.containsKey(cast(v)))
            map.put(cast(v), vcast(v))
            assertEquals(values.size, map.size())
        }
        assertEquals(values.size, map.size())
    }

    @Test
    fun testPutAll() {
        map.put(key1, value1)
        map.put(key2, value1)

        val map2 = newInstance()
        map2.put(key2, value2)
        map2.put(keyE, value1)

        assertEquals(1, map.putAll(map2))
        assertEquals(value2, map.get(key2))
        assertEquals(value1, map.get(keyE))
        assertEquals(3, map.size())
    }

    @Test
    fun testPutIfAbsent() {
        assertTrue(map.putIfAbsent(key1, value1))
        assertFalse(map.putIfAbsent(key1, value2))
        assertEquals(value1, map.get(key1))
    }
}

