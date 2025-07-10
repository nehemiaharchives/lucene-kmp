package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.tests.util.BaseDocIdSetTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRoaringDocIdSet : BaseDocIdSetTestCase<RoaringDocIdSet>() {

    @Test
    override fun testNoBit() = super.testNoBit()

    @Test
    override fun test1Bit() = super.test1Bit()

    @Test
    override fun test2Bits() = super.test2Bits()

    @Test
    override fun testAgainstBitSet() = super.testAgainstBitSet()

    @Test
    override fun testRamBytesUsed() = super.testRamBytesUsed()

    @Test
    override fun testIntoBitSet() = super.testIntoBitSet()

    @Test
    override fun testIntoBitSetBoundChecks() = super.testIntoBitSetBoundChecks()

    override fun copyOf(bs: BitSet, length: Int): RoaringDocIdSet {
        val builder = RoaringDocIdSet.Builder(length)
        var i = bs.nextSetBit(0)
        while (i != -1) {
            builder.add(i)
            i = bs.nextSetBit(i + 1)
        }
        return builder.build()
    }

    override fun assertEquals(numBits: Int, ds1: BitSet, ds2: RoaringDocIdSet) {
        super.assertEquals(numBits, ds1, ds2)
        assertEquals(ds1.cardinality(), ds2.cardinality())
    }
}
