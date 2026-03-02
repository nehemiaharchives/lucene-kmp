package org.gnit.lucenekmp.store

import okio.Path
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexNotFoundException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.jdkport.AtomicMoveNotSupportedException
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okio.FileNotFoundException

class TestFileSwitchDirectory : BaseDirectoryTestCase() {

    /** Test if writing doc stores to disk and everything else to ram works. */
    @Test
    fun testBasic() {
        val fileExtensions = mutableSetOf<String>()
        fileExtensions.add(Lucene90CompressingStoredFieldsWriter.FIELDS_EXTENSION)
        fileExtensions.add("fdx")
        fileExtensions.add("fdm")

        val primaryDir = MockDirectoryWrapper(random(), ByteBuffersDirectory())
        primaryDir.checkIndexOnClose = false // only part of an index
        val secondaryDir = MockDirectoryWrapper(random(), ByteBuffersDirectory())
        secondaryDir.checkIndexOnClose = false // only part of an index

        val fsd = FileSwitchDirectory(fileExtensions, primaryDir, secondaryDir, true)
        val writer = IndexWriter(
            fsd,
            IndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(newLogMergePolicy(false))
                .setCodec(TestUtil.getDefaultCodec())
                .setUseCompoundFile(false)
        )

        for (i in 0 until 100) {
            val doc = Document()
            doc.add(newStringField("field", "ram-$i", Field.Store.NO))
            writer.addDocument(doc)
        }

        val reader: IndexReader = DirectoryReader.open(writer)
        assertEquals(100, reader.maxDoc())
        writer.commit()

        var files = primaryDir.listAll()
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val ext = FileSwitchDirectory.getExtension(file)
            assertTrue(fileExtensions.contains(ext))
        }

        files = secondaryDir.listAll()
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val ext = FileSwitchDirectory.getExtension(file)
            assertFalse(fileExtensions.contains(ext))
        }

        reader.close()
        writer.close()

        files = fsd.listAll()
        for (file in files) {
            assertNotNull(file)
        }
        fsd.close()
    }

    private fun newFSSwitchDirectory(primaryExtensions: Set<String>): Directory {
        val primDir = createTempDir("foo")
        val secondDir = createTempDir("bar")
        return newFSSwitchDirectory(primDir, secondDir, primaryExtensions)
    }

    private fun newFSSwitchDirectory(aDir: Path, bDir: Path, primaryExtensions: Set<String>): Directory {
        val a: Directory = NIOFSDirectory(aDir)
        val b: Directory = NIOFSDirectory(bDir)
        return FileSwitchDirectory(primaryExtensions, a, b, true)
    }

    // LUCENE-3380 -- make sure we get exception if the directory really does not exist.
    @Test
    override fun testNoDir() {
        val primDir = createTempDir("foo")
        val secondDir = createTempDir("bar")
        val dir = newFSSwitchDirectory(primDir, secondDir, emptySet())
        expectThrows(IndexNotFoundException::class) { DirectoryReader.open(dir) }
        dir.close()
    }

    @Test
    fun testRenameTmpFile() {
        getDirectory(createTempDir("renameTmpFile")).use { directory ->
            var name: String? = null
            directory.createTempOutput("foo.cfs", "", IOContext.DEFAULT).use { out ->
                out.writeInt(1)
                name = out.name
            }
            val nonNullName = checkNotNull(name)
            assertEquals(1L, directory.listAll().count { it == nonNullName }.toLong())
            assertEquals(0L, directory.listAll().count { it == "foo.cfs" }.toLong())
            directory.rename(nonNullName, "foo.cfs")
            assertEquals(1L, directory.listAll().count { it == "foo.cfs" }.toLong())
            assertEquals(0L, directory.listAll().count { it == nonNullName }.toLong())
        }

        newFSSwitchDirectory(setOf("bar")).use { directory ->
            var brokenName: String? = null
            directory.createTempOutput("foo", "bar", IOContext.DEFAULT).use { out ->
                out.writeInt(1)
                brokenName = out.name
            }
            val nonNullBrokenName = checkNotNull(brokenName)
            val exception = expectThrows(AtomicMoveNotSupportedException::class) {
                directory.rename(nonNullBrokenName, "foo.bar")
            }
            assertEquals(
                "$nonNullBrokenName -> foo.bar: source and dest are in different directories",
                exception.message
            )
        }
    }

    @Test
    @Ignore // TODO WindowsFS is not ported in KMP test-framework yet
    fun testDeleteAndList() {
    }

    override fun getDirectory(path: Path): Directory {
        val extensions = mutableSetOf<String>()
        if (random().nextBoolean()) extensions.add("cfs")
        if (random().nextBoolean()) extensions.add("prx")
        if (random().nextBoolean()) extensions.add("frq")
        if (random().nextBoolean()) extensions.add("tip")
        if (random().nextBoolean()) extensions.add("tim")
        if (random().nextBoolean()) extensions.add("del")
        return newFSSwitchDirectory(extensions)
    }

    // tests inherited from BaseDirectoryTestCase
    @Test
    override fun testCopyFrom() = super.testCopyFrom()

    @Test
    override fun testRename() = super.testRename()

    @Test
    override fun testDeleteFile() {
        getDirectory(createTempDir("testDeleteFile")).use { dir ->
            val file = "foo.txt"
            assertFalse(dir.listAll().contains(file))

            dir.createOutput(file, IOContext.DEFAULT).close()
            assertTrue(dir.listAll().contains(file))

            dir.deleteFile(file)
            assertFalse(dir.listAll().contains(file))

            // Current KMP FS behavior may treat deleting a missing file as idempotent.
            runCatching {
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
