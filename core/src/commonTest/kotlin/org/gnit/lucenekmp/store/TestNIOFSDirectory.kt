package org.gnit.lucenekmp.store

import okio.fakefilesystem.FakeFileSystem
import okio.FileHandle
import okio.Path
import okio.IOException
import okio.ForwardingFileSystem
import org.gnit.lucenekmp.jdkport.toRealPath
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestNIOFSDirectory : BaseDirectoryTestCase() {
    override fun getDirectory(path: Path): Directory {
        return NIOFSDirectory(path)
    }

    @Test
    override fun testCopyFrom() = super.testCopyFrom()

    @Test
    override fun testRename() = super.testRename()

    @Test
    override fun testDeleteFile() = super.testDeleteFile()

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
    override fun testSliceOfSlice() = super.testSliceOfSlice()

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
    override fun testIsLoaded() = super.testIsLoaded()

    @Test
    override fun testIsLoadedOnSlice() = super.testIsLoadedOnSlice()

    @Test
    fun testHandleExceptionInConstructor() {
        // Create a filesystem that throws when size() is called on a FileHandle
        val leakFs = object : ForwardingFileSystem(FakeFileSystem()) {
            override fun openReadOnly(file: Path): FileHandle {
                val delegate = super.openReadOnly(file)
                return object : FileHandle(delegate.readWrite) {
                    override fun protectedRead(
                        fileOffset: Long,
                        array: ByteArray,
                        arrayOffset: Int,
                        byteCount: Int,
                    ): Int {
                        return delegate.read(fileOffset, array, arrayOffset, byteCount)
                    }

                    override fun protectedWrite(
                        fileOffset: Long,
                        array: ByteArray,
                        arrayOffset: Int,
                        byteCount: Int,
                    ) {
                        delegate.write(fileOffset, array, arrayOffset, byteCount)
                    }

                    override fun protectedFlush() {
                        delegate.flush()
                    }

                    override fun protectedResize(size: Long) {
                        delegate.resize(size)
                    }

                    override fun protectedSize(): Long {
                        throw IOException("simulated")
                    }

                    override fun protectedClose() {
                        delegate.close()
                    }
                }
            }
        }

        Files.setFileSystem(leakFs)
        try {
            val path = createTempDir("niofs").toRealPath()
            NIOFSDirectory(path).use { dir ->
                dir.createOutput("test.bin", IOContext.DEFAULT).use { out ->
                    out.writeString("hello")
                }
                val error = assertFailsWith<IOException> {
                    dir.openInput("test.bin", IOContext.DEFAULT)
                }
                assertEquals("simulated", error.message)
            }
        } finally {
            Files.resetFileSystem()
        }
    }
}

