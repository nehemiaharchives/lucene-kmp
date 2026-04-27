package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.mockfile.ExtrasFS
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestSegmentInfos : LuceneTestCase() {

    @Test
    fun testIllegalCreatedVersion() {
        var e: IllegalArgumentException =
            expectThrows(IllegalArgumentException::class) { SegmentInfos(5) }
        assertEquals("indexCreatedVersionMajor must be >= 6, got: 5", e.message)
        e =
            expectThrows(
                IllegalArgumentException::class
            ) { SegmentInfos(Version.LATEST.major + 1) }
        assertEquals(
            "indexCreatedVersionMajor is in the future: " + (Version.LATEST.major + 1), e.message
        )
    }

    // LUCENE-5954
    @Test
    @Throws(IOException::class)
    fun testVersionsNoSegments() {
        var sis = SegmentInfos(Version.LATEST.major)
        val dir: BaseDirectoryWrapper = newDirectory()
        dir.checkIndexOnClose = false
        sis.commit(dir)
        sis = SegmentInfos.readLatestCommit(dir)
        assertNull(sis.getMinSegmentLuceneVersion())
        assertEquals(Version.LATEST, sis.commitLuceneVersion)
        dir.close()
    }

    // LUCENE-5954
    @Test
    @Throws(IOException::class)
    fun testVersionsOneSegment() {
        val dir: BaseDirectoryWrapper = newDirectory()
        dir.checkIndexOnClose = false
        val id: ByteArray = StringHelper.randomId()
        val codec: Codec = Codec.default

        var sis = SegmentInfos(Version.LATEST.major)
        val info =
            SegmentInfo(
                dir,
                Version.LUCENE_11_0_0,
                Version.LUCENE_11_0_0,
                "_0",
                1,
                false,
                false,
                Codec.default,
                mutableMapOf(),
                id,
                mutableMapOf(),
                null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        val commitInfo =
            SegmentCommitInfo(info, 0, 0, -1, -1, -1, StringHelper.randomId())

        sis.add(commitInfo)
        sis.commit(dir)
        sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(Version.LUCENE_11_0_0, sis.getMinSegmentLuceneVersion())
        assertEquals(Version.LATEST, sis.commitLuceneVersion)
        dir.close()
    }

    // LUCENE-5954
    @Test
    @Throws(IOException::class)
    fun testVersionsTwoSegments() {
        val dir: BaseDirectoryWrapper = newDirectory()
        dir.checkIndexOnClose = false
        val id: ByteArray = StringHelper.randomId()
        val codec: Codec = Codec.default

        var sis = SegmentInfos(Version.LATEST.major)
        var info =
            SegmentInfo(
                dir,
                Version.LUCENE_11_0_0,
                Version.LUCENE_11_0_0,
                "_0",
                1,
                false,
                false,
                Codec.default,
                mutableMapOf(),
                id,
                mutableMapOf(),
                null
            )

        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        var commitInfo =
            SegmentCommitInfo(info, 0, 0, -1, -1, -1, StringHelper.randomId())
        sis.add(commitInfo)

        info =
            SegmentInfo(
                dir,
                Version.LUCENE_11_0_0,
                Version.LUCENE_11_0_0,
                "_1",
                1,
                false,
                false,
                Codec.default,
                mutableMapOf(),
                id,
                mutableMapOf(),
                null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        commitInfo = SegmentCommitInfo(info, 0, 0, -1, -1, -1, StringHelper.randomId())
        sis.add(commitInfo)

        sis.commit(dir)
        val commitInfoId0: ByteArray? = sis.info(0).getId()
        val commitInfoId1: ByteArray? = sis.info(1).getId()
        sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(Version.LUCENE_11_0_0, sis.getMinSegmentLuceneVersion())
        assertEquals(Version.LATEST, sis.commitLuceneVersion)
        assertEquals(
            StringHelper.idToString(commitInfoId0), StringHelper.idToString(sis.info(0).getId())
        )
        assertEquals(
            StringHelper.idToString(commitInfoId1), StringHelper.idToString(sis.info(1).getId())
        )
        dir.close()
    }


    /** Test toString method  */
    @Test
    @Throws(Throwable::class)
    fun testToString() {
        var si: SegmentInfo
        val dir: Directory = newDirectory()
        val codec: Codec = Codec.default

        // diagnostics map
        val diagnostics: MutableMap<String, String> = mutableMapOf("key1" to "value1", "key2" to "value2")

        // attributes map
        val attributes: MutableMap<String, String> = mutableMapOf("akey1" to "value1", "akey2" to "value2")

        // diagnostics X, attributes X
        si =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "TEST",
                10000,
                isCompoundFile = false,
                hasBlocks = false,
                codec = codec,
                diagnostics = mutableMapOf(),
                id = StringHelper.randomId(),
                attributes = mutableMapOf(),
                indexSort = Sort.INDEXORDER
            )
        assertEquals(
            "TEST(" + Version.LATEST.toString() + ")" + ":C10000" + ":[indexSort=<doc>]",
            si.toString()
        )

        // diagnostics O, attributes X
        si =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "TEST",
                10000,
                isCompoundFile = false,
                hasBlocks = false,
                codec = codec,
                diagnostics = diagnostics,
                id = StringHelper.randomId(),
                attributes = mutableMapOf(),
                indexSort = Sort.INDEXORDER
            )
        assertEquals(
            ("TEST("
                    + Version.LATEST.toString()
                    + ")"
                    + ":C10000"
                    + ":[indexSort=<doc>]"
                    + ":[diagnostics="
                    + diagnostics
                    + "]"),
            si.toString()
        )

        // diagnostics X, attributes O
        si =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "TEST",
                10000,
                false,
                false,
                codec,
                mutableMapOf(),
                StringHelper.randomId(),
                attributes,
                Sort.INDEXORDER
            )
        assertEquals(
            ("TEST("
                    + Version.LATEST.toString()
                    + ")"
                    + ":C10000"
                    + ":[indexSort=<doc>]"
                    + ":[attributes="
                    + attributes
                    + "]"),
            si.toString()
        )

        // diagnostics O, attributes O
        si =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "TEST",
                10000,
                false,
                false,
                codec,
                diagnostics,
                StringHelper.randomId(),
                attributes,
                Sort.INDEXORDER
            )
        assertEquals(
            ("TEST("
                    + Version.LATEST.toString()
                    + ")"
                    + ":C10000"
                    + ":[indexSort=<doc>]"
                    + ":[diagnostics="
                    + diagnostics
                    + "]"
                    + ":[attributes="
                    + attributes
                    + "]"),
            si.toString()
        )

        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testIDChangesOnAdvance() {
        newDirectory().use { dir ->
            dir.checkIndexOnClose = false
            var id: ByteArray? = StringHelper.randomId()
            val info =
                SegmentInfo(
                    dir,
                    Version.LUCENE_10_0_0,
                    Version.LUCENE_10_0_0,
                    "_0",
                    1,
                    false,
                    false,
                    Codec.default,
                    mutableMapOf(),
                    StringHelper.randomId(),
                    mutableMapOf(),
                    null
                )
            val commitInfo = SegmentCommitInfo(info, 0, 0, -1, -1, -1, id)
            assertEquals(StringHelper.idToString(id), StringHelper.idToString(commitInfo.getId()))
            commitInfo.advanceDelGen()
            assertNotEquals(StringHelper.idToString(id), StringHelper.idToString(commitInfo.getId()))

            id = commitInfo.getId()
            commitInfo.advanceDocValuesGen()
            assertNotEquals(StringHelper.idToString(id), StringHelper.idToString(commitInfo.getId()))

            id = commitInfo.getId()
            commitInfo.advanceFieldInfosGen()
            assertNotEquals(StringHelper.idToString(id), StringHelper.idToString(commitInfo.getId()))
            val clone: SegmentCommitInfo = commitInfo.clone()
            id = commitInfo.getId()
            assertEquals(StringHelper.idToString(id), StringHelper.idToString(commitInfo.getId()))
            assertEquals(StringHelper.idToString(id), StringHelper.idToString(clone.getId()))

            commitInfo.advanceFieldInfosGen()
            assertNotEquals(StringHelper.idToString(id), StringHelper.idToString(commitInfo.getId()))
            assertEquals(
                StringHelper.idToString(id),
                StringHelper.idToString(clone.getId()),
                "clone changed but shouldn't"
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testBitFlippedTriggersCorruptIndexException() {
        val dir: BaseDirectoryWrapper = newDirectory()
        dir.checkIndexOnClose = false
        val id: ByteArray = StringHelper.randomId()
        val codec: Codec = Codec.default

        val sis = SegmentInfos(Version.LATEST.major)
        var info =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "_0",
                1,
                isCompoundFile = false,
                hasBlocks = false,
                codec = Codec.default,
                diagnostics = mutableMapOf(),
                id = id,
                attributes = mutableMapOf(),
                indexSort = null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        var commitInfo =
            SegmentCommitInfo(info, 0, 0, -1, -1, -1, StringHelper.randomId())
        sis.add(commitInfo)

        info =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "_1",
                1,
                isCompoundFile = false,
                hasBlocks = false,
                codec = Codec.default,
                diagnostics = mutableMapOf(),
                id = id,
                attributes = mutableMapOf(),
                indexSort = null
            )
        info.setFiles(mutableSetOf())
        codec.segmentInfoFormat().write(dir, info, IOContext.DEFAULT)
        commitInfo = SegmentCommitInfo(info, 0, 0, -1, -1, -1, StringHelper.randomId())
        sis.add(commitInfo)

        sis.commit(dir)

        val corruptDir: BaseDirectoryWrapper = newDirectory()
        corruptDir.checkIndexOnClose = false
        var corrupt = false
        for (file in dir.listAll()) {
            if (file.startsWith(IndexFileNames.SEGMENTS)) {
                dir.openInput(file, IOContext.READONCE).use { `in` ->
                    corruptDir.createOutput(file, IOContext.DEFAULT).use { out ->
                        val corruptIndex: Long = TestUtil.nextLong(random(), 0, `in`.length() - 1)
                        out.copyBytes(`in`, corruptIndex)
                        val b: Int = Byte.toUnsignedInt(`in`.readByte()) + TestUtil.nextInt(random(), 0x01, 0xff)
                        out.writeByte(b.toByte())
                        out.copyBytes(`in`, `in`.length() - `in`.filePointer)
                    }
                }
                try {
                    corruptDir.openInput(file, IOContext.READONCE).use { `in` ->
                        CodecUtil.checksumEntireFile(`in`)
                        if (VERBOSE) {
                            println("TEST: Altering the file did not update the checksum, aborting...")
                        }
                        return
                    }
                } catch (e: CorruptIndexException) {
                    // ok
                }
                corrupt = true
            } else if (ExtrasFS.isExtra(file) == false) {
                corruptDir.copyFrom(dir, file, file, IOContext.DEFAULT)
            }
        }
        assertTrue(corrupt, "No segments file found")

        expectThrowsAnyOf(
            mutableListOf(
                CorruptIndexException::class,
                IndexFormatTooOldException::class,
                IndexFormatTooNewException::class
            )
        ) { SegmentInfos.readLatestCommit(corruptDir) }
        dir.close()
        corruptDir.close()
    }

    /** Test addDiagnostics method  */
    @Test
    @Throws(Throwable::class)
    fun testAddDiagnostics() {
        var si: SegmentInfo
        val dir: Directory = newDirectory()
        val codec: Codec = Codec.default

        // diagnostics map
        val diagnostics: MutableMap<String, String> = mutableMapOf("key1" to "value1", "key2" to "value2")

        // adds an additional key/value pair
        si =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "TEST",
                10000,
                false,
                false,
                codec,
                diagnostics,
                StringHelper.randomId(),
                mutableMapOf(),
                Sort.INDEXORDER
            )
        si.addDiagnostics(mutableMapOf("key3" to "value3"))
        assertEquals(mutableMapOf("key1" to "value1", "key2" to "value2", "key3" to "value3"), si.diagnostics)

        // modifies an existing key/value pair
        si =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "TEST",
                10000,
                false,
                false,
                codec,
                diagnostics,
                StringHelper.randomId(),
                mutableMapOf(),
                Sort.INDEXORDER
            )
        si.addDiagnostics(mutableMapOf("key2" to "foo"))
        assertEquals(mutableMapOf("key1" to "value1", "key2" to "foo"), si.diagnostics)

        dir.close()
    }
}
