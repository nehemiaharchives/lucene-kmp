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
package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestMultiset : LuceneTestCase() {
    @Test
    fun testDuplicatesMatter() {
        val s1 = Multiset<Int>()
        val s2 = Multiset<Int>()
        assertEquals(s1.size, s2.size)
        assertEquals(s1, s2)

        assertTrue(s1.add(42))
        assertTrue(s2.add(42))
        assertEquals(s1, s2)

        s2.add(42)
        assertFalse(s1 == s2)

        s1.add(43)
        s1.add(43)
        s2.add(43)
        assertEquals(s1.size, s2.size)
        assertFalse(s1 == s2)
    }

    private fun <T> toCountMap(set: Multiset<T>): MutableMap<T, Int> {
        val map = HashMap<T, Int>()
        var recomputedSize = 0
        for (element in set) {
            add(map, element)
            recomputedSize += 1
        }
        assertEquals(recomputedSize, set.size, set.toString())
        return map
    }

    private fun <T> add(map: MutableMap<T, Int>, element: T) {
        map[element] = (map[element] ?: 0) + 1
    }

    private fun <T> remove(map: MutableMap<T, Int>, element: T) {
        val count = map[element]
        if (count == null) {
            return
        } else if (count == 1) {
            map.remove(element)
        } else {
            map[element] = count - 1
        }
    }

    @Test
    fun testRandom() {
        val reference = HashMap<Int, Int>()
        val multiset = Multiset<Int>()
        val iters = atLeast(100)
        for (i in 0 until iters) {
            val value = random().nextInt(10)
            when (random().nextInt(10)) {
                0, 1, 2 -> {
                    remove(reference, value)
                    multiset.remove(value)
                }

                3 -> {
                    reference.clear()
                    multiset.clear()
                }

                else -> {
                    add(reference, value)
                    multiset.add(value)
                }
            }
            assertEquals(reference, toCountMap(multiset))
        }
    }
}
