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

package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.index.NumericDocValuesFieldUpdates.SingleValueNumericDocValuesFieldUpdates
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestDocValuesFieldUpdates : LuceneTestCase() {
    @Test
    fun testMergeIterator() {
        val updates1 = NumericDocValuesFieldUpdates(0, "test", 6)
        val updates2 = NumericDocValuesFieldUpdates(1, "test", 6)
        val updates3 = NumericDocValuesFieldUpdates(2, "test", 6)
        val updates4 = NumericDocValuesFieldUpdates(2, "test", 6)

        updates1.add(0, 1)
        updates1.add(4, 0)
        updates1.add(1, 4)
        updates1.add(2, 5)
        updates1.add(4, 9)
        assertTrue(updates1.any())

        updates2.add(0, 18)
        updates2.add(1, 7)
        updates2.add(2, 19)
        updates2.add(5, 24)
        assertTrue(updates2.any())

        updates3.add(2, 42)
        assertTrue(updates3.any())
        assertFalse(updates4.any())
        updates1.finish()
        updates2.finish()
        updates3.finish()
        updates4.finish()
        val iterators = mutableListOf<DocValuesFieldUpdates.Iterator>(
            updates1.iterator(),
            updates2.iterator(),
            updates3.iterator(),
            updates4.iterator()
        )
        iterators.shuffle(random())
        val iterator =
            DocValuesFieldUpdates.mergedIterator(iterators.toTypedArray())!!
        assertEquals(0, iterator.nextDoc())
        assertEquals(18, iterator.longValue())
        assertEquals(1, iterator.nextDoc())
        assertEquals(7, iterator.longValue())
        assertEquals(2, iterator.nextDoc())
        assertEquals(42, iterator.longValue())
        assertEquals(4, iterator.nextDoc())
        assertEquals(9, iterator.longValue())
        assertEquals(5, iterator.nextDoc())
        assertEquals(24, iterator.longValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, iterator.nextDoc())
    }

    @Test
    fun testUpdateAndResetSameDoc() {
        val updates = NumericDocValuesFieldUpdates(0, "test", 2)
        updates.add(0, 1)
        updates.reset(0)
        updates.finish()
        val iterator = updates.iterator()
        assertEquals(0, iterator.nextDoc())
        assertFalse(iterator.hasValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, iterator.nextDoc())
    }

    @Test
    fun testUpdateAndResetUpdateSameDoc() {
        val updates = NumericDocValuesFieldUpdates(0, "test", 3)
        updates.add(0, 1)
        updates.add(0)
        updates.add(0, 2)
        updates.finish()
        val iterator = updates.iterator()
        assertEquals(0, iterator.nextDoc())
        assertTrue(iterator.hasValue())
        assertEquals(2, iterator.longValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, iterator.nextDoc())
    }

    @Test
    fun testUpdatesAndResetRandom() {
        val updates = NumericDocValuesFieldUpdates(0, "test", 10)
        val numUpdates = 10 + random().nextInt(100)
        val values = arrayOfNulls<Int>(5)
        for (i in 0..<5) {
            values[i] = if (random().nextBoolean()) null else random().nextInt(100)
            if (values[i] == null) {
                updates.reset(i)
            } else {
                updates.add(i, values[i]!!.toLong())
            }
        }
        for (i in 0..<numUpdates) {
            val docId = random().nextInt(5)
            values[docId] = if (random().nextBoolean()) null else random().nextInt(100)
            if (values[docId] == null) {
                updates.reset(docId)
            } else {
                updates.add(docId, values[docId]!!.toLong())
            }
        }

        updates.finish()
        val iterator = updates.iterator()
        var idx = 0
        while (iterator.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            assertEquals(idx, iterator.docID())
            if (values[idx] == null) {
                assertFalse(iterator.hasValue())
            } else {
                assertTrue(iterator.hasValue())
                assertEquals(values[idx]!!.toLong(), iterator.longValue())
            }
            idx++
        }
    }

    @Test
    fun testSharedValueUpdates() {
        val delGen = random().nextInt().toLong()
        val maxDoc = 1 + random().nextInt(1000)
        val value = random().nextLong()
        val update = SingleValueNumericDocValuesFieldUpdates(delGen, "foo", maxDoc, value)
        assertEquals(value, update.longValue())
        val values = arrayOfNulls<Boolean>(maxDoc)
        var any = false
        val noReset = random().nextBoolean() // sometimes don't reset
        for (i in 0..<maxDoc) {
            if (random().nextBoolean()) {
                values[i] = true
                any = true
                update.add(i, value)
            } else if (random().nextBoolean() && noReset == false) {
                values[i] = null
                any = true
                update.reset(i)
            } else {
                values[i] = false
            }
        }
        if (noReset == false) {
            for (i in values.indices) {
                if (rarely()) {
                    if (values[i] == null) {
                        values[i] = true
                        update.add(i, value)
                    } else if (values[i]!!) {
                        values[i] = null
                        update.reset(i)
                    }
                }
            }
        }
        update.finish()
        val iterator = update.iterator()
        assertEquals(any, update.any())
        assertEquals(delGen, iterator.delGen())
        var index = 0
        while (iterator.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            val doc = iterator.docID()
            if (index < iterator.docID()) {
                while (index < doc) {
                    assertFalse(values[index]!!)
                    index++
                }
            }
            if (index == doc) {
                if (values[index++] == null) {
                    assertFalse(iterator.hasValue())
                } else {
                    assertTrue(iterator.hasValue())
                    assertEquals(value, iterator.longValue())
                }
            }
        }
    }
}
