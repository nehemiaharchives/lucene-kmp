package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.RecyclingByteBlockAllocator
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertContentEquals

class TestByteSlicePool : LuceneTestCase() {
    @Test
    fun testAllocKnownSizeSlice() {
        val bytesUsed = Counter.newCounter()
        val blockPool = ByteBlockPool(ByteBlockPool.DirectTrackingAllocator(bytesUsed))
        blockPool.nextBuffer()
        val slicePool = ByteSlicePool(blockPool)
        for (i in 0 until 100) {
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
                val buffer = blockPool.buffer!!
                if ((buffer[upto].toInt() and 16) == 0) {
                    buffer[upto++] = randomData[offset++]
                } else {
                    val offsetAndLength = slicePool.allocKnownSizeSlice(buffer, upto)
                    val sliceLength = offsetAndLength and 0xff
                    upto = offsetAndLength ushr 8
                    assertNotEquals(0, blockPool.buffer!![upto + sliceLength - 1].toInt())
                    assertEquals(0, blockPool.buffer!![upto].toInt())
                    val writeLength = min(sliceLength - 1, size - offset)
                    System.arraycopy(randomData, offset, blockPool.buffer!!, upto, writeLength)
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
        expectThrows(IllegalArgumentException::class) {
            slicePool.newSlice(ByteBlockPool.BYTE_BLOCK_SIZE + 1)
        }
    }

    /** Create a random byte array and write it to a [ByteSlicePool] one slice at a time. */
    private class SliceWriter(slicePool: ByteSlicePool) {
        private var hasStarted = false
        private val blockPool: ByteBlockPool = slicePool.pool
        private val slicePool: ByteSlicePool = slicePool
        var size: Int
        var randomData: ByteArray
        private var dataOffset = 0
        private var slice: ByteArray
        private var sliceLength: Int = 0
        private var sliceOffset: Int = 0
        var firstSliceOffset: Int = 0
        var firstSlice: ByteArray

        init {
            size = if (random().nextBoolean()) {
                TestUtil.nextInt(random(), 100, 1000)
            } else {
                TestUtil.nextInt(random(), 50000, 100000)
            }
            randomData = ByteArray(size)
            random().nextBytes(randomData)
            slice = blockPool.buffer!!
            firstSlice = slice
        }

        fun writeSlice(): Boolean {
            if (!hasStarted) {
                dataOffset = 0
                sliceLength = ByteSlicePool.FIRST_LEVEL_SIZE
                sliceOffset = slicePool.newSlice(sliceLength)
                firstSliceOffset = sliceOffset
                firstSlice = blockPool.buffer!!
                slice = firstSlice
                val writeLength = min(size, sliceLength - 1)
                System.arraycopy(randomData, dataOffset, blockPool.buffer!!, sliceOffset, writeLength)
                dataOffset += writeLength
                hasStarted = true
                return true
            }
            if (dataOffset == size) {
                return false
            }
            val offsetAndLength = slicePool.allocKnownSizeSlice(slice, sliceOffset + sliceLength - 1)
            slice = blockPool.buffer!!
            sliceLength = offsetAndLength and 0xff
            sliceOffset = offsetAndLength ushr 8
            val writeLength = min(size - dataOffset, sliceLength - 1)
            System.arraycopy(randomData, dataOffset, slice, sliceOffset, writeLength)
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
        private var hasStarted = false
        private val blockPool: ByteBlockPool = slicePool.pool
        private val slicePool: ByteSlicePool = slicePool
        var readData: ByteArray = ByteArray(size)
        private var dataOffset = 0
        private var sliceLength = 0
        private var sliceOffset: Int = firstSliceOffset
        private var slice: ByteArray = firstSlice
        private var sliceSizeIdx = 0

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
                System.arraycopy(slice, sliceOffset, readData, dataOffset, readLength)
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
            System.arraycopy(slice, sliceOffset, readData, dataOffset, readLength)
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
        for (iter in 0 until nIterations) {
            val n = TestUtil.nextInt(random(), 2, 3)
            val sliceWriters = Array(n) { SliceWriter(slicePool) }
            val sliceReaders = Array(n) { SliceReader(slicePool, 0, 0, ByteArray(0)) }

            // Initialize slice writers already done above

            // Write slices
            while (true) {
                val i = random().nextInt(n)
                val succeeded = sliceWriters[i].writeSlice()
                if (!succeeded) {
                    for (j in 0 until n) {
                        while (sliceWriters[j].writeSlice()) {
                        }
                    }
                    break
                }
            }

            // Init slice readers
            for (i in 0 until n) {
                sliceReaders[i] = SliceReader(
                    slicePool,
                    sliceWriters[i].size,
                    sliceWriters[i].firstSliceOffset,
                    sliceWriters[i].firstSlice
                )
            }

            // Read slices
            while (true) {
                val i = random().nextInt(n)
                val succeeded = sliceReaders[i].readSlice()
                if (!succeeded) {
                    for (j in 0 until n) {
                        while (sliceReaders[j].readSlice()) {
                        }
                    }
                    break
                }
            }

            // Compare written data with read data
            for (i in 0 until n) {
                assertContentEquals(sliceWriters[i].randomData, sliceReaders[i].readData)
            }

            blockPool.reset(true, random().nextBoolean())
        }
    }
}

