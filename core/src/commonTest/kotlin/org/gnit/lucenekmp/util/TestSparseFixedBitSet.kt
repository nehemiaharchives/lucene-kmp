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
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSparseFixedBitSet : LuceneTestCase() {

    @Throws(IOException::class)
    private fun copyOf(bs: BitSet, length: Int): SparseFixedBitSet {
        val set = SparseFixedBitSet(length)
        var doc = bs.nextSetBit(0)
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            set.set(doc)
            doc = if (doc + 1 >= length) DocIdSetIterator.NO_MORE_DOCS else bs.nextSetBit(doc + 1)
        }
        return set
    }


    @Test
    fun testApproximateCardinality() {
        val set = SparseFixedBitSet(10000)
        val first = random().nextInt(1000)
        val interval = 200 + random().nextInt(1000)
        var i = first
        while (i < set.length) {
            set.set(i)
            i += interval
        }
        assertTrue(kotlin.math.abs(set.cardinality() - set.approximateCardinality()) <= 20)
    }

    @Test
    fun testApproximateCardinalityOnDenseSet() {
        val numDocs = TestUtil.nextInt(random(), 1, 10000)
        val set = SparseFixedBitSet(numDocs)
        for (i in 0 until set.length) {
            set.set(i)
        }
        assertEquals(numDocs, set.approximateCardinality())
    }

    @Test
    @Throws(IOException::class)
    fun testRamBytesUsed() {
        val size = 1000 + random().nextInt(1000)
        val original: BitSet = SparseFixedBitSet(size)
        repeat(3) { original.set(random().nextInt(size)) }
        assertTrue(original.ramBytesUsed() > 0)

        var copy = copyOf(original, size) as BitSet
        val otherBitSet: BitSet = SparseFixedBitSet(size)
        val interval = 10 + random().nextInt(100)
        var i = 0
        while (i < size) {
            otherBitSet.set(i)
            i += interval
        }
        copy.or(BitSetIterator(otherBitSet, size.toLong()))
        assertTrue(copy.ramBytesUsed() > original.ramBytesUsed())

        copy = copyOf(original, size)
        copy.or(DocIdSetIterator.all(size))
        assertTrue(copy.ramBytesUsed() > original.ramBytesUsed())
        assertTrue(copy.ramBytesUsed() > size.toLong() / Byte.SIZE_BITS)

        val setCopy = copyOf(original, size) as BitSet
        assertEquals(setCopy.ramBytesUsed(), original.ramBytesUsed())

        val orCopy = SparseFixedBitSet(size)
        orCopy.or(BitSetIterator(original, size.toLong()))
        assertTrue(kotlin.math.abs(original.ramBytesUsed() - orCopy.ramBytesUsed()) <= 64L)
    }
}
