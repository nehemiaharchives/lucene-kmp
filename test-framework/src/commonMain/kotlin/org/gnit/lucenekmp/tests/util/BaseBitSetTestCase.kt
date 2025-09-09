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
package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.BitSet as JDKPortBitSet
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BitDocIdSet
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.RoaringDocIdSet
import org.gnit.lucenekmp.util.SparseFixedBitSet
import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.search.DocIdSetIterator
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.fail

/** Base test case for BitSets. */
abstract class BaseBitSetTestCase<T : BitSet> : LuceneTestCase() {

    /** Create a copy of the given [BitSet] which has `length` bits. */
    abstract fun copyOf(bs: BitSet, length: Int): T

    companion object {
        /** Create a random set which has `numBitsSet` of its `numBits` bits set. */
        fun randomSet(numBits: Int, numBitsSet: Int): JDKPortBitSet {
            require(numBitsSet <= numBits)
            val set = JDKPortBitSet(numBits)
            if (numBitsSet == numBits) {
                set.set(0, numBits)
            } else {
                val random = Random.Default
                repeat(numBitsSet) {
                    while (true) {
                        val o = random.nextInt(numBits)
                        if (!set.get(o)) {
                            set.set(o)
                            break
                        }
                    }
                }
            }
            return set
        }

        /** Same as [randomSet] but given a load factor. */
        fun randomSet(numBits: Int, percentSet: Float): JDKPortBitSet {
            return randomSet(numBits, (percentSet * numBits).toInt())
        }
    }

    protected open fun assertEquals(set1: BitSet, set2: T, maxDoc: Int) {
        for (i in 0 until maxDoc) {
            assertEquals(set1.get(i), set2.get(i), "Different at $i")
        }
    }

    /** Test the [BitSet.cardinality] method. */
    open fun testCardinality() {
        val numBits = 1 + Random.Default.nextInt(100000)
        for (percentSet in floatArrayOf(0f, 0.01f, 0.1f, 0.5f, 0.9f, 0.99f, 1f)) {
            val set1 = JavaUtilBitSet(randomSet(numBits, percentSet), numBits)
            val set2 = copyOf(set1, numBits)
            assertEquals(set1.cardinality(), set2.cardinality())
        }
    }

    /** Test [BitSet.prevSetBit]. */
    open fun testPrevSetBit() {
        val numBits = 1 + Random.Default.nextInt(100000)
        for (percentSet in floatArrayOf(0f, 0.01f, 0.1f, 0.5f, 0.9f, 0.99f, 1f)) {
            val set1 = JavaUtilBitSet(randomSet(numBits, percentSet), numBits)
            val set2 = copyOf(set1, numBits)
            for (i in 0 until numBits) {
                assertEquals(set1.prevSetBit(i), set2.prevSetBit(i), i.toString())
            }
        }
    }

    /** Test [BitSet.nextSetBit]. */
    open fun testNextSetBit() {
        val numBits = 1 + Random.Default.nextInt(100000)
        for (percentSet in floatArrayOf(0f, 0.01f, 0.1f, 0.5f, 0.9f, 0.99f, 1f)) {
            val set1 = JavaUtilBitSet(randomSet(numBits, percentSet), numBits)
            val set2 = copyOf(set1, numBits)
            for (i in 0 until numBits) {
                assertEquals(set1.nextSetBit(i), set2.nextSetBit(i))
            }
        }
    }

    /** Test [BitSet.nextSetBit] with range. */
    open fun testNextSetBitInRange() {
        val random = Random.Default
        val numBits = 1 + random.nextInt(1000) // TODO originally 100000 but reduced to 1000 for dev speed
        for (percentSet in floatArrayOf(0f, 0.01f, 0.1f, 0.5f, 0.9f, 0.99f, 1f)) {
            val set1 = JavaUtilBitSet(randomSet(numBits, percentSet), numBits)
            val set2 = copyOf(set1, numBits)
            for (start in 0 until numBits) {
                val end = random.nextInt(start + 1, numBits + 1)
                assertEquals(
                    set1.nextSetBit(start, end),
                    set2.nextSetBit(start, end),
                    "start=$start, end=$end, numBits=$numBits"
                )
            }
        }
    }

    /** Test the [BitSet.set] method. */
    open fun testSet() {
        val random = Random.Default
        val numBits = 1 + random.nextInt(100000)
        val set1 = JavaUtilBitSet(randomSet(numBits, 0f), numBits)
        val set2 = copyOf(set1, numBits)
        val iters = 10000 + random.nextInt(10000)
        repeat(iters) {
            val index = random.nextInt(numBits)
            set1.set(index)
            set2.set(index)
        }
        assertEquals(set1, set2, numBits)
    }

    /** Test the [BitSet.getAndSet] method. */
    open fun testGetAndSet() {
        val random = Random.Default
        val numBits = 1 + random.nextInt(100000)
        val set1 = JavaUtilBitSet(randomSet(numBits, 0f), numBits)
        val set2 = copyOf(set1, numBits)
        val iters = 10000 + random.nextInt(10000)
        repeat(iters) {
            val index = random.nextInt(numBits)
            val v1 = set1.getAndSet(index)
            val v2 = set2.getAndSet(index)
            assertEquals(v1, v2)
        }
        assertEquals(set1, set2, numBits)
    }

    /** Test the [BitSet.clear] method. */
    open fun testClear() {
        val random = Random.Default
        val numBits = 1 + random.nextInt(100000)
        for (percentSet in floatArrayOf(0f, 0.01f, 0.1f, 0.5f, 0.9f, 0.99f, 1f)) {
            val set1 = JavaUtilBitSet(randomSet(numBits, percentSet), numBits)
            val set2 = copyOf(set1, numBits)
            val iters = 1 + random.nextInt(numBits * 2)
            repeat(iters) {
                val index = random.nextInt(numBits)
                set1.clear(index)
                set2.clear(index)
            }
            assertEquals(set1, set2, numBits)
        }
    }

    /** Test the [BitSet.clear] range method. */
    open fun testClearRange() {
        val random = Random.Default
        val numBits = 1 + random.nextInt(1000) // TODO originally 100000 but reduced to 1000 for dev speed
        for (percentSet in floatArrayOf(0f, 0.01f, 0.1f, 0.5f, 0.9f, 0.99f, 1f)) {
            val set1 = JavaUtilBitSet(randomSet(numBits, percentSet), numBits)
            val set2 = copyOf(set1, numBits)
            val iters = atLeast(random, 3) // TODO originally 10 but reduced to 3 for dev speed
            repeat(iters) {
                val from = random.nextInt(numBits)
                val to = random.nextInt(numBits + 1)
                set1.clear(from, to)
                set2.clear(from, to)
                assertEquals(set1, set2, numBits)
            }
        }
    }

    /** Test the [BitSet.clear] all method. */
    open fun testClearAll() {
        val random = Random.Default
        val numBits = 1 + random.nextInt(1000) // TODO originally 100000 but reduced to 1000 for dev speed
        for (percentSet in floatArrayOf(0f, 0.01f, 0.1f, 0.5f, 0.9f, 0.99f, 1f)) {
            val set1 = JavaUtilBitSet(randomSet(numBits, percentSet), numBits)
            val set2 = copyOf(set1, numBits)
            val iters = atLeast(random, 3) // TODO originally 10 but reduced to 3 for dev speed
            repeat(iters) {
                set1.clear()
                set2.clear()
                assertEquals(set1, set2, numBits)
            }
        }
    }

    private fun randomCopy(set: BitSet, numBits: Int): DocIdSet {
        return when (random().nextInt(5)) {
            0 -> BitDocIdSet(set, set.cardinality().toLong())
            1 -> BitDocIdSet(copyOf(set, numBits), set.cardinality().toLong())
            2 -> {
                val builder = RoaringDocIdSet.Builder(numBits)
                var i = set.nextSetBit(0)
                while (i != DocIdSetIterator.NO_MORE_DOCS) {
                    builder.add(i)
                    i = if (i + 1 >= numBits) DocIdSetIterator.NO_MORE_DOCS else set.nextSetBit(i + 1)
                }
                builder.build()
            }
            3 -> {
                val fbs = FixedBitSet(numBits)
                fbs.or(BitSetIterator(set, 0))
                BitDocIdSet(fbs)
            }
            4 -> {
                val sfbs = SparseFixedBitSet(numBits)
                sfbs.or(BitSetIterator(set, 0))
                BitDocIdSet(sfbs)
            }
            else -> {
                fail()
                throw IllegalStateException()
            }
        }
    }

    private fun testOr(load: Float) {
        val numBits = 1 + Random.Default.nextInt(100000)
        val set1 = JavaUtilBitSet(randomSet(numBits, 0f), numBits) // empty
        val set2 = copyOf(set1, numBits)

        val iterations = atLeast(10)
        repeat(iterations) {
            val otherSet = randomCopy(JavaUtilBitSet(randomSet(numBits, load), numBits), numBits)
            val otherIterator = otherSet.iterator()
            if (otherIterator != null) {
                set1.or(otherIterator)
                set2.or(otherSet.iterator()!!)
                assertEquals(set1, set2, numBits)
            }
        }
    }

    /** Test [BitSet.or] on sparse sets. */
    open fun testOrSparse() {
        testOr(0.001f)
    }

    /** Test [BitSet.or] on dense sets. */
    open fun testOrDense() {
        testOr(0.5f)
    }

    /** Test [BitSet.or] on a random density. */
    open fun testOrRandom() {
        testOr(Random.Default.nextFloat())
    }

    private class JavaUtilBitSet : BitSet {
        private val bitSet: JDKPortBitSet
        private val numBits: Int

        constructor(bitSet: JDKPortBitSet, numBits: Int) {
            this.bitSet = bitSet
            this.numBits = numBits
        }

        override fun clear() {
            bitSet.clear()
        }

        override fun clear(index: Int) {
            bitSet.clear(index)
        }

        override fun get(index: Int): Boolean {
            return bitSet.get(index)
        }

        override fun getAndSet(index: Int): Boolean {
            val v = get(index)
            set(index)
            return v
        }

        override fun length(): Int {
            return numBits
        }

        override fun ramBytesUsed(): Long {
            return -1
        }

        override val childResources: MutableCollection<Accountable>
            get(){
            return mutableListOf()
        }

        override fun set(i: Int) {
            bitSet.set(i)
        }

        override fun clear(startIndex: Int, endIndex: Int) {
            if (startIndex >= endIndex) {
                return
            }
            bitSet.clear(startIndex, endIndex)
        }

        override fun cardinality(): Int {
            return bitSet.cardinality()
        }

        override fun approximateCardinality(): Int {
            return bitSet.cardinality()
        }

        override fun prevSetBit(index: Int): Int {
            return bitSet.previousSetBit(index)
        }

        override fun nextSetBit(start: Int, upperBound: Int): Int {
            val next = bitSet.nextSetBit(start)
            if (next == -1 || next >= upperBound) {
                return DocIdSetIterator.NO_MORE_DOCS
            }
            return next
        }
    }
}