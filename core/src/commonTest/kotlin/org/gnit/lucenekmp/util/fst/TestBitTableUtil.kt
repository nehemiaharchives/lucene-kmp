package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.fst.BitTableUtil
import org.gnit.lucenekmp.util.fst.FST
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Port of Lucene's TestBitTableUtil from commit ec75fcad.
 */
class TestBitTableUtil : LuceneTestCase() {
    @Test
    fun testNextBitSet() {
        val numIterations = atLeast(1000)
        for (i in 0 until numIterations) {
            val bits = buildRandomBits()
            val numBytes = bits.size - 1
            val numBits = numBytes * Byte.SIZE_BITS

            // Verify nextBitSet with countBitsUpTo for all bit indexes.
            for (bitIndex in -1 until numBits) {
                val nextIndex = BitTableUtil.nextBitSet(bitIndex, numBytes, reader(bits))
                if (nextIndex == -1) {
                    assertEquals(
                        BitTableUtil.countBitsUpTo(bitIndex + 1, reader(bits)),
                        BitTableUtil.countBits(numBytes, reader(bits)),
                        "No next bit set, so expected no bit count diff (i=$i bitIndex=$bitIndex)"
                    )
                } else {
                    assertTrue(
                        BitTableUtil.isBitSet(nextIndex, reader(bits)),
                        "Expected next bit set at nextIndex=$nextIndex (i=$i bitIndex=$bitIndex)"
                    )
                    assertEquals(
                        BitTableUtil.countBitsUpTo(bitIndex + 1, reader(bits)) + 1,
                        BitTableUtil.countBitsUpTo(nextIndex + 1, reader(bits)),
                        "Next bit set at nextIndex=$nextIndex so expected bit count diff of 1 (i=$i bitIndex=$bitIndex)"
                    )
                }
            }
        }
    }

    @Test
    fun testPreviousBitSet() {
        val numIterations = atLeast(1000)
        for (i in 0 until numIterations) {
            val bits = buildRandomBits()
            val numBytes = bits.size - 1
            val numBits = numBytes * Byte.SIZE_BITS

            // Verify previousBitSet with countBitsUpTo for all bit indexes.
            for (bitIndex in 0..numBits) {
                val previousIndex = BitTableUtil.previousBitSet(bitIndex, reader(bits))
                if (previousIndex == -1) {
                    assertEquals(
                        0,
                        BitTableUtil.countBitsUpTo(bitIndex, reader(bits)),
                        "No previous bit set, so expected bit count 0 (i=$i bitIndex=$bitIndex)"
                    )
                } else {
                    assertTrue(
                        BitTableUtil.isBitSet(previousIndex, reader(bits)),
                        "Expected previous bit set at previousIndex=$previousIndex (i=$i bitIndex=$bitIndex)"
                    )
                    val bitCount = BitTableUtil.countBitsUpTo(min(bitIndex + 1, numBits), reader(bits))
                    val expectedPreviousBitCount =
                        if (bitIndex < numBits && BitTableUtil.isBitSet(bitIndex, reader(bits))) bitCount - 1 else bitCount
                    assertEquals(
                        expectedPreviousBitCount,
                        BitTableUtil.countBitsUpTo(previousIndex + 1, reader(bits)),
                        "Previous bit set at previousIndex=$previousIndex with current bitCount=$bitCount so expected previousBitCount=$expectedPreviousBitCount (i=$i bitIndex=$bitIndex)"
                    )
                }
            }
        }
    }

    private fun buildRandomBits(): ByteArray {
        val bits = ByteArray(random().nextInt(24) + 2)
        for (i in bits.indices) {
            // Bias towards zeros which require special logic.
            bits[i] = if (random().nextInt(4) == 0) 0 else random().nextInt().toByte()
        }
        return bits
    }

    private fun reader(bits: ByteArray): FST.BytesReader {
        return ByteArrayBytesReader(bits)
    }

    private class ByteArrayBytesReader(private val bits: ByteArray) : FST.BytesReader() {
        override var position: Long = 0

        override fun readByte(): Byte {
            return bits[position++.toInt()]
        }

        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            throw UnsupportedOperationException()
        }

        override fun skipBytes(numBytes: Long) {
            position += numBytes
        }
    }
}

