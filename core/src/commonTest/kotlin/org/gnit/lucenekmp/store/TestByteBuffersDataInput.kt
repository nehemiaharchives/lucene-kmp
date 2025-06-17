package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.tests.util.RandomizedTest
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IOConsumer
import org.gnit.lucenekmp.jdkport.ByteBuffer
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals
import okio.EOFException

class TestByteBuffersDataInput : RandomizedTest() {

    private fun addRandomData(out: ByteBuffersDataOutput, rnd: Random, maxAddCalls: Int): List<(ByteBuffersDataInput) -> Unit> {
        val operations = mutableListOf<(ByteBuffersDataInput) -> Unit>()
        repeat(maxAddCalls) {
            when (rnd.nextInt(11)) {
                0 -> {
                    val value = rnd.nextInt().toByte()
                    out.writeByte(value)
                    operations.add { src -> assertEquals(value, src.readByte()) }
                }
                1 -> {
                    val bytes = ByteArray(RandomNumbers.randomIntBetween(rnd, 0, 100))
                    rnd.nextBytes(bytes)
                    if (rnd.nextBoolean()) {
                        out.writeBytes(ByteBuffer.wrap(bytes))
                    } else {
                        out.writeBytes(bytes, bytes.size)
                    }
                    val useBuffer = rnd.nextBoolean()
                    operations.add { src ->
                        val read = ByteArray(bytes.size)
                        if (useBuffer) {
                            src.readBytes(ByteBuffer.wrap(read), read.size)
                        } else {
                            src.readBytes(read, 0, read.size)
                        }
                        assertContentEquals(bytes, read)
                    }
                }
                2 -> {
                    val bytes = ByteArray(RandomNumbers.randomIntBetween(rnd, 0, 100))
                    rnd.nextBytes(bytes)
                    val off = RandomNumbers.randomIntBetween(rnd, 0, bytes.size)
                    val len = RandomNumbers.randomIntBetween(rnd, 0, bytes.size - off)
                    out.writeBytes(bytes, off, len)
                    operations.add { src ->
                        val read = ByteArray(bytes.size + off)
                        src.readBytes(read, off, len)
                        val expected = ArrayUtil.copyOfSubArray(bytes, off, off + len)
                        val actual = ArrayUtil.copyOfSubArray(read, off, off + len)
                        assertContentEquals(expected, actual)
                    }
                }
                3 -> {
                    val v = rnd.nextInt()
                    out.writeInt(v)
                    operations.add { src -> assertEquals(v, src.readInt()) }
                }
                4 -> {
                    val v = rnd.nextLong()
                    out.writeLong(v)
                    operations.add { src -> assertEquals(v, src.readLong()) }
                }
                5 -> {
                    val v = rnd.nextInt().toShort()
                    out.writeShort(v)
                    operations.add { src -> assertEquals(v, src.readShort()) }
                }
                6 -> {
                    val v = rnd.nextInt()
                    out.writeVInt(v)
                    operations.add { src -> assertEquals(v, src.readVInt()) }
                }
                7 -> {
                    val v = rnd.nextInt()
                    out.writeZInt(v)
                    operations.add { src -> assertEquals(v, src.readZInt()) }
                }
                8 -> {
                    val v = rnd.nextLong() ushr 1
                    out.writeVLong(v)
                    operations.add { src -> assertEquals(v, src.readVLong()) }
                }
                9 -> {
                    val v = rnd.nextLong()
                    out.writeZLong(v)
                    operations.add { src -> assertEquals(v, src.readZLong()) }
                }
                else -> {
                    val len = if (rnd.nextInt(50) == 0) {
                        RandomNumbers.randomIntBetween(rnd, 2048, 4096)
                    } else {
                        RandomNumbers.randomIntBetween(rnd, 0, 10)
                    }
                    val s = TestUtil.randomUnicodeString(rnd, len)
                    out.writeString(s)
                    operations.add { src -> assertEquals(s, src.readString()) }
                }
            }
        }
        return operations
    }

    @Test
    fun testSanity() {
        val out = ByteBuffersDataOutput()
        val o1 = out.toDataInput()
        assertEquals(0L, o1.length())
        LuceneTestCase.expectThrows(EOFException::class) { o1.readByte() }

        out.writeByte(1)

        val o2 = out.toDataInput()
        assertEquals(1L, o2.length())
        assertEquals(0L, o2.position())
        assertEquals(0L, o1.length())

        assertTrue(o2.ramBytesUsed() > 0)
        assertEquals(1.toByte(), o2.readByte())
        assertEquals(1L, o2.position())
        assertEquals(1.toByte(), o2.readByte(0))
        LuceneTestCase.expectThrows(EOFException::class) { o2.readByte() }
        assertEquals(1L, o2.position())
    }

    @Test
    fun testRandomReads() {
        val dst = ByteBuffersDataOutput()
        val seed = LuceneTestCase.random().nextLong()
        val max = if (LuceneTestCase.TEST_NIGHTLY) 1_000 else 100
        val ops = addRandomData(dst, Random(seed), max)
        val src = dst.toDataInput()
        for (op in ops) {
            op(src)
        }
        LuceneTestCase.expectThrows(EOFException::class) { src.readByte() }
    }

    @Test
    fun testRandomReadsOnSlices() {
        repeat(RandomNumbers.randomIntBetween(LuceneTestCase.random(), 1, 3)) {
            val dst = ByteBuffersDataOutput()
            val prefix = ByteArray(RandomNumbers.randomIntBetween(LuceneTestCase.random(), 0, 1024 * 8))
            dst.writeBytes(prefix)
            val seed = LuceneTestCase.random().nextLong()
            val ops = addRandomData(dst, Random(seed), 50)
            val suffix = ByteArray(RandomNumbers.randomIntBetween(LuceneTestCase.random(), 0, 1024 * 8))
            dst.writeBytes(suffix)
            val src = dst.toDataInput().slice(prefix.size.toLong(), dst.size() - prefix.size - suffix.size)
            assertEquals(0L, src.position())
            assertEquals(dst.size() - prefix.size - suffix.size, src.length())
            for (op in ops) { op(src) }
            LuceneTestCase.expectThrows(EOFException::class) { src.readByte() }
        }
    }

    @Test
    fun testSeekEmpty() {
        val dst = ByteBuffersDataOutput()
        val input = dst.toDataInput()
        input.seek(0)
        LuceneTestCase.expectThrows(EOFException::class) { input.seek(1) }
        input.seek(0)
        LuceneTestCase.expectThrows(EOFException::class) { input.readByte() }
    }

    @Test
    fun testSeekAndSkip() {
        repeat(RandomNumbers.randomIntBetween(LuceneTestCase.random(), 1, 3)) {
            val dst = ByteBuffersDataOutput()
            var prefix = ByteArray(0)
            if (LuceneTestCase.random().nextBoolean()) {
                prefix = ByteArray(RandomNumbers.randomIntBetween(LuceneTestCase.random(), 1, 1024 * 8))
                dst.writeBytes(prefix)
            }
            val seed = LuceneTestCase.random().nextLong()
            val ops = addRandomData(dst, Random(seed), 20)
            val input = dst.toDataInput().slice(prefix.size.toLong(), dst.size() - prefix.size)

            input.seek(0)
            for (op in ops) { op(input) }
            input.seek(0)
            for (op in ops) { op(input) }

            var array = dst.toArrayCopy()
            array = ArrayUtil.copyOfSubArray(array, prefix.size, array.size)

            for (i in 0 until 10) {
                val offs = RandomNumbers.randomIntBetween(LuceneTestCase.random(), 0, array.size - 1)
                input.seek(offs.toLong())
                assertEquals(offs.toLong(), input.position())
                assertEquals(array[offs], input.readByte())
            }

            val maxSkipTo = array.size - 1
            input.seek(0)
            var curr = 0
            while (curr < maxSkipTo) {
                val skipTo = RandomNumbers.randomIntBetween(LuceneTestCase.random(), curr, maxSkipTo)
                val step = skipTo - curr
                input.skipBytes(step.toLong())
                assertEquals(array[skipTo], input.readByte())
                curr = skipTo + 1
            }

            input.seek(input.length())
            assertEquals(input.length(), input.position())
            LuceneTestCase.expectThrows(EOFException::class) { input.readByte() }
        }
    }

    @Test
    fun testSlicingWindow() {
        val dst = ByteBuffersDataOutput()
        assertEquals(0L, dst.toDataInput().slice(0, 0).length())

        val bytes = ByteArray(1024 * 8)
        LuceneTestCase.random().nextBytes(bytes)
        dst.writeBytes(bytes)
        val input = dst.toDataInput()
        val max = dst.size().toInt()
        for (offset in 0 until max) {
            assertEquals(0L, input.slice(offset.toLong(), 0).length())
            assertEquals(1L, input.slice(offset.toLong(), 1).length())
            val window = min(max - offset, 1024)
            assertEquals(window.toLong(), input.slice(offset.toLong(), window.toLong()).length())
        }
        assertEquals(0L, input.slice(dst.size(), 0).length())
    }

    @Test
    fun testEofOnArrayReadPastBufferSize() {
        val dst = ByteBuffersDataOutput()
        dst.writeBytes(ByteArray(10))
        LuceneTestCase.expectThrows(EOFException::class) {
            val input = dst.toDataInput()
            input.readBytes(ByteArray(100), 0, 100)
        }
        LuceneTestCase.expectThrows(EOFException::class) {
            val input = dst.toDataInput()
            input.readBytes(ByteBuffer.allocate(100), 100)
        }
    }

    @Test
    fun testSlicingLargeBuffers() {
        val MB = 1024 * 1024
        val pageBytes = ByteArray(4 * MB)
        LuceneTestCase.random().nextBytes(pageBytes)
        val shift = RandomNumbers.randomIntBetween(LuceneTestCase.random(), 0, pageBytes.size / 2)
        val simulatedLength = LuceneTestCase.random().nextLong(0, 2019) + 4L * Int.MAX_VALUE
        val buffers = mutableListOf<ByteBuffer>()
        var remaining = simulatedLength + shift
        while (remaining > 0) {
            val bb = ByteBuffer.wrap(pageBytes)
            if (bb.remaining() > remaining) {
                bb.limit = (bb.position + remaining).toInt()
            }
            buffers.add(bb)
            remaining -= bb.remaining().toLong()
        }
        buffers[0].position = shift
        val input = ByteBuffersDataInput(buffers)
        assertEquals(simulatedLength, input.length())
        val max = input.length()
        var offset = 0L
        while (offset < max) {
            assertEquals(0L, input.slice(offset, 0).length())
            assertEquals(1L, input.slice(offset, 1).length())
            val window = min(max - offset, 1024L)
            val slice = input.slice(offset, window)
            assertEquals(window, slice.length())
            for (i in 0 until window) {
                val expected = pageBytes[((shift + offset + i) % pageBytes.size).toInt()]
                assertEquals(expected, slice.readByte(i.toLong()))
            }
            offset += RandomNumbers.randomIntBetween(LuceneTestCase.random(), MB, 4 * MB).toLong()
        }
    }
}
