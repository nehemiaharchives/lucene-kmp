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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gnit.lucenekmp.tests.util.LuceneTestCase

/** */
class TestSentinelIntSet : LuceneTestCase() {

    @Test
    fun test() {
        val set = SentinelIntSet(10, -1)
        assertFalse(set.exists(50))
        set.put(50)
        assertTrue(set.exists(50))
        assertEquals(1, set.size())
        assertEquals(-11, set.find(10))
        assertEquals(1, set.size())
        set.clear()
        assertEquals(0, set.size())
        assertEquals(50, set.hash(50))
        // force a rehash
        for (i in 0 until 20) {
            set.put(i)
        }
        assertEquals(20, set.size())
        assertEquals(24, set.rehashCount)
    }

    @Test
    fun testRandom() {
        for (i in 0 until 10000) {
            val initSz = random().nextInt(20)
            val num = random().nextInt(30)
            val maxVal =
                (if (random().nextBoolean()) random().nextInt(50) else random().nextInt(Int.MAX_VALUE)) + 1

            val a = CollectionUtil.newHashSet<Int>(initSz)
            val b = SentinelIntSet(initSz, -1)

            for (j in 0 until num) {
                val `val` = random().nextInt(maxVal)
                val exists = !a.add(`val`)
                val existsB = b.exists(`val`)
                assertEquals(exists, existsB)
                val slot = b.find(`val`)
                assertEquals(exists, slot >= 0)
                b.put(`val`)

                assertEquals(a.size, b.size())
            }
        }
    }
}
