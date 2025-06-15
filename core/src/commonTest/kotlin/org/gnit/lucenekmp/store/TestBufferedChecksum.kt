package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.CRC32
import org.gnit.lucenekmp.jdkport.Checksum
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import org.gnit.lucenekmp.util.getShortLE
import org.gnit.lucenekmp.util.getIntLE
import org.gnit.lucenekmp.util.getLongLE
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBufferedChecksum : LuceneTestCase() {
    @Test
    fun testSimple() {
        val c: Checksum = BufferedChecksum(CRC32())
        c.update(1)
        c.update(2)
        c.update(3)
        assertEquals(1438416925L, c.getValue())
    }

    @Test
    fun testRandom() {
        val c1: Checksum = CRC32()
        val c2: Checksum = BufferedChecksum(CRC32())
        val iterations = atLeast(10000)
        for (i in 0 until iterations) {
            when (random().nextInt(4)) {
                0 -> {
                    val length = random().nextInt(1024)
                    val bytes = ByteArray(length)
                    random().nextBytes(bytes)
                    c1.update(bytes, 0, bytes.size)
                    c2.update(bytes, 0, bytes.size)
                }
                1 -> {
                    val b = random().nextInt(256)
                    c1.update(b)
                    c2.update(b)
                }
                2 -> {
                    c1.reset()
                    c2.reset()
                }
                3 -> {
                    assertEquals(c1.getValue(), c2.getValue())
                }
            }
        }
        assertEquals(c1.getValue(), c2.getValue())
    }

    @Test
    fun testDifferentInputTypes() {
        val crc = CRC32()
        val buffered = BufferedChecksum(CRC32())
        val iterations = atLeast(1000)
        for (i in 0 until iterations) {
            val input = ByteArray(4096)
            random().nextBytes(input)
            crc.update(input)
            val checksum = crc.getValue()
            crc.reset()
            updateByShorts(checksum, buffered, input)
            updateByInts(checksum, buffered, input)
            updateByLongs(checksum, buffered, input)
            updateByChunkOfBytes(checksum, buffered, input)
            updateByChunkOfLongs(checksum, buffered, input)
        }
    }

    private fun updateByChunkOfBytes(expected: Long, checksum: BufferedChecksum, input: ByteArray) {
        for (b in input) {
            checksum.update(b.toInt())
        }
        checkChecksumValueAndReset(expected, checksum)

        checksum.update(input)
        checkChecksumValueAndReset(expected, checksum)

        val iterations = atLeast(10)
        for (ite in 0 until iterations) {
            val len0 = random().nextInt(input.size / 2)
            checksum.update(input, 0, len0)
            checksum.update(input, len0, input.size - len0)
            checkChecksumValueAndReset(expected, checksum)

            checksum.update(input, 0, len0)
            val len1 = random().nextInt(input.size / 4)
            for (i in 0 until len1) {
                checksum.update(input[len0 + i].toInt())
            }
            checksum.update(input, len0 + len1, input.size - len1 - len0)
            checkChecksumValueAndReset(expected, checksum)
        }
    }

    private fun updateByShorts(expected: Long, checksum: BufferedChecksum, input: ByteArray) {
        var ix = shiftArray(checksum, input)
        while (ix <= input.size - Short.SIZE_BYTES) {
            checksum.updateShort(input.getShortLE(ix))
            ix += Short.SIZE_BYTES
        }
        checksum.update(input, ix, input.size - ix)
        checkChecksumValueAndReset(expected, checksum)
    }

    private fun updateByInts(expected: Long, checksum: BufferedChecksum, input: ByteArray) {
        var ix = shiftArray(checksum, input)
        while (ix <= input.size - Int.SIZE_BYTES) {
            checksum.updateInt(input.getIntLE(ix))
            ix += Int.SIZE_BYTES
        }
        checksum.update(input, ix, input.size - ix)
        checkChecksumValueAndReset(expected, checksum)
    }

    private fun updateByLongs(expected: Long, checksum: BufferedChecksum, input: ByteArray) {
        var ix = shiftArray(checksum, input)
        while (ix <= input.size - Long.SIZE_BYTES) {
            checksum.updateLong(input.getLongLE(ix))
            ix += Long.SIZE_BYTES
        }
        checksum.update(input, ix, input.size - ix)
        checkChecksumValueAndReset(expected, checksum)
    }

    private fun shiftArray(checksum: BufferedChecksum, input: ByteArray): Int {
        val ix = random().nextInt(input.size / 4)
        checksum.update(input, 0, ix)
        return ix
    }

    private fun updateByChunkOfLongs(expected: Long, checksum: BufferedChecksum, input: ByteArray) {
        val ix = random().nextInt(input.size / 4)
        val remaining = (Long.SIZE_BYTES - ix) and 7
        val b = ByteBuffer.wrap(input).position(ix).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        val longInput = LongArray((input.size - ix) / Long.SIZE_BYTES)
        b.get(longInput)

        checksum.update(input, 0, ix)
        for (value in longInput) {
            checksum.updateLong(value)
        }
        checksum.update(input, input.size - remaining, remaining)
        checkChecksumValueAndReset(expected, checksum)

        checksum.update(input, 0, ix)
        checksum.updateLongs(longInput, 0, longInput.size)
        checksum.update(input, input.size - remaining, remaining)
        checkChecksumValueAndReset(expected, checksum)

        val iterations = atLeast(10)
        for (ite in 0 until iterations) {
            val len0 = random().nextInt(longInput.size / 2)
            checksum.update(input, 0, ix)
            checksum.updateLongs(longInput, 0, len0)
            checksum.updateLongs(longInput, len0, longInput.size - len0)
            checksum.update(input, input.size - remaining, remaining)
            checkChecksumValueAndReset(expected, checksum)

            checksum.update(input, 0, ix)
            checksum.updateLongs(longInput, 0, len0)
            val len1 = random().nextInt(longInput.size / 4)
            for (i in 0 until len1) {
                checksum.updateLong(longInput[len0 + i])
            }
            checksum.updateLongs(longInput, len0 + len1, longInput.size - len1 - len0)
            checksum.update(input, input.size - remaining, remaining)
            checkChecksumValueAndReset(expected, checksum)

            checksum.update(input, 0, ix)
            checksum.updateLongs(longInput, 0, len0)
            checksum.update(input, ix + len0 * Long.SIZE_BYTES, input.size - len0 * Long.SIZE_BYTES - ix)
            checkChecksumValueAndReset(expected, checksum)
        }
    }

    private fun checkChecksumValueAndReset(expected: Long, checksum: Checksum) {
        assertEquals(expected, checksum.getValue())
        checksum.reset()
    }
}

