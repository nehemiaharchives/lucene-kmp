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
package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.RecyclingByteBlockAllocator
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class TestByteSlicePool : LuceneTestCase() {

    @Test
    fun testAllocKnownSizeSlice() {
        val bytesUsed = Counter.newCounter()
        val blockPool = ByteBlockPool(ByteBlockPool.DirectTrackingAllocator(bytesUsed))
        blockPool.nextBuffer()
        val slicePool = ByteSlicePool(blockPool)
        repeat(100) {
            val size = if (random().nextBoolean()) {
                TestUtil.nextInt(random(), 100, 1000)
            } else {
                TestUtil.nextInt(random(), 50000, 100000)
            }
            val randomData = ByteArray(size)
            random().nextBytes(randomData)

            var upto = slicePool.newSlice(ByteSlicePool.FIRST_LEVEL_SIZE)

            var offset = 0
            while (offset < size) {
                if ((blockPool.buffer!![upto].toInt() and 16) == 0) {
                    blockPool.buffer!![upto++] = randomData[offset++]
                } else {
                    val offsetAndLength = slicePool.allocKnownSizeSlice(blockPool.buffer!!, upto)
                    val sliceLength = offsetAndLength and 0xff
                    upto = offsetAndLength shr 8
                    assertNotEquals(0, blockPool.buffer!![upto + sliceLength - 1].toInt())
                    assertEquals(0, blockPool.buffer!![upto].toInt())
                    val writeLength = min(sliceLength - 1, size - offset)
                    randomData.copyInto(blockPool.buffer!!, upto, offset, offset + writeLength)
                    offset += writeLength
                    upto += writeLength
                }
            }
        }
    }

    @Test
    fun testAllocLargeSlice() {
        val blockPool = ByteBlockPool(ByteBlockPool.DirectAllocator())
        val slicePool = ByteSlicePool(blockPool)

        assertEquals(0, slicePool.newSlice(ByteBlockPool.BYTE_BLOCK_SIZE))
        assertContentEquals(blockPool.buffer!!, blockPool.getBuffer(0))

        blockPool.nextBuffer()
        assertFailsWith<IllegalArgumentException> {
            slicePool.newSlice(ByteBlockPool.BYTE_BLOCK_SIZE + 1)
        }
    }

    /** Create a random byte array and write it to a [ByteSlicePool] one slice at a time. */
    private class SliceWriter(private val slicePool: ByteSlicePool) {
        var hasStarted = false

        val blockPool: ByteBlockPool = slicePool.pool

        val size: Int
        val randomData: ByteArray
        var dataOffset = 0

        var slice: ByteArray? = null
        var sliceLength = 0
        var sliceOffset = 0

        var firstSliceOffset = 0
        var firstSlice: ByteArray? = null

        init {
            size = if (random().nextBoolean()) {
                TestUtil.nextInt(random(), 100, 1000)
            } else {
                TestUtil.nextInt(random(), 50000, 100000)
            }
            randomData = ByteArray(size)
            random().nextBytes(randomData)
        }

        /**
         * Write the next slice of data.
         *
         * @return true if we wrote a slice and false if we're out of data to write
         */
        fun writeSlice(): Boolean {
            if (!hasStarted) {
                dataOffset = 0
                sliceLength = ByteSlicePool.FIRST_LEVEL_SIZE
                sliceOffset = slicePool.newSlice(sliceLength)
                firstSliceOffset = sliceOffset
                firstSlice = blockPool.buffer
                slice = firstSlice
                val writeLength = min(size, sliceLength - 1)
                randomData.copyInto(blockPool.buffer!!, sliceOffset, dataOffset, dataOffset + writeLength)
                dataOffset += writeLength

                hasStarted = true
                return true
            }
            if (dataOffset == size) {
                return false
            }
            val offsetAndLength = slicePool.allocKnownSizeSlice(slice!!, sliceOffset + sliceLength - 1)
            slice = blockPool.buffer
            sliceLength = offsetAndLength and 0xff
            sliceOffset = offsetAndLength shr 8
            val writeLength = min(size - dataOffset, sliceLength - 1)
            randomData.copyInto(slice!!, sliceOffset, dataOffset, dataOffset + writeLength)
            dataOffset += writeLength
            return true
        }
    }

    /** Read a sequence of slices into a byte array. */
    private class SliceReader(
        slicePool: ByteSlicePool,
        val size: Int,
        firstSliceOffset: Int,
        firstSlice: ByteArray
    ) {
        var hasStarted = false

        val blockPool: ByteBlockPool = slicePool.pool

        val readData: ByteArray = ByteArray(size)
        var dataOffset = 0

        var sliceLength = 0
        var sliceOffset: Int = firstSliceOffset

        var slice: ByteArray = firstSlice
        var sliceSizeIdx = 0

        /**
         * Read the next slice of data.
         *
         * @return true if we read a slice and false if we'd already read the entire sequence of slices
         */
        fun readSlice(): Boolean {
            if (!hasStarted) {
                dataOffset = 0
                sliceSizeIdx = 0
                sliceLength = ByteSlicePool.LEVEL_SIZE_ARRAY[sliceSizeIdx] - 4
                val readLength = if (dataOffset + sliceLength + 3 >= size) {
                    size - dataOffset
                } else {
                    sliceLength
                }
                slice.copyInto(readData, dataOffset, sliceOffset, sliceOffset + readLength)
                dataOffset += readLength
                sliceSizeIdx = min(sliceSizeIdx + 1, ByteSlicePool.LEVEL_SIZE_ARRAY.size - 1)

                hasStarted = true
                return true
            }
            if (dataOffset == size) {
                return false
            }
            val globalSliceOffset = BitUtil.VH_LE_INT.get(slice, sliceOffset + sliceLength)
            slice = blockPool.getBuffer(globalSliceOffset / ByteBlockPool.BYTE_BLOCK_SIZE)
            sliceOffset = globalSliceOffset % ByteBlockPool.BYTE_BLOCK_SIZE
            sliceLength = ByteSlicePool.LEVEL_SIZE_ARRAY[sliceSizeIdx] - 4
            val readLength = if (dataOffset + sliceLength + 3 >= size) {
                size - dataOffset
            } else {
                sliceLength
            }
            slice.copyInto(readData, dataOffset, sliceOffset, sliceOffset + readLength)
            dataOffset += readLength
            sliceSizeIdx = min(sliceSizeIdx + 1, ByteSlicePool.LEVEL_SIZE_ARRAY.size - 1)
            return true
        }
    }

    /**
     * Run multiple slice writers, creating interleaved slices. Read the slices afterwards and check
     * that we read back the same data we wrote.
     */
    @Test
    fun testRandomInterleavedSlices() {
        val blockPool = ByteBlockPool(RecyclingByteBlockAllocator())
        val slicePool = ByteSlicePool(blockPool)

        val nIterations = TestUtil.nextInt(random(), 1, 3)
        repeat(nIterations) {
            val n = TestUtil.nextInt(random(), 2, 3)
            val sliceWriters = Array(n) { SliceWriter(slicePool) }
            val sliceReaders = arrayOfNulls<SliceReader>(n)

            while (true) {
                val i = random().nextInt(n)
                val succeeded = sliceWriters[i].writeSlice()
                if (!succeeded) {
                    for (j in 0..<n) {
                        while (sliceWriters[j].writeSlice()) {
                            // exhaust remaining slices for this writer
                        }
                    }
                    break
                }
            }

            for (i in 0..<n) {
                sliceReaders[i] = SliceReader(
                    slicePool,
                    sliceWriters[i].size,
                    sliceWriters[i].firstSliceOffset,
                    sliceWriters[i].firstSlice!!
                )
            }

            while (true) {
                val i = random().nextInt(n)
                val succeeded = sliceReaders[i]!!.readSlice()
                if (!succeeded) {
                    for (j in 0..<n) {
                        while (sliceReaders[j]!!.readSlice()) {
                            // exhaust remaining slices for this reader
                        }
                    }
                    break
                }
            }

            for (i in 0..<n) {
                assertContentEquals(sliceWriters[i].randomData, sliceReaders[i]!!.readData)
            }

            blockPool.reset(true, random().nextBoolean())
        }
    }
}
