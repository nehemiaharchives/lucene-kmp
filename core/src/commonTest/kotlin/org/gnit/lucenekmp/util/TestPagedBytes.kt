package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RamUsageTester
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestPagedBytes : LuceneTestCase() {

    // Writes random byte/s to "normal" file in dir, then
    // copies into PagedBytes and verifies with
    // PagedBytes.Reader:
    @Test
    fun testDataInputOutput() {
        val random: Random = random()
        val numIters: Int = atLeast(1)
        for (iter in 0..<numIters) {
            val dir: BaseDirectoryWrapper = newFSDirectory(createTempDir("testOverflow"))
            if (dir is MockDirectoryWrapper) {
                dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
            }
            val blockBits: Int = TestUtil.nextInt(random, 1, 20)
            val blockSize = 1 shl blockBits
            val p = PagedBytes(blockBits)
            val out: IndexOutput = dir.createOutput("foo", IOContext.DEFAULT)
            val numBytes: Int =
                if (TEST_NIGHTLY) {
                    TestUtil.nextInt(random(), 2, 10_000_000)
                } else {
                    TestUtil.nextInt(random(), 2, 1_000_000)
                }

            val answer = ByteArray(numBytes)
            random.nextBytes(answer)
            var written = 0
            while (written < numBytes) {
                if (random.nextInt(100) == 7) {
                    out.writeByte(answer[written++])
                } else {
                    val chunk = minOf(random.nextInt(1000), numBytes - written)
                    out.writeBytes(answer, written, chunk)
                    written += chunk
                }
            }

            out.close()
            val input: IndexInput = dir.openInput("foo", IOContext.DEFAULT)
            val `in`: DataInput = input.clone()

            p.copy(input, input.length())
            val reader: PagedBytes.Reader = p.freeze(random.nextBoolean())

            val verify = ByteArray(numBytes)
            var read = 0
            while (read < numBytes) {
                if (random.nextInt(100) == 7) {
                    verify[read++] = `in`.readByte()
                } else {
                    val chunk = minOf(random.nextInt(1000), numBytes - read)
                    `in`.readBytes(verify, read, chunk)
                    read += chunk
                }
            }
            assertTrue(answer.contentEquals(verify))

            val slice = BytesRef()
            for (iter2 in 0..<100) {
                val pos = random.nextInt(numBytes - 1)
                assertEquals(answer[pos], reader.getByte(pos.toLong()))
                val len = random.nextInt(minOf(blockSize + 1, numBytes - pos))
                reader.fillSlice(slice, pos.toLong(), len)
                for (byteUpto in 0..<len) {
                    assertEquals(answer[pos + byteUpto], slice.bytes[slice.offset + byteUpto])
                }
            }
            input.close()
            dir.close()
        }
    }

    // Writes random byte/s into PagedBytes via
    // .getDataOutput(), then verifies with
    // PagedBytes.getDataInput():
    @Test
    fun testDataInputOutput2() {
        val random: Random = random()
        val numIters: Int = atLeast(1)
        for (iter in 0..<numIters) {
            val blockBits: Int = TestUtil.nextInt(random, 1, 20)
            val blockSize = 1 shl blockBits
            val p = PagedBytes(blockBits)
            val out: DataOutput = p.dataOutput
            val numBytes: Int =
                if (TEST_NIGHTLY) {
                    TestUtil.nextInt(random(), 1, 10_000_000)
                } else {
                    TestUtil.nextInt(random(), 1, 1_000_000)
                }

            val answer = ByteArray(numBytes)
            random().nextBytes(answer)
            var written = 0
            while (written < numBytes) {
                if (random().nextInt(10) == 7) {
                    out.writeByte(answer[written++])
                } else {
                    val chunk = minOf(random().nextInt(1000), numBytes - written)
                    out.writeBytes(answer, written, chunk)
                    written += chunk
                }
            }

            val reader: PagedBytes.Reader = p.freeze(random.nextBoolean())

            val `in`: DataInput = p.dataInput

            val verify = ByteArray(numBytes)
            var read = 0
            while (read < numBytes) {
                if (random().nextInt(10) == 7) {
                    verify[read++] = `in`.readByte()
                } else {
                    val chunk = minOf(random().nextInt(1000), numBytes - read)
                    `in`.readBytes(verify, read, chunk)
                    read += chunk
                }
            }
            assertTrue(answer.contentEquals(verify))

            val slice = BytesRef()
            for (iter2 in 0..<100) {
                val pos = random.nextInt(numBytes - 1)
                val len = random.nextInt(minOf(blockSize + 1, numBytes - pos))
                reader.fillSlice(slice, pos.toLong(), len)
                for (byteUpto in 0..<len) {
                    assertEquals(answer[pos + byteUpto], slice.bytes[slice.offset + byteUpto])
                }
            }

            // test skipping
            val in2: DataInput = p.dataInput
            val maxSkipTo = numBytes - 1
            // skip chunks of bytes until exhausted
            var curr = 0
            while (curr < maxSkipTo) {
                val skipTo = TestUtil.nextInt(random, curr, maxSkipTo)
                val step = skipTo - curr
                in2.skipBytes(step.toLong())
                assertEquals(answer[skipTo], in2.readByte())
                curr = skipTo + 1 // +1 for read byte
            }
        }
    }

    @Ignore // memory hole
    @Test
    @Throws(IOException::class)
    fun testOverflow() {
        val dir: BaseDirectoryWrapper = newFSDirectory(createTempDir("testOverflow"))
        if (dir is MockDirectoryWrapper) {
            dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        }
        val blockBits: Int = TestUtil.nextInt(random(), 14, 28)
        val blockSize = 1 shl blockBits
        val arr = ByteArray(TestUtil.nextInt(random(), blockSize / 2, blockSize * 2))
        for (i in arr.indices) {
            arr[i] = i.toByte()
        }
        val numBytes = (1L shl 31) + TestUtil.nextInt(random(), 1, blockSize * 3)
        val p = PagedBytes(blockBits)
        val out: IndexOutput = dir.createOutput("foo", IOContext.DEFAULT)
        var i = 0L
        while (i < numBytes) {
            assertEquals(i, out.filePointer)
            val len = minOf(arr.size.toLong(), numBytes - i).toInt()
            out.writeBytes(arr, len)
            i += len.toLong()
        }
        assertEquals(numBytes, out.filePointer)
        out.close()
        val `in`: IndexInput = dir.openInput("foo", IOContext.DEFAULT)
        p.copy(`in`, numBytes)
        val reader: PagedBytes.Reader = p.freeze(random().nextBoolean())

        for (offset in longArrayOf(0L, Int.MAX_VALUE.toLong(), numBytes - 1, TestUtil.nextLong(random(), 1, numBytes - 2))) {
            val b = BytesRef()
            reader.fillSlice(b, offset, 1)
            assertEquals(arr[(offset % arr.size).toInt()], b.bytes[b.offset])
        }
        `in`.close()
        dir.close()
    }

    @Test
    fun testRamBytesUsed() {
        val blockBits: Int = TestUtil.nextInt(random(), 4, 22)
        val b = PagedBytes(blockBits)
        val totalBytes = random().nextInt(10000)
        var pointer = 0L
        while (pointer < totalBytes) {
            val bytes = newBytesRef(TestUtil.randomSimpleString(random(), 10))
            pointer = b.copyUsingLengthPrefix(bytes)
        }
        assertEquals(RamUsageTester.ramUsed(b), b.ramBytesUsed())
        val reader: PagedBytes.Reader = b.freeze(random().nextBoolean())
        assertEquals(RamUsageTester.ramUsed(b), b.ramBytesUsed())
        assertEquals(RamUsageTester.ramUsed(reader), reader.ramBytesUsed())
    }
}
