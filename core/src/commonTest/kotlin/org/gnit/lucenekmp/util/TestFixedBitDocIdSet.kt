package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.BitSet
import kotlin.test.Test

class TestFixedBitDocIdSet : BaseDocIdSetTestCase<BitDocIdSet>() {

    override fun copyOf(bs: BitSet, length: Int): BitDocIdSet {
        val set = FixedBitSet(length)
        var doc = bs.nextSetBit(0)
        while (doc != -1) {
            set.set(doc)
            doc = bs.nextSetBit(doc + 1)
        }
        return BitDocIdSet(set)
    }

    // tests inherited from BaseDocIdSetTestCase
    @Test fun testNoBitWrapper() = testNoBit()

    @Test fun test1BitWrapper() = test1Bit()

    @Test fun test2BitsWrapper() = test2Bits()

    @Test fun testAgainstBitSetWrapper() = testAgainstBitSet()

    @Test fun testRamBytesUsedWrapper() = testRamBytesUsed()

    @Test fun testIntoBitSetWrapper() = testIntoBitSet()

    @Test fun testIntoBitSetBoundChecksWrapper() = testIntoBitSetBoundChecks()
}
