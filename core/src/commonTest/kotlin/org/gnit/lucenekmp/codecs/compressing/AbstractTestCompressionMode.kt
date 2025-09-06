package org.gnit.lucenekmp.codecs.compressing

import okio.IOException
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.compressing.Compressor
import org.gnit.lucenekmp.codecs.compressing.Decompressor
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.TEST_NIGHTLY
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.atLeast
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.random
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

abstract class AbstractTestCompressionMode : LuceneTestCase() {

    lateinit var mode: CompressionMode

    companion object {
        fun randomArray(random: Random): ByteArray {
            val bigsize = if (TEST_NIGHTLY) 192 * 1024 else 33 * 1024
            val max = if (random.nextBoolean()) random.nextInt(4) else random.nextInt(255)
            val length = if (random.nextBoolean()) random.nextInt(20) else random.nextInt(bigsize)
            return randomArray(random, length, max)
        }

        fun randomArray(random: Random, length: Int, max: Int): ByteArray {
            val arr = ByteArray(length)
            for (i in arr.indices) {
                arr[i] = RandomNumbers.randomIntBetween(random, 0, max).toByte()
            }
            return arr
        }

        @Throws(IOException::class)
        fun compress(
            compressor: Compressor,
            decompressed: ByteArray,
            off: Int,
            len: Int
        ): ByteArray {
            val compressed = ByteArray(len * 3 + 16)
            val bb = ByteBuffer.wrap(decompressed)
            val input = ByteBuffersDataInput(mutableListOf(bb)).slice(off.toLong(), len.toLong())
            val out = ByteArrayDataOutput(compressed)
            compressor.compress(input, out)
            val compressedLen = out.position
            return ArrayUtil.copyOfSubArray(compressed, 0, compressedLen)
        }

        @Throws(IOException::class)
        fun decompress(
            decompressor: Decompressor,
            compressed: ByteArray,
            originalLength: Int
        ): ByteArray {
            val bytes = BytesRef()
            decompressor.decompress(
                ByteArrayDataInput(compressed), originalLength, 0, originalLength, bytes
            )
            return BytesRef.deepCopyOf(bytes).bytes
        }
    }

    @Throws(IOException::class)
    fun compress(decompressed: ByteArray, off: Int, len: Int): ByteArray {
        val compressor = mode.newCompressor()
        return compress(compressor, decompressed, off, len)
        }

    @Throws(IOException::class)
    fun decompress(compressed: ByteArray, originalLength: Int): ByteArray {
        val decompressor = mode.newDecompressor()
        return decompress(decompressor, compressed, originalLength)
    }

    @Throws(IOException::class)
    fun decompress(
        compressed: ByteArray,
        originalLength: Int,
        offset: Int,
        length: Int
    ): ByteArray {
        val decompressor = mode.newDecompressor()
        val bytes = BytesRef()
        decompressor.decompress(
            ByteArrayDataInput(compressed), originalLength, offset, length, bytes
        )
        return BytesRef.deepCopyOf(bytes).bytes
    }

    @Test
    @Throws(IOException::class)
    open fun testDecompress() {
        val rnd = random()
        val iterations = atLeast(rnd, 3)
        for (i in 0 until iterations) {
            val decompressed = randomArray(rnd)
            val off = if (rnd.nextBoolean()) 0 else TestUtil.nextInt(rnd, 0, decompressed.size)
            val len = if (rnd.nextBoolean()) {
                decompressed.size - off
            } else {
                TestUtil.nextInt(rnd, 0, decompressed.size - off)
            }
            val compressed = compress(decompressed, off, len)
            val restored = decompress(compressed, len)
            assertContentEquals(ArrayUtil.copyOfSubArray(decompressed, off, off + len), restored)
        }
    }

    @Test
    @Throws(IOException::class)
    open fun testPartialDecompress() {
        val rnd = random()
        val iterations = atLeast(rnd, 3)
        for (i in 0 until iterations) {
            val decompressed = randomArray(rnd)
            val compressed = compress(decompressed, 0, decompressed.size)
            val (offset, length) = if (decompressed.isEmpty()) {
                0 to 0
            } else {
                val o = rnd.nextInt(decompressed.size)
                val l = rnd.nextInt(decompressed.size - o)
                o to l
            }
            val restored = decompress(compressed, decompressed.size, offset, length)
            assertContentEquals(
                ArrayUtil.copyOfSubArray(decompressed, offset, offset + length),
                restored
            )
        }
    }

    @Throws(IOException::class)
    fun test(decompressed: ByteArray): ByteArray {
        return test(decompressed, 0, decompressed.size)
    }

    @Throws(IOException::class)
    fun test(decompressed: ByteArray, off: Int, len: Int): ByteArray {
        val compressed = compress(decompressed, off, len)
        val restored = decompress(compressed, len)
        assertEquals(len, restored.size)
        return compressed
    }

    @Test
    @Throws(IOException::class)
    fun testEmptySequence() {
        test(ByteArray(0))
    }

    @Test
    @Throws(IOException::class)
    fun testShortSequence() {
        test(byteArrayOf(random().nextInt(256).toByte()))
    }

    @Test
    @Throws(IOException::class)
    fun testIncompressible() {
        val decompressed = ByteArray(RandomNumbers.randomIntBetween(random(), 20, 256))
        for (i in decompressed.indices) {
            decompressed[i] = i.toByte()
        }
        test(decompressed)
    }

    @Test
    @Throws(IOException::class)
    open fun testConstant() {
        val decompressed = ByteArray(TestUtil.nextInt(random(), 1, 10000))
        decompressed.fill(random().nextInt().toByte())
        test(decompressed)
    }

    @Test
    @Throws(IOException::class)
    open fun testExtremelyLargeInput() {
        val decompressed = ByteArray(1 shl 24)
        for (i in decompressed.indices) {
            decompressed[i] = (i and 0x0F).toByte()
        }
        test(decompressed)
    }
}

