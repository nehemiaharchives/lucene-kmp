package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TestForDeltaUtil : LuceneTestCase() {
    @Test
    fun testEncodeDecode() {
        val iterations = RandomNumbers.randomIntBetween(random(), 50, 1000)
        val values = IntArray(iterations * ForUtil.BLOCK_SIZE)
        for (i in 0 until iterations) {
            val bpv = TestUtil.nextInt(random(), 1, 31 - 7)
            for (j in 0 until ForUtil.BLOCK_SIZE) {
                values[i * ForUtil.BLOCK_SIZE + j] =
                    RandomNumbers.randomIntBetween(random(), 1, PackedInts.maxValue(bpv).toInt())
            }
        }

        val d: Directory = ByteBuffersDirectory()
        val endPointer: Long
        d.createOutput("test.bin", IOContext.DEFAULT).use { out ->
            val forDeltaUtil = ForDeltaUtil()
            for (i in 0 until iterations) {
                val source = IntArray(ForUtil.BLOCK_SIZE)
                for (j in 0 until ForUtil.BLOCK_SIZE) {
                    source[j] = values[i * ForUtil.BLOCK_SIZE + j]
                }
                val bitsPerValue = forDeltaUtil.bitsRequired(source)
                out.writeByte(bitsPerValue.toByte())
                forDeltaUtil.encodeDeltas(bitsPerValue, source, out)
            }
            endPointer = out.filePointer
        }

        d.openInput("test.bin", IOContext.READONCE).use { `in` ->
            val pdu: PostingDecodingUtil =
                Lucene101PostingsReader.VECTORIZATION_PROVIDER.newPostingDecodingUtil(`in`)
            val forDeltaUtil = ForDeltaUtil()
            for (i in 0 until iterations) {
                val base = 0
                val restored = IntArray(ForUtil.BLOCK_SIZE)
                val bitsPerValue = pdu.`in`.readByte().toInt()
                forDeltaUtil.decodeAndPrefixSum(bitsPerValue, pdu, base, restored)
                val expected = IntArray(ForUtil.BLOCK_SIZE)
                for (j in 0 until ForUtil.BLOCK_SIZE) {
                    expected[j] = values[i * ForUtil.BLOCK_SIZE + j]
                    if (j > 0) {
                        expected[j] += expected[j - 1]
                    } else {
                        expected[j] += base
                    }
                }
                assertContentEquals(expected, restored, restored.contentToString())
            }
            assertEquals(endPointer, `in`.filePointer)
        }
        d.close()
    }
}

