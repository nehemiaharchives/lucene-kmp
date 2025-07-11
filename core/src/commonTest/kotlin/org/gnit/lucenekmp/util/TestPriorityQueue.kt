package org.gnit.lucenekmp.util

import okio.ArrayIndexOutOfBoundsException
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.*
import kotlin.test.assertEquals

@Suppress("BoxedPrimitiveEquality")
class TestPriorityQueue : LuceneTestCase() {

    private open class IntegerQueue(count: Int) : PriorityQueue<Int>(count) {
        override fun lessThan(a: Int, b: Int): Boolean {
            return a < b
        }

        fun checkValidity() {
            @Suppress("UNCHECKED_CAST")
            val arr = heapArray as Array<Any?>
            for (i in 1..size()) {
                val parent = i ushr 1
                if (parent > 1) {
                    val parentVal = arr[parent] as Int
                    val childVal = arr[i] as Int
                    if (!lessThan(parentVal, childVal)) {
                        assertEquals(parentVal, childVal)
                    }
                }
            }
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
    fun testPQ() {
        testPQ(
            atLeast(10000),
            random()
        )
    }

    fun testPQ(count: Int, gen: Random) {
        val pq: PriorityQueue<Int> = IntegerQueue(count)
        var sum = 0
        var sum2 = 0

        for (i in 0..<count) {
            val next: Int = gen.nextInt()
            sum += next
            pq.add(next)
        }

        var last = Int.Companion.MIN_VALUE
        for (i in 0..<count) {
            val next: Int? = pq.pop()
            assertTrue(next!! >= last)
            last = next
            sum2 += last
        }

        assertEquals(sum.toLong(), sum2.toLong())
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
    fun testAddAllToEmptyQueue() {
        val random: Random = random()
        val size = 10
        val list: MutableList<Int> = ArrayList()
        for (i in 0..<size) {
            list.add(random.nextInt())
        }
        val pq = IntegerQueue(size)
        pq.addAll(list)

        pq.checkValidity()
        assertOrderedWhenDrained(pq, list)
    }

    @Test
    fun testAddAllToPartiallyFilledQueue() {
        val pq = IntegerQueue(20)
        val oneByOne: MutableList<Int> = ArrayList()
        val bulkAdded: MutableList<Int> = ArrayList()
        val random: Random = random()
        for (i in 0..9) {
            bulkAdded.add(random.nextInt())

            val x: Int = random.nextInt()
            pq.add(x)
            oneByOne.add(x)
        }

        pq.addAll(bulkAdded)
        pq.checkValidity()

        oneByOne.addAll(bulkAdded) // Gather all "reference" data.
        assertOrderedWhenDrained(pq, oneByOne)
    }

    @Test
    fun testAddAllDoesNotFitIntoQueue() {
        val pq = IntegerQueue(20)
        val list: MutableList<Int> = ArrayList()
        val random: Random = random()
        for (i in 0..10) {
            list.add(random.nextInt())
            pq.add(random.nextInt())
        }

        assertFailsWith(
            exceptionClass = ArrayIndexOutOfBoundsException::class,
            message = "Cannot add 11 elements to a queue with remaining capacity: 9",
        ) {
            pq.addAll(list)
        }
    }

    @Test
    fun testRemovalsAndInsertions() {
        val random: Random = random()
        val numDocsInPQ: Int = TestUtil.nextInt(random, 1, 100)
        val pq = IntegerQueue(numDocsInPQ)
        var lastLeast: Int? = null

        // Basic insertion of new content
        val sds: ArrayList<Int> = ArrayList(numDocsInPQ)
        for (i in 0..<numDocsInPQ * 10) {
            val newEntry: Int = abs(random.nextInt())
            sds.add(newEntry)
            val evicted: Int? = pq.insertWithOverflow(newEntry)
            pq.checkValidity()
            if (evicted != null) {
                sds.remove(evicted)
            }
            val newLeast: Int = pq.top()
            lastLeast = newLeast
        }

        // Try many random additions to existing entries - we should always see
        // increasing scores in the lowest entry in the PQ
        for (p in 0..499999) {
            val element = (random.nextFloat() * (sds.size - 1)).toInt()
            val objectToRemove: Int = sds[element]
            assertEquals(objectToRemove, sds.removeAt(element))
            pq.remove(objectToRemove)
            pq.checkValidity()
            val newEntry: Int = abs(random.nextInt())
            sds.add(newEntry)
            assertNull(pq.insertWithOverflow(newEntry))
            pq.checkValidity()
            val newLeast: Int = pq.top()
            lastLeast = newLeast
        }
    }

    @Test
    fun testIteratorEmpty() {
        val queue = IntegerQueue(3)

        val it = queue.iterator()
        assertFalse(it.hasNext())
        expectThrows(NoSuchElementException::class) {
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
        expectThrows(NoSuchElementException::class) {
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
        expectThrows(NoSuchElementException::class) {
            it.next()
        }
    }

    @Test
    fun testIteratorRandom() {
        val maxSize: Int =
            TestUtil.nextInt(random(), 1, 20)
        val queue = IntegerQueue(maxSize)
        val iters: Int = atLeast(100)
        val expected: MutableList<Int> = ArrayList()
        for (iter in 0..<iters) {
            if (queue.size() == 0 || (queue.size() < maxSize && random()
                    .nextBoolean())
            ) {
                val value: Int = random().nextInt(10)
                queue.add(value)
                expected.add(value)
            } else {
                expected.remove(queue.pop())
            }
            val actual: MutableList<Int> = ArrayList()
            for (value in queue) {
                actual.add(value)
            }
            CollectionUtil.introSort(expected)
            CollectionUtil.introSort(actual)
            assertEquals(expected, actual)
        }
    }

    @Test
    fun testMaxIntSize() {
        expectThrows(IllegalArgumentException::class) {
            object : PriorityQueue<Boolean>(Int.MAX_VALUE) {
                override fun lessThan(a: Boolean, b: Boolean): Boolean {
                    return true
                }
            }
        }
    }

    private fun assertOrderedWhenDrained(
        pq: IntegerQueue,
        referenceDataList: MutableList<Int>
    ) {
        referenceDataList.sort()
        var i = 0
        while (pq.size() > 0) {
            assertEquals(pq.pop(), referenceDataList[i])
            i++
        }
    }
}
