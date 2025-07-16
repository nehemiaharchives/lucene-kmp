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
package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.get
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestByteBuffersDataOutput : BaseDataOutputTestCase<ByteBuffersDataOutput>() {

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testReuse() {
        val allocations = AtomicInt(0)
        val recycler = ByteBuffersDataOutput.ByteBufferRecycler { size ->
            allocations.incrementAndFetch()
            ByteBuffer.allocate(size)
        }

        val out = ByteBuffersDataOutput(
            ByteBuffersDataOutput.DEFAULT_MIN_BITS_PER_BLOCK,
            ByteBuffersDataOutput.DEFAULT_MAX_BITS_PER_BLOCK,
            recycler::allocate,
            recycler::reuse
        )

        val seed = random().nextLong()
        val addCount = TestUtil.nextInt(random(), 1000, 5000)
        addRandomData(out, Random(seed), addCount)
        val data = out.toArrayCopy()

        val expectedAllocations = allocations.get()
        out.reset()
        addRandomData(out, Random(seed), addCount)

        assertEquals(expectedAllocations, allocations.get())
        assertContentEquals(data, out.toArrayCopy())
    }

    @Test
    fun testConstructorWithExpectedSize() {
        run {
            val o = ByteBuffersDataOutput(0L)
            o.writeByte(0)
            assertEquals(1 shl ByteBuffersDataOutput.DEFAULT_MIN_BITS_PER_BLOCK, o.toBufferList()[0].capacity)
        }
        run {
            val mb = 1024 * 1024L
            val expected = random().nextLong(mb, mb * 1024)
            val o = ByteBuffersDataOutput(expected)
            o.writeByte(0)
            val cap = o.toBufferList()[0].capacity
            assertTrue((cap shr 1) * ByteBuffersDataOutput.MAX_BLOCKS_BEFORE_BLOCK_EXPANSION < expected)
            assertTrue(cap * ByteBuffersDataOutput.MAX_BLOCKS_BEFORE_BLOCK_EXPANSION >= expected)
        }
    }

    @Test
    fun testIllegalMinBitsPerBlock() {
        expectThrows(IllegalArgumentException::class) {
            ByteBuffersDataOutput(
                ByteBuffersDataOutput.LIMIT_MIN_BITS_PER_BLOCK - 1,
                ByteBuffersDataOutput.DEFAULT_MAX_BITS_PER_BLOCK,
                ByteBuffersDataOutput.ALLOCATE_BB_ON_HEAP,
                ByteBuffersDataOutput.NO_REUSE
            )
        }
    }

    @Test
    fun testIllegalMaxBitsPerBlock() {
        expectThrows(IllegalArgumentException::class) {
            ByteBuffersDataOutput(
                ByteBuffersDataOutput.DEFAULT_MIN_BITS_PER_BLOCK,
                ByteBuffersDataOutput.LIMIT_MAX_BITS_PER_BLOCK + 1,
                ByteBuffersDataOutput.ALLOCATE_BB_ON_HEAP,
                ByteBuffersDataOutput.NO_REUSE
            )
        }
    }

    @Test
    fun testIllegalBitsPerBlockRange() {
        expectThrows(IllegalArgumentException::class) {
            ByteBuffersDataOutput(
                20,
                19,
                ByteBuffersDataOutput.ALLOCATE_BB_ON_HEAP,
                ByteBuffersDataOutput.NO_REUSE
            )
        }
    }

    @Test
    fun testNullAllocator() {
        expectThrows(NullPointerException::class) {
            ByteBuffersDataOutput(
                ByteBuffersDataOutput.DEFAULT_MIN_BITS_PER_BLOCK,
                ByteBuffersDataOutput.DEFAULT_MAX_BITS_PER_BLOCK,
                null as (Int) -> ByteBuffer,
                ByteBuffersDataOutput.NO_REUSE
            )
        }
    }

    @Test
    fun testNullRecycler() {
        expectThrows(NullPointerException::class) {
            ByteBuffersDataOutput(
                ByteBuffersDataOutput.DEFAULT_MIN_BITS_PER_BLOCK,
                ByteBuffersDataOutput.DEFAULT_MAX_BITS_PER_BLOCK,
                ByteBuffersDataOutput.ALLOCATE_BB_ON_HEAP,
                null as (ByteBuffer) -> Unit
            )
        }
    }

    @Test
    fun testSanity() {
        val o = ByteBuffersDataOutput()
        assertEquals(0, o.size())
        assertEquals(0, o.toArrayCopy().size)
        assertEquals(0, o.ramBytesUsed())

        o.writeByte(1)
        assertEquals(1, o.size())
        assertTrue(o.ramBytesUsed() > 0)
        assertContentEquals(byteArrayOf(1), o.toArrayCopy())

        o.writeBytes(byteArrayOf(2, 3, 4), 3)
        assertEquals(4, o.size())
        assertContentEquals(byteArrayOf(1, 2, 3, 4), o.toArrayCopy())
    }

    @Test
    fun testWriteByteBuffer() {
        val o = ByteBuffersDataOutput()
        val bytes = ByteArray(1024 * 8 + 10)
        random().nextBytes(bytes)
        val src = ByteBuffer.wrap(bytes)
        val offset = TestUtil.nextInt(random(), 0, 100)
        val len = bytes.size - offset
        src.position = offset
        src.limit = offset + len
        o.writeBytes(src)
        assertEquals(len.toLong(), o.size())
        assertContentEquals(ArrayUtil.copyOfSubArray(bytes, offset, offset + len), o.toArrayCopy())
    }

    @Test
    fun testLargeArrayAdd() {
        val o = ByteBuffersDataOutput()
        val mb = 1024 * 1024
        val bytes = if (LuceneTestCase.TEST_NIGHTLY) {
            ByteArray(TestUtil.nextInt(random(), 5 * mb, 15 * mb))
        } else {
            ByteArray(TestUtil.nextInt(random(), mb / 2, mb))
        }
        random().nextBytes(bytes)
        val offset = TestUtil.nextInt(random(), 0, 100)
        val len = bytes.size - offset
        o.writeBytes(bytes, offset, len)
        assertEquals(len.toLong(), o.size())
        assertContentEquals(ArrayUtil.copyOfSubArray(bytes, offset, offset + len), o.toArrayCopy())
    }

    @Test
    fun testCopyBytesOnHeap() {
        val bytes = ByteArray(1024 * 8 + 10)
        random().nextBytes(bytes)
        val offset = TestUtil.nextInt(random(), 0, 100)
        val len = bytes.size - offset
        val input = ByteArrayDataInput(bytes, offset, len)
        val o = ByteBuffersDataOutput(
            ByteBuffersDataOutput.DEFAULT_MIN_BITS_PER_BLOCK,
            ByteBuffersDataOutput.DEFAULT_MAX_BITS_PER_BLOCK,
            ByteBuffersDataOutput.ALLOCATE_BB_ON_HEAP,
            ByteBuffersDataOutput.NO_REUSE
        )
        o.copyBytes(input, len.toLong())
        assertContentEquals(o.toArrayCopy(), ArrayUtil.copyOfSubArray(bytes, offset, offset + len))
    }

    @Test
    fun testCopyBytesOnDirectByteBuffer() {
        val bytes = ByteArray(1024 * 8 + 10)
        random().nextBytes(bytes)
        val offset = TestUtil.nextInt(random(), 0, 100)
        val len = bytes.size - offset
        val input = ByteArrayDataInput(bytes, offset, len)
        val o = ByteBuffersDataOutput(
            ByteBuffersDataOutput.DEFAULT_MIN_BITS_PER_BLOCK,
            ByteBuffersDataOutput.DEFAULT_MAX_BITS_PER_BLOCK,
            ByteBuffer.Companion::allocate,
            ByteBuffersDataOutput.NO_REUSE
        )
        o.copyBytes(input, len.toLong())
        assertContentEquals(o.toArrayCopy(), ArrayUtil.copyOfSubArray(bytes, offset, offset + len))
    }

    @Test
    fun testToBufferListReturnsReadOnlyBuffers() {
        val dst = ByteBuffersDataOutput()
        dst.writeBytes(ByteArray(100))
        for (bb in dst.toBufferList()) {
            assertTrue(bb.isReadOnly)
        }
    }

    @Test
    fun testToWriteableBufferListReturnsOriginalBuffers() {
        val dst = ByteBuffersDataOutput()
        for (bb in dst.toWriteableBufferList()) {
            assertTrue(!bb.isReadOnly)
            assertTrue(bb.hasArray())
        }
        dst.writeBytes(ByteArray(100))
        for (bb in dst.toWriteableBufferList()) {
            assertTrue(!bb.isReadOnly)
            assertTrue(bb.hasArray())
        }
    }

    @Test
    fun testRamBytesUsed() {
        val out = ByteBuffersDataOutput()
        assertEquals(0, out.ramBytesUsed())

        out.writeInt(4)
        assertEquals(out.ramBytesUsed(), computeRamBytesUsed(out))

        while (out.toBufferList().size < 2) {
            out.writeLong(42)
        }
        assertEquals(out.ramBytesUsed(), computeRamBytesUsed(out))

        var currentBlockCapacity = out.toBufferList()[0].capacity
        do {
            out.writeLong(42)
        } while (out.toBufferList()[0].capacity == currentBlockCapacity)
        assertEquals(out.ramBytesUsed(), computeRamBytesUsed(out))

        out.reset()
        assertEquals(0, out.ramBytesUsed())

        out.writeInt(4)
        assertEquals(out.ramBytesUsed(), computeRamBytesUsed(out))
    }

    @Test
    override fun testRandomizedWrites() = super.testRandomizedWrites()

    private fun computeRamBytesUsed(out: ByteBuffersDataOutput): Long {
        if (out.size() == 0L) return 0L
        val buffers = out.toBufferList()
        var sum = 0L
        for (bb in buffers) {
            sum += bb.capacity.toLong()
        }
        return sum + buffers.size * RamUsageEstimator.NUM_BYTES_OBJECT_REF
    }

    override fun newInstance(): ByteBuffersDataOutput {
        return ByteBuffersDataOutput()
    }

    override fun toBytes(instance: ByteBuffersDataOutput): ByteArray {
        return instance.toArrayCopy()
    }
}

