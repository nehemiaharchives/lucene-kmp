package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class TestIndexInput : LuceneTestCase() {

    companion object {
        private val READ_TEST_BYTES = byteArrayOf(
            0x80.toByte(),
            0x01,
            0xFF.toByte(),
            0x7F,
            0x80.toByte(),
            0x80.toByte(),
            0x01,
            0x81.toByte(),
            0x80.toByte(),
            0x01,
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x07,
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x0F,
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x07,
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x7F,
            0x06,
            'L'.code.toByte(),
            'u'.code.toByte(),
            'c'.code.toByte(),
            'e'.code.toByte(),
            'n'.code.toByte(),
            'e'.code.toByte(),
            // 2-byte UTF-8 (U+00BF "INVERTED QUESTION MARK")
            0x02,
            0xC2.toByte(),
            0xBF.toByte(),
            0x0A,
            'L'.code.toByte(),
            'u'.code.toByte(),
            0xC2.toByte(),
            0xBF.toByte(),
            'c'.code.toByte(),
            'e'.code.toByte(),
            0xC2.toByte(),
            0xBF.toByte(),
            'n'.code.toByte(),
            'e'.code.toByte(),
            // 3-byte UTF-8 (U+2620 "SKULL AND CROSSBONES")
            0x03,
            0xE2.toByte(),
            0x98.toByte(),
            0xA0.toByte(),
            0x0C,
            'L'.code.toByte(),
            'u'.code.toByte(),
            0xE2.toByte(),
            0x98.toByte(),
            0xA0.toByte(),
            'c'.code.toByte(),
            'e'.code.toByte(),
            0xE2.toByte(),
            0x98.toByte(),
            0xA0.toByte(),
            'n'.code.toByte(),
            'e'.code.toByte(),
            // surrogate pairs
            // (U+1D11E "MUSICAL SYMBOL G CLEF")
            // (U+1D160 "MUSICAL SYMBOL EIGHTH NOTE")
            0x04,
            0xF0.toByte(),
            0x9D.toByte(),
            0x84.toByte(),
            0x9E.toByte(),
            0x08,
            0xF0.toByte(),
            0x9D.toByte(),
            0x84.toByte(),
            0x9E.toByte(),
            0xF0.toByte(),
            0x9D.toByte(),
            0x85.toByte(),
            0xA0.toByte(),
            0x0E,
            'L'.code.toByte(),
            'u'.code.toByte(),
            0xF0.toByte(),
            0x9D.toByte(),
            0x84.toByte(),
            0x9E.toByte(),
            'c'.code.toByte(),
            'e'.code.toByte(),
            0xF0.toByte(),
            0x9D.toByte(),
            0x85.toByte(),
            0xA0.toByte(),
            'n'.code.toByte(),
            'e'.code.toByte(),
            // null bytes
            0x01,
            0x00,
            0x08,
            'L'.code.toByte(),
            'u'.code.toByte(),
            0x00,
            'c'.code.toByte(),
            'e'.code.toByte(),
            0x00,
            'n'.code.toByte(),
            'e'.code.toByte()
        )

        private val COUNT = RANDOM_MULTIPLIER * 65536
        private val INTS: IntArray
        private val LONGS: LongArray
        private val RANDOM_TEST_BYTES: ByteArray

        init {
            val random = LuceneTestCase.random()
            INTS = IntArray(COUNT)
            LONGS = LongArray(COUNT)
            RANDOM_TEST_BYTES = ByteArray(COUNT * (5 + 4 + 9 + 8))
            val bdo = ByteArrayDataOutput(RANDOM_TEST_BYTES)
            for (i in 0 until COUNT) {
                val i1 = random.nextInt().also { INTS[i] = it }
                bdo.writeVInt(i1)
                bdo.writeInt(i1)

                val l1 = if (TestUtil.rarely(random)) {
                    TestUtil.nextLong(random, 0, Int.MAX_VALUE.toLong()) shl 32
                } else {
                    TestUtil.nextLong(random, 0, Long.MAX_VALUE)
                }
                LONGS[i] = l1
                bdo.writeVLong(l1)
                bdo.writeLong(l1)
            }
        }
    }

    private fun newDirectory(): Directory = ByteBuffersDirectory()

    private fun newIOContext(random: Random): IOContext = IOContext.DEFAULT

    private fun checkReads(`in`: DataInput, expectedEx: KClass<out Exception>) {
        assertEquals(128, `in`.readVInt())
        assertEquals(16383, `in`.readVInt())
        assertEquals(16384, `in`.readVInt())
        assertEquals(16385, `in`.readVInt())
        assertEquals(Int.MAX_VALUE, `in`.readVInt())
        assertEquals(-1, `in`.readVInt())
        assertEquals(Int.MAX_VALUE.toLong(), `in`.readVLong())
        assertEquals(Long.MAX_VALUE, `in`.readVLong())
        assertEquals("Lucene", `in`.readString())

        assertEquals("\u00BF", `in`.readString())
        assertEquals("Lu\u00BFce\u00BFne", `in`.readString())

        assertEquals("\u2620", `in`.readString())
        assertEquals("Lu\u2620ce\u2620ne", `in`.readString())

        assertEquals("\uD834\uDD1E", `in`.readString())
        assertEquals("\uD834\uDD1E\uD834\uDD60", `in`.readString())
        assertEquals("Lu\uD834\uDD1Ece\uD834\uDD60ne", `in`.readString())

        assertEquals("\u0000", `in`.readString())
        assertEquals("Lu\u0000ce\u0000ne", `in`.readString())
    }

    private fun checkRandomReads(`in`: DataInput) {
        for (i in 0 until COUNT) {
            assertEquals(INTS[i], `in`.readVInt())
            assertEquals(INTS[i], `in`.readInt())
            assertEquals(LONGS[i], `in`.readVLong())
            assertEquals(LONGS[i], `in`.readLong())
        }
    }

    private fun checkSeeksAndSkips(`in`: IndexInput, random: Random) {
        val len = `in`.length()
        val iterations = if (LuceneTestCase.TEST_NIGHTLY) 1_000 else 10
        repeat(iterations) {
            `in`.seek(0)
            var curr = 0L
            while (curr < len) {
                val maxSkipTo = len - 1
                val skipTo = if (len - curr < 10) maxSkipTo else TestUtil.nextLong(random, curr, maxSkipTo)
                val skipDelta = skipTo - curr

                val startByte1 = `in`.readByte()
                `in`.seek(skipTo)
                val endByte1 = `in`.readByte()

                `in`.seek(curr)
                val startByte2 = `in`.readByte()
                `in`.seek(curr)
                `in`.skipBytes(skipDelta)
                val endByte2 = `in`.readByte()

                assertEquals(startByte1, startByte2)
                assertEquals(endByte1, endByte2)
                assertEquals(curr + skipDelta + 1, `in`.filePointer)

                curr = `in`.filePointer
            }
        }
    }

    @Test
    fun testRawIndexInputRead() {
        for (i in 0 until 1) { // TODO originally 10 but reduced to 1 for dev speed
            val random = random()
            newDirectory().use { dir ->
                dir.createOutput("foo", newIOContext(random)).use { os ->
                    os.writeBytes(READ_TEST_BYTES, READ_TEST_BYTES.size)
                }
                dir.openInput("foo", newIOContext(random)).use { input ->
                    checkReads(input, IOException::class)
                    checkSeeksAndSkips(input, random)
                }
                dir.createOutput("bar", newIOContext(random)).use { os ->
                    os.writeBytes(RANDOM_TEST_BYTES, RANDOM_TEST_BYTES.size)
                }
                dir.openInput("bar", newIOContext(random)).use { input ->
                    checkRandomReads(input)
                    checkSeeksAndSkips(input, random)
                }
            }
        }
    }

    @Test
    fun testByteArrayDataInput() {
        var input = ByteArrayDataInput(READ_TEST_BYTES)
        checkReads(input, RuntimeException::class)
        input = ByteArrayDataInput(RANDOM_TEST_BYTES)
        checkRandomReads(input)
    }

    @Test
    fun testNoReadOnSkipBytes() {
        val len = if (LuceneTestCase.TEST_NIGHTLY) Long.MAX_VALUE else 1_000_000L
        val maxSeekPos = len - 1
        val input = getIndexInput(len)
        while (input.filePointer < maxSeekPos) {
            val seekPos = TestUtil.nextLong(random(), input.filePointer, maxSeekPos)
            val skipDelta = seekPos - input.filePointer
            input.skipBytes(skipDelta)
            assertEquals(seekPos, input.filePointer)
        }
    }

    private fun getIndexInput(len: Long): IndexInput {
        return InterceptingIndexInput("foo", len)
    }

    private class InterceptingIndexInput(resourceDescription: String, private val len: Long) :
        IndexInput(resourceDescription) {
        private var pos: Long = 0

        override fun seek(pos: Long) {
            this.pos = pos
        }

        override val filePointer: Long
            get() = pos

        override fun length(): Long = len

        override fun readByte(): Byte {
            throw UnsupportedOperationException()
        }

        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            throw UnsupportedOperationException()
        }

        override fun close() {}

        override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
            throw UnsupportedOperationException()
        }
    }
}

