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

import org.gnit.lucenekmp.jdkport.BitSet
import kotlin.test.Test

class TestSparseFixedBitDocIdSet : BaseDocIdSetTestCase<BitDocIdSet>() {

    override fun copyOf(bs: BitSet, length: Int): BitDocIdSet {
        val set = SparseFixedBitSet(length)
        // SparseFixedBitSet can be sensitive to the order of insertion so
        // randomize insertion a bit
        val buffer = mutableListOf<Int>()
        var doc = bs.nextSetBit(0)
        while (doc != -1) {
            buffer.add(doc)
            if (buffer.size >= 100000) {
                buffer.shuffle(random())
                for (i in buffer) {
                    set.set(i)
                }
                buffer.clear()
            }
            doc = bs.nextSetBit(doc + 1)
        }
        buffer.shuffle(random())
        for (i in buffer) {
            set.set(i)
        }
        return BitDocIdSet(set, set.approximateCardinality().toLong())
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
