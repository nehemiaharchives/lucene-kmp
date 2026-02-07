package org.gnit.lucenekmp.codecs.simpletext

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.index.BaseSegmentInfoFormatTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.fail


/** Tests SimpleTextSegmentInfoFormat  */
class TestSimpleTextSegmentInfoFormat : BaseSegmentInfoFormatTestCase() {
    override val codec: Codec = SimpleTextCodec()

    override val versions: Array<Version>
        get() = arrayOf(Version.LATEST)

    @Test
    @Throws(IOException::class)
    fun testFileIsUTF8() {
        val dir: Directory =
            newDirectory()
        val codec: Codec = this.codec
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
        val segFileName: String =
            IndexFileNames.segmentFileName(
                "_123",
                "",
                SimpleTextSegmentInfoFormat.SI_EXTENSION
            )
        dir.openChecksumInput(segFileName).use { input ->
            val length: Long = input.length()
            if (length > 5000) {
                // Avoid allocating a huge array if the length is wrong
                fail("SegmentInfos should not be this large")
            }
            val bytes = ByteArray(length.toInt())
            val bytesRef = BytesRef(bytes)
            // If the following are equal, it means the bytes were not well-formed UTF8.
            assertNotEquals(bytesRef.toString(), Term.toString(bytesRef))
        }
        dir.close()
    }

    // tests inherited from BaseSegmentInfoFormatTestCase

    @Test
    override fun testFiles() = super.testFiles()

    @Test
    override fun testHasBlocks() = super.testHasBlocks()

    @Test
    override fun testAddsSelfToFiles() = super.testAddsSelfToFiles()

    @Test
    override fun testDiagnostics() = super.testDiagnostics()

    @Test
    override fun testAttributes() = super.testAttributes()

    @Test
    override fun testUniqueID() = super.testUniqueID()

    @Test
    override fun testVersions() = super.testVersions()

    @Test
    override fun testSort() = super.testSort()

    @Test
    override fun testExceptionOnCreateOutput() = super.testExceptionOnCreateOutput()

    @Test
    override fun testExceptionOnCloseOutput() = super.testExceptionOnCloseOutput()

    @Test
    override fun testExceptionOnOpenInput() = super.testExceptionOnOpenInput()

    @Test
    override fun testExceptionOnCloseInput() = super.testExceptionOnCloseInput()

    @Test
    override fun testRandom() = super.testRandom()

}
