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

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoubleRangeDocValuesField
import org.gnit.lucenekmp.document.FloatRangeDocValuesField
import org.gnit.lucenekmp.document.IntRangeDocValuesField
import org.gnit.lucenekmp.document.LongRangeDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRangeFieldsDocValuesQuery : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testDoubleRangeDocValuesIntersectsQuery() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val iters = atLeast(10)
        val min = doubleArrayOf(112.7, 296.0, 512.4)
        val max = doubleArrayOf(119.3, 314.8, 524.3)
        repeat(iters) {
            val doc = Document()
            doc.add(DoubleRangeDocValuesField("dv", min, max))
            iw.addDocument(doc)
        }
        iw.commit()

        val nonMatchingMin = doubleArrayOf(256.7, 296.0, 532.4)
        val nonMatchingMax = doubleArrayOf(259.3, 364.8, 534.3)

        val doc = Document()
        doc.add(DoubleRangeDocValuesField("dv", nonMatchingMin, nonMatchingMax))
        iw.addDocument(doc)
        iw.commit()

        val reader: IndexReader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()

        val lowRange = doubleArrayOf(111.3, 294.4, 517.4)
        val highRange = doubleArrayOf(116.7, 319.4, 533.0)

        var query: Query = DoubleRangeDocValuesField.newSlowIntersectsQuery("dv", lowRange, highRange)
        assertEquals(iters, searcher.count(query))

        val lowRange2 = doubleArrayOf(116.3, 299.3, 517.0)
        val highRange2 = doubleArrayOf(121.0, 317.1, 531.2)

        query = DoubleRangeDocValuesField.newSlowIntersectsQuery("dv", lowRange2, highRange2)

        assertEquals(iters, searcher.count(query))

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testIntRangeDocValuesIntersectsQuery() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val iters = atLeast(10)
        val min = intArrayOf(3, 11, 17)
        val max = intArrayOf(27, 35, 49)
        repeat(iters) {
            val doc = Document()
            doc.add(IntRangeDocValuesField("dv", min, max))
            iw.addDocument(doc)
        }

        val min2 = intArrayOf(11, 19, 27)
        val max2 = intArrayOf(29, 38, 56)

        val doc = Document()
        doc.add(IntRangeDocValuesField("dv", min2, max2))

        iw.commit()

        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()

        val lowRange = intArrayOf(6, 16, 19)
        val highRange = intArrayOf(29, 41, 42)

        var query: Query = IntRangeDocValuesField.newSlowIntersectsQuery("dv", lowRange, highRange)

        assertEquals(iters, searcher.count(query))

        val lowRange2 = intArrayOf(2, 9, 18)
        val highRange2 = intArrayOf(25, 34, 41)

        query = IntRangeDocValuesField.newSlowIntersectsQuery("dv", lowRange2, highRange2)

        assertEquals(iters, searcher.count(query))

        val lowRange3 = intArrayOf(101, 121, 153)
        val highRange3 = intArrayOf(156, 127, 176)

        query = IntRangeDocValuesField.newSlowIntersectsQuery("dv", lowRange3, highRange3)

        assertEquals(0, searcher.count(query))

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testLongRangeDocValuesIntersectQuery() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val iters = atLeast(10)
        val min = longArrayOf(31, 15, 2)
        val max = longArrayOf(95, 27, 4)
        repeat(iters) {
            val doc = Document()
            doc.add(LongRangeDocValuesField("dv", min, max))
            iw.addDocument(doc)
        }

        val min2 = longArrayOf(101, 124, 137)
        val max2 = longArrayOf(138, 145, 156)
        val doc = Document()
        doc.add(LongRangeDocValuesField("dv", min2, max2))

        iw.commit()

        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()

        val lowRange = longArrayOf(6, 12, 1)
        val highRange = longArrayOf(34, 24, 3)

        var query: Query = LongRangeDocValuesField.newSlowIntersectsQuery("dv", lowRange, highRange)

        assertEquals(iters, searcher.count(query))

        val lowRange2 = longArrayOf(32, 18, 3)
        val highRange2 = longArrayOf(96, 29, 5)

        query = LongRangeDocValuesField.newSlowIntersectsQuery("dv", lowRange2, highRange2)

        assertEquals(iters, searcher.count(query))

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFloatRangeDocValuesIntersectQuery() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val iters = atLeast(10)
        val min = floatArrayOf(3.7f, 11.0f, 33.4f)
        val max = floatArrayOf(8.3f, 21.6f, 59.8f)
        repeat(iters) {
            val doc = Document()
            doc.add(FloatRangeDocValuesField("dv", min, max))
            iw.addDocument(doc)
        }

        val nonMatchingMin = floatArrayOf(11.4f, 29.7f, 102.4f)
        val nonMatchingMax = floatArrayOf(17.6f, 37.2f, 160.2f)
        val doc = Document()
        doc.add(FloatRangeDocValuesField("dv", nonMatchingMin, nonMatchingMax))
        iw.addDocument(doc)

        iw.commit()

        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()

        val lowRange = floatArrayOf(1.2f, 8.3f, 21.4f)
        val highRange = floatArrayOf(6.0f, 17.6f, 47.1f)

        var query: Query = FloatRangeDocValuesField.newSlowIntersectsQuery("dv", lowRange, highRange)

        assertEquals(iters, searcher.count(query))

        val lowRange2 = floatArrayOf(6.1f, 17.0f, 31.3f)
        val highRange2 = floatArrayOf(14.2f, 23.4f, 61.1f)

        query = FloatRangeDocValuesField.newSlowIntersectsQuery("dv", lowRange2, highRange2)

        assertEquals(iters, searcher.count(query))

        reader.close()
        dir.close()
    }

    @Test
    fun testToString() {
        val doubleMin = doubleArrayOf(112.7, 296.0, 512.4f.toDouble())
        val doubleMax = doubleArrayOf(119.3, 314.8, 524.3f.toDouble())
        val q1 = DoubleRangeDocValuesField.newSlowIntersectsQuery("foo", doubleMin, doubleMax)
        assertEquals(
            "foo:[[112.7, 296.0, 512.4000244140625] TO [119.3, 314.8, 524.2999877929688]]",
            q1.toString(),
        )

        val intMin = intArrayOf(3, 11, 17)
        val intMax = intArrayOf(27, 35, 49)
        val q2 = IntRangeDocValuesField.newSlowIntersectsQuery("foo", intMin, intMax)
        assertEquals("foo:[[3, 11, 17] TO [27, 35, 49]]", q2.toString())

        val floatMin = floatArrayOf(3.7f, 11.0f, 33.4f)
        val floatMax = floatArrayOf(8.3f, 21.6f, 59.8f)
        val q3 = FloatRangeDocValuesField.newSlowIntersectsQuery("foo", floatMin, floatMax)
        assertEquals("foo:[[3.7, 11.0, 33.4] TO [8.3, 21.6, 59.8]]", q3.toString())

        val longMin = longArrayOf(101, 124, 137)
        val longMax = longArrayOf(138, 145, 156)
        val q4 = LongRangeDocValuesField.newSlowIntersectsQuery("foo", longMin, longMax)
        assertEquals("foo:[[101, 124, 137] TO [138, 145, 156]]", q4.toString())
    }

    @Test
    @Throws(IOException::class)
    fun testNoData() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(StringField("foo", "abc", org.gnit.lucenekmp.document.Field.Store.NO))
        iw.addDocument(doc)

        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()

        // test on field that doesn't exist
        val q1 = LongRangeDocValuesField.newSlowIntersectsQuery("bar", longArrayOf(20), longArrayOf(27))
        val r = searcher.search(q1, 10)
        assertEquals(0, r.totalHits.value)

        // test on field of wrong type
        val q2 = LongRangeDocValuesField.newSlowIntersectsQuery("foo", longArrayOf(20), longArrayOf(27))
        expectThrows(IllegalStateException::class) { searcher.search(q2, 10) }

        reader.close()
        dir.close()
    }
}
