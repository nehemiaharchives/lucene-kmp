package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestBufferedIndexInput : LuceneTestCase() {

    private val TEST_FILE_LENGTH: Long = 100 * 1024L

    @Test
    fun testReadByte() {
        val input = MyBufferedIndexInput()
        for (i in 0 until BufferedIndexInput.BUFFER_SIZE * 10) {
            assertEquals(byten(i.toLong()), input.readByte())
        }
    }

    @Test
    fun testReadBytes() {
        val input = MyBufferedIndexInput()
        runReadBytes(input, BufferedIndexInput.BUFFER_SIZE, random())
    }

    private fun runReadBytes(input: IndexInput, bufferSize: Int, r: Random) {
        var pos = 0
        // gradually increasing size
        var size = 1
        while (size < bufferSize * 10) {
            checkReadBytes(input, size, pos)
            pos += size
            if (pos >= TEST_FILE_LENGTH) {
                pos = 0
                input.seek(0L)
            }
            size = size + size / 200 + 1
        }
        // wildly fluctuating size
        for (i in 0 until 100) {
            val s = r.nextInt(10000)
            checkReadBytes(input, 1 + s, pos)
            pos += 1 + s
            if (pos >= TEST_FILE_LENGTH) {
                pos = 0
                input.seek(0L)
            }
        }
        // constant small size (7 bytes)
        for (i in 0 until bufferSize) {
            checkReadBytes(input, 7, pos)
            pos += 7
            if (pos >= TEST_FILE_LENGTH) {
                pos = 0
                input.seek(0L)
            }
        }
    }

    private var buffer = ByteArray(10)

    private fun checkReadBytes(input: IndexInput, size0: Int, pos: Int) {
        var size = size0
        val offset = size % 10
        buffer = ArrayUtil.grow(buffer, offset + size)
        assertEquals(pos.toLong(), input.filePointer)
        var left = TEST_FILE_LENGTH - input.filePointer
        if (left <= 0) {
            return
        } else if (left < size) {
            size = left.toInt()
        }
        input.readBytes(buffer, offset, size)
        assertEquals((pos + size).toLong(), input.filePointer)
        for (i in 0 until size) {
            assertEquals(
                byten((pos + i).toLong()),
                buffer[offset + i],
                "pos=$i filepos=${pos + i}"
            )
        }
    }

    @Test
    fun testEOF() {
        val input = MyBufferedIndexInput(1024)
        checkReadBytes(input, input.length().toInt(), 0)
        var pos = input.length().toInt() - 10
        input.seek(pos.toLong())
        checkReadBytes(input, 10, pos)
        input.seek(pos.toLong())
        expectThrows<okio.IOException>(okio.IOException::class) {
            checkReadBytes(input, 11, pos)
        }
        input.seek(pos.toLong())
        expectThrows<okio.IOException>(okio.IOException::class) {
            checkReadBytes(input, 50, pos)
        }
        input.seek(pos.toLong())
        expectThrows<okio.IOException>(okio.IOException::class) {
            checkReadBytes(input, 100000, pos)
        }
    }

    @Test
    fun testBackwardsByteReads() {
        val input = MyBufferedIndexInput(1024 * 8L)
        var i = 2048
        while (i > 0) {
            assertEquals(byten(i.toLong()), input.readByte(i.toLong()))
            i -= random().nextInt(16)
        }
        assertEquals(3L, input.readCount)
    }

    @Test
    fun testBackwardsShortReads() {
        val input = MyBufferedIndexInput(1024 * 8L)
        val bb = ByteBuffer.allocate(2)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        var i = 2048
        while (i > 0) {
            bb.clear()
            bb.put(byten(i.toLong()))
            bb.put(byten((i + 1).toLong()))
            assertEquals(bb.getShort(0), input.readShort(i.toLong()))
            i -= random().nextInt(16) + 1
        }
        assertTrue(input.readCount == 4L || input.readCount == 3L, "Expected 4 or 3, got ${input.readCount}")
    }

    @Test
    fun testBackwardsIntReads() {
        val input = MyBufferedIndexInput(1024 * 8L)
        val bb = ByteBuffer.allocate(4)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        var i = 2048
        while (i > 0) {
            bb.clear()
            bb.put(byten(i.toLong()))
            bb.put(byten((i + 1).toLong()))
            bb.put(byten((i + 2).toLong()))
            bb.put(byten((i + 3).toLong()))
            assertEquals(bb.getInt(0), input.readInt(i.toLong()))
            i -= random().nextInt(16) + 3
        }
        assertTrue(input.readCount == 4L || input.readCount == 3L, "Expected 4 or 3, got ${input.readCount}")
    }

    @Test
    fun testBackwardsLongReads() {
        val input = MyBufferedIndexInput(1024 * 8L)
        val bb = ByteBuffer.allocate(8)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        var i = 2048
        while (i > 0) {
            bb.clear()
            bb.put(byten(i.toLong()))
            bb.put(byten((i + 1).toLong()))
            bb.put(byten((i + 2).toLong()))
            bb.put(byten((i + 3).toLong()))
            bb.put(byten((i + 4).toLong()))
            bb.put(byten((i + 5).toLong()))
            bb.put(byten((i + 6).toLong()))
            bb.put(byten((i + 7).toLong()))
            assertEquals(bb.getLong(0), input.readLong(i.toLong()))
            i -= random().nextInt(16) + 7
        }
        assertTrue(input.readCount == 4L || input.readCount == 3L, "Expected 4 or 3, got ${input.readCount}")
    }

    @Test
    fun testReadFloats() {
        val length = 64 * 8 // TODO originally 1024 * 8 but reduced to 64 * 8 for dev speed
        val input = MyBufferedIndexInput(length.toLong())
        val bb = ByteBuffer.allocate(Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val bufferLength = 128
        val floatBuffer = FloatArray(bufferLength)
        for (alignment in 0 until Float.SIZE_BYTES) {
            input.seek(0)
            repeat(alignment) { input.readByte() }
            val bulkReads = length / (bufferLength * Float.SIZE_BYTES) - 1
            for (i in 0 until bulkReads) {
                val pos = alignment + i * bufferLength * Float.SIZE_BYTES
                val floatOffset = random().nextInt(3)
                input.skipBytes((floatOffset * Float.SIZE_BYTES).toLong())
                input.readFloats(floatBuffer, floatOffset, bufferLength - floatOffset)
                for (idx in floatOffset until bufferLength) {
                    val offset = pos + idx * Float.SIZE_BYTES
                    bb.position(0)
                    bb.put(byten(offset.toLong()))
                    bb.put(byten((offset + 1).toLong()))
                    bb.put(byten((offset + 2).toLong()))
                    bb.put(byten((offset + 3).toLong()))
                    assertEquals(
                        Float.fromBits(bb.getInt(0)),
                        floatBuffer[idx]
                    )
                }
            }
        }
    }

    @Test
    fun testReadInts() {
        val length = 64 * 8 // TODO originally 1024 * 8 but reduced to 64 * 8 for dev speed
        val input = MyBufferedIndexInput(length.toLong())
        val bb = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val bufferLength = 128
        val intBuffer = IntArray(bufferLength)
        for (alignment in 0 until Int.SIZE_BYTES) {
            input.seek(0)
            repeat(alignment) { input.readByte() }
            val bulkReads = length / (bufferLength * Int.SIZE_BYTES) - 1
            for (i in 0 until bulkReads) {
                val pos = alignment + i * bufferLength * Int.SIZE_BYTES
                val intOffset = random().nextInt(3)
                input.skipBytes((intOffset * Int.SIZE_BYTES).toLong())
                input.readInts(intBuffer, intOffset, bufferLength - intOffset)
                for (idx in intOffset until bufferLength) {
                    val offset = pos + idx * Int.SIZE_BYTES
                    bb.position(0)
                    bb.put(byten(offset.toLong()))
                    bb.put(byten((offset + 1).toLong()))
                    bb.put(byten((offset + 2).toLong()))
                    bb.put(byten((offset + 3).toLong()))
                    assertEquals(bb.getInt(0), intBuffer[idx])
                }
            }
        }
    }

    @Test
    fun testReadLongs() {
        val length = 64 * 8 // TODO originally 1024 * 8 but reduced to 64 * 8 for dev speed
        val input = MyBufferedIndexInput(length.toLong())
        val bb = ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val bufferLength = 128
        val longBuffer = LongArray(bufferLength)
        for (alignment in 0 until Long.SIZE_BYTES) {
            input.seek(0)
            repeat(alignment) { input.readByte() }
            val bulkReads = length / (bufferLength * Long.SIZE_BYTES) - 1
            for (i in 0 until bulkReads) {
                val pos = alignment + i * bufferLength * Long.SIZE_BYTES
                val longOffset = random().nextInt(3)
                input.skipBytes((longOffset * Long.SIZE_BYTES).toLong())
                input.readLongs(longBuffer, longOffset, bufferLength - longOffset)
                for (idx in longOffset until bufferLength) {
                    val offset = pos + idx * Long.SIZE_BYTES
                    bb.position(0)
                    bb.put(byten(offset.toLong()))
                    bb.put(byten((offset + 1).toLong()))
                    bb.put(byten((offset + 2).toLong()))
                    bb.put(byten((offset + 3).toLong()))
                    bb.put(byten((offset + 4).toLong()))
                    bb.put(byten((offset + 5).toLong()))
                    bb.put(byten((offset + 6).toLong()))
                    bb.put(byten((offset + 7).toLong()))
                    assertEquals(bb.getLong(0), longBuffer[idx])
                }
            }
        }
    }

    private class MyBufferedIndexInput(private val len: Long = Long.MAX_VALUE) :
        BufferedIndexInput("MyBufferedIndexInput(len=$len)", BufferedIndexInput.BUFFER_SIZE) {
        private var pos: Long = 0
        var readCount: Long = 0
            private set

        override fun readInternal(b: ByteBuffer) {
            readCount++
            while (b.hasRemaining()) {
                b.put(byten(pos++))
            }
        }

        override fun seekInternal(pos: Long) {
            this.pos = pos
        }

        override fun close() {}

        override fun length(): Long = len

        override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
            throw UnsupportedOperationException()
        }
    }
}


private fun byten(n: Long): Byte = ((n * n) % 256).toByte()

