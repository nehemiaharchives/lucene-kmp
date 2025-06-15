package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.gnit.lucenekmp.jdkport.Arrays

class TestLongArrayList : LuceneTestCase() {
    private val key0 = cast(0)
    private val key1 = cast(1)
    private val key2 = cast(2)
    private val key3 = cast(3)
    private val key4 = cast(4)
    private val key5 = cast(5)
    private val key6 = cast(6)
    private val key7 = cast(7)

    private fun cast(v: Int): Long = v.toLong()

    private lateinit var list: LongArrayList

    @BeforeTest
    fun initialize() {
        list = LongArrayList()
    }


    @Test
    fun testInitiallyEmpty() {
        assertEquals(0, list.size())
    }

    @Test
    fun testAdd() {
        list.add(key1)
        list.add(key2)
        assertListEquals(list.toArray(), 1, 2)
    }

    @Test
    fun testAddTwoArgs() {
        list.add(key1)
        list.add(key2)
        list.add(key3)
        list.add(key4)
        assertListEquals(list.toArray(), 1, 2, 3, 4)
    }

    @Test
    fun testAddArray() {
        list.add(asArray(0, 1, 2, 3), 1, 2)
        assertListEquals(list.toArray(), 1, 2)
    }

    @Test
    fun testAddVarArg() {
        list.add(*asArray(0, 1, 2, 3))
        list.add(key4, key5, key6, key7)
        assertListEquals(list.toArray(), 0, 1, 2, 3, 4, 5, 6, 7)
    }

    @Test
    fun testAddAll() {
        val list2 = LongArrayList()
        list2.add(*asArray(0, 1, 2))
        list.addAll(list2)
        list.addAll(list2)
        assertListEquals(list.toArray(), 0, 1, 2, 0, 1, 2)
    }

    @Test
    fun testInsert() {
        list.insert(0, key1)
        list.insert(0, key2)
        list.insert(2, key3)
        list.insert(1, key4)
        assertListEquals(list.toArray(), 2, 4, 1, 3)
    }

    @Test
    fun testSet() {
        list.add(*asArray(0, 1, 2))
        assertEquals(0, list.set(0, key3))
        assertEquals(1, list.set(1, key4))
        assertEquals(2, list.set(2, key5))
        assertListEquals(list.toArray(), 3, 4, 5)
    }

    @Test
    fun testRemoveAt() {
        list.add(*asArray(0, 1, 2, 3, 4))
        list.removeAt(0)
        list.removeAt(2)
        list.removeAt(1)
        assertListEquals(list.toArray(), 1, 4)
    }

    @Test
    fun testRemoveLast() {
        list.add(*asArray(0, 1, 2, 3, 4))
        assertEquals(4, list.removeLast())
        assertEquals(4, list.size())
        assertListEquals(list.toArray(), 0, 1, 2, 3)
        assertEquals(3, list.removeLast())
        assertEquals(3, list.size())
        assertListEquals(list.toArray(), 0, 1, 2)
        assertEquals(2, list.removeLast())
        assertEquals(1, list.removeLast())
        assertEquals(0, list.removeLast())
        assertTrue(list.isEmpty)
    }

    @Test
    fun testRemoveElement() {
        list.add(*asArray(0, 1, 2, 3, 3, 4))
        assertTrue(list.removeElement(3))
        assertTrue(list.removeElement(2))
        assertFalse(list.removeElement(5))
        assertListEquals(list.toArray(), 0, 1, 3, 4)
    }

    @Test
    fun testRemoveRange() {
        list.add(*asArray(0, 1, 2, 3, 4))
        list.removeRange(0, 2)
        assertListEquals(list.toArray(), 2, 3, 4)
        list.removeRange(2, 3)
        assertListEquals(list.toArray(), 2, 3)
        list.removeRange(1, 1)
        assertListEquals(list.toArray(), 2, 3)
        list.removeRange(0, 1)
        assertListEquals(list.toArray(), 3)
    }

    @Test
    fun testRemoveFirstLast() {
        list.add(*asArray(0, 1, 2, 1, 0))
        assertEquals(-1, list.removeFirst(key5))
        assertEquals(-1, list.removeLast(key5))
        assertListEquals(list.toArray(), 0, 1, 2, 1, 0)
        assertEquals(1, list.removeFirst(key1))
        assertListEquals(list.toArray(), 0, 2, 1, 0)
        assertEquals(3, list.removeLast(key0))
        assertListEquals(list.toArray(), 0, 2, 1)
        assertEquals(0, list.removeLast(key0))
        assertListEquals(list.toArray(), 2, 1)
        assertEquals(-1, list.removeLast(key0))
    }

    @Test
    fun testRemoveAll() {
        list.add(*asArray(0, 1, 0, 1, 0))
        assertEquals(0, list.removeAll(key2))
        assertEquals(3, list.removeAll(key0))
        assertListEquals(list.toArray(), 1, 1)
        assertEquals(2, list.removeAll(key1))
        assertTrue(list.isEmpty)
    }

    @Test
    fun testIndexOf() {
        list.add(*asArray(0, 1, 2, 1, 0))
        assertEquals(0, list.indexOf(key0))
        assertEquals(-1, list.indexOf(key3))
        assertEquals(2, list.indexOf(key2))
    }

    @Test
    fun testLastIndexOf() {
        list.add(*asArray(0, 1, 2, 1, 0))
        assertEquals(4, list.lastIndexOf(key0))
        assertEquals(-1, list.lastIndexOf(key3))
        assertEquals(2, list.lastIndexOf(key2))
    }

    @Test
    fun testEnsureCapacity() {
        val l = LongArrayList(0)
        assertEquals(l.size(), l.buffer.size)
        val buffer1 = l.buffer
        l.ensureCapacity(100)
        assertTrue(buffer1 !== l.buffer)
    }

    @Test
    fun testResizeAndCleanBuffer() {
        list.ensureCapacity(20)
        Arrays.fill(list.buffer, key1)
        list.resize(10)
        assertEquals(10, list.size())
        for (i in 0 until list.size()) {
            assertEquals(0, list.get(i))
        }
        Arrays.fill(list.buffer, 0)
        for (i in 5 until list.size()) {
            list.set(i, key1)
        }
        list.resize(5)
        assertEquals(5, list.size())
        for (i in list.size() until list.buffer.size) {
            assertEquals(0, list.buffer[i])
        }
    }

    @Test
    fun testTrimToSize() {
        list.add(*asArray(1, 2))
        list.trimToSize()
        assertEquals(2, list.buffer.size)
    }

    @Test
    fun testRelease() {
        list.add(*asArray(1, 2))
        list.release()
        assertEquals(0, list.size())
        list.add(*asArray(1, 2))
        assertEquals(2, list.size())
    }

    @Test
    fun testIterable() {
        list.add(*asArray(0, 1, 2, 3))
        var count = 0
        for (cursor in list) {
            count++
            assertEquals(list.get(cursor.index), cursor.value)
            assertEquals(list.buffer[cursor.index], cursor.value)
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
        list.add(*asArray(0, 1, 2, 3))
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
        list.add(*asArray(1, 2, 3))
        list.clear()
        assertTrue(list.isEmpty)
        assertEquals(-1, list.indexOf(cast(1)))
    }

    @Test
    fun testFrom() {
        list = LongArrayList.from(key1, key2, key3)
        assertEquals(3, list.size())
        assertListEquals(list.toArray(), 1, 2, 3)
        assertEquals(list.size(), list.buffer.size)
    }

    @Test
    fun testCopyList() {
        list.add(*asArray(1, 2, 3))
        val copy = LongArrayList(list)
        assertEquals(3, copy.size())
        assertListEquals(copy.toArray(), 1, 2, 3)
        assertEquals(copy.size(), copy.buffer.size)
    }

    @Test
    fun testHashCodeEquals() {
        val l0 = LongArrayList.from()
        assertEquals(1, l0.hashCode())
        assertEquals(l0, LongArrayList.from())
        val l1 = LongArrayList.from(key1, key2, key3)
        val l2 = LongArrayList.from(key1, key2)
        l2.add(key3)
        assertEquals(l1.hashCode(), l2.hashCode())
        assertEquals(l1, l2)
    }

    @Test
    fun testEqualElements() {
        class Access : LongArrayList() {
            fun eq(other: LongArrayList) = equalElements(other)
        }

        val l1 = LongArrayList.from(key1, key2, key3)
        val l2 = Access()
        l2.addAll(l1)
        assertEquals(l1.hashCode(), l2.hashCode())
        assertTrue(l2.eq(l1))
    }

    @Test
    fun testToArray() {
        val l1 = LongArrayList.from(key1, key2, key3)
        l1.ensureCapacity(100)
        val result = l1.toArray()
        assertTrue(result.contentEquals(longArrayOf(key1, key2, key3)))
    }

    @Test
    fun testClone() {
        list.add(key1)
        list.add(key2)
        list.add(key3)
        val cloned = list.clone()
        cloned.removeAt(cloned.indexOf(key1))
        assertSortedListEquals(list.toArray(), key1, key2, key3)
        assertSortedListEquals(cloned.toArray(), key2, key3)
    }

    @Test
    fun testToString() {
        assertEquals("[" + key1 + ", " + key2 + ", " + key3 + "]", LongArrayList.from(key1, key2, key3).toString())
    }

    @Test
    fun testEqualsSameClass() {
        val l1 = LongArrayList.from(key1, key2, key3)
        val l2 = LongArrayList.from(key1, key2, key3)
        val l3 = LongArrayList.from(key1, key3, key2)
        assertEquals(l1, l2)
        assertEquals(l1.hashCode(), l2.hashCode())
        assertFalse(l1 == l3)
    }

    @Test
    fun testEqualsSubClass() {
        class Sub : LongArrayList()
        val l1 = LongArrayList.from(key1, key2, key3)
        val l2: LongArrayList = Sub()
        val l3: LongArrayList = Sub()
        l2.addAll(l1)
        l3.addAll(l1)
        assertEquals(l2, l3)
        assertFalse(l1 == l3)
    }

    @Test
    fun testStream() {
        assertEquals(key1, LongArrayList.from(key1, key2, key3).asSequence().minOrNull())
        assertEquals(key3, LongArrayList.from(key2, key1, key3).asSequence().maxOrNull())
        assertEquals(0, LongArrayList.from(key1, key2, -key3).asSequence().sum())
        assertFailsWith<NoSuchElementException> {
            LongArrayList.from().asSequence().minOrNull() ?: throw NoSuchElementException()
        }
    }

    @Test
    fun testSort() {
        list.add(key3)
        list.add(key1)
        list.add(key3)
        list.add(key2)
        val list2 = LongArrayList()
        list2.ensureCapacity(100)
        list2.addAll(list)
        assertEquals(list2, list2.sort())
        assertEquals(LongArrayList.from(key1, key2, key3, key3), list2)
    }

    @Test
    fun testReverse() {
        for (i in 0 until 10) {
            val elements = LongArray(i) { cast(it) }
            val l = LongArrayList()
            l.ensureCapacity(30)
            l.add(*elements)
            assertEquals(l, l.reverse())
            assertEquals(elements.size, l.size())
            var reverseIndex = elements.size - 1
            for (cursor in l) {
                assertEquals(elements[reverseIndex--], cursor.value)
            }
        }
    }

    private fun assertListEquals(array: LongArray, vararg elements: Long) {
        assertEquals(elements.size, array.size)
        assertTrue(array.contentEquals(elements))
    }

    private fun asArray(vararg elements: Long): LongArray = elements

    private fun assertSortedListEquals(array: LongArray, vararg elements: Long) {
        assertEquals(elements.size, array.size)
        val sortedArray = array.copyOf()
        val sortedElements = elements.copyOf()
        Arrays.sort(sortedArray)
        Arrays.sort(sortedElements)
        assertTrue(sortedArray.contentEquals(sortedElements))
    }
}
