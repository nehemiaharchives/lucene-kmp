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
package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class TestLongHeap : LuceneTestCase() {

    private fun checkValidity(heap: LongHeap) {
        val heapArray = heap.heapArray
        for (i in 2..heap.size()) {
            val parent = i ushr 1
            assertTrue(heapArray[parent] <= heapArray[i])
        }
    }

    @Test
    fun testPQ() {
        testPQ(atLeast(10000), random())
    }

    private fun testPQ(count: Int, gen: Random) {
        val pq = LongHeap(count)
        var sum: Long = 0
        var sum2: Long = 0
        for (i in 0 until count) {
            val next = gen.nextLong()
            sum += next
            pq.push(next)
        }
        var last = Long.MIN_VALUE
        for (i in 0 until count) {
            val next = pq.pop()
            assertTrue(next >= last)
            last = next
            sum2 += last
        }
        assertEquals(sum, sum2)
    }

    @Test
    fun testClear() {
        val pq = LongHeap(3)
        pq.push(2)
        pq.push(3)
        pq.push(1)
        assertEquals(3, pq.size())
        pq.clear()
        assertEquals(0, pq.size())
    }

    @Test
    fun testExceedBounds() {
        val pq = LongHeap(1)
        pq.push(2)
        pq.push(0)
        assertEquals(2, pq.size())
        assertEquals(0, pq.top())
    }

    @Test
    fun testFixedSize() {
        val pq = LongHeap(3)
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
    fun testDuplicateValues() {
        val pq = LongHeap(3)
        pq.push(2)
        pq.push(3)
        pq.push(1)
        assertEquals(1, pq.top())
        pq.updateTop(3)
        assertEquals(3, pq.size())
        assertContentEquals(longArrayOf(0, 2, 3, 3), pq.heapArray)
    }

    @Test
    fun testInsertions() {
        val random = random()
        val numDocsInPQ = TestUtil.nextInt(random, 1, 100)
        val pq = LongHeap(numDocsInPQ)
        var lastLeast: Long? = null
        for (i in 0 until numDocsInPQ * 10) {
            val newEntry = kotlin.math.abs(random.nextLong())
            pq.insertWithOverflow(newEntry)
            checkValidity(pq)
            val newLeast = pq.top()
            if (lastLeast != null && newLeast != newEntry && newLeast != lastLeast) {
                assertTrue(newLeast <= newEntry)
                assertTrue(newLeast >= lastLeast!!)
            }
            lastLeast = newLeast
        }
    }

    @Test
    fun testInvalid() {
        expectThrows(
            IllegalArgumentException::class
        ) { LongHeap(-1) }
        expectThrows(
            IllegalArgumentException::class
        ) { LongHeap(0) }
        expectThrows(
            IllegalArgumentException::class
        ) { LongHeap(ArrayUtil.MAX_ARRAY_LENGTH) }
    }

    @Test
    fun testUnbounded() {
        val initialSize = random().nextInt(10) + 1
        val pq = LongHeap(initialSize)
        val num = random().nextInt(100) + 1
        var maxValue = Long.MIN_VALUE
        var count = 0
        for (i in 0 until num) {
            val value = random().nextLong()
            if (random().nextBoolean()) {
                pq.push(value)
                count++
            } else {
                val full = pq.size() >= initialSize
                if (pq.insertWithOverflow(value)) {
                    if (!full) {
                        count++
                    }
                }
            }
            maxValue = maxOf(maxValue, value)
        }
        assertEquals(count, pq.size())
        var last = Long.MIN_VALUE
        while (pq.size() > 0) {
            val top = pq.top()
            val next = pq.pop()
            assertEquals(top, next)
            --count
            assertTrue(next >= last)
            last = next
        }
        assertEquals(0, count)
        assertEquals(maxValue, last)
    }
}

