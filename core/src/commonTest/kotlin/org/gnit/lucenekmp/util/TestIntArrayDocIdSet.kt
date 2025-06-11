package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.tests.util.BaseDocIdSetTestCase
import org.gnit.lucenekmp.search.DocIdSetIterator
import kotlin.test.Test
internal class TestIntArrayDocIdSet : BaseDocIdSetTestCase<IntArrayDocIdSet>() {
    
    override fun copyOf(bs: BitSet, length: Int): IntArrayDocIdSet {
        var docs = IntArray(0)
        var l = 0
        var doc = bs.nextSetBit(0)
        while (doc != -1) {
            docs = ArrayUtil.grow(docs, length + 1)
            docs[l++] = doc
            doc = bs.nextSetBit(doc + 1)
        }
        docs = ArrayUtil.grow(docs, length + 1)
        docs[l] = DocIdSetIterator.NO_MORE_DOCS
        return IntArrayDocIdSet(docs, l)
    }

    // inherit tests from BaseDocIdSetTestCase, but need @Test annotations
    @Test fun testNoBitWrapper() = testNoBit()
    @Test fun test1BitWrapper() = test1Bit()
    @Test fun test2BitsWrapper() = test2Bits()
    @Test fun testAgainstBitSetWrapper() = testAgainstBitSet()
    @Test fun testRamBytesUsedWrapper() = testRamBytesUsed()
    @Test fun testIntoBitSetWrapper() = testIntoBitSet()
    @Test fun testIntoBitSetBoundChecksWrapper() = testIntoBitSetBoundChecks()
}
