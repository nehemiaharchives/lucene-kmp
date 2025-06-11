package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class TestLongBitSet : LuceneTestCase() {

    private fun doGet(a: java.util.BitSet, b: LongBitSet) {
        assertEquals(a.cardinality().toLong(), b.cardinality())
        val max = b.length()
        for (i in 0 until max) {
            if (a.get(i.toInt()) != b.get(i)) {
                fail("mismatch: BitSet=[" + i + "]=" + a.get(i.toInt()))
            }
        }
    }

    private fun doNextSetBit(a: java.util.BitSet, b: LongBitSet) {
        assertEquals(a.cardinality().toLong(), b.cardinality())
        var aa = -1
        var bb: Long = -1
        do {
            aa = a.nextSetBit(aa + 1)
            bb = if (bb < b.length() - 1) b.nextSetBit(bb + 1) else -1
            assertEquals(aa.toLong(), bb)
        } while (aa >= 0)
    }

    private fun doPrevSetBit(a: java.util.BitSet, b: LongBitSet) {
        assertEquals(a.cardinality().toLong(), b.cardinality())
        var aa = a.size() + random().nextInt(100)
        var bb: Long = aa.toLong()
        do {
            aa--
            while (aa >= 0 && !a.get(aa)) {
                aa--
            }
            bb = when {
                b.length() == 0L -> -1
                bb > b.length() - 1 -> b.prevSetBit(b.length() - 1)
                bb < 1 -> -1
                else -> if (bb >= 1) b.prevSetBit(bb - 1) else -1
            }
            assertEquals(aa.toLong(), bb)
        } while (aa >= 0)
    }

    @Throws(Exception::class)
    private fun doRandomSets(maxSize: Int, iter: Int, mode: Int) {
        var a0: java.util.BitSet? = null
        var b0: LongBitSet? = null

        for (i in 0 until iter) {
            val sz = TestUtil.nextInt(random(), 2, maxSize)
            val a = java.util.BitSet(sz)
            val b = LongBitSet(sz.toLong())

            if (sz > 0) {
                val nOper = random().nextInt(sz)
                for (j in 0 until nOper) {
                    var idx: Int

                    idx = random().nextInt(sz)
                    a.set(idx)
                    b.set(idx.toLong())

                    idx = random().nextInt(sz)
                    a.clear(idx)
                    b.clear(idx.toLong())

                    idx = random().nextInt(sz)
                    a.flip(idx, idx + 1)
                    b.flip(idx.toLong(), (idx + 1).toLong())

                    idx = random().nextInt(sz)
                    a.flip(idx)
                    b.flip(idx.toLong())

                    val val2 = b.get(idx.toLong())
                    val value = b.getAndSet(idx.toLong())
                    assertTrue(val2 == value)
                    assertTrue(b.get(idx.toLong()))

                    if (!value) b.clear(idx.toLong())
                    assertTrue(b.get(idx.toLong()) == value)
                }
            }

            doGet(a, b)

            var fromIndex: Int
            var toIndex: Int
            fromIndex = random().nextInt(sz / 2)
            toIndex = fromIndex + random().nextInt(sz - fromIndex)
            var aa = a.clone() as java.util.BitSet
            aa.flip(fromIndex, toIndex)
            var bb = b.clone()
            bb.flip(fromIndex.toLong(), toIndex.toLong())

            fromIndex = random().nextInt(sz / 2)
            toIndex = fromIndex + random().nextInt(sz - fromIndex)
            aa = a.clone() as java.util.BitSet
            aa.clear(fromIndex, toIndex)
            bb = b.clone()
            bb.clear(fromIndex.toLong(), toIndex.toLong())

            doNextSetBit(aa, bb)
            doPrevSetBit(aa, bb)

            fromIndex = random().nextInt(sz / 2)
            toIndex = fromIndex + random().nextInt(sz - fromIndex)
            aa = a.clone() as java.util.BitSet
            aa.set(fromIndex, toIndex)
            bb = b.clone()
            bb.set(fromIndex.toLong(), toIndex.toLong())

            doNextSetBit(aa, bb)
            doPrevSetBit(aa, bb)

            if (b0 != null && b0!!.length() <= b.length()) {
                assertEquals(a.cardinality().toLong(), b.cardinality())

                val a_and = a.clone() as java.util.BitSet
                a_and.and(a0)
                val a_or = a.clone() as java.util.BitSet
                a_or.or(a0)
                val a_xor = a.clone() as java.util.BitSet
                a_xor.xor(a0)
                val a_andn = a.clone() as java.util.BitSet
                a_andn.andNot(a0)

                val b_and = b.clone()
                assertEquals(b, b_and)
                b_and.and(b0!!)
                val b_or = b.clone()
                b_or.or(b0!!)
                val b_xor = b.clone()
                b_xor.xor(b0!!)
                val b_andn = b.clone()
                b_andn.andNot(b0!!)

                assertEquals(a0!!.cardinality().toLong(), b0!!.cardinality())
                assertEquals(a_or.cardinality().toLong(), b_or.cardinality())

                assertEquals(a_and.cardinality().toLong(), b_and.cardinality())
                assertEquals(a_or.cardinality().toLong(), b_or.cardinality())
                assertEquals(a_xor.cardinality().toLong(), b_xor.cardinality())
                assertEquals(a_andn.cardinality().toLong(), b_andn.cardinality())
            }

            a0 = a
            b0 = b
        }
    }

    @Test
    fun testSmall() {
        val iters = if (TEST_NIGHTLY) atLeast(1000) else 100
        doRandomSets(atLeast(1200), iters, 1)
        doRandomSets(atLeast(1200), iters, 2)
    }

    @Test
    fun testEquals() {
        val numBits = random().nextInt(2000) + 1
        val b1 = LongBitSet(numBits.toLong())
        val b2 = LongBitSet(numBits.toLong())
        assertTrue(b1 == b2)
        assertTrue(b2 == b1)
        for (iter in 0 until 10 * RANDOM_MULTIPLIER) {
            val idx = random().nextInt(numBits)
            if (!b1.get(idx.toLong())) {
                b1.set(idx.toLong())
                assertFalse(b1 == b2)
                assertFalse(b2 == b1)
                b2.set(idx.toLong())
                assertTrue(b1 == b2)
                assertTrue(b2 == b1)
            }
        }
        assertFalse(b1.equals(Any()))
    }

    @Test
    fun testHashCodeEquals() {
        val numBits = random().nextInt(2000) + 1
        val b1 = LongBitSet(numBits.toLong())
        val b2 = LongBitSet(numBits.toLong())
        assertTrue(b1 == b2)
        assertTrue(b2 == b1)
        for (iter in 0 until 10 * RANDOM_MULTIPLIER) {
            val idx = random().nextInt(numBits)
            if (!b1.get(idx.toLong())) {
                b1.set(idx.toLong())
                assertFalse(b1 == b2)
                assertFalse(b1.hashCode() == b2.hashCode())
                b2.set(idx.toLong())
                assertEquals(b1, b2)
                assertEquals(b1.hashCode(), b2.hashCode())
            }
        }
    }

    @Test
    fun testTooLarge() {
        val e = expectThrows(IllegalArgumentException::class) {
            LongBitSet(LongBitSet.MAX_NUM_BITS + 1)
        }
        assertTrue(e!!.message!!.startsWith("numBits must be 0 .. "))
    }

    @Test
    fun testNegativeNumBits() {
        val e = expectThrows(IllegalArgumentException::class) {
            LongBitSet(-17)
        }
        assertTrue(e!!.message!!.startsWith("numBits must be 0 .. "))
    }

    @Test
    fun testSmallBitSets() {
        for (numBits in 0 until 10) {
            val b1 = LongBitSet(numBits.toLong())
            val b2 = LongBitSet(numBits.toLong())
            assertTrue(b1 == b2)
            assertEquals(b1.hashCode(), b2.hashCode())
            assertEquals(0, b1.cardinality())
            if (numBits > 0) {
                b1.set(0, numBits.toLong())
                assertEquals(numBits.toLong(), b1.cardinality())
                b1.flip(0, numBits.toLong())
                assertEquals(0, b1.cardinality())
            }
        }
    }

    private fun makeLongBitSet(a: IntArray, numBits: Int): LongBitSet {
        val bs: LongBitSet = if (random().nextBoolean()) {
            val bits2words = LongBitSet.bits2words(numBits.toLong())
            val words = LongArray(bits2words + random().nextInt(100))
            LongBitSet(words, numBits.toLong())
        } else {
            LongBitSet(numBits.toLong())
        }
        for (e in a) {
            bs.set(e.toLong())
        }
        return bs
    }

    private fun makeBitSet(a: IntArray): java.util.BitSet {
        val bs = java.util.BitSet()
        for (e in a) {
            bs.set(e)
        }
        return bs
    }

    private fun checkPrevSetBitArray(a: IntArray, numBits: Int) {
        val obs = makeLongBitSet(a, numBits)
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
        val obs = makeLongBitSet(a, numBits)
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
        val bits = LongBitSet(5)
        bits.set(1)
        bits.set(4)

        var newBits = LongBitSet.ensureCapacity(bits, 8)
        assertTrue(newBits.get(1))
        assertTrue(newBits.get(4))
        newBits.clear(1)
        assertTrue(bits.get(1))
        assertFalse(newBits.get(1))

        newBits.set(1)
        newBits = LongBitSet.ensureCapacity(newBits, newBits.length() - 2)
        assertTrue(newBits.get(1))

        bits.set(1)
        newBits = LongBitSet.ensureCapacity(bits, 72)
        assertTrue(newBits.get(1))
        assertTrue(newBits.get(4))
        newBits.clear(1)
        assertTrue(bits.get(1))
        assertFalse(newBits.get(1))
    }

    @Test
    fun testHugeCapacity() {
        val moreThanMaxInt = Int.MAX_VALUE.toLong() + 5

        val bits = LongBitSet(42)
        assertEquals(42, bits.length())

        val hugeBits = LongBitSet.ensureCapacity(bits, moreThanMaxInt)

        assertTrue(hugeBits.length() >= moreThanMaxInt)
    }

    @Test
    fun testBits2Words() {
        assertEquals(0, LongBitSet.bits2words(0))
        assertEquals(1, LongBitSet.bits2words(1))
        assertEquals(1, LongBitSet.bits2words(64))
        assertEquals(2, LongBitSet.bits2words(65))
        assertEquals(2, LongBitSet.bits2words(128))
        assertEquals(3, LongBitSet.bits2words(129))
        assertEquals(1 shl (31 - 6), LongBitSet.bits2words(Int.MAX_VALUE.toLong() + 1))
        assertEquals((1 shl (31 - 6)) + 1, LongBitSet.bits2words(Int.MAX_VALUE.toLong() + 2))
        assertEquals(1 shl (32 - 6), LongBitSet.bits2words(1L shl 32))
        assertEquals((1 shl (32 - 6)) + 1, LongBitSet.bits2words((1L shl 32) + 1))
        assertTrue(LongBitSet.bits2words(LongBitSet.MAX_NUM_BITS) > 0)
    }
}

