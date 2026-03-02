package org.gnit.lucenekmp.store

import okio.Path
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestNRTCachingDirectory : BaseDirectoryTestCase() {
    // TODO: RAMDir used here, because it's still too slow to use e.g. SimpleFS
    // for the threads tests... maybe because of the synchronization in listAll?
    // would be good to investigate further...
    @Throws(Exception::class)
    override fun getDirectory(path: Path): Directory {
        return NRTCachingDirectory(
            ByteBuffersDirectory(),
            0.1 + 2.0 * random().nextDouble(),
            0.1 + 5.0 * random().nextDouble()
        )
    }

    @Test
    fun testNRTAndCommit() {
        val dir = newDirectory()
        val cachedDir = NRTCachingDirectory(dir, 2.0, 25.0)
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val conf = newIndexWriterConfig(analyzer)
        val w = RandomIndexWriter(random(), cachedDir, conf)
        val docs = LineFileDocs(random())
        val numDocs = TestUtil.nextInt(random(), 100, 400)

        if (VERBOSE) {
            println("TEST: numDocs=$numDocs")
        }

        val ids = mutableListOf<BytesRef>()
        var r: DirectoryReader? = null
        for (docCount in 0 until numDocs) {
            val doc: Document = docs.nextDoc()
            ids.add(BytesRef(requireNotNull(doc.get("docid"))))
            w.addDocument(doc)
            if (random().nextInt(20) == 17) {
                if (r == null) {
                    r = DirectoryReader.open(w.w)
                } else {
                    val r2 = DirectoryReader.openIfChanged(r)
                    if (r2 != null) {
                        r!!.close()
                        r = r2
                    }
                }
                assertEquals(1 + docCount, r!!.numDocs())
                val s = newSearcher(r)
                // Just make sure search can run; we can't assert
                // totHits since it could be 0
                s.search(TermQuery(Term("body", "the")), 10)
            }
        }

        r?.close()

        // Close should force cache to clear since all files are sync'd
        w.close()

        val cachedFiles = cachedDir.listCachedFiles()
        for (file in cachedFiles) {
            println("FAIL: cached file $file remains after sync")
        }
        assertEquals(0, cachedFiles.size)

        val r2 = DirectoryReader.open(dir)
        for (id in ids) {
            assertEquals(1, r2.docFreq(Term("docid", id)))
        }
        r2.close()
        cachedDir.close()
        docs.close()
    }

    // NOTE: not a test; just here to make sure the code frag
    // in the javadocs is correct!
    @Throws(Exception::class)
    fun verifyCompiles() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val fsDir: Directory = FSDirectory.open(createTempDir("verify"))
        val cachedFSDir = NRTCachingDirectory(fsDir, 2.0, 25.0)
        val conf = IndexWriterConfig(analyzer)
        val writer = IndexWriter(cachedFSDir, conf)
        writer.close()
        cachedFSDir.close()
    }

    @Test
    fun testCreateTempOutputSameName() {
        val fsDir: Directory = FSDirectory.open(createTempDir("verify"))
        val nrtDir = NRTCachingDirectory(fsDir, 2.0, 25.0)
        val name = "foo_bar_0.tmp"
        nrtDir.createOutput(name, IOContext.DEFAULT).close()

        val out = nrtDir.createTempOutput("foo", "bar", IOContext.DEFAULT)
        assertFalse(name == out.name)
        out.close()
        nrtDir.close()
        fsDir.close()
    }

    @Test
    fun testUnknownFileSize() {
        val dir = newDirectory()

        val nrtDir1 = object : NRTCachingDirectory(dir, 1.0, 1.0) {
            override fun doCacheWrite(name: String, context: IOContext): Boolean {
                val cache = super.doCacheWrite(name, context)
                assertTrue(cache)
                return cache
            }
        }
        var ioContext = IOContext(FlushInfo(3, 42))
        nrtDir1.createOutput("foo", ioContext).close()
        nrtDir1.createTempOutput("bar", "baz", ioContext).close()

        val nrtDir2 = object : NRTCachingDirectory(dir, 1.0, 1.0) {
            override fun doCacheWrite(name: String, context: IOContext): Boolean {
                val cache = super.doCacheWrite(name, context)
                assertFalse(cache)
                return cache
            }
        }
        ioContext = IOContext.DEFAULT
        nrtDir2.createOutput("foo", ioContext).close()
        nrtDir2.createTempOutput("bar", "baz", ioContext).close()

        dir.close()
    }

    @Test
    fun testCacheSizeAfterDelete() {
        val ioContext = IOContext(FlushInfo(3, 40))
        val fn = "f1"
        newDirectory().use { dir ->
            NRTCachingDirectory(dir, 1.0, 1.0).use { nrt ->
                // deletes a closed file
                nrt.createOutput(fn, ioContext).use { out ->
                    for (i in 0 until 10) out.writeInt(i)
                }
                assertEquals(40, nrt.ramBytesUsed())
                nrt.deleteFile(fn)
                assertEquals(0, nrt.ramBytesUsed())

                // Deletes an unclosed file (write before and after deletion
                nrt.createOutput(fn, ioContext).use { out ->
                    for (i in 0 until 10) out.writeInt(i)
                    nrt.deleteFile(fn)
                    for (i in 0 until 10) out.writeInt(i)
                }
                assertEquals(0, nrt.ramBytesUsed())
            }
        }
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
