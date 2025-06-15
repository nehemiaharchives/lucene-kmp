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
import org.gnit.lucenekmp.tests.util.RamUsageTester
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.Ignore
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

    @Test
    fun testControlledEquality() {
        val valueCount = 255
        val bitsPerValue = 8
        val packedInts = createPackedInts(valueCount, bitsPerValue)
        for (packed in packedInts) {
            for (i in 0 until packed.size()) {
                packed.set(i, (i + 1).toLong())
            }
        }
        assertListEquality(packedInts)
    }

    @Test
    fun testRandomEquality() {
        val numIters = if (LuceneTestCase.TEST_NIGHTLY) atLeast(2) else 1
        repeat(numIters) {
            val valueCount = TestUtil.nextInt(random(), 1, 300)
            for (bpv in 1..64) {
                assertRandomEquality(valueCount, bpv, random().nextLong())
            }
        }
    }

    @Test
    fun testSecondaryBlockChange() {
        val mutable = Packed64(26, 5)
        mutable.set(24, 31)
        assertEquals(31, mutable.get(24), "The value #24 should be correct")
        mutable.set(4, 16)
        assertEquals(31, mutable.get(24), "The value #24 should remain unchanged")
    }

    @Test
    @Ignore
    fun testFill() {
        val valueCount = 1111
        val from = random().nextInt(valueCount + 1)
        val to = from + random().nextInt(valueCount + 1 - from)
        for (bpv in 1..64) {
            val valToFill = nextLong(random(), 0, PackedInts.maxValue(bpv))
            val packedInts = createPackedInts(valueCount, bpv)
            for (ints in packedInts) {
                val msg = "${ints::class.simpleName} bpv=$bpv, from=$from, to=$to, val=$valToFill"
                ints.fill(0, ints.size(), 1)
                ints.fill(from, to, valToFill)
                for (i in 0 until ints.size()) {
                    if (i in from until to) {
                        assertEquals(valToFill, ints.get(i), "$msg, i=$i")
                    } else {
                        assertEquals(1, ints.get(i), "$msg, i=$i")
                    }
                }
            }
        }
    }

    @Test
    fun testPackedIntsNull() {
        val size = TestUtil.nextInt(random(), 11, 256)
        val packed = PackedInts.NullReader.forCount(size)
        assertEquals(0, packed.get(TestUtil.nextInt(random(), 0, size - 1)))
        val arr = LongArray(size + 10) { 1 }
        var r = packed.get(0, arr, 0, size - 1)
        assertEquals(size - 1, r)
        for (i in 0 until r) {
            assertEquals(0, arr[i])
        }
        for (i in arr.indices) arr[i] = 1
        r = packed.get(10, arr, 0, size + 10)
        assertEquals(size - 10, r)
        for (i in 0 until size - 10) {
            assertEquals(0, arr[i])
        }
    }

    @Test
    @Ignore
    fun testBulkGet() {
        val valueCount = 1111
        val index = random().nextInt(valueCount)
        val len = TestUtil.nextInt(random(), 1, valueCount * 2)
        val off = random().nextInt(77)

        for (bpv in 1..64) {
            val mask = PackedInts.maxValue(bpv)
            val packedInts = createPackedInts(valueCount, bpv)
            for (ints in packedInts) {
                for (i in 0 until ints.size()) {
                    ints.set(i, (31L * i - 1099) and mask)
                }
                val arr = LongArray(off + len)
                val msg = "${ints::class.simpleName} valueCount=$valueCount, index=$index, len=$len, off=$off"
                val gets = ints.get(index, arr, off, len)
                assertTrue(gets > 0, msg)
                assertTrue(gets <= len, msg)
                assertTrue(gets <= ints.size() - index, msg)
                for (i in arr.indices) {
                    val m = "$msg, i=$i"
                    if (i in off until off + gets) {
                        assertEquals(ints.get(i - off + index), arr[i], m)
                    } else {
                        assertEquals(0, arr[i], m)
                    }
                }
            }
        }
    }

    @Test
    @Ignore
    fun testBulkSet() {
        val valueCount = 1111
        val index = random().nextInt(valueCount)
        val len = TestUtil.nextInt(random(), 1, valueCount * 2)
        val off = random().nextInt(77)
        val arr = LongArray(off + len)

        for (bpv in 1..64) {
            val mask = PackedInts.maxValue(bpv)
            val packedInts = createPackedInts(valueCount, bpv)
            for (i in arr.indices) {
                arr[i] = (31L * i + 19) and mask
            }
            for (ints in packedInts) {
                val msg = "${ints::class.simpleName} valueCount=$valueCount, index=$index, len=$len, off=$off"
                val sets = ints.set(index, arr, off, len)
                assertTrue(sets > 0, msg)
                assertTrue(sets <= len, msg)
                for (i in 0 until ints.size()) {
                    val m = "$msg, i=$i"
                    if (i in index until index + sets) {
                        assertEquals(arr[off - index + i], ints.get(i), m)
                    } else {
                        assertEquals(0, ints.get(i), m)
                    }
                }
            }
        }
    }

    @Test
    @Ignore
    fun testCopy() {
        val valueCount = TestUtil.nextInt(random(), 5, 600)
        val off1 = random().nextInt(valueCount)
        val off2 = random().nextInt(valueCount)
        val len = random().nextInt(kotlin.math.min(valueCount - off1, valueCount - off2))
        val mem = random().nextInt(1024)

        for (bpv in 1..64) {
            val mask = PackedInts.maxValue(bpv)
            for (r1 in createPackedInts(valueCount, bpv)) {
                for (i in 0 until r1.size()) {
                    r1.set(i, (31L * i - 1023) and mask)
                }
                for (r2 in createPackedInts(valueCount, bpv)) {
                    val msg = "src=$r1, dest=$r2, srcPos=$off1, destPos=$off2, len=$len, mem=$mem"
                    PackedInts.copy(r1, off1, r2, off2, len, mem)
                    for (i in 0 until r2.size()) {
                        val m = "$msg, i=$i"
                        if (i in off2 until off2 + len) {
                            assertEquals(r1.get(i - off2 + off1), r2.get(i), m)
                        } else {
                            assertEquals(0, r2.get(i), m)
                        }
                    }
                }
            }
        }
    }

    @Test
    @Ignore
    fun testGrowableWriter() {
        val valueCount = 113 + random().nextInt(1111)
        var wrt = GrowableWriter(1, valueCount, PackedInts.DEFAULT)
        wrt.set(4, 2)
        wrt.set(7, 10)
        wrt.set(valueCount - 10, 99)
        wrt.set(99, 999)
        wrt.set(valueCount - 1, 1 shl 10)
        assertEquals(1 shl 10, wrt.get(valueCount - 1))
        wrt.set(99, (1 shl 23) - 1)
        assertEquals(1 shl 10, wrt.get(valueCount - 1))
        wrt.set(1, Long.MAX_VALUE)
        wrt.set(2, -3)
        assertEquals(64, wrt.bitsPerValue)
        assertEquals(1 shl 10, wrt.get(valueCount - 1))
        assertEquals(Long.MAX_VALUE, wrt.get(1))
        assertEquals(-3L, wrt.get(2))
        assertEquals(2, wrt.get(4))
        assertEquals((1 shl 23) - 1, wrt.get(99))
        assertEquals(10, wrt.get(7))
        assertEquals(99, wrt.get(valueCount - 10))
        assertEquals(1 shl 10, wrt.get(valueCount - 1))
        assertEquals(RamUsageTester.ramUsed(wrt), wrt.ramBytesUsed())
    }

    // helper methods ---------------------------------------------------------

    private fun createPackedInts(valueCount: Int, bitsPerValue: Int): List<PackedInts.Mutable> {
        val list = mutableListOf<PackedInts.Mutable>()
        list.add(Packed64(valueCount, bitsPerValue))
        for (bpv in bitsPerValue..Packed64SingleBlock.MAX_SUPPORTED_BITS_PER_VALUE) {
            if (Packed64SingleBlock.isSupported(bpv)) {
                list.add(Packed64SingleBlock.create(valueCount, bpv))
            }
        }
        return list
    }

    private fun fill(packedInt: PackedInts.Mutable, bitsPerValue: Int, randomSeed: Long) {
        val rnd = Random(randomSeed)
        val maxValue = if (bitsPerValue == 64) Long.MAX_VALUE else (1L shl bitsPerValue) - 1
        for (i in 0 until packedInt.size()) {
            val value = if (bitsPerValue == 64) random().nextLong() else nextLong(rnd, 0, maxValue)
            packedInt.set(i, value)
            assertEquals(value, packedInt.get(i), "The set/get of the value at index $i should match for ${packedInt::class.simpleName}")
        }
    }

    private fun assertRandomEquality(valueCount: Int, bitsPerValue: Int, randomSeed: Long) {
        val packedInts = createPackedInts(valueCount, bitsPerValue)
        for (packed in packedInts) {
            fill(packed, bitsPerValue, randomSeed)
        }
        assertListEquality(packedInts)
    }

    private fun assertListEquality(packedInts: List<out PackedInts.Reader>) {
        assertListEquality("", packedInts)
    }

    private fun assertListEquality(message: String, packedInts: List<out PackedInts.Reader>) {
        if (packedInts.isEmpty()) return
        val base = packedInts[0]
        val valueCount = base.size()
        for (pi in packedInts) {
            assertEquals(valueCount, pi.size(), "$message. The number of values should be the same ")
        }
        for (i in 0 until valueCount) {
            for (j in 1 until packedInts.size) {
                val msg = "$message. The value at index $i should be the same for ${base::class.simpleName} and ${packedInts[j]::class.simpleName}"
                assertEquals(base.get(i), packedInts[j].get(i), msg)
            }
        }
    }

    private fun nextLong(r: Random, start: Long, end: Long): Long {
        require(end >= start)
        if (start == end) {
            return start
        }
        return if (end == Long.MAX_VALUE) {
            var v: Long
            do {
                v = r.nextLong()
            } while (v < start)
            v
        } else {
            r.nextLong(start, end + 1)
        }
    }
}
