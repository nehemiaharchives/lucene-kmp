package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("BoxedPrimitiveEquality")
class TestPriorityQueue : LuceneTestCase() {

    private class IntegerQueue(count: Int) : PriorityQueue<Int>(count) {
        override fun lessThan(a: Int, b: Int): Boolean {
            return a < b
        }

    }

    @Test
    fun testZeroSizedQueue() {
        val pq: PriorityQueue<Int> = IntegerQueue(0)
        assertEquals(1, pq.insertWithOverflow(1))
        assertEquals(0, pq.size())

        // should fail, but passes and modifies the top...
        pq.add(1)
        assertEquals(1, pq.top())
    }

    @Test
    fun testNoExtraWorkOnEqualElements() {
        class Value(val index: Int, val value: Int)

        val pq: PriorityQueue<Value> = object : PriorityQueue<Value>(5) {
            override fun lessThan(a: Value, b: Value): Boolean {
                return a.value < b.value
            }
        }

        // Make all elements equal but record insertion order.
        for (i in 0 until 100) {
            pq.insertWithOverflow(Value(i, 0))
        }

        val indexes = ArrayList<Int>()
        for (e in pq) {
            indexes.add(e.index)
        }

        val expected = setOf(0, 1, 2, 3, 4)
        assertEquals(expected, indexes.toSet())
    }


    @Test
    fun testClear() {
        val pq = IntegerQueue(3)
        pq.add(2)
        pq.add(3)
        pq.add(1)
        assertEquals(3, pq.size())
        pq.clear()
        assertEquals(0, pq.size())
    }

    @Test
    fun testFixedSize() {
        val pq = IntegerQueue(3)
        pq.insertWithOverflow(2)
        pq.insertWithOverflow(3)
        pq.insertWithOverflow(1)
        pq.insertWithOverflow(5)
        pq.insertWithOverflow(7)
        pq.insertWithOverflow(1)
        assertEquals(3, pq.size())
        assertEquals(3, pq.top())
    }

    @Test
    fun testInsertWithOverflow() {
        val size = 4
        val pq = IntegerQueue(size)
        val i1 = 2
        val i2 = 3
        val i3 = 1
        val i4 = 5
        val i5 = 7
        val i6 = 1

        assertNull(pq.insertWithOverflow(i1))
        assertNull(pq.insertWithOverflow(i2))
        assertNull(pq.insertWithOverflow(i3))
        assertNull(pq.insertWithOverflow(i4))
        assertTrue(pq.insertWithOverflow(i5) === i3)
        assertTrue(pq.insertWithOverflow(i6) === i6)
        assertEquals(size, pq.size())
        assertEquals(2, pq.top())
    }

    @Test
    fun testIteratorEmpty() {
        val queue = IntegerQueue(3)

        val it = queue.iterator()
        assertFalse(it.hasNext())
        expectThrows<NoSuchElementException>(NoSuchElementException::class) {
            it.next()
        }
    }

    @Test
    fun testIteratorOne() {
        val queue = IntegerQueue(3)

        queue.add(1)
        val it = queue.iterator()
        assertTrue(it.hasNext())
        assertEquals(1, it.next())
        assertFalse(it.hasNext())
        expectThrows<NoSuchElementException>(NoSuchElementException::class) {
            it.next()
        }
    }

    @Test
    fun testIteratorTwo() {
        val queue = IntegerQueue(3)

        queue.add(1)
        queue.add(2)
        val it = queue.iterator()
        assertTrue(it.hasNext())
        assertEquals(1, it.next())
        assertTrue(it.hasNext())
        assertEquals(2, it.next())
        assertFalse(it.hasNext())
        expectThrows<NoSuchElementException>(NoSuchElementException::class) {
            it.next()
        }
    }


    @Test
    fun testMaxIntSize() {
        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            object : PriorityQueue<Boolean>(Int.MAX_VALUE) {
                override fun lessThan(a: Boolean, b: Boolean): Boolean {
                    return true
                }
            }
        }
    }

}

