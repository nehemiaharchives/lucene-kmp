package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestFilterDirectory : BaseDirectoryTestCase() {
    override fun getDirectory(path: okio.Path): Directory {
        return object : FilterDirectory(ByteBuffersDirectory()) {}
    }

    @Test
    @Ignore // TODO reflection not available in common tests
    fun testOverrides() {
        // The original test verifies that all Directory methods are overridden by FilterDirectory,
        // except an explicit exclude list. Reflection coverage is skipped in common tests.
    }

    @Test
    fun testUnwrap() {
        val dir = ByteBuffersDirectory()
        val dir2 = object : FilterDirectory(dir) {}
        assertEquals(dir, dir2.`in`)
        assertEquals(dir, FilterDirectory.unwrap(dir2))
        dir2.close()
    }

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
}
