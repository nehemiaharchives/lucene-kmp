package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.SortedNumericSortField
import org.gnit.lucenekmp.search.SortedSetSortField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper.Failure
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Abstract class to do basic tests for si format. NOTE: This test focuses on the si impl, nothing
 * else. The [stretch] goal is for this test to be so thorough in testing a new si format that if
 * this test passes, then all Lucene tests should also pass. Ie, if there is some bug in a given si
 * Format that this test fails to catch then this test needs to be improved!
 */
abstract class BaseSegmentInfoFormatTestCase : BaseIndexFileFormatTestCase() {
    /** Whether this format records min versions.  */
    protected fun supportsMinVersion(): Boolean {
        return true
    }

    /** Test files map  */
    @Throws(Exception::class)
    open fun testFiles() {
        val dir: Directory = newDirectory()
        val codec: Codec = codec
        val id: ByteArray = StringHelper.randomId()
        val info =
            SegmentInfo(
                dir,
                this.versions[0],
                this.versions[0],
                "_123",
                1,
                isCompoundFile = false,
                hasBlocks = false,
                codec = codec,
                diagnostics = mutableMapOf<String, String>(),
                id = id,
                attributes = mutableMapOf<String, String>(),
                indexSort = null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        val info2 = codec.segmentInfoFormat().read(dir, "_123", id, IOContext.DEFAULT)
        assertEquals(info.files(), info2.files())
        dir.close()
    }

    @Throws(IOException::class)
    open fun testHasBlocks() {
        assumeTrue(
            "test requires a codec that can read/write hasBlocks",
            supportsHasBlocks()
        )

        val dir: Directory = newDirectory()
        val codec: Codec = codec
        val id: ByteArray = StringHelper.randomId()
        val info =
            SegmentInfo(
                dir,
                this.versions[0],
                this.versions[0],
                "_123",
                1,
                false,
                random().nextBoolean(),
                codec,
                mutableMapOf<String, String>(),
                id,
                mutableMapOf<String, String>(),
                null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        val info2 = codec.segmentInfoFormat().read(dir, "_123", id, IOContext.DEFAULT)
        assertEquals(info.hasBlocks, info2.hasBlocks)
        dir.close()
    }

    /** Tests SI writer adds itself to files...  */
    @Throws(Exception::class)
    open fun testAddsSelfToFiles() {
        val dir: Directory = newDirectory()
        val codec: Codec = codec
        val id: ByteArray = StringHelper.randomId()
        val info =
            SegmentInfo(
                dir,
                this.versions[0],
                this.versions[0],
                "_123",
                1,
                isCompoundFile = false,
                hasBlocks = false,
                codec = codec,
                diagnostics = mutableMapOf<String, String>(),
                id = id,
                attributes = mutableMapOf<String, String>(),
                indexSort = null
            )
        val originalFiles = mutableSetOf("_123.a")
        info.setFiles(originalFiles)
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)

        val modifiedFiles: MutableSet<String> = info.files()
        assertTrue(modifiedFiles.containsAll(originalFiles))
        assertTrue(modifiedFiles.size > originalFiles.size, "did you forget to add yourself to files()")

        val info2 = codec.segmentInfoFormat().read(dir, "_123", id, IOContext.DEFAULT)
        assertEquals(info.files(), info2.files())

        // files set should be immutable
        expectThrows(UnsupportedOperationException::class) {
            info2.files().add("bogus")
        }

        dir.close()
    }

    /** Test diagnostics map  */
    @Throws(Exception::class)
    open fun testDiagnostics() {
        val dir: Directory = newDirectory()
        val codec: Codec = codec
        val id: ByteArray = StringHelper.randomId()
        val diagnostics: MutableMap<String, String> = mutableMapOf()
        diagnostics["key1"] = "value1"
        diagnostics["key2"] = "value2"
        val info =
            SegmentInfo(
                dir,
                this.versions[0],
                this.versions[0],
                "_123",
                1,
                isCompoundFile = false,
                hasBlocks = false,
                codec = codec,
                diagnostics = diagnostics,
                id = id,
                attributes = mutableMapOf<String, String>(),
                indexSort = null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        val info2 = codec.segmentInfoFormat()
            .read(dir, "_123", id, IOContext.DEFAULT)
        assertEquals(diagnostics, info2.diagnostics)

        // diagnostics map should be immutable
        expectThrows(UnsupportedOperationException::class) {
            info2.diagnostics["bogus"] = "bogus"
        }

        dir.close()
    }

    /** Test attributes map  */
    @Throws(Exception::class)
    open fun testAttributes() {
        val dir: Directory = newDirectory()
        val codec: Codec = codec
        val id: ByteArray = StringHelper.randomId()
        val attributes: MutableMap<String, String> = mutableMapOf()
        attributes["key1"] = "value1"
        attributes["key2"] = "value2"
        val info =
            SegmentInfo(
                dir,
                this.versions[0],
                this.versions[0],
                "_123",
                1,
                isCompoundFile = false,
                hasBlocks = false,
                codec = codec,
                diagnostics = mutableMapOf<String, String>(),
                id = id,
                attributes = attributes,
                indexSort = null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        val info2 = codec.segmentInfoFormat().read(dir, "_123", id, IOContext.DEFAULT)
        assertEquals(attributes, info2.attributes)

        // attributes map should be immutable
        expectThrows(UnsupportedOperationException::class) {
            info2.attributes["bogus"] = "bogus"
        }

        dir.close()
    }

    /** Test unique ID  */
    @Throws(Exception::class)
    open fun testUniqueID() {
        val codec: Codec = codec
        val dir: Directory = newDirectory()
        val id: ByteArray = StringHelper.randomId()
        val info =
            SegmentInfo(
                dir,
                this.versions[0],
                this.versions[0],
                "_123",
                1,
                isCompoundFile = false,
                hasBlocks = false,
                codec = codec,
                diagnostics = mutableMapOf<String, String>(),
                id = id,
                attributes = mutableMapOf<String, String>(),
                indexSort = null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        val info2 = codec.segmentInfoFormat().read(dir, "_123", id, IOContext.DEFAULT)
        assertArrayEquals(id, info2.getId())
        dir.close()
    }

    /** Test versions  */
    @Throws(Exception::class)
    open fun testVersions() {
        val codec: Codec = codec
        for (v in this.versions) {
            for (minV in arrayOf(v, null)) {
                val dir: Directory = newDirectory()
                val id: ByteArray = StringHelper.randomId()
                val info = SegmentInfo(
                        dir,
                        v,
                        minV,
                        "_123",
                        1,
                    isCompoundFile = false,
                    hasBlocks = false,
                    codec = codec,
                    diagnostics = mutableMapOf(),
                    id = id,
                    attributes = mutableMapOf(),
                    indexSort = null
                    )
                info.setFiles(mutableSetOf())
                codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
                val info2 = codec.segmentInfoFormat().read(dir, "_123", id, IOContext.DEFAULT)
                assertEquals(info2.version, v)
                if (supportsMinVersion()) {
                    assertEquals(info2.minVersion, minV)
                } else {
                    assertEquals(info2.minVersion, null)
                }
                dir.close()
            }
        }
    }

    protected fun supportsIndexSort(): Boolean {
        return true
    }

    protected fun supportsHasBlocks(): Boolean {
        return true
    }

    private fun randomIndexSortField(): SortField {
        val reversed: Boolean = random().nextBoolean()
        val sortField: SortField?
        when (random().nextInt(10)) {
            0 -> {
                sortField = SortField(TestUtil.randomSimpleString(random()), SortField.Type.INT, reversed)
                if (random().nextBoolean()) {
                    sortField.missingValue = random().nextInt()
                }
            }

            1 -> {
                sortField =
                    SortedNumericSortField(
                        TestUtil.randomSimpleString(random()),
                        SortField.Type.INT,
                        reversed
                    )
                if (random().nextBoolean()) {
                    sortField.missingValue = random().nextInt()
                }
            }

            2 -> {
                sortField = SortField(TestUtil.randomSimpleString(random()), SortField.Type.LONG, reversed)
                if (random().nextBoolean()) {
                    sortField.missingValue = random().nextLong()
                }
            }

            3 -> {
                sortField = SortedNumericSortField(TestUtil.randomSimpleString(random()), SortField.Type.LONG, reversed)
                if (random().nextBoolean()) {
                    sortField.missingValue = random().nextLong()
                }
            }

            4 -> {
                sortField = SortField(TestUtil.randomSimpleString(random()), SortField.Type.FLOAT, reversed)
                if (random().nextBoolean()) {
                    sortField.missingValue = random().nextFloat()
                }
            }

            5 -> {
                sortField = SortedNumericSortField(TestUtil.randomSimpleString(random()), SortField.Type.FLOAT, reversed)
                if (random().nextBoolean()) {
                    sortField.missingValue = random().nextFloat()
                }
            }

            6 -> {
                sortField = SortField(TestUtil.randomSimpleString(random()), SortField.Type.DOUBLE, reversed)
                if (random().nextBoolean()) {
                    sortField.missingValue = random().nextDouble()
                }
            }

            7 -> {
                sortField = SortedNumericSortField(TestUtil.randomSimpleString(random()), SortField.Type.DOUBLE, reversed)
                if (random().nextBoolean()) {
                    sortField.missingValue = random().nextDouble()
                }
            }

            8 -> {
                sortField = SortField(TestUtil.randomSimpleString(random()), SortField.Type.STRING, reversed)
                if (random().nextBoolean()) {
                    sortField.missingValue = SortField.STRING_LAST
                }
            }

            9 -> {
                sortField = SortedSetSortField(TestUtil.randomSimpleString(random()), reversed)
                if (random().nextBoolean()) {
                    sortField.missingValue = SortField.STRING_LAST
                }
            }

            else -> {
                sortField = null
                fail()
            }
        }
        return sortField
    }

    /** Test sort  */
    @Throws(IOException::class)
    open fun testSort() {
        assumeTrue("test requires a codec that can read/write index sort", supportsIndexSort())

        val iters: Int = atLeast(5)
        for (i in 0..<iters) {
            val sort: Sort?
            if (i == 0) {
                sort = null
            } else {
                val numSortFields: Int = TestUtil.nextInt(random(), 1, 3)
                val sortFields: Array<SortField> = Array(numSortFields)
                /*for (j in 0..<numSortFields)*/{
                    /*sortFields[j] =*/ randomIndexSortField()
                }
                sort = Sort(*sortFields)
            }

            val dir: Directory = newDirectory()
            val codec: Codec = codec
            val id: ByteArray = StringHelper.randomId()
            val info =
                SegmentInfo(
                    dir,
                    this.versions[0],
                    this.versions[0],
                    "_123",
                    1,
                    false,
                    false,
                    codec,
                    mutableMapOf<String, String>(),
                    id,
                    mutableMapOf<String, String>(),
                    sort
                )
            info.setFiles(mutableSetOf())
            codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
            val info2 = codec.segmentInfoFormat().read(dir, "_123", id, IOContext.DEFAULT)
            assertEquals(sort, info2.indexSort)
            dir.close()
        }
    }

    /**
     * Test segment infos write that hits exception immediately on open. make sure we get our
     * exception back, no file handle leaks, etc.
     */
    @Throws(Exception::class)
    open fun testExceptionOnCreateOutput() {
        val fail: Failure =
            object : Failure() {
                @Throws(IOException::class)
                override fun eval(dir: MockDirectoryWrapper) {
                    if (doFail && callStackContainsAnyOf("createOutput")) {
                        throw MockDirectoryWrapper.FakeIOException()
                    }
                }
            }

        val dir: MockDirectoryWrapper = newMockDirectory()
        dir.failOn(fail)
        val codec: Codec = codec
        val id: ByteArray = StringHelper.randomId()
        val info =
            SegmentInfo(
                dir,
                this.versions[0],
                this.versions[0],
                "_123",
                1,
                false,
                false,
                codec,
                mutableMapOf<String, String>(),
                id,
                mutableMapOf<String, String>(),
                null
            )
        info.setFiles(mutableSetOf())

        fail.setDoFail()
        expectThrows(MockDirectoryWrapper.FakeIOException::class) {
            codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        }
        fail.clearDoFail()

        dir.close()
    }

    /**
     * Test segment infos write that hits exception on close. make sure we get our exception back, no
     * file handle leaks, etc.
     */
    @Throws(Exception::class)
    open fun testExceptionOnCloseOutput() {
        val fail: Failure =
            object : Failure() {
                @Throws(IOException::class)
                override fun eval(dir: MockDirectoryWrapper) {
                    if (doFail && callStackContainsAnyOf("close")) {
                        throw MockDirectoryWrapper.FakeIOException()
                    }
                }
            }

        val dir: MockDirectoryWrapper = newMockDirectory()
        dir.failOn(fail)
        val codec: Codec = codec
        val id: ByteArray = StringHelper.randomId()
        val info =
            SegmentInfo(
                dir,
                this.versions[0],
                this.versions[0],
                "_123",
                1,
                false,
                false,
                codec,
                mutableMapOf<String, String>(),
                id,
                mutableMapOf<String, String>(),
                null
            )
        info.setFiles(mutableSetOf())

        fail.setDoFail()
        expectThrows(MockDirectoryWrapper.FakeIOException::class) {
            codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        }
        fail.clearDoFail()

        dir.close()
    }

    /**
     * Test segment infos read that hits exception immediately on open. make sure we get our exception
     * back, no file handle leaks, etc.
     */
    @Throws(Exception::class)
    open fun testExceptionOnOpenInput() {
        val fail: Failure =
            object : Failure() {
                @Throws(IOException::class)
                override fun eval(dir: MockDirectoryWrapper) {
                    if (doFail && callStackContainsAnyOf("openInput")) {
                        throw MockDirectoryWrapper.FakeIOException()
                    }
                }
            }

        val dir: MockDirectoryWrapper = newMockDirectory()
        dir.failOn(fail)
        val codec: Codec = codec
        val id: ByteArray = StringHelper.randomId()
        val info =
            SegmentInfo(
                dir,
                this.versions[0],
                this.versions[0],
                "_123",
                1,
                false,
                false,
                codec,
                mutableMapOf<String, String>(),
                id,
                mutableMapOf<String, String>(),
                null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)

        fail.setDoFail()
        expectThrows(MockDirectoryWrapper.FakeIOException::class) {
            codec.segmentInfoFormat().read(dir, "_123", id, IOContext.DEFAULT)
        }
        fail.clearDoFail()

        dir.close()
    }

    /**
     * Test segment infos read that hits exception on close make sure we get our exception back, no
     * file handle leaks, etc.
     */
    @Throws(Exception::class)
    open fun testExceptionOnCloseInput() {
        val fail: Failure =
            object : Failure() {
                @Throws(IOException::class)
                override fun eval(dir: MockDirectoryWrapper) {
                    if (doFail && callStackContainsAnyOf("close")) {
                        throw MockDirectoryWrapper.FakeIOException()
                    }
                }
            }

        val dir: MockDirectoryWrapper = newMockDirectory()
        dir.failOn(fail)
        val codec: Codec = codec
        val id: ByteArray = StringHelper.randomId()
        val info =
            SegmentInfo(
                dir,
                this.versions[0],
                this.versions[0],
                "_123",
                1,
                false,
                false,
                codec,
                mutableMapOf<String, String>(),
                id,
                mutableMapOf<String, String>(),
                null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)

        fail.setDoFail()
        expectThrows(MockDirectoryWrapper.FakeIOException::class) { codec.segmentInfoFormat().read(dir, "_123", id, IOContext.DEFAULT)
        }
        fail.clearDoFail()

        dir.close()
    }

    /**
     * Sets some otherwise hard-to-test properties: random segment names, ID values, document count,
     * etc and round-trips
     */
    @Throws(Exception::class)
    open fun testRandom() {
        val codec: Codec = codec
        val versions: Array<Version> = this.versions
        for (i in 0..9) {
            val dir: Directory = newDirectory()
            val version: Version =
                versions[random().nextInt(versions.size)]
            val randomSegmentIndex: Long =
                abs(random().nextLong())
            val name =
                ("_"
                        + (if (randomSegmentIndex != Long.MIN_VALUE)
                            randomSegmentIndex
                else
                    random().nextInt(Int.MAX_VALUE).toLong()).toString(Character.MAX_RADIX.coerceIn(2, 36)))
            val docCount: Int = TestUtil.nextInt(
                random(),
                1,
                IndexWriter.MAX_DOCS
            )
            val isCompoundFile: Boolean =
                random().nextBoolean()
            val files: MutableSet<String> = mutableSetOf()
            val numFiles: Int = random().nextInt(10)
            for (j in 0..<numFiles) {
                val file: String = IndexFileNames.segmentFileName(name, "", j.toString())
                files.add(file)
                dir.createOutput(file, IOContext.DEFAULT).close()
            }
            val diagnostics: MutableMap<String, String> = mutableMapOf()
            val numDiags: Int = random().nextInt(10)
            for (j in 0..<numDiags) {
                diagnostics[TestUtil.randomUnicodeString(random())] = TestUtil.randomUnicodeString(random())
            }
            val id = ByteArray(StringHelper.ID_LENGTH)
            random().nextBytes(id)

            val attributes: MutableMap<String, String> = mutableMapOf()
            val numAttributes: Int = random().nextInt(10)
            for (j in 0..<numAttributes) {
                attributes[TestUtil.randomUnicodeString(random())] = TestUtil.randomUnicodeString(random())
            }

            val info =
                SegmentInfo(
                    dir,
                    version,
                    null,
                    name,
                    docCount,
                    isCompoundFile,
                    false,
                    codec,
                    diagnostics,
                    id,
                    attributes,
                    null
                )
            info.setFiles(files)
            codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
            val info2 = codec.segmentInfoFormat().read(dir, name, id, IOContext.DEFAULT)
            assertEquals(info, info2)

            dir.close()
        }
    }

    protected fun assertEquals(expected: SegmentInfo, actual: SegmentInfo) {
        assertSame(expected.dir, actual.dir)
        assertEquals(expected.name, actual.name)
        assertEquals(expected.files(), actual.files())
        // we don't assert this, because SI format has nothing to do with it... set by SIS
        // assertSame(expected.codec, actual.codec);
        assertEquals(expected.diagnostics, actual.diagnostics)
        assertEquals(expected.maxDoc().toLong(), actual.maxDoc().toLong())
        assertArrayEquals(expected.getId(), actual.getId())
        assertEquals(expected.useCompoundFile, actual.useCompoundFile)
        assertEquals(expected.version, actual.version)
        assertEquals(expected.attributes, actual.attributes)
    }

    /** Returns the versions this SI should test  */
    protected abstract val versions: Array<Version>

    override fun addRandomFields(doc: Document) {
        doc.add(StoredField("foobar", TestUtil.randomSimpleString(random())))
    }
}
