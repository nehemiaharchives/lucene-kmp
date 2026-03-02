package org.gnit.lucenekmp.store

import okio.Path
import org.gnit.lucenekmp.tests.store.BaseChunkedDirectoryTestCase
import org.gnit.lucenekmp.util.BitUtil
import kotlin.test.Test

/** Tests ByteBuffersDirectory's chunking */
class TestMultiByteBuffersDirectory : BaseChunkedDirectoryTestCase() {
    @Throws(Exception::class)
    override fun getDirectory(path: Path, maxChunkSize: Int): Directory {
        // round down huge values (above 20) to keep RAM usage low in tests (especially in nightly)
        val bitsPerBlock = minOf(
            20,
            maxOf(
                ByteBuffersDataOutput.LIMIT_MIN_BITS_PER_BLOCK,
                BitUtil.nextHighestPowerOfTwo(maxChunkSize).countTrailingZeroBits()
            )
        )
        val outputSupplier = {
            ByteBuffersDataOutput(
                bitsPerBlock,
                bitsPerBlock,
                ByteBuffersDataOutput.ALLOCATE_BB_ON_HEAP,
                ByteBuffersDataOutput.NO_REUSE
            )
        }
        return ByteBuffersDirectory(
            SingleInstanceLockFactory(),
            outputSupplier,
            ByteBuffersDirectory.OUTPUT_AS_MANY_BUFFERS
        )
    }

    // tests inherited from BaseChunkedDirectoryTestCase
    @Test
    override fun testGroupVIntMultiBlocks() = super.testGroupVIntMultiBlocks()

    @Test
    override fun testCloneClose() = super.testCloneClose()

    @Test
    override fun testCloneSliceClose() = super.testCloneSliceClose()

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
}
