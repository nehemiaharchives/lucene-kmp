package org.gnit.lucenekmp.codecs.lucene99

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BaseSegmentInfoFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test

class TestLucene99SegmentInfoFormat : BaseSegmentInfoFormatTestCase() {
    override val versions: Array<Version>
        get() = arrayOf(Version.LATEST)

    override val codec: Codec
        get() = TestUtil.getDefaultCodec()

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
