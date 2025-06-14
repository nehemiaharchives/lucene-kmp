package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.tests.util.BaseDocIdSetTestCase
import kotlin.test.assertEquals

class TestRoaringDocIdSet : BaseDocIdSetTestCase<RoaringDocIdSet>() {
    override fun copyOf(bs: BitSet, length: Int): RoaringDocIdSet {
        val builder = RoaringDocIdSet.Builder(length)
        var i = bs.nextSetBit(0)
        while (i != -1) {
            builder.add(i)
            i = bs.nextSetBit(i + 1)
        }
        return builder.build()
    }

    override fun assertEqualsDocSet(numBits: Int, ds1: BitSet, ds2: RoaringDocIdSet) {
        super.assertEqualsDocSet(numBits, ds1, ds2)
        assertEquals(ds1.cardinality(), ds2.cardinality())
    }
}
