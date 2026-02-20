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

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.*

/**
 * Tests for [FloatArrayList].
 *
 * Mostly forked and trimmed from com.carrotsearch.hppc.FloatArrayListTest
 *
 * github: https://github.com/carrotsearch/hppc release: 0.9.0
 */
class TestFloatArrayList : LuceneTestCase() {
    private val key0 = cast(0)
    private val key1 = cast(1)
    private val key2 = cast(2)
    private val key3 = cast(3)
    private val key4 = cast(4)
    private val key5 = cast(5)
    private val key6 = cast(6)
    private val key7 = cast(7)

    /** Convert to target type from an integer used to test stuff. */
    private fun cast(v: Int): Float = v.toFloat()

    /** Per-test fresh initialized instance. */
    private lateinit var list: FloatArrayList

    @BeforeTest
    fun initialize() {
        list = FloatArrayList()
    }

    @Test
    fun testInitiallyEmpty() {
        assertEquals(0, list.size())
    }

    @Test
    fun testAdd() {
        list.add(key1, key2)
        assertListEquals(list.toArray(), 1f, 2f)
    }

    @Test
    fun testAddTwoArgs() {
        list.add(key1, key2)
        list.add(key3, key4)
        assertListEquals(list.toArray(), 1f, 2f, 3f, 4f)
    }

    @Test
    fun testAddArray() {
        list.add(asArray(0f, 1f, 2f, 3f), 1, 2)
        assertListEquals(list.toArray(), 1f, 2f)
    }

    @Test
    fun testAddVarArg() {
        list.add(*asArray(0f, 1f, 2f, 3f))
        list.add(key4, key5, key6, key7)
        assertListEquals(list.toArray(), 0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
    }

    @Test
    fun testAddAll() {
        val list2 = FloatArrayList()
        list2.add(*asArray(0f, 1f, 2f))

        list.addAll(list2)
        list.addAll(list2)

        assertListEquals(list.toArray(), 0f, 1f, 2f, 0f, 1f, 2f)
    }

    @Test
    fun testInsert() {
        list.insert(0, key1)
        list.insert(0, key2)
        list.insert(2, key3)
        list.insert(1, key4)

        assertListEquals(list.toArray(), 2f, 4f, 1f, 3f)
    }

    @Test
    fun testSet() {
        list.add(*asArray(0f, 1f, 2f))

        assertFloatEquals(0f, list.set(0, key3))
        assertFloatEquals(1f, list.set(1, key4))
        assertFloatEquals(2f, list.set(2, key5))

        assertListEquals(list.toArray(), 3f, 4f, 5f)
    }

    @Test
    fun testRemoveAt() {
        list.add(*asArray(0f, 1f, 2f, 3f, 4f))

        list.removeAt(0)
        list.removeAt(2)
        list.removeAt(1)

        assertListEquals(list.toArray(), 1f, 4f)
    }

    @Test
    fun testRemoveLast() {
        list.add(*asArray(0f, 1f, 2f, 3f, 4f))

        assertFloatEquals(4f, list.removeLast())
        assertEquals(4, list.size())
        assertListEquals(list.toArray(), 0f, 1f, 2f, 3f)
        assertFloatEquals(3f, list.removeLast())
        assertEquals(3, list.size())
        assertListEquals(list.toArray(), 0f, 1f, 2f)
        assertFloatEquals(2f, list.removeLast())
        assertFloatEquals(1f, list.removeLast())
        assertFloatEquals(0f, list.removeLast())
        assertTrue(list.isEmpty)
    }

    @Test
    fun testRemoveElement() {
        list.add(*asArray(0f, 1f, 2f, 3f, 3f, 4f))

        assertTrue(list.removeElement(3f))
        assertTrue(list.removeElement(2f))
        assertFalse(list.removeElement(5f))

        assertListEquals(list.toArray(), 0f, 1f, 3f, 4f)
    }

    @Test
    fun testRemoveRange() {
        list.add(*asArray(0f, 1f, 2f, 3f, 4f))

        list.removeRange(0, 2)
        assertListEquals(list.toArray(), 2f, 3f, 4f)

        list.removeRange(2, 3)
        assertListEquals(list.toArray(), 2f, 3f)

        list.removeRange(1, 1)
        assertListEquals(list.toArray(), 2f, 3f)

        list.removeRange(0, 1)
        assertListEquals(list.toArray(), 3f)
    }

    @Test
    fun testRemoveFirstLast() {
        list.add(*asArray(0f, 1f, 2f, 1f, 0f))

        assertEquals(-1, list.removeFirst(key5))
        assertEquals(-1, list.removeLast(key5))
        assertListEquals(list.toArray(), 0f, 1f, 2f, 1f, 0f)

        assertEquals(1, list.removeFirst(key1))
        assertListEquals(list.toArray(), 0f, 2f, 1f, 0f)
        assertEquals(3, list.removeLast(key0))
        assertListEquals(list.toArray(), 0f, 2f, 1f)
        assertEquals(0, list.removeLast(key0))
        assertListEquals(list.toArray(), 2f, 1f)
        assertEquals(-1, list.removeLast(key0))
    }

    @Test
    fun testRemoveAll() {
        list.add(*asArray(0f, 1f, 0f, 1f, 0f))

        assertEquals(0, list.removeAll(key2))
        assertEquals(3, list.removeAll(key0))
        assertListEquals(list.toArray(), 1f, 1f)

        assertEquals(2, list.removeAll(key1))
        assertTrue(list.isEmpty)
    }

    @Test
    fun testIndexOf() {
        list.add(*asArray(0f, 1f, 2f, 1f, 0f))

        assertEquals(0, list.indexOf(key0))
        assertEquals(-1, list.indexOf(key3))
        assertEquals(2, list.indexOf(key2))
    }

    @Test
    fun testLastIndexOf() {
        list.add(*asArray(0f, 1f, 2f, 1f, 0f))

        assertEquals(4, list.lastIndexOf(key0))
        assertEquals(-1, list.lastIndexOf(key3))
        assertEquals(2, list.lastIndexOf(key2))
    }

    @Test
    fun testEnsureCapacity() {
        val list = FloatArrayList(0)
        assertEquals(list.size(), list.buffer.size)
        val buffer1 = list.buffer
        list.ensureCapacity(100)
        assertTrue(buffer1 !== list.buffer)
    }

    @Test
    fun testResizeAndCleanBuffer() {
        list.ensureCapacity(20)
        list.buffer.fill(key1)

        list.resize(10)
        assertEquals(10, list.size())
        for (i in 0 until list.size()) {
            assertFloatEquals(0f, list.get(i))
        }

        list.buffer.fill(0f)
        for (i in 5 until list.size()) {
            list.set(i, key1)
        }
        list.resize(5)
        assertEquals(5, list.size())
        for (i in list.size() until list.buffer.size) {
            assertFloatEquals(0f, list.buffer[i])
        }
    }

    @Test
    fun testTrimToSize() {
        list.add(*asArray(1f, 2f))
        list.trimToSize()
        assertEquals(2, list.buffer.size)
    }

    @Test
    fun testRelease() {
        list.add(*asArray(1f, 2f))
        list.release()
        assertEquals(0, list.size())
        list.add(*asArray(1f, 2f))
        assertEquals(2, list.size())
    }

    @Test
    fun testIterable() {
        list.add(*asArray(0f, 1f, 2f, 3f))
        var count = 0
        for (cursor in list) {
            count++
            assertFloatEquals(list.get(cursor.index), cursor.value)
            assertFloatEquals(list.buffer[cursor.index], cursor.value)
        }
        assertEquals(count, list.size())

        count = 0
        list.resize(0)
        for (cursor in list) {
            count++
        }
        assertEquals(0, count)
    }

    @Test
    fun testIterator() {
        list.add(*asArray(0f, 1f, 2f, 3f))
        val iterator = list.iterator()
        var count = 0
        while (iterator.hasNext()) {
            iterator.hasNext()
            iterator.hasNext()
            iterator.hasNext()
            iterator.next()
            count++
        }
        assertEquals(count, list.size())

        list.resize(0)
        assertFalse(list.iterator().hasNext())
    }

    @Test
    fun testClear() {
        list.add(*asArray(1f, 2f, 3f))
        list.clear()
        assertTrue(list.isEmpty)
        assertEquals(-1, list.indexOf(cast(1)))
    }

    @Test
    fun testFrom() {
        list = FloatArrayList.from(key1, key2, key3)
        assertEquals(3, list.size())
        assertListEquals(list.toArray(), 1f, 2f, 3f)
        assertEquals(list.size(), list.buffer.size)
    }

    @Test
    fun testCopyList() {
        list.add(*asArray(1f, 2f, 3f))
        val copy = FloatArrayList(list)
        assertEquals(3, copy.size())
        assertListEquals(copy.toArray(), 1f, 2f, 3f)
        assertEquals(copy.size(), copy.buffer.size)
    }

    @Test
    fun testHashCodeEquals() {
        val l0 = FloatArrayList.from()
        assertEquals(1, l0.hashCode())
        assertEquals(l0, FloatArrayList.from())

        val l1 = FloatArrayList.from(key1, key2, key3)
        val l2 = FloatArrayList.from(key1, key2)
        l2.add(key3)

        assertEquals(l1.hashCode(), l2.hashCode())
        assertEquals(l1, l2)
    }

    @Test
    fun testEqualElements() {
        val l1 = FloatArrayList.from(key1, key2, key3)
        val l2 = FloatArrayList.from(key1, key2)
        l2.add(key3)

        assertEquals(l1.hashCode(), l2.hashCode())
        assertTrue(l2.equalElements(l1))
    }

    @Test
    fun testToArray() {
        val l1 = FloatArrayList.from(key1, key2, key3)
        l1.ensureCapacity(100)
        val result = l1.toArray()
        assertArrayEquals(floatArrayOf(key1, key2, key3), result)
    }

    @Test
    fun testClone() {
        list.add(key1, key2, key3)

        val cloned = list.clone()
        cloned.removeAt(cloned.indexOf(key1))

        assertSortedListEquals(list.toArray(), key1, key2, key3)
        assertSortedListEquals(cloned.toArray(), key2, key3)
    }

    @Test
    fun testToString() {
        assertEquals("[$key1, $key2, $key3]", FloatArrayList.from(key1, key2, key3).toString())
    }

    @Test
    fun testEqualsSameClass() {
        val l1 = FloatArrayList.from(key1, key2, key3)
        val l2 = FloatArrayList.from(key1, key2, key3)
        val l3 = FloatArrayList.from(key1, key3, key2)

        assertEquals(l1, l2)
        assertEquals(l1.hashCode(), l2.hashCode())
        assertNotEquals(l1, l3)
    }

    @Test
    fun testEqualsSubClass() {
        class Sub : FloatArrayList()

        val l1 = FloatArrayList.from(key1, key2, key3)
        val l2: FloatArrayList = Sub()
        val l3: FloatArrayList = Sub()
        l2.addAll(l1)
        l3.addAll(l1)

        assertEquals(l2, l3)
        assertNotEquals(l1, l3)
    }

    @Test
    fun testSort() {
        list.add(key3, key1, key3, key2)
        val list2 = FloatArrayList()
        list2.ensureCapacity(100)
        list2.addAll(list)
        assertSame(list2, list2.sort())
        assertEquals(FloatArrayList.from(key1, key2, key3, key3), list2)
    }

    @Test
    fun testReverse() {
        for (i in 0 until 10) {
            val elements = FloatArray(i) { cast(it) }
            val list = FloatArrayList()
            list.ensureCapacity(30)
            list.add(*elements)
            assertSame(list, list.reverse())
            assertEquals(elements.size, list.size())
            var reverseIndex = elements.size - 1
            for (cursor in list) {
                assertFloatEquals(elements[reverseIndex--], cursor.value)
            }
        }
    }

    /** Check if the array's content is identical to a given sequence of elements. */
    private fun assertListEquals(array: FloatArray, vararg elements: Float) {
        assertEquals(elements.size, array.size)
        assertArrayEquals(elements, array)
    }

    private fun asArray(vararg elements: Float): FloatArray {
        return elements
    }

    /** Check if the array's content is identical to a given sequence of elements. */
    private fun assertSortedListEquals(array: FloatArray, vararg elements: Float) {
        assertEquals(elements.size, array.size)
        array.sort()
        val sortedElements = elements.copyOf()
        sortedElements.sort()
        assertArrayEquals(sortedElements, array)
    }

    private fun assertFloatEquals(f1: Float, f2: Float) {
        assertEquals(f1, f2, absoluteTolerance = 0f)
    }

    private fun assertArrayEquals(f1: FloatArray, f2: FloatArray) {
        assertEquals(f1.size, f2.size)
        for (i in f1.indices) {
            assertFloatEquals(f1[i], f2[i])
        }
    }
}
