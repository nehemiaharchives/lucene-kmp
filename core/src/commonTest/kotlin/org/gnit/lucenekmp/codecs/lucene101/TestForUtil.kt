package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.packed.PackedInts
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Port of Lucene's TestForUtil from commit ec75fcad.
 */
class TestForUtil : LuceneTestCase() {
    @Test
    fun testEncodeDecode() {
        val iterations = RandomNumbers.randomIntBetween(random(), 50, 1000)
        val values = IntArray(iterations * ForUtil.BLOCK_SIZE)

        for (i in 0 until iterations) {
            val bpv = TestUtil.nextInt(random(), 1, 31)
            for (j in 0 until ForUtil.BLOCK_SIZE) {
                val max = PackedInts.maxValue(bpv).toInt()
                values[i * ForUtil.BLOCK_SIZE + j] = if (max == Int.MAX_VALUE) {
                    random().nextInt() and Int.MAX_VALUE
                } else {
                    RandomNumbers.randomIntBetween(random(), 0, max)
                }
            }
        }

        val d: Directory = ByteBuffersDirectory()
        val endPointer: Long

        // encode
        run {
            val out: IndexOutput = d.createOutput("test.bin", IOContext.DEFAULT)
            val forUtil = ForUtil()
            for (i in 0 until iterations) {
                val source = IntArray(ForUtil.BLOCK_SIZE)
                var or = 0L
                for (j in 0 until ForUtil.BLOCK_SIZE) {
                    source[j] = values[i * ForUtil.BLOCK_SIZE + j]
                    or = or or source[j].toLong()
                }
                val bpv = PackedInts.bitsRequired(or)
                out.writeByte(bpv.toByte())
                forUtil.encode(source, bpv, out)
            }
            endPointer = out.filePointer
            out.close()
        }

        // decode
        run {
            val input: IndexInput = d.openInput("test.bin", IOContext.READONCE)
            val pdu: PostingDecodingUtil =
                Lucene101PostingsReader.VECTORIZATION_PROVIDER.newPostingDecodingUtil(input)
            val forUtil = ForUtil()
            for (i in 0 until iterations) {
                val bitsPerValue = input.readByte().toInt()
                val currentFilePointer = input.filePointer
                val restored = IntArray(ForUtil.BLOCK_SIZE)
                forUtil.decode(bitsPerValue, pdu, restored)
                val ints = IntArray(ForUtil.BLOCK_SIZE)
                for (j in 0 until ForUtil.BLOCK_SIZE) {
                    ints[j] = restored[j]
                }
                assertContentEquals(
                    ArrayUtil.copyOfSubArray(
                        values,
                        i * ForUtil.BLOCK_SIZE,
                        (i + 1) * ForUtil.BLOCK_SIZE
                    ),
                    ints,
                    ints.contentToString()
                )
                assertEquals(
                    ForUtil.numBytes(bitsPerValue).toLong(),
                    input.filePointer - currentFilePointer
                )
            }
            assertEquals(endPointer, input.filePointer)
            input.close()
        }

        d.close()
    }
}

