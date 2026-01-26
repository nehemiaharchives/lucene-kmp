package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.CompoundDirectory
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.SegmentInfos
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.NRTCachingDirectory
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Abstract class to do basic tests for a compound format. NOTE: This test focuses on the compound
 * impl, nothing else. The [stretch] goal is for this test to be so thorough in testing a new
 * CompoundFormat that if this test passes, then all Lucene tests should also pass. Ie, if there is
 * some bug in a given CompoundFormat that this test fails to catch then this test needs to be
 * improved!
 */
abstract class BaseCompoundFormatTestCase :
    BaseIndexFileFormatTestCase() {
    // test that empty CFS is empty
    @Throws(IOException::class)
    fun testEmpty() {
        val dir: Directory =
            newDirectory()

        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        si.setFiles(mutableSetOf<String>())
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory =
            si.codec.compoundFormat().getCompoundReader(dir, si)
        assertEquals(0, cfs.listAll().size.toLong())
        cfs.close()
        dir.close()
    }

    /**
     * This test creates compound file based on a single file. Files of different sizes are tested: 0,
     * 1, 10, 100 bytes.
     */
    @Throws(IOException::class)
    fun testSingleFile() {
        val data = intArrayOf(0, 1, 10, 100)
        for (i in data.indices) {
            val testfile = "_$i.test"
            val dir: Directory =
                newDirectory()
            val si: SegmentInfo = newSegmentInfo(dir, "_" + i)
            createSequenceFile(dir, testfile, 0.toByte(), data[i], si.getId(), "suffix")

            si.setFiles(mutableSetOf<String>(testfile))
            si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
            val cfs: Directory =
                si.codec.compoundFormat().getCompoundReader(dir, si)

            val expected: IndexInput = dir.openInput(
                testfile,
                newIOContext(random())
            )
            val actual: IndexInput = cfs.openInput(
                testfile,
                newIOContext(random())
            )
            assertSameStreams(testfile, expected, actual)
            assertSameSeekBehavior(testfile, expected, actual)
            expected.close()
            actual.close()
            cfs.close()
            dir.close()
        }
    }

    /** This test creates compound file based on two files.  */
    @Throws(IOException::class)
    fun testTwoFiles() {
        val files = arrayOf<String>("_123.d1", "_123.d2")
        val dir: Directory =
            newDirectory()
        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        createSequenceFile(dir, files[0], 0.toByte(), 15, si.getId(), "suffix")
        createSequenceFile(dir, files[1], 0.toByte(), 114, si.getId(), "suffix")

        si.setFiles(files.toMutableList())
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory =
            si.codec.compoundFormat().getCompoundReader(dir, si)

        for (file in files) {
            val expected: IndexInput = dir.openInput(
                file,
                newIOContext(random())
            )
            val actual: IndexInput = cfs.openInput(
                file,
                newIOContext(random())
            )
            assertSameStreams(file, expected, actual)
            assertSameSeekBehavior(file, expected, actual)
            expected.close()
            actual.close()
        }

        cfs.close()
        dir.close()
    }

    // test that a second call to close() behaves according to Closeable
    @Throws(IOException::class)
    fun testDoubleClose() {
        val testfile = "_123.test"

        val dir: Directory =
            newDirectory()
        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        dir.createOutput(testfile, IOContext.DEFAULT).use { out ->
            CodecUtil.writeIndexHeader(out, "Foo", 0, si.getId(), "suffix")
            out.writeInt(3)
            CodecUtil.writeFooter(out)
        }
        si.setFiles(mutableSetOf<String>(testfile))
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory =
            si.codec.compoundFormat().getCompoundReader(dir, si)
        assertEquals(1, cfs.listAll().size.toLong())
        cfs.close()
        cfs.close() // second close should not throw exception
        dir.close()
    }

    // LUCENE-5724: things like NRTCachingDir rely upon IOContext being properly passed down
    @Throws(IOException::class)
    fun testPassIOContext() {
        val testfile = "_123.test"
        val myContext: IOContext = IOContext.DEFAULT

        val dir: Directory =
            object :
                FilterDirectory(newDirectory()) {
                @Throws(IOException::class)
                override fun createOutput(
                    name: String,
                    context: IOContext
                ): IndexOutput {
                    assertSame(myContext, context)
                    return super.createOutput(name, context)
                }
            }
        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        dir.createOutput(testfile, myContext).use { out ->
            CodecUtil.writeIndexHeader(out, "Foo", 0, si.getId(), "suffix")
            out.writeInt(3)
            CodecUtil.writeFooter(out)
        }
        si.setFiles(mutableSetOf<String>(testfile))
        si.codec.compoundFormat().write(dir, si, myContext)
        dir.close()
    }

    // LUCENE-5724: actually test we play nice with NRTCachingDir and massive file
    @Throws(IOException::class)
    fun testLargeCFS() {
        val testfile = "_123.test"
        val context: IOContext = IOContext(
            FlushInfo(
                0,
                (512 * 1024 * 1024).toLong()
            )
        )

        val dir: Directory = NRTCachingDirectory(
            LuceneTestCase.newFSDirectory(createTempDir()),
            2.0,
            25.0
        )

        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        dir.createOutput(testfile, context).use { out ->
            CodecUtil.writeIndexHeader(out, "Foo", 0, si.getId(), "suffix")
            val bytes = ByteArray(512)
            for (i in 0..<1024 * 1024) {
                out.writeBytes(bytes, 0, bytes.size)
            }
            CodecUtil.writeFooter(out)
        }
        si.setFiles(mutableSetOf<String>(testfile))
        si.codec.compoundFormat().write(dir, si, context)

        dir.close()
    }

    // Just tests that we can open all files returned by listAll
    @Throws(Exception::class)
    fun testListAll() {
        val dir: Directory =
            newDirectory()
        // riw should sometimes create docvalues fields, etc
        val riw: RandomIndexWriter =
            RandomIndexWriter(
                random(),
                dir
            )
        val doc: Document = Document()
        // these fields should sometimes get term vectors, etc
        val idField: Field =
            newStringField(
                "id",
                "",
                Field.Store.NO
            )
        val bodyField: Field =
            LuceneTestCase.newTextField(
                "body",
                "",
                Field.Store.NO
            )
        doc.add(idField)
        doc.add(bodyField)
        for (i in 0..99) {
            idField.setStringValue(i.toString())
            bodyField.setStringValue(TestUtil.randomUnicodeString(random()))
            riw.addDocument<IndexableField>(doc)
            if (random().nextInt(7) == 0) {
                riw.commit()
            }
        }
        riw.close()
        val infos: SegmentInfos =
            SegmentInfos.readLatestCommit(dir)
        for (si in infos) {
            if (si.info.useCompoundFile) {
                si.info.codec.compoundFormat().getCompoundReader(dir, si.info).use { cfsDir ->
                    for (cfsFile in cfsDir.listAll()) {
                        cfsDir.openInput(cfsFile, IOContext.DEFAULT)
                            .use { cfsIn ->
                                checkNotNull(cfsIn)
                            }
                    }
                }
            }
        }
        dir.close()
    }

    // test that cfs reader is read-only
    @Throws(IOException::class)
    fun testCreateOutputDisabled() {
        val dir: Directory = newDirectory()
        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        si.setFiles(mutableListOf())
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory = si.codec.compoundFormat().getCompoundReader(dir, si)
        expectThrows(
            UnsupportedOperationException::class
        ) {
            cfs.createOutput("bogus", IOContext.DEFAULT)
        }
        cfs.close()
        dir.close()
    }

    // test that cfs reader is read-only
    @Throws(IOException::class)
    fun testDeleteFileDisabled() {
        val testfile = "_123.test"

        val dir: Directory = newDirectory()
        val out: IndexOutput = dir.createOutput(testfile, IOContext.DEFAULT)
        out.writeInt(3)
        out.close()

        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        si.setFiles(mutableListOf())
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory = si.codec.compoundFormat().getCompoundReader(dir, si)
        expectThrows(
            UnsupportedOperationException::class
        ) {
            cfs.deleteFile(testfile)
        }

        cfs.close()
        dir.close()
    }

    // test that cfs reader is read-only
    @Throws(IOException::class)
    fun testRenameFileDisabled() {
        val testfile = "_123.test"

        val dir: Directory = newDirectory()
        val out: IndexOutput = dir.createOutput(testfile, IOContext.DEFAULT)
        out.writeInt(3)
        out.close()

        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        si.setFiles(mutableListOf())
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory = si.codec.compoundFormat().getCompoundReader(dir, si)
        expectThrows(
            UnsupportedOperationException::class,
            LuceneTestCase.ThrowingRunnable {
                cfs.rename(testfile, "bogus")
            })

        cfs.close()
        dir.close()
    }

    // test that cfs reader is read-only
    @Throws(IOException::class)
    fun testSyncDisabled() {
        val testfile = "_123.test"

        val dir: Directory = newDirectory()
        val out: IndexOutput = dir.createOutput(testfile, IOContext.DEFAULT)
        out.writeInt(3)
        out.close()

        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        si.setFiles(mutableListOf())
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory = si.codec.compoundFormat().getCompoundReader(dir, si)
        expectThrows(
            UnsupportedOperationException::class
        ) {
            cfs.sync(mutableSetOf<String>(testfile))
        }

        cfs.close()
        dir.close()
    }

    // test that cfs reader is read-only
    @Throws(IOException::class)
    fun testMakeLockDisabled() {
        val testfile = "_123.test"

        val dir: Directory = newDirectory()
        val out: IndexOutput = dir.createOutput(testfile, IOContext.DEFAULT)
        out.writeInt(3)
        out.close()

        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        si.setFiles(mutableListOf())
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory = si.codec.compoundFormat().getCompoundReader(dir, si)
        expectThrows(
            UnsupportedOperationException::class
        ) {
            cfs.obtainLock("foobar")
        }

        cfs.close()
        dir.close()
    }

    /**
     * This test creates a compound file based on a large number of files of various length. The file
     * content is generated randomly. The sizes range from 0 to 1Mb. Some of the sizes are selected to
     * test the buffering logic in the file reading code. For this the chunk variable is set to the
     * length of the buffer used internally by the compound file logic.
     */
    @Throws(IOException::class)
    fun testRandomFiles() {
        val dir: Directory = newDirectory()
        // Setup the test segment
        val segment = "_123"
        val chunk = 1024 // internal buffer size used by the stream
        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        val segId: ByteArray = si.getId()
        createRandomFile(dir, "$segment.zero", 0, segId)
        createRandomFile(dir, "$segment.one", 1, segId)
        createRandomFile(dir, "$segment.ten", 10, segId)
        createRandomFile(dir, "$segment.hundred", 100, segId)
        createRandomFile(dir, "$segment.big1", chunk, segId)
        createRandomFile(dir, "$segment.big2", chunk - 1, segId)
        createRandomFile(dir, "$segment.big3", chunk + 1, segId)
        createRandomFile(dir, "$segment.big4", 3 * chunk, segId)
        createRandomFile(dir, "$segment.big5", 3 * chunk - 1, segId)
        createRandomFile(dir, "$segment.big6", 3 * chunk + 1, segId)
        createRandomFile(dir, "$segment.big7", 1000 * chunk, segId)

        val files: MutableList<String> = mutableListOf()
        for (file in dir.listAll()) {
            if (file.startsWith(segment)) {
                files.add(file)
            }
        }

        si.setFiles(files)
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory = si.codec.compoundFormat().getCompoundReader(dir, si)

        for (file in files) {
            val check: IndexInput = dir.openInput(
                file,
                newIOContext(random())
            )
            val test: IndexInput = cfs.openInput(
                file,
                newIOContext(random())
            )
            assertSameStreams(file, check, test)
            assertSameSeekBehavior(file, check, test)
            test.close()
            check.close()
        }
        cfs.close()
        dir.close()
    }

    // Make sure we don't somehow use more than 1 descriptor
    // when reading a CFS with many subs:
    @Throws(IOException::class)
    fun testManySubFiles() {
        val dir: MockDirectoryWrapper = newMockFSDirectory(createTempDir("CFSManySubFiles"))

        val FILE_COUNT: Int = atLeast(500)

        val files: MutableList<String> = mutableListOf()
        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        for (fileIdx in 0..<FILE_COUNT) {
            val file = "_123.$fileIdx"
            files.add(file)
            dir.createOutput(
                file,
                newIOContext(random())
            ).use { out ->
                CodecUtil.writeIndexHeader(
                    out,
                    "Foo",
                    0,
                    si.getId(),
                    "suffix"
                )
                out.writeByte(fileIdx.toByte())
                CodecUtil.writeFooter(out)
            }
        }

        assertEquals(0, dir.fileHandleCount)

        si.setFiles(files)
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory =
            si.codec.compoundFormat().getCompoundReader(dir, si)

        val ins: Array<IndexInput> =
            Array<IndexInput>(FILE_COUNT)
        /*for (fileIdx in 0..<FILE_COUNT)*/ { fileIdx ->
            /*ins[fileIdx]*/ val entry = cfs.openInput(
                "_123.$fileIdx",
                newIOContext(random())
            )
            CodecUtil.checkIndexHeader(
                /*ins[fileIdx]*/ entry,
                "Foo",
                0,
                0,
                si.getId(),
                "suffix"
            )
            entry
        }

        assertEquals(1, dir.fileHandleCount)

        for (fileIdx in 0..<FILE_COUNT) {
            assertEquals(
                fileIdx.toByte().toLong(),
                ins[fileIdx].readByte().toLong()
            )
        }

        assertEquals(1, dir.fileHandleCount)

        for (fileIdx in 0..<FILE_COUNT) {
            ins[fileIdx].close()
        }
        cfs.close()

        dir.close()
    }

    @Throws(IOException::class)
    fun testClonedStreamsClosing() {
        val dir: Directory = newDirectory()
        val cr: Directory = createLargeCFS(dir)

        // basic clone
        val expected: IndexInput = dir.openInput(
            "_123.f11",
            newIOContext(random())
        )

        val one: IndexInput = cr.openInput(
            "_123.f11",
            newIOContext(random())
        )

        val two: IndexInput = one.clone()

        assertSameStreams("basic clone one", expected, one)
        expected.seek(0)
        assertSameStreams("basic clone two", expected, two)

        // Now close the compound reader
        cr.close()
        expected.close()
        dir.close()
    }

    /**
     * This test opens two files from a compound stream and verifies that their file positions are
     * independent of each other.
     */
    @Throws(IOException::class)
    fun testRandomAccess() {
        val dir: Directory = newDirectory()
        val cr: Directory = createLargeCFS(dir)

        // Open two files
        val e1: IndexInput = dir.openInput(
            "_123.f11",
            newIOContext(random())
        )
        val e2: IndexInput = dir.openInput(
            "_123.f3",
            newIOContext(random())
        )

        val a1: IndexInput = cr.openInput(
            "_123.f11",
            newIOContext(random())
        )
        val a2: IndexInput = dir.openInput(
            "_123.f3",
            newIOContext(random())
        )

        // Seek the first pair
        e1.seek(100)
        a1.seek(100)
        assertEquals(100, e1.filePointer)
        assertEquals(100, a1.filePointer)
        var be1: Byte = e1.readByte()
        var ba1: Byte = a1.readByte()
        assertEquals(be1.toLong(), ba1.toLong())

        // Now seek the second pair
        e2.seek(1027)
        a2.seek(1027)
        assertEquals(1027, e2.filePointer)
        assertEquals(1027, a2.filePointer)
        var be2: Byte = e2.readByte()
        var ba2: Byte = a2.readByte()
        assertEquals(be2.toLong(), ba2.toLong())

        // Now make sure the first one didn't move
        assertEquals(101, e1.filePointer)
        assertEquals(101, a1.filePointer)
        be1 = e1.readByte()
        ba1 = a1.readByte()
        assertEquals(be1.toLong(), ba1.toLong())

        // Now more the first one again, past the buffer length
        e1.seek(1910)
        a1.seek(1910)
        assertEquals(1910, e1.filePointer)
        assertEquals(1910, a1.filePointer)
        be1 = e1.readByte()
        ba1 = a1.readByte()
        assertEquals(be1.toLong(), ba1.toLong())

        // Now make sure the second set didn't move
        assertEquals(1028, e2.filePointer)
        assertEquals(1028, a2.filePointer)
        be2 = e2.readByte()
        ba2 = a2.readByte()
        assertEquals(be2.toLong(), ba2.toLong())

        // Move the second set back, again cross the buffer size
        e2.seek(17)
        a2.seek(17)
        assertEquals(17, e2.filePointer)
        assertEquals(17, a2.filePointer)
        be2 = e2.readByte()
        ba2 = a2.readByte()
        assertEquals(be2.toLong(), ba2.toLong())

        // Finally, make sure the first set didn't move
        // Now make sure the first one didn't move
        assertEquals(1911, e1.filePointer)
        assertEquals(1911, a1.filePointer)
        be1 = e1.readByte()
        ba1 = a1.readByte()
        assertEquals(be1.toLong(), ba1.toLong())

        e1.close()
        e2.close()
        a1.close()
        a2.close()
        cr.close()
        dir.close()
    }

    /**
     * This test opens two files from a compound stream and verifies that their file positions are
     * independent of each other.
     */
    @Throws(IOException::class)
    fun testRandomAccessClones() {
        val dir: Directory = newDirectory()
        val cr: Directory = createLargeCFS(dir)

        // Open two files
        val e1: IndexInput = cr.openInput(
            "_123.f11",
            newIOContext(random())
        )
        val e2: IndexInput = cr.openInput(
            "_123.f3",
            newIOContext(random())
        )

        val a1: IndexInput = e1.clone()
        val a2: IndexInput = e2.clone()

        // Seek the first pair
        e1.seek(100)
        a1.seek(100)
        assertEquals(100, e1.filePointer)
        assertEquals(100, a1.filePointer)
        var be1: Byte = e1.readByte()
        var ba1: Byte = a1.readByte()
        assertEquals(be1.toLong(), ba1.toLong())

        // Now seek the second pair
        e2.seek(1027)
        a2.seek(1027)
        assertEquals(1027, e2.filePointer)
        assertEquals(1027, a2.filePointer)
        var be2: Byte = e2.readByte()
        var ba2: Byte = a2.readByte()
        assertEquals(be2.toLong(), ba2.toLong())

        // Now make sure the first one didn't move
        assertEquals(101, e1.filePointer)
        assertEquals(101, a1.filePointer)
        be1 = e1.readByte()
        ba1 = a1.readByte()
        assertEquals(be1.toLong(), ba1.toLong())

        // Now more the first one again, past the buffer length
        e1.seek(1910)
        a1.seek(1910)
        assertEquals(1910, e1.filePointer)
        assertEquals(1910, a1.filePointer)
        be1 = e1.readByte()
        ba1 = a1.readByte()
        assertEquals(be1.toLong(), ba1.toLong())

        // Now make sure the second set didn't move
        assertEquals(1028, e2.filePointer)
        assertEquals(1028, a2.filePointer)
        be2 = e2.readByte()
        ba2 = a2.readByte()
        assertEquals(be2.toLong(), ba2.toLong())

        // Move the second set back, again cross the buffer size
        e2.seek(17)
        a2.seek(17)
        assertEquals(17, e2.filePointer)
        assertEquals(17, a2.filePointer)
        be2 = e2.readByte()
        ba2 = a2.readByte()
        assertEquals(be2.toLong(), ba2.toLong())

        // Finally, make sure the first set didn't move
        // Now make sure the first one didn't move
        assertEquals(1911, e1.filePointer)
        assertEquals(1911, a1.filePointer)
        be1 = e1.readByte()
        ba1 = a1.readByte()
        assertEquals(be1.toLong(), ba1.toLong())

        e1.close()
        e2.close()
        a1.close()
        a2.close()
        cr.close()
        dir.close()
    }

    @Throws(IOException::class)
    fun testFileNotFound() {
        val dir: Directory = newDirectory()
        val cr: Directory = createLargeCFS(dir)

        // Open bogus file
        expectThrows(
            IOException::class
        ) {
            cr.openInput(
                "bogus",
                newIOContext(random())
            )
        }

        cr.close()
        dir.close()
    }

    @Throws(IOException::class)
    fun testReadPastEOF() {
        val dir: Directory = newDirectory()
        val cr: Directory = createLargeCFS(dir)
        val `is`: IndexInput = cr.openInput(
            "_123.f2",
            newIOContext(random())
        )
        `is`.seek(`is`.length() - 10)
        val b = ByteArray(100)
        `is`.readBytes(b, 0, 10)

        // Single byte read past end of file
        expectThrows(
            IOException::class
        ) {
            `is`.readByte()
        }

        `is`.seek(`is`.length() - 10)

        // Block read past end of file
        expectThrows(
            IOException::class
        ) {
            `is`.readBytes(b, 0, 50)
        }

        `is`.close()
        cr.close()
        dir.close()
    }

    override fun addRandomFields(doc: Document) {
        doc.add(
            StoredField(
                "foobar",
                TestUtil.randomSimpleString(random())
            )
        )
    }

    @Throws(Exception::class)
    override fun testMergeStability() {
        assumeTrue("test does not work with CFS", true)
    }

    // LUCENE-6311: make sure the resource name inside a compound file confesses that it's inside a
    // compound file
    @Throws(Exception::class)
    fun testResourceNameInsideCompoundFile() {
        val dir: Directory = newDirectory()
        val subFile = "_123.xyz"
        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        createSequenceFile(dir, subFile, 0.toByte(), 10, si.getId(), "suffix")

        si.setFiles(mutableListOf(subFile))
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
        val cfs: Directory = si.codec.compoundFormat().getCompoundReader(dir, si)
        val `in`: IndexInput = cfs.openInput(subFile, IOContext.DEFAULT)
        val desc = `in`.toString()
        assertTrue(desc.contains("[slice=$subFile]"), message = "resource description hides that it's inside a compound file: $desc")
        cfs.close()
        dir.close()
    }

    @Throws(Exception::class)
    fun testMissingCodecHeadersAreCaught() {
        val dir: Directory = newDirectory()
        val subFile = "_123.xyz"

        dir.createOutput(
            subFile,
            newIOContext(random())
        ).use { os ->
            for (i in 0..1023) {
                os.writeByte(i.toByte())
            }
        }
        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        si.setFiles(mutableListOf(subFile))
        val e: Exception = expectThrows(CorruptIndexException::class) {
                si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
            }
        assertTrue(e.message!!.contains("codec header mismatch"))
        dir.close()
    }

    @Throws(Exception::class)
    fun testCorruptFilesAreCaught() {
        val dir: Directory = newDirectory()
        val subFile = "_123.xyz"

        // wrong checksum
        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        dir.createOutput(
            subFile,
            newIOContext(random())
        ).use { os ->
            CodecUtil.writeIndexHeader(os, "Foo", 0, si.getId(), "suffix")
            for (i in 0..1023) {
                os.writeByte(i.toByte())
            }

            // write footer w/ wrong checksum
            CodecUtil.writeBEInt(os, CodecUtil.FOOTER_MAGIC)
            CodecUtil.writeBEInt(os, 0)
            val checksum: Long = os.getChecksum()
            CodecUtil.writeBELong(os, checksum + 1)
        }
        si.setFiles(mutableListOf(subFile))
        val e: Exception =
            expectThrows(
                CorruptIndexException::class
            ) {
                si.codec.compoundFormat()
                    .write(dir, si, IOContext.DEFAULT)
            }
        assertTrue(e.message!!.contains("checksum failed (hardware problem)"))
        dir.close()
    }

    @Throws(IOException::class)
    fun testCheckIntegrity() {
        val dir: Directory = newDirectory()
        val subFile = "_123.xyz"
        val si: SegmentInfo = newSegmentInfo(dir, "_123")
        dir.createOutput(
            subFile,
            newIOContext(random())
        ).use { os ->
            CodecUtil.writeIndexHeader(os, "Foo", 0, si.getId(), "suffix")
            for (i in 0..1023) {
                os.writeByte(i.toByte())
            }
            CodecUtil.writeBEInt(
                os,
                CodecUtil.FOOTER_MAGIC
            )
            CodecUtil.writeBEInt(os, 0)
            val checksum: Long = os.getChecksum()
            CodecUtil.writeBELong(os, checksum)
        }
        si.setFiles(mutableListOf(subFile))

        val writeTrackingDir = FileTrackingDirectoryWrapper(dir)
        si.codec.compoundFormat().write(writeTrackingDir, si, IOContext.DEFAULT)
        val createdFiles: MutableSet<String> = writeTrackingDir.getFiles()

        val readTrackingDir = ReadBytesDirectoryWrapper(dir)
        val compoundDir: CompoundDirectory = si.codec.compoundFormat().getCompoundReader(readTrackingDir, si)
        compoundDir.checkIntegrity()
        val readBytes: MutableMap<String, FixedBitSet> = readTrackingDir.getReadBytes()
        assertEquals(createdFiles, readBytes.keys)
        for (entry in readBytes.entries) {
            val file = entry.key
            val set: FixedBitSet = entry.value.clone()
            set.flip(0, set.length())
            val next: Int = set.nextSetBit(0)
            assertEquals(
                "Byte at offset $next of $file was not read",
                DocIdSetIterator.NO_MORE_DOCS.toLong(),
                next.toLong()
            )
        }
        compoundDir.close()
        dir.close()
    }

    companion object {
        /** Returns a new fake segment  */
        protected fun newSegmentInfo(
            dir: Directory,
            name: String
        ): SegmentInfo {
            val minVersion: Version? = if (random().nextBoolean()) null else Version.LATEST
            return SegmentInfo(
                dir,
                Version.LATEST,
                minVersion,
                name,
                10000,
                false,
                false,
                Codec.default,
                mutableMapOf<String, String>(),
                StringHelper.randomId(),
                mutableMapOf<String, String>(),
                null
            )
        }

        /** Creates a file of the specified size with random data.  */
        @Throws(IOException::class)
        protected fun createRandomFile(
            dir: Directory,
            name: String,
            size: Int,
            segId: ByteArray
        ) {
            val rnd: Random = random()
            dir.createOutput(
                name,
                newIOContext(random())
            ).use { os ->
                CodecUtil.writeIndexHeader(os, "Foo", 0, segId, "suffix")
                for (i in 0..<size) {
                    val b = rnd.nextInt(256).toByte()
                    os.writeByte(b)
                }
                CodecUtil.writeFooter(os)
            }
        }

        /**
         * Creates a file of the specified size with sequential data. The first byte is written as the
         * start byte provided. All subsequent bytes are computed as start + offset where offset is the
         * number of the byte.
         */
        @Throws(IOException::class)
        protected fun createSequenceFile(
            dir: Directory,
            name: String,
            start: Byte,
            size: Int,
            segID: ByteArray,
            segSuffix: String
        ) {
            var start = start
            dir.createOutput(
                name,
                newIOContext(random())
            ).use { os ->
                CodecUtil.writeIndexHeader(os, "Foo", 0, segID, segSuffix)
                for (i in 0..<size) {
                    os.writeByte(start)
                    start++
                }
                CodecUtil.writeFooter(os)
            }
        }

        @Throws(IOException::class)
        protected fun assertSameStreams(
            msg: String,
            expected: IndexInput,
            test: IndexInput
        ) {
            assertNotNull( expected, message = "$msg null expected")
            assertNotNull(test, message = "$msg null test")
            assertEquals( expected.length(), test.length(), message = "$msg length")
            assertEquals(
                expected.filePointer,
                test.filePointer,
                message = "$msg position"
            )

            val expectedBuffer = ByteArray(512)
            val testBuffer = ByteArray(expectedBuffer.size)

            var remainder: Long = expected.length() - expected.filePointer
            while (remainder > 0) {
                val readLen = min(remainder, expectedBuffer.size.toLong()).toInt()
                expected.readBytes(expectedBuffer, 0, readLen)
                test.readBytes(testBuffer, 0, readLen)
                assertEqualArrays(
                    "$msg, remainder $remainder",
                    expectedBuffer,
                    testBuffer,
                    0,
                    readLen
                )
                remainder -= readLen.toLong()
            }
        }

        @Throws(IOException::class)
        protected fun assertSameStreams(
            msg: String,
            expected: IndexInput,
            actual: IndexInput,
            seekTo: Long
        ) {
            if (seekTo >= 0 && seekTo < expected.length()) {
                expected.seek(seekTo)
                actual.seek(seekTo)
                assertSameStreams("$msg, seek(mid)", expected, actual)
            }
        }

        @Throws(IOException::class)
        protected fun assertSameSeekBehavior(
            msg: String,
            expected: IndexInput,
            actual: IndexInput
        ) {
            // seek to 0
            var point: Long = 0
            assertSameStreams("$msg, seek(0)", expected, actual, point)

            // seek to middle
            point = expected.length() / 2L
            assertSameStreams("$msg, seek(mid)", expected, actual, point)

            // seek to end - 2
            point = expected.length() - 2
            assertSameStreams("$msg, seek(end-2)", expected, actual, point)

            // seek to end - 1
            point = expected.length() - 1
            assertSameStreams("$msg, seek(end-1)", expected, actual, point)

            // seek to the end
            point = expected.length()
            assertSameStreams("$msg, seek(end)", expected, actual, point)

            // seek past end
            point = expected.length() + 1
            assertSameStreams("$msg, seek(end+1)", expected, actual, point)
        }

        protected fun assertEqualArrays(
            msg: String, expected: ByteArray, test: ByteArray, start: Int, len: Int
        ) {
            assertNotNull( expected, "$msg null expected")
            assertNotNull( test, message = "$msg null test")

            for (i in start..<len) {
                assertEquals(
                    expected[i].toLong(),
                    test[i].toLong(),
                    message = "$msg $i"
                )
            }
        }

        /**
         * Setup a large compound file with a number of components, each of which is a sequential file (so
         * that we can easily tell that we are reading in the right byte). The methods sets up 20 files -
         * _123.0 to _123.19, the size of each file is 1000 bytes.
         */
        @Throws(IOException::class)
        protected fun createLargeCFS(dir: Directory): Directory {
            val files: MutableList<String> = mutableListOf()
            val si: SegmentInfo = newSegmentInfo(dir, "_123")
            for (i in 0..19) {
                createSequenceFile(dir, "_123.f$i", 0.toByte(), 2000, si.getId(), "suffix")
                files.add("_123.f$i")
            }

            si.setFiles(files)
            si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)
            val cfs: Directory = si.codec.compoundFormat().getCompoundReader(dir, si)
            return cfs
        }
    }
}
