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
package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Port of Lucene's TestPackedInts from commit ec75fcad.
 */
class TestPackedInts : LuceneTestCase() {
    @Test
    fun testByteCount() {
        val iters = atLeast(3)
        for (i in 0 until iters) {
            // avoid overflow in TestUtil.nextInt when end == Int.MAX_VALUE
            val valueCount = TestUtil.nextInt(random(), 1, Int.MAX_VALUE - 1)
            for (format in PackedInts.Format.values()) {
                for (bpv in 1..64) {
                    val byteCount = format.byteCount(PackedInts.VERSION_CURRENT, valueCount, bpv)
                    val msg = "format=$format, byteCount=$byteCount, valueCount=$valueCount, bpv=$bpv"
                    assertTrue(byteCount * 8 >= valueCount.toLong() * bpv, msg)
                    if (format == PackedInts.Format.PACKED) {
                        assertTrue((byteCount - 1) * 8 < valueCount.toLong() * bpv, msg)
                    }
                }
            }
        }
    }

    @Test
    fun testBitsRequired() {
        assertEquals(61, PackedInts.bitsRequired(2.0.pow(61.0).toLong() - 1))
        assertEquals(61, PackedInts.bitsRequired(0x1FFFFFFFFFFFFFFFL))
        assertEquals(62, PackedInts.bitsRequired(0x3FFFFFFFFFFFFFFFL))
        assertEquals(63, PackedInts.bitsRequired(0x7FFFFFFFFFFFFFFFL))
        assertEquals(64, PackedInts.unsignedBitsRequired(-1))
        assertEquals(64, PackedInts.unsignedBitsRequired(Long.MIN_VALUE))
        assertEquals(1, PackedInts.bitsRequired(0))
    }

    @Test
    fun testMaxValues() {
        assertEquals(1, PackedInts.maxValue(1), "1 bit -> max == 1")
        assertEquals(3, PackedInts.maxValue(2), "2 bit -> max == 3")
        assertEquals(255, PackedInts.maxValue(8), "8 bit -> max == 255")
        assertEquals(Long.MAX_VALUE, PackedInts.maxValue(63), "63 bit -> max == Long.MAX_VALUE")
        assertEquals(Long.MAX_VALUE, PackedInts.maxValue(64), "64 bit -> max == Long.MAX_VALUE (same as for 63 bit)")
    }
}
