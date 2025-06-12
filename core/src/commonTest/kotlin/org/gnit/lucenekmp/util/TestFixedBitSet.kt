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
import kotlin.test.fail
import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.System

class TestFixedBitSet : LuceneTestCase() {

    private fun copyOf(bs: BitSet, length: Int): FixedBitSet {
        val set = FixedBitSet(length)
        var doc = bs.nextSetBit(0)
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            set.set(doc)
            doc = if (doc + 1 >= length) DocIdSetIterator.NO_MORE_DOCS else bs.nextSetBit(doc + 1)
        }
        return set
    }

    @Test
    fun testApproximateCardinality() {
        val set = FixedBitSet(TestUtil.nextInt(random(), 100_000, 200_000))
        val first = random().nextInt(10)
        val interval = TestUtil.nextInt(random(), 10, 20)
        var i = first
        while (i < set.length()) {
            set.set(i)
            i += interval
        }
        val cardinality = set.cardinality()
        assertTrue(kotlin.math.abs(cardinality - set.approximateCardinality()) <= cardinality / 20)
    }

    private fun doGet(a: BitSet, b: FixedBitSet) {
        assertEquals(a.cardinality(), b.cardinality())
        val max = b.length()
        for (i in 0 until max) {
            if (a.get(i) != b.get(i)) {
                fail("mismatch: BitSet=[" + i + "]=" + a.get(i))
            }
        }
    }

    private fun doNextSetBit(a: BitSet, b: FixedBitSet) {
        assertEquals(a.cardinality(), b.cardinality())
        var aa = -1
        var bb = -1
        do {
            aa = a.nextSetBit(aa + 1)
            if (aa == -1) {
                aa = DocIdSetIterator.NO_MORE_DOCS
            }
            bb = if (bb < b.length() - 1) b.nextSetBit(bb + 1) else DocIdSetIterator.NO_MORE_DOCS
            assertEquals(aa, bb)
        } while (aa != DocIdSetIterator.NO_MORE_DOCS)
    }

    private fun doPrevSetBit(a: BitSet, b: FixedBitSet) {
        assertEquals(a.cardinality(), b.cardinality())
        var aa = a.size() + random().nextInt(100)
        var bb = aa
        do {
            aa--
            while (aa >= 0 && !a.get(aa)) {
                aa--
            }
            bb = when {
                b.length() == 0 -> -1
                bb > b.length() - 1 -> b.prevSetBit(b.length() - 1)
                bb < 1 -> -1
                else -> if (bb >= 1) b.prevSetBit(bb - 1) else -1
            }
            assertEquals(aa, bb)
        } while (aa >= 0)
    }

    @Throws(IOException::class)
    private fun doIterate(a: BitSet, b: FixedBitSet) {
        assertEquals(a.cardinality(), b.cardinality())
        var aa = -1
        var bb = -1
        val iterator: DocIdSetIterator = BitSetIterator(b, 0)
        do {
            aa = a.nextSetBit(aa + 1)
            bb = if (random().nextBoolean()) iterator.nextDoc() else iterator.advance(bb + 1)
            assertEquals(if (aa == -1) DocIdSetIterator.NO_MORE_DOCS else aa, bb)
        } while (aa >= 0)
    }

    @Throws(IOException::class)
    private fun doRandomSets(maxSize: Int, iter: Int) {
        var a0: BitSet? = null
        var b0: FixedBitSet? = null

        for (i in 0 until iter) {
            val sz = TestUtil.nextInt(random(), 2, maxSize)
            val a = BitSet(sz)
            val b = FixedBitSet(sz)

            if (sz > 0) {
                val nOper = random().nextInt(sz)
                for (j in 0 until nOper) {
                    var idx: Int
                    idx = random().nextInt(sz)
                    a.set(idx)
                    b.set(idx)

                    idx = random().nextInt(sz)
                    a.clear(idx)
                    b.clear(idx)

                    idx = random().nextInt(sz)
                    a.flip(idx, idx + 1)
                    b.flip(idx, idx + 1)

                    idx = random().nextInt(sz)
                    a.flip(idx)
                    b.flip(idx)

                    val val2 = b.get(idx)
                    val value = b.getAndSet(idx)
                    assertTrue(val2 == value)
                    assertTrue(b.get(idx))

                    if (!value) b.clear(idx)
                    assertTrue(b.get(idx) == value)
                }
            }

            doGet(a, b)

            var fromIndex: Int
            var toIndex: Int
            fromIndex = random().nextInt(sz / 2)
            toIndex = fromIndex + random().nextInt(sz - fromIndex)
            var aa = a.clone()
            aa.flip(fromIndex, toIndex)
            var bb = b.clone()
            bb.flip(fromIndex, toIndex)

            doIterate(aa, bb)

            fromIndex = random().nextInt(sz / 2)
            toIndex = fromIndex + random().nextInt(sz - fromIndex)
            aa = a.clone()
            aa.clear(fromIndex, toIndex)
            bb = b.clone()
            bb.clear(fromIndex, toIndex)

            doNextSetBit(aa, bb)
            doPrevSetBit(aa, bb)

            fromIndex = random().nextInt(sz / 2)
            toIndex = fromIndex + random().nextInt(sz - fromIndex)
            aa = a.clone()
            aa.set(fromIndex, toIndex)
            bb = b.clone()
            bb.set(fromIndex, toIndex)

            doNextSetBit(aa, bb)
            doPrevSetBit(aa, bb)

            if (b0 != null && b0!!.length() <= b.length()) {
                assertEquals(a.cardinality(), b.cardinality())

                val a_and = a.clone()
                a_and.and(a0!!)
                val a_or = a.clone()
                a_or.or(a0!!)
                val a_xor = a.clone()
                a_xor.xor(a0!!)
                val a_andn = a.clone()
                a_andn.andNot(a0!!)

                val b_and = b.clone()
                assertEquals(b, b_and)
                b_and.and(b0!!)
                val b_or = b.clone()
                b_or.or(b0!!)
                val b_xor = b.clone()
                b_xor.xor(b0!!)
                val b_andn = b.clone()
                b_andn.andNot(b0!!)

                assertEquals(a0!!.cardinality(), b0!!.cardinality())
                assertEquals(a_or.cardinality(), b_or.cardinality())

                doIterate(a_and, b_and)
                doIterate(a_or, b_or)
                doIterate(a_andn, b_andn)
                doIterate(a_xor, b_xor)

                assertEquals(a_and.cardinality(), b_and.cardinality())
                assertEquals(a_or.cardinality(), b_or.cardinality())
                assertEquals(a_xor.cardinality(), b_xor.cardinality())
                assertEquals(a_andn.cardinality(), b_andn.cardinality())
            }

            a0 = a
            b0 = b
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSmall() {
        val iters = if (TEST_NIGHTLY) atLeast(1000) else 100
        doRandomSets(atLeast(1200), iters)
    }

    @Test
    fun testEquals() {
        val numBits = random().nextInt(2000) + 1
        val b1 = FixedBitSet(numBits)
        val b2 = FixedBitSet(numBits)
        assertTrue(b1 == b2)
        assertTrue(b2 == b1)
        for (iter in 0 until 10 * RANDOM_MULTIPLIER) {
            val idx = random().nextInt(numBits)
            if (!b1.get(idx)) {
                b1.set(idx)
                assertFalse(b1 == b2)
                assertFalse(b2 == b1)
                b2.set(idx)
                assertTrue(b1 == b2)
                assertTrue(b2 == b1)
            }
        }
        assertFalse(b1.equals(Any()))
    }

    @Test
    fun testHashCodeEquals() {
        val numBits = random().nextInt(2000) + 1
        val b1 = FixedBitSet(numBits)
        val b2 = FixedBitSet(numBits)
        assertTrue(b1 == b2)
        assertTrue(b2 == b1)
        for (iter in 0 until 10 * RANDOM_MULTIPLIER) {
            val idx = random().nextInt(numBits)
            if (!b1.get(idx)) {
                b1.set(idx)
                assertFalse(b1 == b2)
                assertFalse(b1.hashCode() == b2.hashCode())
                b2.set(idx)
                assertEquals(b1, b2)
                assertEquals(b1.hashCode(), b2.hashCode())
            }
        }
    }

    @Test
    fun testSmallBitSets() {
        for (numBits in 0 until 10) {
            val b1 = FixedBitSet(numBits)
            val b2 = FixedBitSet(numBits)
            assertTrue(b1 == b2)
            assertEquals(b1.hashCode(), b2.hashCode())
            assertEquals(0, b1.cardinality())
            if (numBits > 0) {
                b1.set(0, numBits)
                assertEquals(numBits, b1.cardinality())
                b1.flip(0, numBits)
                assertEquals(0, b1.cardinality())
            }
        }
    }

    private fun makeFixedBitSet(a: IntArray, numBits: Int): FixedBitSet {
        val bs: FixedBitSet = if (random().nextBoolean()) {
            val bits2words = FixedBitSet.bits2words(numBits)
            val words = LongArray(bits2words + random().nextInt(100))
            FixedBitSet(words, numBits)
        } else {
            FixedBitSet(numBits)
        }
        for (e in a) {
            bs.set(e)
        }
        return bs
    }

    private fun makeBitSet(a: IntArray): BitSet {
        val bs = BitSet()
        for (e in a) {
            bs.set(e)
        }
        return bs
    }

    private fun checkPrevSetBitArray(a: IntArray, numBits: Int) {
        val obs = makeFixedBitSet(a, numBits)
        val bs = makeBitSet(a)
        doPrevSetBit(bs, obs)
    }

    @Test
    fun testPrevSetBit() {
        checkPrevSetBitArray(intArrayOf(), 0)
        checkPrevSetBitArray(intArrayOf(0), 1)
        checkPrevSetBitArray(intArrayOf(0, 2), 3)
    }

    private fun checkNextSetBitArray(a: IntArray, numBits: Int) {
        val obs = makeFixedBitSet(a, numBits)
        val bs = makeBitSet(a)
        doNextSetBit(bs, obs)
    }

    @Test
    fun testNextBitSet() {
        val setBits = IntArray(random().nextInt(1000))
        for (i in setBits.indices) {
            setBits[i] = random().nextInt(setBits.size)
        }
        checkNextSetBitArray(setBits, setBits.size + random().nextInt(10))
        checkNextSetBitArray(IntArray(0), setBits.size + random().nextInt(10))
    }

    @Test
    fun testEnsureCapacity() {
        val bits = FixedBitSet(5)
        bits.set(1)
        bits.set(4)

        var newBits = FixedBitSet.ensureCapacity(bits, 8)
        assertTrue(newBits.get(1))
        assertTrue(newBits.get(4))
        newBits.clear(1)
        assertTrue(bits.get(1))
        assertFalse(newBits.get(1))

        newBits.set(1)
        newBits = FixedBitSet.ensureCapacity(newBits, newBits.length() - 2)
        assertTrue(newBits.get(1))

        bits.set(1)
        newBits = FixedBitSet.ensureCapacity(bits, 72)
        assertTrue(newBits.get(1))
        assertTrue(newBits.get(4))
        newBits.clear(1)
        assertTrue(bits.get(1))
        assertFalse(newBits.get(1))
    }

    @Test
    fun testBits2Words() {
        assertEquals(0, FixedBitSet.bits2words(0))
        assertEquals(1, FixedBitSet.bits2words(1))
        assertEquals(1, FixedBitSet.bits2words(64))
        assertEquals(2, FixedBitSet.bits2words(65))
        assertEquals(2, FixedBitSet.bits2words(128))
        assertEquals(3, FixedBitSet.bits2words(129))
        assertEquals(1 shl (31 - 6), FixedBitSet.bits2words(Int.MAX_VALUE))
    }

    private fun makeIntArray(random: kotlin.random.Random, count: Int, min: Int, max: Int): IntArray {
        val rv = IntArray(count)
        for (i in 0 until count) {
            rv[i] = TestUtil.nextInt(random, min, max)
        }
        return rv
    }

    @Test
    fun testIntersectionCount() {
        val random = random()
        val numBits1 = TestUtil.nextInt(random, 1000, 2000)
        val numBits2 = TestUtil.nextInt(random, 1000, 2000)
        val count1 = TestUtil.nextInt(random, 0, numBits1 - 1)
        val count2 = TestUtil.nextInt(random, 0, numBits2 - 1)
        val bits1 = makeIntArray(random, count1, 0, numBits1 - 1)
        val bits2 = makeIntArray(random, count2, 0, numBits2 - 1)
        val fixedBitSet1 = makeFixedBitSet(bits1, numBits1)
        val fixedBitSet2 = makeFixedBitSet(bits2, numBits2)
        val intersectionCount = FixedBitSet.intersectionCount(fixedBitSet1, fixedBitSet2)
        val bitSet1 = makeBitSet(bits1)
        val bitSet2 = makeBitSet(bits2)
        bitSet1.and(bitSet2)
        assertEquals(bitSet1.cardinality(), intersectionCount.toInt())
    }

    @Test
    @Throws(IOException::class)
    fun testAndNot() {
        val random = random()
        val numBits2 = TestUtil.nextInt(random, 1000, 2000)
        val numBits1 = TestUtil.nextInt(random, 1000, numBits2)
        val count1 = TestUtil.nextInt(random, 0, numBits1 - 1)
        val count2 = TestUtil.nextInt(random, 0, numBits2 - 1)
        val min = TestUtil.nextInt(random, 0, numBits1 - 1)
        val offsetWord1 = min shr 6
        val offset1 = offsetWord1 shl 6
        val bits1 = makeIntArray(random, count1, min, numBits1 - 1)
        val bits2 = makeIntArray(random, count2, 0, numBits2 - 1)
        val bitSet1 = makeBitSet(bits1)
        val bitSet2 = makeBitSet(bits2)
        bitSet2.andNot(bitSet1)
        run {
            val fixedBitSet2 = makeFixedBitSet(bits2, numBits2)
            val disi: DocIdSetIterator = BitSetIterator(makeFixedBitSet(bits1, numBits1), count1.toLong())
            fixedBitSet2.andNot(disi)
            doGet(bitSet2, fixedBitSet2)
        }
        run {
            val fixedBitSet2 = makeFixedBitSet(bits2, numBits2)
            val offsetBits = bits1.map { it - offset1 }.toIntArray()
            val disi: DocIdSetIterator =
                DocBaseBitSetIterator(makeFixedBitSet(offsetBits, numBits1 - offset1), count1.toLong(), offset1)
            fixedBitSet2.andNot(disi)
            doGet(bitSet2, fixedBitSet2)
        }
        run {
            val fixedBitSet2 = makeFixedBitSet(bits2, numBits2)
            val sorted = IntArray(bits1.size + 1)
            System.arraycopy(bits1, 0, sorted, 0, bits1.size)
            sorted[bits1.size] = DocIdSetIterator.NO_MORE_DOCS
            val disi: DocIdSetIterator = IntArrayDocIdSet.IntArrayDocIdSetIterator(sorted, count1)
            fixedBitSet2.andNot(disi)
            doGet(bitSet2, fixedBitSet2)
        }
    }

    @Test
    fun testUnionCount() {
        val random = random()
        val numBits1 = TestUtil.nextInt(random, 1000, 2000)
        val numBits2 = TestUtil.nextInt(random, 1000, 2000)
        val count1 = TestUtil.nextInt(random, 0, numBits1 - 1)
        val count2 = TestUtil.nextInt(random, 0, numBits2 - 1)
        val bits1 = makeIntArray(random, count1, 0, numBits1 - 1)
        val bits2 = makeIntArray(random, count2, 0, numBits2 - 1)
        val fixedBitSet1 = makeFixedBitSet(bits1, numBits1)
        val fixedBitSet2 = makeFixedBitSet(bits2, numBits2)
        val unionCount = FixedBitSet.unionCount(fixedBitSet1, fixedBitSet2)
        val bitSet1 = makeBitSet(bits1)
        val bitSet2 = makeBitSet(bits2)
        bitSet1.or(bitSet2)
        assertEquals(bitSet1.cardinality(), unionCount.toInt())
    }

    @Test
    fun testAndNotCount() {
        val random = random()
        val numBits1 = TestUtil.nextInt(random, 1000, 2000)
        val numBits2 = TestUtil.nextInt(random, 1000, 2000)
        val count1 = TestUtil.nextInt(random, 0, numBits1 - 1)
        val count2 = TestUtil.nextInt(random, 0, numBits2 - 1)
        val bits1 = makeIntArray(random, count1, 0, numBits1 - 1)
        val bits2 = makeIntArray(random, count2, 0, numBits2 - 1)
        val fixedBitSet1 = makeFixedBitSet(bits1, numBits1)
        val fixedBitSet2 = makeFixedBitSet(bits2, numBits2)
        val andNotCount = FixedBitSet.andNotCount(fixedBitSet1, fixedBitSet2)
        val bitSet1 = makeBitSet(bits1)
        val bitSet2 = makeBitSet(bits2)
        bitSet1.andNot(bitSet2)
        assertEquals(bitSet1.cardinality(), andNotCount.toInt())
    }

    @Test
    fun testCopyOf() {
        val random = random()
        val numBits = TestUtil.nextInt(random, 1000, 2000)
        val count = TestUtil.nextInt(random, 0, numBits - 1)
        val bits = makeIntArray(random, count, 0, numBits - 1)
        val fixedBitSet = FixedBitSet(numBits)
        for (e in bits) {
            fixedBitSet.set(e)
        }
        for (readOnly in arrayOf(false, true)) {
            val bitsToCopy: Bits = if (readOnly) fixedBitSet.asReadOnlyBits() else fixedBitSet
            val mutableCopy = FixedBitSet.copyOf(bitsToCopy)
            assertTrue(mutableCopy !== bitsToCopy as Any)
            assertEquals(mutableCopy, fixedBitSet)
        }
        val bitsToCopy = object : Bits {
            override fun get(index: Int): Boolean {
                return fixedBitSet.get(index)
            }
            override fun length(): Int {
                return fixedBitSet.length()
            }
        }
        val mutableCopy = FixedBitSet.copyOf(bitsToCopy)
        assertTrue(bitsToCopy !== mutableCopy as Any)
        assertTrue(fixedBitSet !== mutableCopy)
        assertEquals(mutableCopy, fixedBitSet)
    }

    @Test
    fun testAsBits() {
        val set = FixedBitSet(10)
        set.set(3)
        set.set(4)
        set.set(9)
        val bits = set.asReadOnlyBits()
        assertFalse(bits is FixedBitSet)
        assertEquals(set.length(), bits.length())
        for (i in 0 until set.length()) {
            assertEquals(set.get(i), bits.get(i))
        }
        set.set(5)
        assertTrue(bits.get(5))
    }

    @Test
    fun testScanIsEmpty() {
        var set = FixedBitSet(0)
        assertTrue(set.scanIsEmpty())
        set = FixedBitSet(13)
        assertTrue(set.scanIsEmpty())
        set.set(10)
        assertFalse(set.scanIsEmpty())
        set = FixedBitSet(1024)
        assertTrue(set.scanIsEmpty())
        set.set(3)
        assertFalse(set.scanIsEmpty())
        set.clear(3)
        set.set(1020)
        assertFalse(set.scanIsEmpty())
        set = FixedBitSet(1030)
        assertTrue(set.scanIsEmpty())
        set.set(3)
        assertFalse(set.scanIsEmpty())
        set.clear(3)
        set.set(1028)
        assertFalse(set.scanIsEmpty())
    }

    @Test
    fun testOrRange() {
        val dest = FixedBitSet(1_000)
        val source = FixedBitSet(10_000)
        var i = 0
        while (i < source.length()) {
            source.set(i)
            i += 3
        }
        for (sourceFrom in 64 until 128) {
            for (destFrom in 256 until 320) {
                for (length in arrayOf(0, TestUtil.nextInt(random(), 1, Long.SIZE_BITS - 1), TestUtil.nextInt(random(), Long.SIZE_BITS, 512))) {
                    dest.clear()
                    var j = 0
                    while (j < dest.length()) {
                        dest.set(j)
                        j += 10
                    }
                    FixedBitSet.orRange(source, sourceFrom, dest, destFrom, length)
                    for (k in 0 until dest.length()) {
                        val destSet = k % 10 == 0
                        if (k < destFrom || k >= destFrom + length) {
                            assertEquals(destSet, dest.get(k), "" + k)
                        } else {
                            val sourceSet = source.get(sourceFrom + (k - destFrom))
                            assertEquals(sourceSet || destSet, dest.get(k))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testAndRange() {
        val dest = FixedBitSet(1_000)
        val source = FixedBitSet(10_000)
        var i = 0
        while (i < source.length()) {
            source.set(i)
            i += 3
        }
        for (sourceFrom in 64 until 128) {
            for (destFrom in 256 until 320) {
                for (length in arrayOf(0, TestUtil.nextInt(random(), 1, Long.SIZE_BITS - 1), TestUtil.nextInt(random(), Long.SIZE_BITS, 512))) {
                    dest.clear()
                    var j = 0
                    while (j < dest.length()) {
                        dest.set(j)
                        j += 2
                    }
                    FixedBitSet.andRange(source, sourceFrom, dest, destFrom, length)
                    for (k in 0 until dest.length()) {
                        val destSet = k % 2 == 0
                        if (k < destFrom || k >= destFrom + length) {
                            assertEquals(destSet, dest.get(k), "" + k)
                        } else {
                            val sourceSet = source.get(sourceFrom + (k - destFrom))
                            assertEquals(sourceSet && destSet, dest.get(k), "" + k)
                        }
                    }
                }
            }
        }
    }
}
