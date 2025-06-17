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
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.util.LongsRef
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.LongBuffer
import org.gnit.lucenekmp.util.packed.PagedGrowableWriter
import org.gnit.lucenekmp.util.packed.PagedMutable
import org.gnit.lucenekmp.util.packed.PackedLongValues
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.Ignore
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Port of Lucene's TestPackedInts from commit ec75fcad.
 */
class TestPackedInts : LuceneTestCase() {
    private enum class DataType { PACKED, DELTA_PACKED, MONOTONIC }
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
    fun testPackedInts() {
        val iters = atLeast(3)
        repeat(iters) {
            for (nbits in 1..64) {
                val maxValue = PackedInts.maxValue(nbits)
                val valueCount = TestUtil.nextInt(random(), 1, 600)
                val bufferSize = if (random().nextBoolean()) {
                    TestUtil.nextInt(random(), 0, 48)
                } else {
                    TestUtil.nextInt(random(), 0, 4096)
                }

                val byteCount = PackedInts.Format.PACKED.byteCount(
                    PackedInts.VERSION_CURRENT,
                    valueCount,
                    nbits
                ).toInt()
                val bytes = ByteArray(byteCount)
                val out = ByteArrayDataOutput(bytes)
                val mem = random().nextInt(2 * PackedInts.DEFAULT_BUFFER_SIZE)
                val writer = PackedInts.getWriterNoHeader(
                    out,
                    PackedInts.Format.PACKED,
                    valueCount,
                    nbits,
                    mem
                )
                val start = out.position
                val actualValueCount = if (random().nextBoolean()) {
                    valueCount
                } else {
                    TestUtil.nextInt(random(), 0, valueCount)
                }
                val values = LongArray(valueCount)
                for (i in 0 until actualValueCount) {
                    val v = if (nbits == 64) random().nextLong() else nextLong(random(), 0, maxValue)
                    values[i] = v
                    writer.add(v)
                }
                writer.finish()
                val fp = out.position
                val expectedBytes = PackedInts.Format.PACKED.byteCount(
                    PackedInts.VERSION_CURRENT,
                    valueCount,
                    nbits
                )
                assertEquals(expectedBytes, (fp - start).toLong())

                // reader iterator next
                val input1 = ByteArrayDataInput(bytes, 0, fp)
                val r1 = PackedInts.getReaderIteratorNoHeader(
                    input1,
                    PackedInts.Format.PACKED,
                    PackedInts.VERSION_CURRENT,
                    valueCount,
                    nbits,
                    bufferSize
                )
                for (i in 0 until valueCount) {
                    val msg = "index=$i valueCount=$valueCount nbits=$nbits for ${r1::class.simpleName}"
                    assertEquals(values[i], r1.next(), msg)
                    assertEquals(i, r1.ord())
                }
                assertEquals(fp.toLong(), input1.position.toLong())

                // reader iterator bulk next
                val input2 = ByteArrayDataInput(bytes, 0, fp)
                val r2 = PackedInts.getReaderIteratorNoHeader(
                    input2,
                    PackedInts.Format.PACKED,
                    PackedInts.VERSION_CURRENT,
                    valueCount,
                    nbits,
                    bufferSize
                )
                var i = 0
                while (i < valueCount) {
                    val count = TestUtil.nextInt(random(), 1, 95)
                    val next = r2.next(count)
                    for (k in 0 until next.length) {
                        val msg = "index=${i + k} valueCount=$valueCount nbits=$nbits for ${r2::class.simpleName}"
                        assertEquals(values[i + k], next.longs[next.offset + k], msg)
                    }
                    i += next.length
                }
                assertEquals(fp.toLong(), input2.position.toLong())
            }
        }
    }

    @Test
    fun testEndPointer() {
        val valueCount = TestUtil.nextInt(random(), 1, 1000)
        for (version in PackedInts.VERSION_START..PackedInts.VERSION_CURRENT) {
            for (bpv in 1..64) {
                for (format in PackedInts.Format.values()) {
                    if (format != PackedInts.Format.PACKED) continue
                    if (!format.isSupported(bpv)) continue
                    val byteCount = format.byteCount(version, valueCount, bpv)
                    val bytes = ByteArray(byteCount.toInt())
                    val input = ByteArrayDataInput(bytes)
                    val msg = "format=$format,version=$version,valueCount=$valueCount,bpv=$bpv"
                    val iterator = PackedInts.getReaderIteratorNoHeader(
                        input,
                        format,
                        version,
                        valueCount,
                        bpv,
                        TestUtil.nextInt(random(), 1, 1 shl 16)
                    )
                    repeat(valueCount) { iterator.next() }
                    assertEquals(byteCount, input.position.toLong(), msg)
                }
            }
        }
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
    fun testRandomBulkCopy() {
        val rnd = random()
        val numIters = atLeast(rnd, 3)
        repeat(numIters) {
            val valueCount = if (LuceneTestCase.TEST_NIGHTLY) atLeast(rnd, 100000) else atLeast(rnd, 10000)
            var bits1 = TestUtil.nextInt(rnd, 1, 64)
            var bits2 = TestUtil.nextInt(rnd, 1, 64)
            if (bits1 > bits2) {
                val tmp = bits1
                bits1 = bits2
                bits2 = tmp
            }
            val packed1 = PackedInts.getMutable(valueCount, bits1, PackedInts.COMPACT)
            val packed2 = PackedInts.getMutable(valueCount, bits2, PackedInts.COMPACT)
            val maxValue = PackedInts.maxValue(bits1)
            for (i in 0 until valueCount) {
                val v = nextLong(rnd, 0, maxValue)
                packed1.set(i, v)
                packed2.set(i, v)
            }
            val buffer = LongArray(valueCount)
            repeat(20) {
                val start = rnd.nextInt(valueCount - 1)
                val len = TestUtil.nextInt(rnd, 1, valueCount - start)
                val offset = if (len == valueCount) 0 else rnd.nextInt(valueCount - len)
                if (rnd.nextBoolean()) {
                    val got = packed1.get(start, buffer, offset, len)
                    assertTrue(got <= len)
                    val sot = packed2.set(start, buffer, offset, got)
                    assertTrue(sot <= got)
                } else {
                    PackedInts.copy(packed1, offset, packed2, offset, len, rnd.nextInt(10 * len))
                }
            }
            for (i in 0 until valueCount) {
                assertEquals(packed1.get(i), packed2.get(i), "value $i")
            }
        }
    }

    @Test
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

    @Test
    @Ignore
    fun testIntOverflow() {
        val index = (1 shl 30) + 1
        val bits = 2
        var p64: Packed64? = null
        try {
            p64 = Packed64(index, bits)
        } catch (_: OutOfMemoryError) {
        }
        if (p64 != null) {
            p64.set(index - 1, 1)
            assertEquals(1L, p64.get(index - 1), "The value at position ${index - 1} should be correct for Packed64")
            p64 = null
        }
        var p64sb: Packed64SingleBlock? = null
        try {
            p64sb = Packed64SingleBlock.create(index, bits)
        } catch (_: OutOfMemoryError) {
        }
        if (p64sb != null) {
            p64sb.set(index - 1, 1)
            assertEquals(1L, p64sb.get(index - 1), "The value at position ${index - 1} should be correct for ${p64sb::class.simpleName}")
        }
    }

    @Test
    @Ignore
    fun testPagedGrowableWriter() {
        val rnd = random()
        val pageSize = 1 shl TestUtil.nextInt(rnd, 6, 30)
        var writer = PagedGrowableWriter(0, pageSize, TestUtil.nextInt(rnd, 1, 64), rnd.nextFloat())
        assertEquals(0L, writer.size())

        val buf = PackedLongValues.deltaPackedBuilder(rnd.nextFloat())
        val size = if (LuceneTestCase.TEST_NIGHTLY) rnd.nextInt(1_000_000) else rnd.nextInt(100_000)
        var max = 5L
        for (i in 0 until size) {
            buf.add(nextLong(rnd, 0, max))
            if (TestUtil.rarely(rnd)) {
                max = PackedInts.maxValue(
                    if (TestUtil.rarely(rnd)) TestUtil.nextInt(rnd, 0, 63) else TestUtil.nextInt(rnd, 0, 31)
                )
            }
        }
        writer = PagedGrowableWriter(size.toLong(), pageSize, TestUtil.nextInt(rnd, 1, 64), rnd.nextFloat())
        assertEquals(size.toLong(), writer.size())
        val values = buf.build()
        for (i in size - 1 downTo 0) {
            writer.set(i.toLong(), values.get(i.toLong()))
        }
        for (i in 0 until size) {
            assertEquals(values.get(i.toLong()), writer.get(i.toLong()))
        }
        assertEquals(RamUsageTester.ramUsed(writer), writer.ramBytesUsed())

        val copySize = nextLong(rnd, writer.size() / 2, writer.size() * 3 / 2)
        val copy = writer.resize(copySize)
        for (i in 0 until copy.size()) {
            if (i < writer.size()) {
                assertEquals(writer.get(i), copy.get(i))
            } else {
                assertEquals(0L, copy.get(i))
            }
        }

        val growSize = nextLong(rnd, writer.size() / 2, writer.size() * 3 / 2)
        val grow = writer.grow(growSize)
        for (i in 0 until grow.size()) {
            if (i < writer.size()) {
                assertEquals(writer.get(i), grow.get(i))
            } else {
                assertEquals(0L, grow.get(i))
            }
        }
    }

    @Test
    fun testPagedGrowableWriterOverflow() {
        val size = nextLong(random(), 2L * Int.MAX_VALUE, 3L * Int.MAX_VALUE)
        val pageSize = 1 shl TestUtil.nextInt(random(), 16, 30)
        val writer = PagedGrowableWriter(size, pageSize, 1, random().nextFloat(), false)
        val index = nextLong(random(), Int.MAX_VALUE.toLong(), size - 1)
        val pageIdx = writer.pageIndex(index)
        val valueCount = if (pageIdx == writer.subMutables.size - 1) writer.lastPageSize(size) else writer.pageSize()
        writer.subMutables[pageIdx] = GrowableWriter(1, valueCount, writer.acceptableOverheadRatio)
        writer.set(index, 2)
        assertEquals(2L, writer.get(index))
        repeat(1000) {
            val idx = nextLong(random(), 0, size)
            val pIdx = writer.pageIndex(idx)
            val sub = writer.subMutables[pIdx]
            val value = sub?.get(writer.indexInPage(idx)) ?: 0L
            val expected = if (idx == index) 2L else 0L
            assertEquals(expected, value)
        }
    }

    @Test
    fun testPagedMutable() {
        val rnd = random()
        val bitsPerValue = TestUtil.nextInt(rnd, 1, 64)
        val max = PackedInts.maxValue(bitsPerValue)
        val pageSize = 1 shl TestUtil.nextInt(rnd, 6, 30)
        var writer = PagedMutable(0, pageSize, bitsPerValue, rnd.nextFloat() / 2)
        assertEquals(0L, writer.size())

        val buf = PackedLongValues.deltaPackedBuilder(rnd.nextFloat())
        val size = if (LuceneTestCase.TEST_NIGHTLY) rnd.nextInt(1_000_000) else rnd.nextInt(100_000)
        for (i in 0 until size) {
            val v = if (bitsPerValue == 64) rnd.nextLong() else nextLong(rnd, 0, max)
            buf.add(v)
        }
        writer = PagedMutable(size.toLong(), pageSize, bitsPerValue, rnd.nextFloat())
        assertEquals(size.toLong(), writer.size())
        val values = buf.build()
        for (i in size - 1 downTo 0) {
            writer.set(i.toLong(), values.get(i.toLong()))
        }
        for (i in 0 until size) {
            assertEquals(values.get(i.toLong()), writer.get(i.toLong()))
        }
        assertEquals(RamUsageTester.ramUsed(writer) - RamUsageTester.ramUsed(writer.format), writer.ramBytesUsed())

        val copySize = nextLong(rnd, writer.size() / 2, writer.size() * 3 / 2)
        val copy = writer.resize(copySize)
        for (i in 0 until copy.size()) {
            if (i < writer.size()) {
                assertEquals(writer.get(i), copy.get(i))
            } else {
                assertEquals(0L, copy.get(i))
            }
        }

        val growSize = nextLong(rnd, writer.size() / 2, writer.size() * 3 / 2)
        val grow = writer.grow(growSize)
        for (i in 0 until grow.size()) {
            if (i < writer.size()) {
                assertEquals(writer.get(i), grow.get(i))
            } else {
                assertEquals(0L, grow.get(i))
            }
        }
    }

    @Test
    fun testEncodeDecode() {
        for (format in PackedInts.Format.values()) {
            for (bpv in 1..64) {
                if (!format.isSupported(bpv)) continue
                val msg = "$format $bpv"
                val encoder = PackedInts.getEncoder(format, PackedInts.VERSION_CURRENT, bpv)
                val decoder = PackedInts.getDecoder(format, PackedInts.VERSION_CURRENT, bpv)
                val longBlockCount = encoder.longBlockCount()
                val longValueCount = encoder.longValueCount()
                val byteBlockCount = encoder.byteBlockCount()
                val byteValueCount = encoder.byteValueCount()
                assertEquals(longBlockCount, decoder.longBlockCount())
                assertEquals(longValueCount, decoder.longValueCount())
                assertEquals(byteBlockCount, decoder.byteBlockCount())
                assertEquals(byteValueCount, decoder.byteValueCount())

                val longIterations = random().nextInt(100)
                val byteIterations = longIterations * longValueCount / byteValueCount
                assertEquals(longIterations * longValueCount, byteIterations * byteValueCount)
                val blocksOffset = random().nextInt(100)
                val valuesOffset = random().nextInt(100)
                val blocksOffset2 = random().nextInt(100)
                val blocksLen = longIterations * longBlockCount

                val blocks = LongArray(blocksOffset + blocksLen) { random().nextLong() }
                if (format == PackedInts.Format.PACKED_SINGLE_BLOCK && 64 % bpv != 0) {
                    val toClear = 64 % bpv
                    for (i in blocks.indices) {
                        blocks[i] = (blocks[i] shl toClear) ushr toClear
                    }
                }

                val values = LongArray(valuesOffset + longIterations * longValueCount)
                decoder.decode(blocks, blocksOffset, values, valuesOffset, longIterations)
                for (value in values) {
                    assertTrue(value <= PackedInts.maxValue(bpv))
                }
                val intValues: IntArray? = if (bpv <= 32) {
                    IntArray(values.size).also { decoder.decode(blocks, blocksOffset, it, valuesOffset, longIterations); assertTrue(equals(it, values)) }
                } else null

                val blocks2 = LongArray(blocksOffset2 + blocksLen)
                encoder.encode(values, valuesOffset, blocks2, blocksOffset2, longIterations)
                assertContentEquals(
                    ArrayUtil.copyOfSubArray(blocks, blocksOffset, blocks.size),
                    ArrayUtil.copyOfSubArray(blocks2, blocksOffset2, blocks2.size),
                    msg
                )
                if (bpv <= 32) {
                    val blocks3 = LongArray(blocks2.size)
                    encoder.encode(intValues!!, valuesOffset, blocks3, blocksOffset2, longIterations)
                    assertContentEquals(blocks2, blocks3, msg)
                }

                val byteBlocks = ByteArray(8 * blocks.size)
                val bb = ByteBuffer.wrap(byteBlocks)
                for (b in blocks) bb.putLong(b)
                val values2 = LongArray(valuesOffset + longIterations * longValueCount)
                decoder.decode(byteBlocks, blocksOffset * 8, values2, valuesOffset, byteIterations)
                for (value in values2) {
                    assertTrue(value <= PackedInts.maxValue(bpv), msg)
                }
                assertContentEquals(values, values2, msg)
                if (bpv <= 32) {
                    val intValues2 = IntArray(values2.size)
                    decoder.decode(byteBlocks, blocksOffset * 8, intValues2, valuesOffset, byteIterations)
                    assertTrue(equals(intValues2, values2), msg)
                }

                val blocks3 = ByteArray(8 * (blocksOffset2 + blocksLen))
                encoder.encode(values, valuesOffset, blocks3, 8 * blocksOffset2, byteIterations)
                assertEquals(LongBuffer.wrap(blocks2), ByteBuffer.wrap(blocks3).asLongBuffer(), msg)
                if (bpv <= 32) {
                    val blocks4 = ByteArray(blocks3.size)
                    encoder.encode(intValues!!, valuesOffset, blocks4, 8 * blocksOffset2, byteIterations)
                    assertContentEquals(blocks3, blocks4, msg)
                }
            }
        }
    }

    @Test
    fun testPackedLongValuesOnZeros() {
        val pageSize = 1 shl TestUtil.nextInt(random(), 6, 20)
        val ratio = random().nextFloat()

        val a = PackedLongValues.packedBuilder(pageSize, ratio).add(0).build().ramBytesUsed()
        val b = PackedLongValues.packedBuilder(pageSize, ratio).add(0).add(0).build().ramBytesUsed()
        assertEquals(a, b)

        val v = random().nextLong()
        val c = PackedLongValues.deltaPackedBuilder(pageSize, ratio).add(v).build().ramBytesUsed()
        val d = PackedLongValues.deltaPackedBuilder(pageSize, ratio).add(v).add(v).build().ramBytesUsed()
        assertEquals(c, d)

        val avg = random().nextInt(100)
        val e = PackedLongValues.monotonicBuilder(pageSize, ratio).add(v).add(v + avg).build().ramBytesUsed()
        val f = PackedLongValues.monotonicBuilder(pageSize, ratio).add(v).add(v + avg).add(v + 2L * avg).build().ramBytesUsed()
        assertEquals(e, f)
    }

    @Test
    fun testPackedLongValues() {
        val arr = LongArray(if (LuceneTestCase.TEST_NIGHTLY) random().nextInt(1_000_000) else random().nextInt(10_000).coerceAtLeast(1))
        val ratioOptions = floatArrayOf(PackedInts.DEFAULT, PackedInts.COMPACT, PackedInts.FAST)
        val bpvOptions = intArrayOf(0, 1, 63, 64, TestUtil.nextInt(random(), 2, 62))
        for (bpv in bpvOptions) {
            for (dataType in DataType.values()) {
                val pageSize = 1 shl TestUtil.nextInt(random(), 6, 20)
                val ratio = ratioOptions[TestUtil.nextInt(random(), 0, ratioOptions.size - 1)]
                var buf: PackedLongValues.Builder
                val inc: Int
                when (dataType) {
                    DataType.PACKED -> { buf = PackedLongValues.packedBuilder(pageSize, ratio); inc = 0 }
                    DataType.DELTA_PACKED -> { buf = PackedLongValues.deltaPackedBuilder(pageSize, ratio); inc = 0 }
                    DataType.MONOTONIC -> { buf = PackedLongValues.monotonicBuilder(pageSize, ratio); inc = TestUtil.nextInt(random(), -1000, 1000) }
                }

                if (bpv == 0) {
                    arr[0] = random().nextLong()
                    for (i in 1 until arr.size) arr[i] = arr[i - 1] + inc
                } else if (bpv == 64) {
                    for (i in arr.indices) arr[i] = random().nextLong()
                } else {
                    val minValue = nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE - PackedInts.maxValue(bpv))
                    for (i in arr.indices) arr[i] = (minValue + inc * i + random().nextLong()) and PackedInts.maxValue(bpv)
                }

                for (v in arr) buf.add(v)
                assertEquals(arr.size.toLong(), buf.size())
                val values = buf.build()
                LuceneTestCase.Companion.expectThrows(IllegalStateException::class) { buf.add(random().nextLong()) }
                assertEquals(arr.size.toLong(), values.size())

                for (i in arr.indices) assertEquals(arr[i], values.get(i.toLong()))

                val it = values.iterator()
                for (i in arr.indices) {
                    if (random().nextBoolean()) assertTrue(it.hasNext())
                    assertEquals(arr[i], it.next())
                }
                assertFalse(it.hasNext())
            }
        }
    }

    @Test
    @Ignore
    fun testPackedInputOutput() {
        // PackedDataInput/PackedDataOutput not yet implemented
    }

    @Test
    fun testBlockPackedReaderWriter() {
        val rnd = random()
        val iters = atLeast(2)
        repeat(iters) {
            val blockSize = 1 shl TestUtil.nextInt(rnd, 6, 18)
            val valueCount = if (LuceneTestCase.TEST_NIGHTLY) rnd.nextInt(1 shl 18) else rnd.nextInt(1 shl 15)
            val values = LongArray(valueCount)
            var minValue = 0L
            var bpv = 0
            for (i in 0 until valueCount) {
                if (i % blockSize == 0) {
                    minValue = if (TestUtil.rarely(rnd)) rnd.nextInt(256).toLong() else if (TestUtil.rarely(rnd)) -5 else rnd.nextLong()
                    bpv = rnd.nextInt(65)
                }
                values[i] = when (bpv) {
                    0 -> minValue
                    64 -> rnd.nextLong()
                    else -> minValue + nextLong(rnd, 0, (1L shl bpv) - 1)
                }
            }

            val out = ByteBuffersDataOutput()
            val writer = BlockPackedWriter(out, blockSize)
            for (i in 0 until valueCount) {
                assertEquals(i.toLong(), writer.ord())
                writer.add(values[i])
            }
            assertEquals(valueCount.toLong(), writer.ord())
            writer.finish()
            assertEquals(valueCount.toLong(), writer.ord())
            val bytes = out.toArrayCopy()

            val in1 = ByteBuffersDataInput(mutableListOf(ByteBuffer.wrap(bytes)))
            val in2 = ByteArrayDataInput(bytes)
            val data = if (rnd.nextBoolean()) in1 else in2

            val it = BlockPackedReaderIterator(data, PackedInts.VERSION_CURRENT, blockSize, valueCount.toLong())
            var i = 0
            while (i < valueCount) {
                if (rnd.nextBoolean()) {
                    assertEquals(values[i], it.next(), "" + i)
                    i++
                } else {
                    val next = it.next(TestUtil.nextInt(rnd, 1, 1024))
                    for (j in 0 until next.length) {
                        assertEquals(values[i + j], next.longs[next.offset + j], "" + (i + j))
                    }
                    i += next.length
                }
                assertEquals(i.toLong(), it.ord())
            }
            val expectedPos = bytes.size.toLong()
            if (data is ByteArrayDataInput) {
                assertEquals(expectedPos, data.position.toLong())
            } else {
                assertEquals(expectedPos, (data as ByteBuffersDataInput).position())
            }
        }
    }

    @Test
    @Ignore
    fun testMonotonicBlockPackedReaderWriter() {
        // MonotonicBlockPackedWriter not implemented
    }

    @Test
    @LuceneTestCase.Companion.Nightly
    fun testBlockReaderOverflow() {
        val valueCount = nextLong(random(), 1L + Int.MAX_VALUE, 2L * Int.MAX_VALUE)
        val blockSize = 1 shl TestUtil.nextInt(random(), 20, 22)
        val out = ByteBuffersDataOutput()
        val writer = BlockPackedWriter(out, blockSize)
        val value = random().nextInt().toLong() and 0xFFFFFFFFL
        val valueOffset = nextLong(random(), 0, valueCount - 1)
        var i = 0L
        while (i < valueCount) {
            assertEquals(i, writer.ord())
            if ((i and (blockSize - 1).toLong()) == 0L && (i + blockSize < valueOffset || (i > valueOffset && i + blockSize < valueCount))) {
                writer.addBlockOfZeros()
                i += blockSize.toLong()
            } else if (i == valueOffset) {
                writer.add(value)
                i++
            } else {
                writer.add(0)
                i++
            }
        }
        writer.finish()
        val bytes = out.toArrayCopy()
        val input = ByteArrayDataInput(bytes)
        val it = BlockPackedReaderIterator(input, PackedInts.VERSION_CURRENT, blockSize, valueCount)
        it.skip(valueOffset)
        assertEquals(value, it.next())
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

    private fun equals(ints: IntArray, longs: LongArray): Boolean {
        if (ints.size != longs.size) return false
        for (i in ints.indices) {
            if ((ints[i].toLong() and 0xFFFFFFFFL) != longs[i]) return false
        }
        return true
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
