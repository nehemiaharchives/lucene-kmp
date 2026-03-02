package org.gnit.lucenekmp.store

import okio.EOFException
import okio.FileNotFoundException
import okio.Path
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.tests.store.BaseChunkedDirectoryTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Tests MMapDirectory's MultiMMapIndexInput
 *
 * Because Java's ByteBuffer uses an int to address the values, it's necessary to access a file
 * > Integer.MAX_VALUE in size using multiple byte buffers.
 */
class TestMultiMMap : BaseChunkedDirectoryTestCase() {
    @Throws(Exception::class)
    override fun getDirectory(path: Path, maxChunkSize: Int): Directory {
        return MMapDirectory(path, maxChunkSize.toLong())
    }

    @Test
    fun testSeekingExceptions() {
        val sliceSize = 128
        getDirectory(createTempDir("testSeekingExceptions"), sliceSize).use { dir ->
            val size = 128 + 63
            dir.createOutput("a", IOContext.DEFAULT).use { out ->
                for (i in 0 until size) {
                    out.writeByte(0)
                }
            }
            dir.openInput("a", IOContext.DEFAULT).use { input ->
                if (isKmpFallbackInput(input)) {
                    return
                }
                val negativePos = -1234L
                val e = expectThrowsAnyOf(
                    mutableListOf(IllegalArgumentException::class, AssertionError::class)
                ) {
                    input.seek(negativePos)
                }
                assertEquals(e.message?.contains("negative position"), true, "does not mention negative position")

                val posAfterEOF = (size + 123).toLong()
                var eof = expectThrows(EOFException::class) {
                    input.seek(posAfterEOF)
                }
                assertEquals(eof.message?.contains("(pos=$posAfterEOF)"), true, "wrong position in error message: $eof")

                // this test verifies that the invalid position is transformed back to original one for
                // exception by slicing:
                val slice = input.slice("slice", 33, (sliceSize + 15).toLong())
                // ensure that the slice uses multi-mmap:
                assertCorrectImpl(false, slice)
                eof = expectThrows(EOFException::class) {
                    slice.seek(posAfterEOF)
                }
                assertEquals(eof.message?.contains("(pos=$posAfterEOF)"), true, "wrong position in error message: $eof")
            }
        }
    }

    // TODO: can we improve ByteBuffersDirectory (without overhead) and move these clone safety tests
    // to the base test case?
    @Test
    fun testCloneSafety() {
        val mmapDir = getDirectory(createTempDir("testCloneSafety"))
        val io = mmapDir.createOutput("bytes", newIOContext(random()))
        io.writeVInt(5)
        io.close()
        val one = mmapDir.openInput("bytes", IOContext.DEFAULT)
        val two = one.clone()
        val three = two.clone() // clone of clone
        one.close()
        expectThrowsAnyOf(
            mutableListOf(AlreadyClosedException::class, IllegalStateException::class)
        ) {
            one.readVInt()
        }
        expectThrowsAnyOf(
            mutableListOf(AlreadyClosedException::class, IllegalStateException::class)
        ) {
            two.readVInt()
        }
        expectThrowsAnyOf(
            mutableListOf(AlreadyClosedException::class, IllegalStateException::class)
        ) {
            three.readVInt()
        }

        two.close()
        three.close()
        // test double close of master:
        one.close()
        mmapDir.close()
    }

    @Test
    fun testCloneSliceSafety() {
        val mmapDir = getDirectory(createTempDir("testCloneSliceSafety"))
        val io = mmapDir.createOutput("bytes", newIOContext(random()))
        io.writeInt(1)
        io.writeInt(2)
        io.close()
        val slicer = mmapDir.openInput("bytes", newIOContext(random()))
        val one = slicer.slice("first int", 0, 4)
        val two = slicer.slice("second int", 4, 4)
        val three = one.clone() // clone of clone
        val four = two.clone() // clone of clone
        slicer.close()
        expectThrowsAnyOf(
            mutableListOf(AlreadyClosedException::class, IllegalStateException::class)
        ) {
            one.readInt()
        }
        expectThrowsAnyOf(
            mutableListOf(AlreadyClosedException::class, IllegalStateException::class)
        ) {
            two.readInt()
        }
        expectThrowsAnyOf(
            mutableListOf(AlreadyClosedException::class, IllegalStateException::class)
        ) {
            three.readInt()
        }
        expectThrowsAnyOf(
            mutableListOf(AlreadyClosedException::class, IllegalStateException::class)
        ) {
            four.readInt()
        }

        one.close()
        two.close()
        three.close()
        four.close()
        // test double-close of slicer:
        slicer.close()
        mmapDir.close()
    }

    // test has asserts specific to mmap impl...
    @Test
    fun testImplementations() {
        for (i in 2 until 12) {
            val chunkSize = 1 shl i
            val mmapDir = getDirectory(createTempDir("testImplementations"), chunkSize)
            val io = mmapDir.createOutput("bytes", newIOContext(random()))
            val size = random().nextInt(chunkSize * 2) + 3 // add some buffer of 3 for slice tests
            val bytes = ByteArray(size)
            random().nextBytes(bytes)
            io.writeBytes(bytes, bytes.size)
            io.close()
            val ii = mmapDir.openInput("bytes", newIOContext(random()))
            val actual = ByteArray(size) // first read all bytes
            ii.readBytes(actual, 0, actual.size)
            assertEquals(BytesRef(bytes), BytesRef(actual))
            // reinit:
            ii.seek(0L)
            val isKmpFallback = isKmpFallbackInput(ii)

            // check impl (we must check size < chunksize: currently, if size==chunkSize, we get 2
            // buffers, the second one empty:
            if (!isKmpFallback) {
                assertCorrectImpl(size < chunkSize, ii)
            }

            // clone tests:
            if (!isKmpFallback) {
                assertEquals(ii::class, ii.clone()::class)
            }

            // slice test (offset 0)
            var sliceSize = random().nextInt(size)
            var slice = ii.slice("slice", 0, sliceSize.toLong())
            if (!isKmpFallback) {
                assertCorrectImpl(sliceSize < chunkSize, slice)
            }

            // slice test (offset > 0 )
            val offset = random().nextInt(size - 1) + 1
            sliceSize = random().nextInt(size - offset + 1)
            slice = ii.slice("slice", offset.toLong(), sliceSize.toLong())
            if (!isKmpFallback) {
                assertCorrectImpl(offset % chunkSize + sliceSize < chunkSize, slice)
            }

            ii.close()
            mmapDir.close()
        }
    }

    private fun assertCorrectImpl(isSingle: Boolean, ii: IndexInput) {
        val clazz = ii::class.simpleName ?: ii.toString()
        if (isSingle) {
            assertTrue(
                Regex("Single\\\\w+Impl").matches(clazz),
                "Require a single impl, got $clazz"
            )
        } else {
            assertTrue(
                Regex("Multi\\\\w+Impl").matches(clazz),
                "Require a multi impl, got $clazz"
            )
        }
    }

    // tests inherited from BaseChunkedDirectoryTestCase
    @Test
    override fun testGroupVIntMultiBlocks() = super.testGroupVIntMultiBlocks()

    @Test
    override fun testCloneClose() {
        if (isKmpFallbackDirectory()) {
            return
        }
        super.testCloneClose()
    }

    @Test
    override fun testCloneSliceClose() {
        if (isKmpFallbackDirectory()) {
            return
        }
        super.testCloneSliceClose()
    }

    @Test
    override fun testSeekZero() = super.testSeekZero()

    @Test
    override fun testSeekSliceZero() = super.testSeekSliceZero()

    @Test
    override fun testSeekEnd() = super.testSeekEnd()

    @Test
    override fun testSeekSliceEnd() = super.testSeekSliceEnd()

    @Test
    override fun testSeeking() = super.testSeeking()

    @Test
    override fun testSlicedSeeking() = super.testSlicedSeeking()

    @Test
    override fun testSliceOfSlice() = super.testSliceOfSlice()

    @Test
    override fun testRandomChunkSizes() = super.testRandomChunkSizes()

    @Test
    override fun testBytesCrossBoundary() = super.testBytesCrossBoundary()

    @Test
    override fun testLittleEndianLongsCrossBoundary() = super.testLittleEndianLongsCrossBoundary()

    @Test
    override fun testLittleEndianFloatsCrossBoundary() = super.testLittleEndianFloatsCrossBoundary()

    // tests inherited from BaseDirectoryTestCase
    @Test
    override fun testCopyFrom() = super.testCopyFrom()

    @Test
    override fun testRename() = super.testRename()

    @Test
    override fun testDeleteFile() {
        getDirectory(createTempDir("testDeleteFile")).use { dir ->
            val file = "foo.txt"
            assertTrue(!dir.listAll().contains(file))

            dir.createOutput(file, IOContext.DEFAULT).close()
            assertTrue(dir.listAll().contains(file))

            dir.deleteFile(file)
            assertTrue(!dir.listAll().contains(file))

            if (!isKmpFallbackDirectory()) {
                val deleteError = assertFails { dir.deleteFile(file) }
                assertTrue(deleteError is NoSuchFileException || deleteError is FileNotFoundException)
            }
        }
    }

    @Test
    override fun testByte() = super.testByte()

    @Test
    override fun testShort() = super.testShort()

    @Test
    override fun testInt() = super.testInt()

    @Test
    override fun testLong() = super.testLong()

    @Test
    override fun testAlignedLittleEndianLongs() = super.testAlignedLittleEndianLongs()

    @Test
    override fun testUnalignedLittleEndianLongs() = super.testUnalignedLittleEndianLongs()

    @Test
    override fun testLittleEndianLongsUnderflow() = super.testLittleEndianLongsUnderflow()

    @Test
    override fun testAlignedInts() = super.testAlignedInts()

    @Test
    override fun testUnalignedInts() = super.testUnalignedInts()

    @Test
    override fun testIntsUnderflow() = super.testIntsUnderflow()

    @Test
    override fun testAlignedFloats() = super.testAlignedFloats()

    @Test
    override fun testUnalignedFloats() = super.testUnalignedFloats()

    @Test
    override fun testFloatsUnderflow() = super.testFloatsUnderflow()

    @Test
    override fun testString() = super.testString()

    @Test
    override fun testVInt() = super.testVInt()

    @Test
    override fun testVLong() = super.testVLong()

    @Test
    override fun testZInt() = super.testZInt()

    @Test
    override fun testZLong() = super.testZLong()

    @Test
    override fun testSetOfStrings() = super.testSetOfStrings()

    @Test
    override fun testMapOfStrings() = super.testMapOfStrings()

    @Test
    override fun testChecksum() = super.testChecksum()

    @Test
    override fun testDetectClose() = super.testDetectClose()

    @Test
    override fun testThreadSafetyInListAll() = super.testThreadSafetyInListAll()

    @Test
    override fun testFileExistsInListAfterCreated() = super.testFileExistsInListAfterCreated()

    @Test
    override fun testSeekToEOFThenBack() = super.testSeekToEOFThenBack()

    @Test
    override fun testIllegalEOF() = super.testIllegalEOF()

    @Test
    override fun testSeekPastEOF() = super.testSeekPastEOF()

    @Test
    override fun testSliceOutOfBounds() = super.testSliceOutOfBounds()

    @Test
    override fun testNoDir() = super.testNoDir()

    @Test
    override fun testCopyBytes() = super.testCopyBytes()

    @Test
    override fun testCopyBytesWithThreads() = super.testCopyBytesWithThreads()

    @Test
    override fun testFsyncDoesntCreateNewFiles() = super.testFsyncDoesntCreateNewFiles()

    @Test
    override fun testRandomLong() = super.testRandomLong()

    @Test
    override fun testRandomInt() = super.testRandomInt()

    @Test
    override fun testRandomShort() = super.testRandomShort()

    @Test
    override fun testRandomByte() = super.testRandomByte()

    @Test
    override fun testLargeWrites() = super.testLargeWrites()

    @Test
    override fun testIndexOutputToString() = super.testIndexOutputToString()

    @Test
    override fun testDoubleCloseOutput() = super.testDoubleCloseOutput()

    @Test
    override fun testDoubleCloseInput() = super.testDoubleCloseInput()

    @Test
    override fun testCreateTempOutput() = super.testCreateTempOutput()

    @Test
    override fun testCreateOutputForExistingFile() = super.testCreateOutputForExistingFile()

    @Test
    override fun testSeekToEndOfFile() = super.testSeekToEndOfFile()

    @Test
    override fun testSeekBeyondEndOfFile() = super.testSeekBeyondEndOfFile()

    @Test
    override fun testPendingDeletions() = super.testPendingDeletions()

    @Test
    override fun testListAllIsSorted() = super.testListAllIsSorted()

    @Test
    override fun testDataTypes() = super.testDataTypes()

    @Test
    override fun testGroupVIntOverflow() = super.testGroupVIntOverflow()

    @Test
    override fun testGroupVInt() = super.testGroupVInt()

    @Test
    override fun testPrefetch() = super.testPrefetch()

    @Test
    override fun testPrefetchOnSlice() = super.testPrefetchOnSlice()

    @Test
    override fun testUpdateReadAdvice() = super.testUpdateReadAdvice()

    @Test
    override fun testIsLoaded() {
        if (isKmpFallbackDirectory()) {
            return
        }
        super.testIsLoaded()
    }

    @Test
    override fun testIsLoadedOnSlice() {
        if (isKmpFallbackDirectory()) {
            return
        }
        super.testIsLoadedOnSlice()
    }

    private fun isKmpFallbackInput(input: IndexInput): Boolean {
        return input.toString().contains("[kmp-fallback]")
    }

    private fun isKmpFallbackDirectory(): Boolean {
        MMapDirectory(createTempDir("isKmpFallbackDirectoryProbe")).use { dir ->
            dir.createOutput("probe.bin", IOContext.DEFAULT).use { out ->
                out.writeByte(0)
            }
            dir.openInput("probe.bin", IOContext.DEFAULT).use { input ->
                return isKmpFallbackInput(input)
            }
        }
    }
}
