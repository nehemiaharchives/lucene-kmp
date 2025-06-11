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
import org.gnit.lucenekmp.tests.util.RamUsageTester
import kotlin.test.Test
import kotlin.test.assertEquals

class TestFrequencyTrackingRingBuffer : LuceneTestCase() {

    private fun assertBuffer(
        buffer: FrequencyTrackingRingBuffer,
        maxSize: Int,
        sentinel: Int,
        items: List<Int>
    ) {
        val recentItems: List<Int> = if (items.size <= maxSize) {
            val list = ArrayList<Int>()
            for (i in items.size until maxSize) {
                list.add(sentinel)
            }
            list.addAll(items)
            list
        } else {
            items.subList(items.size - maxSize, items.size)
        }
        val expectedFrequencies = HashMap<Int, Int>()
        for (item in recentItems) {
            expectedFrequencies[item] = (expectedFrequencies[item] ?: 0) + 1
        }
        assertEquals(expectedFrequencies, buffer.asFrequencyMap())
    }

    @Test
    fun testBasic() {
        val iterations = atLeast(100)
        for (i in 0 until iterations) {
            val maxSize = 2 + random().nextInt(100)
            val numItems = random().nextInt(5000)
            val maxItem = 1 + random().nextInt(100)
            val items = ArrayList<Int>()
            val sentinel = random().nextInt(200)
            val buffer = FrequencyTrackingRingBuffer(maxSize, sentinel)
            for (j in 0 until numItems) {
                val item = random().nextInt(maxItem)
                items.add(item)
                buffer.add(item)
            }
            assertBuffer(buffer, maxSize, sentinel, items)
        }
    }

    @Test
    fun testRamBytesUsed() {
        val maxSize = 2 + random().nextInt(10000)
        val sentinel = random().nextInt()
        val buffer = FrequencyTrackingRingBuffer(maxSize, sentinel)
        for (i in 0 until 10000) {
            buffer.add(random().nextInt())
        }
        assertEquals(RamUsageTester.ramUsed(buffer), buffer.ramBytesUsed())
    }
}
