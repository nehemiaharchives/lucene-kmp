package org.gnit.lucenekmp.tests.store

import okio.Path
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.GroupVIntUtil

/**
 * Base class for Directories that "chunk" the input into blocks.
 *
 * It tries to explicitly chunk with different sizes and test boundary conditions around the chunks.
 */
abstract class BaseChunkedDirectoryTestCase : BaseDirectoryTestCase() {
    @Throws(Exception::class)
    override fun getDirectory(path: Path): Directory {
        return getDirectory(path, 1 shl TestUtil.nextInt(random(), 10, 20))
    }

    /** Creates a new directory with the specified max chunk size */
    @Throws(Exception::class)
    protected abstract fun getDirectory(path: Path, maxChunkSize: Int): Directory

    @Throws(Exception::class)
    open fun testGroupVIntMultiBlocks() {
        val maxChunkSize = random().nextInt(64, 513)
        getDirectory(createTempDir(), maxChunkSize).use { dir ->
            doTestGroupVInt(dir, 10, 1, 31, 1024)
        }
    }

    @Throws(Exception::class)
    open fun testCloneClose() {
        val dir = getDirectory(createTempDir("testCloneClose"))
        val io = dir.createOutput("bytes", LuceneTestCase.newIOContext(random()))
        val values = longArrayOf(0, 7, 11, 9)
        io.writeVInt(5)
        io.writeGroupVInts(values, values.size)
        io.close()
        val one = dir.openInput("bytes", IOContext.DEFAULT)
        val two = one.clone()
        val three = two.clone()
        two.close()
        kotlin.test.assertEquals(5, one.readVInt())
        LuceneTestCase.expectThrows(AlreadyClosedException::class) {
            two.readVInt()
        }
        LuceneTestCase.expectThrows(AlreadyClosedException::class) {
            GroupVIntUtil.readGroupVInts(two, values, values.size)
        }
        kotlin.test.assertEquals(5, three.readVInt())
        one.close()
        three.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testCloneSliceClose() {
        val dir = getDirectory(createTempDir("testCloneSliceClose"))
        val io = dir.createOutput("bytes", LuceneTestCase.newIOContext(random()))
        val values = longArrayOf(0, 7, 11, 9)
        io.writeInt(1)
        io.writeInt(2)
        io.writeGroupVInts(values, values.size)
        io.close()
        val slicer = dir.openInput("bytes", LuceneTestCase.newIOContext(random()))
        val one = slicer.slice("first int", 0, 4 + 5L)
        val two = slicer.slice("second int", 4, 4)
        one.close()
        LuceneTestCase.expectThrows(AlreadyClosedException::class) {
            one.readInt()
        }
        LuceneTestCase.expectThrows(AlreadyClosedException::class) {
            GroupVIntUtil.readGroupVInts(one, values, values.size)
        }
        kotlin.test.assertEquals(2, two.readInt())
        val another = slicer.slice("first int", 0, 4)
        kotlin.test.assertEquals(1, another.readInt())
        another.close()
        two.close()
        slicer.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testSeekZero() {
        val upto = if (LuceneTestCase.TEST_NIGHTLY) 31 else 3
        for (i in 0 until upto) {
            val dir = getDirectory(createTempDir("testSeekZero"), 1 shl i)
            val io = dir.createOutput("zeroBytes", LuceneTestCase.newIOContext(random()))
            io.close()
            val ii = dir.openInput("zeroBytes", LuceneTestCase.newIOContext(random()))
            ii.seek(0L)
            ii.close()
            dir.close()
        }
    }

    @Throws(Exception::class)
    open fun testSeekSliceZero() {
        val upto = if (LuceneTestCase.TEST_NIGHTLY) 31 else 3
        for (i in 0 until upto) {
            val dir = getDirectory(createTempDir("testSeekSliceZero"), 1 shl i)
            val io = dir.createOutput("zeroBytes", LuceneTestCase.newIOContext(random()))
            io.close()
            val slicer = dir.openInput("zeroBytes", LuceneTestCase.newIOContext(random()))
            val ii = slicer.slice("zero-length slice", 0, 0)
            ii.seek(0L)
            ii.close()
            slicer.close()
            dir.close()
        }
    }

    @Throws(Exception::class)
    open fun testSeekEnd() {
        for (i in 0 until 17) {
            val dir = getDirectory(createTempDir("testSeekEnd"), 1 shl i)
            val io = dir.createOutput("bytes", LuceneTestCase.newIOContext(random()))
            val bytes = ByteArray(1 shl i)
            random().nextBytes(bytes)
            io.writeBytes(bytes, bytes.size)
            io.close()
            val ii = dir.openInput("bytes", LuceneTestCase.newIOContext(random()))
            val actual = ByteArray(1 shl i)
            ii.readBytes(actual, 0, actual.size)
            kotlin.test.assertEquals(BytesRef(bytes), BytesRef(actual))
            ii.seek((1 shl i).toLong())
            ii.close()
            dir.close()
        }
    }

    @Throws(Exception::class)
    open fun testSeekSliceEnd() {
        for (i in 0 until 17) {
            val dir = getDirectory(createTempDir("testSeekSliceEnd"), 1 shl i)
            val io = dir.createOutput("bytes", LuceneTestCase.newIOContext(random()))
            val bytes = ByteArray(1 shl i)
            random().nextBytes(bytes)
            io.writeBytes(bytes, bytes.size)
            io.close()
            val slicer = dir.openInput("bytes", LuceneTestCase.newIOContext(random()))
            val ii = slicer.slice("full slice", 0, bytes.size.toLong())
            val actual = ByteArray(1 shl i)
            ii.readBytes(actual, 0, actual.size)
            kotlin.test.assertEquals(BytesRef(bytes), BytesRef(actual))
            ii.seek((1 shl i).toLong())
            ii.close()
            slicer.close()
            dir.close()
        }
    }

    @Throws(Exception::class)
    open fun testSeeking() {
        val numIters = if (LuceneTestCase.TEST_NIGHTLY) 10 else 1
        for (i in 0 until numIters) {
            val dir = getDirectory(createTempDir("testSeeking"), 1 shl i)
            val io = dir.createOutput("bytes", LuceneTestCase.newIOContext(random()))
            val bytes = ByteArray(1 shl (i + 1))
            random().nextBytes(bytes)
            io.writeBytes(bytes, bytes.size)
            io.close()
            val ii = dir.openInput("bytes", LuceneTestCase.newIOContext(random()))
            val actual = ByteArray(1 shl (i + 1))
            ii.readBytes(actual, 0, actual.size)
            kotlin.test.assertEquals(BytesRef(bytes), BytesRef(actual))
            for (sliceStart in bytes.indices) {
                for (sliceLength in 0 until bytes.size - sliceStart) {
                    val slice = ByteArray(sliceLength)
                    ii.seek(sliceStart.toLong())
                    ii.readBytes(slice, 0, slice.size)
                    kotlin.test.assertEquals(BytesRef(bytes, sliceStart, sliceLength), BytesRef(slice))
                }
            }
            ii.close()
            dir.close()
        }
    }

    @Throws(Exception::class)
    open fun testSlicedSeeking() {
        val numIters = if (LuceneTestCase.TEST_NIGHTLY) 10 else 1
        for (i in 0 until numIters) {
            val dir = getDirectory(createTempDir("testSlicedSeeking"), 1 shl i)
            val io = dir.createOutput("bytes", LuceneTestCase.newIOContext(random()))
            val bytes = ByteArray(1 shl (i + 1))
            random().nextBytes(bytes)
            io.writeBytes(bytes, bytes.size)
            io.close()
            val ii = dir.openInput("bytes", LuceneTestCase.newIOContext(random()))
            val actual = ByteArray(1 shl (i + 1))
            ii.readBytes(actual, 0, actual.size)
            ii.close()
            kotlin.test.assertEquals(BytesRef(bytes), BytesRef(actual))
            val slicer = dir.openInput("bytes", LuceneTestCase.newIOContext(random()))
            for (sliceStart in bytes.indices) {
                for (sliceLength in 0 until bytes.size - sliceStart) {
                    assertSlice(bytes, slicer, 0, sliceStart, sliceLength)
                }
            }
            slicer.close()
            dir.close()
        }
    }

    @Throws(Exception::class)
    override fun testSliceOfSlice() {
        val upto = if (LuceneTestCase.TEST_NIGHTLY) 10 else 8
        for (i in 0 until upto) {
            val dir = getDirectory(createTempDir("testSliceOfSlice"), 1 shl i)
            val io = dir.createOutput("bytes", LuceneTestCase.newIOContext(random()))
            val bytes = ByteArray(1 shl (i + 1))
            random().nextBytes(bytes)
            io.writeBytes(bytes, bytes.size)
            io.close()
            val ii = dir.openInput("bytes", LuceneTestCase.newIOContext(random()))
            val actual = ByteArray(1 shl (i + 1))
            ii.readBytes(actual, 0, actual.size)
            ii.close()
            kotlin.test.assertEquals(BytesRef(bytes), BytesRef(actual))
            val outerSlicer = dir.openInput("bytes", LuceneTestCase.newIOContext(random()))
            val outerSliceStart = random().nextInt(bytes.size / 2)
            val outerSliceLength = random().nextInt(bytes.size - outerSliceStart)
            val innerSlicer = outerSlicer.slice("parentBytesSlice", outerSliceStart.toLong(), outerSliceLength.toLong())
            for (sliceStart in 0 until outerSliceLength) {
                for (sliceLength in 0 until outerSliceLength - sliceStart) {
                    assertSlice(bytes, innerSlicer, outerSliceStart, sliceStart, sliceLength)
                }
            }
            innerSlicer.close()
            outerSlicer.close()
            dir.close()
        }
    }

    @Throws(Exception::class)
    private fun assertSlice(
        bytes: ByteArray,
        slicer: IndexInput,
        outerSliceStart: Int,
        sliceStart: Int,
        sliceLength: Int
    ) {
        val slice = ByteArray(sliceLength)
        val input = slicer.slice("bytesSlice", sliceStart.toLong(), slice.size.toLong())
        input.readBytes(slice, 0, slice.size)
        input.close()
        kotlin.test.assertEquals(BytesRef(bytes, outerSliceStart + sliceStart, sliceLength), BytesRef(slice))
    }

    @Throws(Exception::class)
    open fun testRandomChunkSizes() {
        val num = if (LuceneTestCase.TEST_NIGHTLY) LuceneTestCase.atLeast(10) else 3
        for (i in 0 until num) {
            assertChunking(random(), TestUtil.nextInt(random(), 20, 100))
        }
    }

    @Throws(Exception::class)
    private fun assertChunking(random: kotlin.random.Random, chunkSize: Int) {
        val path = createTempDir("mmap$chunkSize")
        val chunkedDir = getDirectory(path, chunkSize)
        val dir = MockDirectoryWrapper(random, chunkedDir)
        val writer = RandomIndexWriter(
            random,
            dir,
            newIndexWriterConfig(MockAnalyzer(random)).setMergePolicy(newLogMergePolicy())
        )
        val doc = Document()
        val docid = newStringField("docid", "0", Field.Store.YES)
        val junk = newStringField("junk", "", Field.Store.YES)
        doc.add(docid)
        doc.add(junk)

        val numDocs = 100
        for (i in 0 until numDocs) {
            docid.setStringValue("$i")
            junk.setStringValue(TestUtil.randomUnicodeString(random))
            writer.addDocument(doc)
        }
        val reader: IndexReader = writer.reader
        writer.close()

        val storedFields: StoredFields = reader.storedFields()
        val numAsserts = LuceneTestCase.atLeast(100)
        for (i in 0 until numAsserts) {
            val docID = random.nextInt(numDocs)
            kotlin.test.assertEquals("$docID", storedFields.document(docID).get("docid"))
        }
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testBytesCrossBoundary() {
        val num = if (LuceneTestCase.TEST_NIGHTLY) TestUtil.nextInt(random(), 100, 1000)
        else TestUtil.nextInt(random(), 50, 100)
        val bytes = ByteArray(num)
        random().nextBytes(bytes)
        getDirectory(createTempDir("testBytesCrossBoundary"), 16).use { dir ->
            dir.createOutput("bytesCrossBoundary", LuceneTestCase.newIOContext(random())).use { out ->
                out.writeBytes(bytes, bytes.size)
            }
            dir.openInput("bytesCrossBoundary", LuceneTestCase.newIOContext(random())).use { input ->
                val slice = input.randomAccessSlice(0, input.length())
                kotlin.test.assertEquals(input.length(), slice.length())
                assertBytes(slice, bytes, 0)

                for (offset in 1 until bytes.size) {
                    val subslice = input.randomAccessSlice(offset.toLong(), input.length() - offset)
                    kotlin.test.assertEquals(input.length() - offset, subslice.length())
                    assertBytes(subslice, bytes, offset)
                }

                for (i in 1 until 7) {
                    val name = "bytes-$i"
                    val o: IndexOutput = dir.createOutput(name, LuceneTestCase.newIOContext(random()))
                    val junk = ByteArray(i)
                    random().nextBytes(junk)
                    o.writeBytes(junk, junk.size)
                    input.seek(0)
                    o.copyBytes(input, input.length())
                    o.close()
                    val padded = dir.openInput(name, LuceneTestCase.newIOContext(random()))
                    val whole: RandomAccessInput = padded.randomAccessSlice(i.toLong(), padded.length() - i)
                    kotlin.test.assertEquals(padded.length() - i, whole.length())
                    assertBytes(whole, bytes, 0)
                    padded.close()
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testLittleEndianLongsCrossBoundary() {
        getDirectory(createTempDir("testLittleEndianLongsCrossBoundary"), 16).use { dir ->
            dir.createOutput("littleEndianLongs", LuceneTestCase.newIOContext(random())).use { out ->
                out.writeByte(2)
                out.writeLong(3L)
                out.writeLong(Long.MAX_VALUE)
                out.writeLong(-3L)
            }
            dir.openInput("littleEndianLongs", LuceneTestCase.newIOContext(random())).use { input ->
                kotlin.test.assertEquals(25L, input.length())
                kotlin.test.assertEquals(2, input.readByte().toInt())
                val l = LongArray(4)
                input.readLongs(l, 1, 3)
                kotlin.test.assertContentEquals(longArrayOf(0L, 3L, Long.MAX_VALUE, -3L), l)
                kotlin.test.assertEquals(25L, input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testLittleEndianFloatsCrossBoundary() {
        getDirectory(createTempDir("testFloatsCrossBoundary"), 8).use { dir ->
            dir.createOutput("Floats", LuceneTestCase.newIOContext(random())).use { out ->
                out.writeByte(2)
                out.writeInt(3f.toRawBits())
                out.writeInt(Float.MAX_VALUE.toRawBits())
                out.writeInt((-3f).toRawBits())
            }
            dir.openInput("Floats", LuceneTestCase.newIOContext(random())).use { input ->
                kotlin.test.assertEquals(13L, input.length())
                kotlin.test.assertEquals(2, input.readByte().toInt())
                val ff = FloatArray(4)
                input.readFloats(ff, 1, 3)
                kotlin.test.assertContentEquals(floatArrayOf(0f, 3f, Float.MAX_VALUE, -3f), ff)
                kotlin.test.assertEquals(13L, input.filePointer)
            }
        }
    }
}
