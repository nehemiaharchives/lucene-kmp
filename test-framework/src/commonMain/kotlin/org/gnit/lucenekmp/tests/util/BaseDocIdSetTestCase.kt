package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.jdkport.BitSet
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Base test class for DocIdSets. */
abstract class BaseDocIdSetTestCase<T : DocIdSet> : LuceneTestCase() {

    /** Create a copy of the given BitSet which has `length` bits. */
    abstract fun copyOf(bs: BitSet, length: Int): T

    /** Test length=0. */
    fun testNoBit() {
        val bs = BitSet()
        val copy = copyOf(bs, 1)
        assertEqualsDocSet(1, bs, copy)
    }

    /** Test length=1. */
    fun test1Bit() {
        val bs = BitSet(1)
        if (Random.Default.nextBoolean()) {
            bs.set(0)
        }
        val copy = copyOf(bs, 1)
        assertEqualsDocSet(1, bs, copy)
    }

    /** Test length=2. */
    fun test2Bits() {
        val bs = BitSet(2)
        if (Random.Default.nextBoolean()) {
            bs.set(0)
        }
        if (Random.Default.nextBoolean()) {
            bs.set(1)
        }
        val copy = copyOf(bs, 2)
        assertEqualsDocSet(2, bs, copy)
    }

    /** Compare the content of the set against a BitSet. */
    fun testAgainstBitSet() {
        val random = Random.Default
        val numBits = TestUtil.nextInt(random, 100, 1 shl 20)
        for (percentSet in arrayOf(0f, 0.0001f, random.nextFloat(), 0.9f, 1f)) {
            val set = randomSet(numBits, percentSet)
            val copy = copyOf(set, numBits)
            assertEqualsDocSet(numBits, set, copy)
        }
        var set = BitSet(numBits)
        set.set(0)
        var copy = copyOf(set, numBits)
        assertEqualsDocSet(numBits, set, copy)
        set.clear(0)
        set.set(random.nextInt(numBits))
        copy = copyOf(set, numBits)
        assertEqualsDocSet(numBits, set, copy)
        var iterations = 0
        val maxIterations = if (TEST_NIGHTLY) Int.MAX_VALUE else 10
        var inc = 2
        while (inc < 1000 && iterations < maxIterations) {
            iterations++
            set = BitSet(numBits)
            var d = random.nextInt(10)
            while (d < numBits) {
                set.set(d)
                d += inc
            }
            copy = copyOf(set, numBits)
            assertEqualsDocSet(numBits, set, copy)
            inc += TestUtil.nextInt(random, 1, 100)
        }
    }

    /** Test ram usage estimation. */
    fun testRamBytesUsed() {
        val random = Random.Default
        val iters = 100
        repeat(iters) {
            val pow = random.nextInt(20)
            val maxDoc = TestUtil.nextInt(random, 1, 1 shl pow)
            val numDocs = TestUtil.nextInt(random, 0, kotlin.math.min(maxDoc, 1 shl TestUtil.nextInt(random, 0, pow)))
            val set = randomSet(maxDoc, numDocs)
            val copy: DocIdSet = copyOf(set, maxDoc)
            val actualBytes = ramBytesUsed(copy, maxDoc)
            val expectedBytes = copy.ramBytesUsed()
            assertEquals(expectedBytes, actualBytes)
        }
    }

    /** Assert that the content of the DocIdSet is the same as the content of the BitSet. */
    fun assertEqualsDocSet(numBits: Int, ds1: BitSet, ds2: T) {
        val random = Random.Default
        var it2: DocIdSetIterator? = ds2.iterator()
        if (it2 == null) {
            assertEquals(-1, ds1.nextSetBit(0))
        } else {
            assertEquals(-1, it2.docID())
            var doc = ds1.nextSetBit(0)
            while (doc != -1) {
                assertEquals(doc, it2.nextDoc())
                assertEquals(doc, it2.docID())
                doc = ds1.nextSetBit(doc + 1)
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, it2.nextDoc())
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, it2.docID())
        }

        it2 = ds2.iterator()
        if (it2 == null) {
            assertEquals(-1, ds1.nextSetBit(0))
        } else {
            var doc = -1
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                if (random.nextBoolean()) {
                    doc = ds1.nextSetBit(doc + 1)
                    if (doc == -1) doc = DocIdSetIterator.NO_MORE_DOCS
                    assertEquals(doc, it2.nextDoc())
                    assertEquals(doc, it2.docID())
                } else {
                    val target = doc + 1 + random.nextInt(if (random.nextBoolean()) 64 else kotlin.math.max(numBits / 8, 1))
                    doc = ds1.nextSetBit(target)
                    if (doc == -1) doc = DocIdSetIterator.NO_MORE_DOCS
                    assertEquals(doc, it2.advance(target))
                    assertEquals(doc, it2.docID())
                }
            }
        }
    }

    private class Dummy {
        var o1: Any? = null
        var o2: Any? = null
    }

    private fun ramBytesUsed(set: DocIdSet, length: Int): Long {
        return set.ramBytesUsed()
    }
    fun testIntoBitSet() {
        val random = Random.Default
        val numBits = TestUtil.nextInt(random, 100, 1 shl 20)
        for (percentSet in arrayOf(0f, 0.0001f, random.nextFloat(), 0.9f, 1f)) {
            val set = randomSet(numBits, percentSet)
            val copy = copyOf(set, numBits)
            val from = TestUtil.nextInt(Random, 0, numBits - 1)
            val to = TestUtil.nextInt(Random, from, numBits + 5)
            val actual = FixedBitSet(to - from)
            val it1 = copy.iterator() ?: continue
            val fromDoc = it1.advance(from)
            it1.intoBitSet(from, actual, from)
            assertTrue(actual.scanIsEmpty())
            assertEquals(fromDoc, it1.docID())

            it1.intoBitSet(to, actual, from)
            val expected = FixedBitSet(to - from)
            val it2 = copy.iterator()
            var doc = it2.advance(from)
            while (doc < to) {
                expected.set(doc - from)
                doc = it2.nextDoc()
            }
            assertEquals(expected, actual)
            assertEquals(it2.docID(), it1.docID())
            if (it2.docID() != DocIdSetIterator.NO_MORE_DOCS) {
                assertEquals(it2.nextDoc(), it1.nextDoc())
            }
        }
    }

    fun testIntoBitSetBoundChecks() {
        val set = BitSet()
        set.set(20)
        set.set(42)
        val copy = copyOf(set, 256)
        val from = TestUtil.nextInt(Random, 0, 20)
        val to = TestUtil.nextInt(Random, 43, 256)
        val offset = TestUtil.nextInt(Random, 0, from)
        val dest1 = FixedBitSet(42 - offset + 1)
        val it1 = copy.iterator()
        it1.advance(from)
        it1.intoBitSet(to, dest1, offset)
        for (i in 0 until dest1.length()) {
            assertEquals(offset + i == 20 || offset + i == 42, dest1.get(i))
        }

        val dest2 = FixedBitSet(42 - offset)
        val it2 = copy.iterator()
        it2.advance(from)
        expectThrows(Throwable::class) { it2.intoBitSet(to, dest2, offset) }

        val dest3 = FixedBitSet(42 - offset + 1)
        val it3 = copy.iterator()
        it3.advance(from)
        expectThrows(Throwable::class) { it3.intoBitSet(to, dest3, 21) }
    }

    companion object {
        fun randomSet(numBits: Int, numBitsSet: Int): BitSet {
            require(numBitsSet <= numBits)
            val set = BitSet(numBits)
            if (numBitsSet == numBits) {
                for (i in 0 until numBits) {
                    set.set(i)
                }
            } else {
                val random = Random.Default
                for (i in 0 until numBitsSet) {
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

        fun randomSet(numBits: Int, percentSet: Float): BitSet {
            return randomSet(numBits, (percentSet * numBits).toInt())
        }
    }
}
