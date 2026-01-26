package org.gnit.lucenekmp.codecs.lucene90

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.index.BaseCompoundFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLucene90CompoundFormat : BaseCompoundFormatTestCase() {
    override val codec: Codec = TestUtil.getDefaultCodec()

    @Test
    override fun testEmpty() = super.testEmpty()

    @Test
    override fun testSingleFile() = super.testSingleFile()

    @Test
    override fun testTwoFiles() = super.testTwoFiles()

    @Test
    override fun testDoubleClose() = super.testDoubleClose()

    @Test
    override fun testPassIOContext() = super.testPassIOContext()

    @Test
    override fun testLargeCFS() = super.testLargeCFS()

    @Test
    override fun testListAll() = super.testListAll()

    @Test
    override fun testCreateOutputDisabled() = super.testCreateOutputDisabled()

    @Test
    override fun testDeleteFileDisabled() = super.testDeleteFileDisabled()

    @Test
    override fun testRenameFileDisabled() = super.testRenameFileDisabled()

    @Test
    override fun testSyncDisabled() = super.testSyncDisabled()

    @Test
    override fun testMakeLockDisabled() = super.testMakeLockDisabled()

    @Test
    override fun testRandomFiles() = super.testRandomFiles()

    @Test
    override fun testManySubFiles() = super.testManySubFiles()

    @Test
    override fun testClonedStreamsClosing() = super.testClonedStreamsClosing()

    @Test
    override fun testRandomAccess() = super.testRandomAccess()

    @Test
    override fun testRandomAccessClones() = super.testRandomAccessClones()

    @Test
    override fun testFileNotFound() = super.testFileNotFound()

    @Test
    override fun testReadPastEOF() = super.testReadPastEOF()

    @Test
    override fun testMergeStability() = super.testMergeStability()

    @Test
    override fun testResourceNameInsideCompoundFile() = super.testResourceNameInsideCompoundFile()

    @Test
    @Throws(IOException::class)
    fun testFileLengthOrdering() {
        val dir: Directory = newDirectory()
        // Setup the test segment
        val segment = "_123"
        val chunk = 1024 // internal buffer size used by the stream
        val si: SegmentInfo = newSegmentInfo(dir, segment)
        val segId: ByteArray = si.getId()
        val orderedFiles: MutableList<String> = mutableListOf()
        var randomFileSize: Int =
            random().nextInt(0, chunk)
        for (i in 0..9) {
            val filename = "$segment.$i"
            createRandomFile(
                dir,
                filename,
                randomFileSize,
                segId
            )
            // increase the next files size by a random amount
            randomFileSize += random().nextInt(1, 100)
            orderedFiles.add(filename)
        }
        val shuffledFiles: MutableList<String> = orderedFiles.toMutableList()
        shuffledFiles.shuffle(random())

        si.setFiles(shuffledFiles)
        si.codec.compoundFormat().write(dir, si, IOContext.DEFAULT)

        // entries file should contain files ordered by their size
        val entriesFileName: String =
            IndexFileNames.segmentFileName(
                si.name,
                "",
                Lucene90CompoundFormat.ENTRIES_EXTENSION
            )
        dir.openChecksumInput(entriesFileName).use { entriesStream ->
            var priorE: Throwable? = null
            try {
                CodecUtil.checkIndexHeader(
                    entriesStream,
                    Lucene90CompoundFormat.ENTRY_CODEC,
                    Lucene90CompoundFormat.VERSION_START,
                    Lucene90CompoundFormat.VERSION_CURRENT,
                    si.getId(),
                    ""
                )
                val numEntries: Int = entriesStream.readVInt()
                var lastOffset: Long = 0
                var lastLength: Long = 0
                for (i in 0..<numEntries) {
                    val id: String = entriesStream.readString()
                    assertEquals(orderedFiles.get(i), segment + id)
                    val offset: Long = entriesStream.readLong()
                    assertTrue(offset > lastOffset)
                    lastOffset = offset
                    val length: Long = entriesStream.readLong()
                    assertTrue(length >= lastLength)
                    lastLength = length
                }
            } catch (exception: Throwable) {
                priorE = exception
            } finally {
                CodecUtil.checkFooter(entriesStream, priorE)
            }
        }
        dir.close()
    }
}
