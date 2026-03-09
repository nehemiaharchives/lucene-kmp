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

import org.gnit.lucenekmp.index.DocValuesUpdate.BinaryDocValuesUpdate
import org.gnit.lucenekmp.index.DocValuesUpdate.NumericDocValuesUpdate
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.Counter
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestFieldUpdatesBuffer : LuceneTestCase() {
    @Test
    fun testBasics() {
        val counter = Counter.newCounter()
        val update = NumericDocValuesUpdate(Term("id", "1"), "age", 6)
        val buffer = FieldUpdatesBuffer(counter, update, 15)
        buffer.addUpdate(Term("id", "10"), 6, 15)
        assertTrue(buffer.hasSingleValue())
        buffer.addUpdate(Term("id", "8"), 12, 15)
        assertFalse(buffer.hasSingleValue())
        buffer.addUpdate(Term("some_other_field", "8"), 13, 17)
        assertFalse(buffer.hasSingleValue())
        buffer.addUpdate(Term("id", "8"), 12, 16)
        assertFalse(buffer.hasSingleValue())
        assertTrue(buffer.isNumeric())
        assertEquals(13, buffer.getMaxNumeric())
        assertEquals(6, buffer.getMinNumeric())
        buffer.finish()
        val iterator = buffer.iterator()
        var value = iterator.next()
        assertNotNull(value)
        assertEquals("id", value.termField)
        assertEquals("1", value.termValue!!.utf8ToString())
        assertEquals(6, value.numericValue)
        assertEquals(15, value.docUpTo)

        value = iterator.next()
        assertNotNull(value)
        assertEquals("id", value.termField)
        assertEquals("10", value.termValue!!.utf8ToString())
        assertEquals(6, value.numericValue)
        assertEquals(15, value.docUpTo)

        value = iterator.next()
        assertNotNull(value)
        assertEquals("id", value.termField)
        assertEquals("8", value.termValue!!.utf8ToString())
        assertEquals(12, value.numericValue)
        assertEquals(15, value.docUpTo)

        value = iterator.next()
        assertNotNull(value)
        assertEquals("some_other_field", value.termField)
        assertEquals("8", value.termValue!!.utf8ToString())
        assertEquals(13, value.numericValue)
        assertEquals(17, value.docUpTo)

        value = iterator.next()
        assertNotNull(value)
        assertEquals("id", value.termField)
        assertEquals("8", value.termValue!!.utf8ToString())
        assertEquals(12, value.numericValue)
        assertEquals(16, value.docUpTo)
        assertNull(iterator.next())
    }

    @Test
    fun testUpdateShareValues() {
        val counter = Counter.newCounter()
        val intValue = random().nextInt().toLong()
        val valueForThree = random().nextBoolean()
        val update = NumericDocValuesUpdate(Term("id", "0"), "enabled", intValue)
        val buffer = FieldUpdatesBuffer(counter, update, Int.MAX_VALUE)
        buffer.addUpdate(Term("id", "1"), intValue, Int.MAX_VALUE)
        buffer.addUpdate(Term("id", "2"), intValue, Int.MAX_VALUE)
        if (valueForThree) {
            buffer.addUpdate(Term("id", "3"), intValue, Int.MAX_VALUE)
        } else {
            buffer.addNoValue(Term("id", "3"), Int.MAX_VALUE)
        }
        buffer.addUpdate(Term("id", "4"), intValue, Int.MAX_VALUE)
        buffer.finish()
        val iterator = buffer.iterator()
        var value: FieldUpdatesBuffer.BufferedUpdate?
        var count = 0
        while ((iterator.next().also { value = it }) != null) {
            val hasValue = count != 3 || valueForThree
            assertEquals("$count", value!!.termValue!!.utf8ToString())
            assertEquals("id", value.termField)
            assertEquals(hasValue, value.hasValue)
            if (hasValue) {
                assertEquals(intValue, value.numericValue)
            } else {
                assertEquals(0, value.numericValue)
            }
            assertEquals(Int.MAX_VALUE, value.docUpTo)
            count++
        }
        assertTrue(buffer.isNumeric())
    }

    @Test
    fun testUpdateShareValuesBinary() {
        val counter = Counter.newCounter()
        val valueForThree = random().nextBoolean()
        val update = BinaryDocValuesUpdate(Term("id", "0"), "enabled", BytesRef(""))
        val buffer = FieldUpdatesBuffer(counter, update, Int.MAX_VALUE)
        buffer.addUpdate(Term("id", "1"), BytesRef(""), Int.MAX_VALUE)
        buffer.addUpdate(Term("id", "2"), BytesRef(""), Int.MAX_VALUE)
        if (valueForThree) {
            buffer.addUpdate(Term("id", "3"), BytesRef(""), Int.MAX_VALUE)
        } else {
            buffer.addNoValue(Term("id", "3"), Int.MAX_VALUE)
        }
        buffer.addUpdate(Term("id", "4"), BytesRef(""), Int.MAX_VALUE)
        buffer.finish()
        val iterator = buffer.iterator()
        var value: FieldUpdatesBuffer.BufferedUpdate?
        var count = 0
        while ((iterator.next().also { value = it }) != null) {
            val hasValue = count != 3 || valueForThree
            assertEquals("$count", value!!.termValue!!.utf8ToString())
            assertEquals("id", value.termField)
            assertEquals(hasValue, value.hasValue)
            if (hasValue) {
                assertEquals(BytesRef(""), value.binaryValue)
            } else {
                assertNull(value.binaryValue)
            }
            assertEquals(Int.MAX_VALUE, value.docUpTo)
            count++
        }
        assertFalse(buffer.isNumeric())
    }

    fun getRandomBinaryUpdate(docIdUpTo: Int): BinaryDocValuesUpdate {
        val termField = RandomPicks.randomFrom(random(), mutableListOf("id", "_id", "some_other_field"))
        val docId = "${random().nextInt(10)}"
        val value = BinaryDocValuesUpdate(
            Term(termField, docId),
            "binary",
            if (rarely()) null else BytesRef(TestUtil.randomRealisticUnicodeString(random()))
        )
        return if (rarely()) value.prepareForApply(docIdUpTo) else value
    }

    fun getRandomNumericUpdate(docIdUpTo: Int): NumericDocValuesUpdate {
        val termField = RandomPicks.randomFrom(random(), mutableListOf("id", "_id", "some_other_field"))
        val docId = "${random().nextInt(10)}"
        val value = NumericDocValuesUpdate(
            Term(termField, docId),
            "numeric",
            if (rarely()) null else random().nextInt(100).toLong()
        )
        return if (rarely()) value.prepareForApply(docIdUpTo) else value
    }

    @Test
    fun testBinaryRandom() {
        val updates = mutableListOf<BinaryDocValuesUpdate>()
        val numUpdates = 1 + random().nextInt(1000)
        val counter = Counter.newCounter()
        var randomUpdate = getRandomBinaryUpdate(0)
        updates.add(randomUpdate)
        val buffer = FieldUpdatesBuffer(counter, randomUpdate, randomUpdate.docIDUpTo)
        for (i in 0 until numUpdates) {
            randomUpdate = getRandomBinaryUpdate(i + 1)
            updates.add(randomUpdate)
            if (randomUpdate.hasValue) {
                buffer.addUpdate(randomUpdate.term!!, randomUpdate.getValue(), randomUpdate.docIDUpTo)
            } else {
                buffer.addNoValue(randomUpdate.term!!, randomUpdate.docIDUpTo)
            }
        }
        buffer.finish()
        val iterator = buffer.iterator()
        var value: FieldUpdatesBuffer.BufferedUpdate?

        var count = 0
        while ((iterator.next().also { value = it }) != null) {
            randomUpdate = updates[count++]
            assertEquals(randomUpdate.term!!.bytes.utf8ToString(), value!!.termValue!!.utf8ToString())
            assertEquals(randomUpdate.term.field, value.termField)
            assertEquals(randomUpdate.hasValue, value.hasValue, "count: $count")
            if (randomUpdate.hasValue) {
                assertEquals(randomUpdate.getValue(), value.binaryValue)
            } else {
                assertNull(value.binaryValue)
            }
            assertEquals(randomUpdate.docIDUpTo, value.docUpTo)
        }
        assertEquals(count, updates.size)
    }

    @Test
    fun testNumericRandom() {
        val updates = mutableListOf<NumericDocValuesUpdate>()
        val numUpdates = 1 + random().nextInt(1000)
        val counter = Counter.newCounter()
        var randomUpdate = getRandomNumericUpdate(0)
        updates.add(randomUpdate)
        val buffer = FieldUpdatesBuffer(counter, randomUpdate, randomUpdate.docIDUpTo)
        for (i in 0 until numUpdates) {
            randomUpdate = getRandomNumericUpdate(i + 1)
            updates.add(randomUpdate)
            if (randomUpdate.hasValue) {
                buffer.addUpdate(randomUpdate.term!!, randomUpdate.getValue(), randomUpdate.docIDUpTo)
            } else {
                buffer.addNoValue(randomUpdate.term!!, randomUpdate.docIDUpTo)
            }
        }
        buffer.finish()
        val lastUpdate = randomUpdate
        val termsSorted =
            lastUpdate.hasValue &&
                updates.all { update ->
                    update.field == lastUpdate.field &&
                        update.hasValue &&
                        update.getValue() == lastUpdate.getValue()
                }
        assertBufferUpdates(buffer, updates, termsSorted)
    }

    @Test
    fun testNoNumericValue() {
        val update = NumericDocValuesUpdate(Term("id", "1"), "age", null)
        val buffer = FieldUpdatesBuffer(Counter.newCounter(), update, update.docIDUpTo)
        assertEquals(0, buffer.getMinNumeric())
        assertEquals(0, buffer.getMaxNumeric())
    }

    @Test
    fun testSortAndDedupNumericUpdatesByTerms() {
        val updates = mutableListOf<NumericDocValuesUpdate>()
        val numUpdates = 1 + random().nextInt(1000)
        val counter = Counter.newCounter()
        val termField = RandomPicks.randomFrom(random(), mutableListOf("id", "_id", "some_other_field"))
        val docValue = (1 + random().nextInt(1000)).toLong()
        var randomUpdate = NumericDocValuesUpdate(
            Term(termField, random().nextInt(1000).toString()),
            "numeric",
            docValue
        )
        randomUpdate = randomUpdate.prepareForApply(0)
        updates.add(randomUpdate)
        val buffer = FieldUpdatesBuffer(counter, randomUpdate, randomUpdate.docIDUpTo)
        for (i in 0 until numUpdates) {
            randomUpdate = NumericDocValuesUpdate(
                Term(termField, random().nextInt(1000).toString()),
                "numeric",
                docValue
            )
            randomUpdate = randomUpdate.prepareForApply(i + 1)
            updates.add(randomUpdate)
            buffer.addUpdate(randomUpdate.term!!, randomUpdate.getValue(), randomUpdate.docIDUpTo)
        }
        buffer.finish()
        assertBufferUpdates(buffer, updates, true)
    }

    fun assertBufferUpdates(
        buffer: FieldUpdatesBuffer,
        inputUpdates: List<NumericDocValuesUpdate>,
        termSorted: Boolean
    ) {
        var updates = inputUpdates
        if (termSorted) {
            updates = updates.sortedBy { it.term!!.bytes }
            val byTerms = TreeMap<BytesRef, NumericDocValuesUpdate>()
            for (update in updates) {
                byTerms.compute(update.term!!.bytes) { _, v: NumericDocValuesUpdate? ->
                    if (v != null && v.docIDUpTo >= update.docIDUpTo) v else update
                }
            }
            updates = byTerms.values.toMutableList()
        }
        val iterator = buffer.iterator()
        var value: FieldUpdatesBuffer.BufferedUpdate?

        var count = 0
        var minValue = Long.MAX_VALUE
        var maxValue = Long.MIN_VALUE
        var hasAtLeastOneValue = false
        var expectedUpdate: NumericDocValuesUpdate
        while ((iterator.next().also { value = it }) != null) {
            val v = buffer.getNumericValue(count)
            expectedUpdate = updates[count++]
            assertEquals(expectedUpdate.term!!.bytes.utf8ToString(), value!!.termValue!!.utf8ToString())
            assertEquals(expectedUpdate.term.field, value.termField)
            assertEquals(expectedUpdate.hasValue, value.hasValue)
            if (expectedUpdate.hasValue) {
                assertEquals(expectedUpdate.getValue(), value.numericValue)
                assertEquals(v, value.numericValue)
                minValue = min(minValue, v)
                maxValue = max(maxValue, v)
                hasAtLeastOneValue = true
            } else {
                assertEquals(0, value.numericValue)
                assertEquals(0, v)
            }
            assertEquals(expectedUpdate.docIDUpTo, value.docUpTo)
        }
        if (hasAtLeastOneValue) {
            assertEquals(maxValue, buffer.getMaxNumeric())
            assertEquals(minValue, buffer.getMinNumeric())
        } else {
            assertEquals(0, buffer.getMaxNumeric())
            assertEquals(0, buffer.getMinNumeric())
        }
        assertEquals(count, updates.size)
    }
}
