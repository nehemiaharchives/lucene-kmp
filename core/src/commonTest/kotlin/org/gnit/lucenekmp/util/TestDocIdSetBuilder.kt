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

import okio.IOException
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestDocIdSetBuilder : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testEmpty() {
        assertEquals(null, DocIdSetBuilder(1 + random().nextInt(1000)).build())
    }

    @Throws(IOException::class)
    private fun assertEquals(d1: DocIdSet?, d2: DocIdSet?) {
        if (d1 == null) {
            if (d2 != null) {
                assertEquals(DocIdSetIterator.NO_MORE_DOCS, d2.iterator().nextDoc())
            }
        } else if (d2 == null) {
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, d1.iterator().nextDoc())
        } else {
            val i1 = d1.iterator()
            val i2 = d2.iterator()
            var doc: Int
            while (i1.nextDoc().also { doc = it } != DocIdSetIterator.NO_MORE_DOCS) {
                assertEquals(doc, i2.nextDoc())
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, i2.nextDoc())
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSparse() {
        val maxDoc = 1_000_000 + random().nextInt(1_000_000)
        val builder = DocIdSetBuilder(maxDoc)
        val numIterators = 1 + random().nextInt(10)
        val ref = FixedBitSet(maxDoc)
        for (i in 0 until numIterators) {
            val baseInc = 200_000 + random().nextInt(10_000)
            val b = RoaringDocIdSet.Builder(maxDoc)
            var doc = random().nextInt(100)
            while (doc < maxDoc) {
                b.add(doc)
                ref.set(doc)
                doc += baseInc + random().nextInt(10_000)
            }
            builder.add(b.build().iterator())
        }
        val result = builder.build()
        assertTrue(result is IntArrayDocIdSet)
        assertEquals(BitDocIdSet(ref), result)
    }

    @Test
    @Throws(IOException::class)
    fun testDense() {
        val maxDoc = 1_000_000 + random().nextInt(1_000_000)
        val builder = DocIdSetBuilder(maxDoc)
        val numIterators = 1 + random().nextInt(10)
        val ref = FixedBitSet(maxDoc)
        for (i in 0 until numIterators) {
            val b = RoaringDocIdSet.Builder(maxDoc)
            var doc = random().nextInt(1000)
            while (doc < maxDoc) {
                b.add(doc)
                ref.set(doc)
                doc += 1 + random().nextInt(100)
            }
            builder.add(b.build().iterator())
        }
        val result = builder.build()
        assertTrue(result is BitDocIdSet)
        assertEquals(BitDocIdSet(ref), result)
    }

    @Test
    @Throws(IOException::class)
    fun testRandom() {
        val maxDoc = if (TEST_NIGHTLY) TestUtil.nextInt(random(), 1, 10_000_000) else TestUtil.nextInt(random(), 1, 100_000)
        var i = 1
        while (i < maxDoc / 2) {
            val numDocs = TestUtil.nextInt(random(), 1, i)
            val docs = FixedBitSet(maxDoc)
            var c = 0
            while (c < numDocs) {
                val d = random().nextInt(maxDoc)
                if (!docs.get(d)) {
                    docs.set(d)
                    c += 1
                }
            }

            val array = IntArray(numDocs + random().nextInt(100))
            val it: DocIdSetIterator = BitSetIterator(docs, 0L)
            var j = 0
            var doc: Int
            while (it.nextDoc().also { doc = it } != DocIdSetIterator.NO_MORE_DOCS) {
                array[j++] = doc
            }
            assertEquals(numDocs, j)

            while (j < array.size) {
                array[j++] = array[random().nextInt(numDocs)]
            }

            for (k in array.size - 1 downTo 1) {
                val idx = random().nextInt(k)
                val tmp = array[k]
                array[k] = array[idx]
                array[idx] = tmp
            }

            val builder = DocIdSetBuilder(maxDoc)
            var idx = 0
            while (idx < array.size) {
                val l = TestUtil.nextInt(random(), 1, array.size - idx)
                var adder: DocIdSetBuilder.BulkAdder? = null
                if (usually()) {
                    var budget = 0
                    for (k in 0 until l) {
                        if (budget == 0 || rarely()) {
                            budget = TestUtil.nextInt(random(), 1, l - k + 5)
                            adder = builder.grow(budget)
                        }
                        adder!!.add(array[idx++])
                        budget--
                    }
                } else {
                    val intsRef = IntsRef(array, idx, l)
                    adder = builder.grow(l)
                    adder.add(intsRef)
                    idx += l
                }
            }

            val expected: DocIdSet = BitDocIdSet(docs)
            val actual = builder.build()
            assertEquals(expected, actual)
            i = i shl 1
        }
    }

    @Test
    @Throws(IOException::class)
    fun testMisleadingDISICost() {
        val maxDoc = TestUtil.nextInt(random(), 1000, 10_000)
        val builder = DocIdSetBuilder(maxDoc)
        val expected = FixedBitSet(maxDoc)
        for (i in 0 until 10) {
            val docs = FixedBitSet(maxDoc)
            val numDocs = random().nextInt(maxDoc / 1000)
            for (j in 0 until numDocs) {
                docs.set(random().nextInt(maxDoc))
            }
            expected.or(docs)
            builder.add(BitSetIterator(docs, 0L))
        }
        assertEquals(BitDocIdSet(expected), builder.build())
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyPoints() {
        val values: PointValues = DummyPointValues(0, 0)
        val builder = DocIdSetBuilder(1, values)
        assertEquals(1.0, builder.numValuesPerDoc)
    }

    @Test
    @Throws(IOException::class)
    fun testLeverageStats() {
        var values: PointValues = DummyPointValues(42, 42)
        var builder = DocIdSetBuilder(100, values)
        assertEquals(1.0, builder.numValuesPerDoc)
        assertFalse(builder.multivalued)
        var adder = builder.grow(2)
        adder.add(5)
        adder.add(7)
        var set = builder.build()
        assertTrue(set is BitDocIdSet)
        assertEquals(2, set.iterator().cost())

        values = DummyPointValues(42, 63)
        builder = DocIdSetBuilder(100, values)
        assertEquals(1.5, builder.numValuesPerDoc)
        assertTrue(builder.multivalued)
        adder = builder.grow(2)
        adder.add(5)
        adder.add(7)
        set = builder.build()
        assertTrue(set is BitDocIdSet)
        assertEquals(1, set.iterator().cost())

        values = DummyPointValues(42, -1)
        builder = DocIdSetBuilder(100, values)
        assertEquals(1.0, builder.numValuesPerDoc)
        assertTrue(builder.multivalued)

        values = DummyPointValues(-1, 84)
        builder = DocIdSetBuilder(100, values)
        assertEquals(1.0, builder.numValuesPerDoc)
        assertTrue(builder.multivalued)

        var terms: Terms = DummyTerms(42, 42)
        builder = DocIdSetBuilder(100, terms)
        assertEquals(1.0, builder.numValuesPerDoc)
        assertFalse(builder.multivalued)
        adder = builder.grow(2)
        adder.add(5)
        adder.add(7)
        set = builder.build()
        assertTrue(set is BitDocIdSet)
        assertEquals(2, set.iterator().cost())

        terms = DummyTerms(42, 63)
        builder = DocIdSetBuilder(100, terms)
        assertEquals(1.5, builder.numValuesPerDoc)
        assertTrue(builder.multivalued)
        adder = builder.grow(2)
        adder.add(5)
        adder.add(7)
        set = builder.build()
        assertTrue(set is BitDocIdSet)
        assertEquals(1, set.iterator().cost())

        terms = DummyTerms(42, -1)
        builder = DocIdSetBuilder(100, terms)
        assertEquals(1.0, builder.numValuesPerDoc)
        assertTrue(builder.multivalued)

        terms = DummyTerms(-1, 84)
        builder = DocIdSetBuilder(100, terms)
        assertEquals(1.0, builder.numValuesPerDoc)
        assertTrue(builder.multivalued)
    }

    @Test
    @Throws(IOException::class)
    fun testCostIsCorrectAfterBitsetUpgrade() {
        val maxDoc = 1_000_000
        val builder = DocIdSetBuilder(maxDoc)
        for (i in 0 until (1_000_000 shr 6)) {
            builder.add(DocIdSetIterator.range(i, i + 1))
        }
        val result = builder.build()
        assertTrue(result is BitDocIdSet)
        assertEquals(1_000_000 shr 6, result.iterator().cost())
    }

    private fun usually(): Boolean {
        return !rarely()
    }

    private fun rarely(): Boolean {
        return TestUtil.rarely(random())
    }

    private class DummyTerms(
        override val docCount: Int,
        private val numValues: Long
    ) : Terms() {
        @Throws(IOException::class)
        override fun iterator(): TermsEnum {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun size(): Long {
            throw UnsupportedOperationException()
        }

        override val sumTotalTermFreq: Long
            get() = throw UnsupportedOperationException()

        override val sumDocFreq: Long
            get() = numValues

        override fun hasFreqs(): Boolean {
            throw UnsupportedOperationException()
        }

        override fun hasOffsets(): Boolean {
            throw UnsupportedOperationException()
        }

        override fun hasPositions(): Boolean {
            throw UnsupportedOperationException()
        }

        override fun hasPayloads(): Boolean {
            throw UnsupportedOperationException()
        }
    }

    private class DummyPointValues(
        override val docCount: Int,
        private val numPoints: Long
    ) : PointValues() {
        override val pointTree: PointValues.PointTree
            get() = throw UnsupportedOperationException()

        override val minPackedValue: ByteArray
            get() = throw UnsupportedOperationException()

        override val maxPackedValue: ByteArray
            get() = throw UnsupportedOperationException()

        override val numDimensions: Int
            get() = throw UnsupportedOperationException()

        override val numIndexDimensions: Int
            get() = throw UnsupportedOperationException()

        override val bytesPerDimension: Int
            get() = throw UnsupportedOperationException()

        override fun size(): Long {
            return numPoints
        }
    }
}
