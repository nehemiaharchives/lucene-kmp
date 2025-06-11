package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.util.BaseDocIdSetTestCase

class TestNotDocIdSet : BaseDocIdSetTestCase<NotDocIdSet>() {
    @Throws(IOException::class)
    override fun copyOf(bs: BitSet, length: Int): NotDocIdSet {
        val set = FixedBitSet(length)
        var doc = bs.nextClearBit(0)
        while (doc < length) {
            set.set(doc)
            doc = bs.nextClearBit(doc + 1)
        }
        return NotDocIdSet(length, BitDocIdSet(set))
    }
}
