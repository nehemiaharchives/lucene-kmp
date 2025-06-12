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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestMergedIterator : LuceneTestCase() {

    companion object {
        private const val REPEATS = 2
        private const val VALS_TO_MERGE = 15000
    }

    @Test
    fun testMergeEmpty() {
        var merged = MergedIterator<Int>()
        assertFalse(merged.hasNext())

        merged = MergedIterator(mutableListOf<Int>().iterator())
        assertFalse(merged.hasNext())

        val itrs = Array(random().nextInt(100)) { mutableListOf<Int>().iterator() }
        merged = MergedIterator(*itrs)
        assertFalse(merged.hasNext())
    }

    @Test
    fun testNoDupsRemoveDups() {
        repeat(REPEATS) { testCase(1, 1, true) }
    }

    @Test
    fun testOffItrDupsRemoveDups() {
        repeat(REPEATS) { testCase(3, 1, true) }
    }

    @Test
    fun testOnItrDupsRemoveDups() {
        repeat(REPEATS) { testCase(1, 3, true) }
    }

    @Test
    fun testOnItrRandomDupsRemoveDups() {
        repeat(REPEATS) { testCase(1, -3, true) }
    }

    @Test
    fun testBothDupsRemoveDups() {
        repeat(REPEATS) { testCase(3, 3, true) }
    }

    @Test
    fun testBothDupsWithRandomDupsRemoveDups() {
        repeat(REPEATS) { testCase(3, -3, true) }
    }

    @Test
    fun testNoDupsKeepDups() {
        repeat(REPEATS) { testCase(1, 1, false) }
    }

    @Test
    fun testOffItrDupsKeepDups() {
        repeat(REPEATS) { testCase(3, 1, false) }
    }

    @Test
    fun testOnItrDupsKeepDups() {
        repeat(REPEATS) { testCase(1, 3, false) }
    }

    @Test
    fun testOnItrRandomDupsKeepDups() {
        repeat(REPEATS) { testCase(1, -3, false) }
    }

    @Test
    fun testBothDupsKeepDups() {
        repeat(REPEATS) { testCase(3, 3, false) }
    }

    @Test
    fun testBothDupsWithRandomDupsKeepDups() {
        repeat(REPEATS) { testCase(3, -3, false) }
    }

    private fun testCase(itrsWithVal: Int, specifiedValsOnItr: Int, removeDups: Boolean) {
        val expected = ArrayList<Int>()
        val rnd = kotlin.random.Random(random().nextLong())
        val numLists = itrsWithVal + rnd.nextInt(1000 - itrsWithVal)
        val lists = Array(numLists) { mutableListOf<Int>() }
        val start = rnd.nextInt(1_000_000)
        val end = start + VALS_TO_MERGE / itrsWithVal / kotlin.math.abs(specifiedValsOnItr)
        for (i in start until end) {
            var maxList = lists.size
            var maxValsOnItr = 0
            var sumValsOnItr = 0
            for (itrWithVal in 0 until itrsWithVal) {
                val list = rnd.nextInt(maxList)
                val valsOnItr = if (specifiedValsOnItr < 0) (1 + rnd.nextInt(-specifiedValsOnItr)) else specifiedValsOnItr
                maxValsOnItr = kotlin.math.max(maxValsOnItr, valsOnItr)
                sumValsOnItr += valsOnItr
                repeat(valsOnItr) { lists[list].add(i) }
                maxList--
                ArrayUtil.swap(lists, list, maxList)
            }
            val maxCount = if (removeDups) maxValsOnItr else sumValsOnItr
            repeat(maxCount) { expected.add(i) }
        }

        val itrs = Array(numLists) { lists[it].iterator() }
        val mergedItr = MergedIterator(removeDups, itrs)
        val expectedItr = expected.iterator()
        while (expectedItr.hasNext()) {
            assertTrue(mergedItr.hasNext())
            assertEquals(expectedItr.next(), mergedItr.next())
        }
        assertFalse(mergedItr.hasNext())
    }
}
