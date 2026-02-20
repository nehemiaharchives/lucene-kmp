package org.gnit.lucenekmp.codecs.perfield

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BasePostingsFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Ignore
import kotlin.test.Test

/** Basic tests of PerFieldPostingsFormat */
class TestPerFieldPostingsFormat : BasePostingsFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.getDefaultCodec()

    @Ignore
    @Test
    override fun testMergeStability() {
        // The MockRandom PF randomizes content on the fly, so we can't check it
    }

    @Ignore
    @Test
    override fun testPostingsEnumReuse() {
        // The MockRandom PF randomizes content on the fly, so we can't check it
    }

    // tests inherited from BasePostingsFormatTestCase
    @Test
    override fun testDocsOnly() = super.testDocsOnly()

    @Test
    override fun testDocsAndFreqs() = super.testDocsAndFreqs()

    @Test
    override fun testDocsAndFreqsAndPositions() = super.testDocsAndFreqsAndPositions()

    @Test
    override fun testDocsAndFreqsAndPositionsAndPayloads() = super.testDocsAndFreqsAndPositionsAndPayloads()

    @Test
    override fun testDocsAndFreqsAndPositionsAndOffsets() = super.testDocsAndFreqsAndPositionsAndOffsets()

    @Test
    override fun testDocsAndFreqsAndPositionsAndOffsetsAndPayloads() = super.testDocsAndFreqsAndPositionsAndOffsetsAndPayloads()

    @Test
    override fun testRandom() = super.testRandom()

    @Test
    override fun testJustEmptyField() = super.testJustEmptyField()

    @Test
    override fun testEmptyFieldAndEmptyTerm() = super.testEmptyFieldAndEmptyTerm()

    @Test
    override fun testDidntWantFreqsButAskedAnyway() = super.testDidntWantFreqsButAskedAnyway()

    @Test
    override fun testAskForPositionsWhenNotThere() = super.testAskForPositionsWhenNotThere()

    @Test
    override fun testGhosts() = super.testGhosts()

    @Test
    override fun testDisorder() = super.testDisorder()

    @Test
    override fun testBinarySearchTermLeaf() = super.testBinarySearchTermLeaf()

    @Test
    override fun testLevel2Ghosts() = super.testLevel2Ghosts()

    @Test
    override fun testInvertedWrite() = super.testInvertedWrite()

    @Test
    override fun testPostingsEnumDocsOnly() = super.testPostingsEnumDocsOnly()

    @Test
    override fun testPostingsEnumFreqs() = super.testPostingsEnumFreqs()

    @Test
    override fun testPostingsEnumPositions() = super.testPostingsEnumPositions()

    @Test
    override fun testPostingsEnumOffsets() = super.testPostingsEnumOffsets()

    @Test
    override fun testPostingsEnumPayloads() = super.testPostingsEnumPayloads()

    @Test
    override fun testPostingsEnumAll() = super.testPostingsEnumAll()

    @Test
    override fun testLineFileDocs() = super.testLineFileDocs()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()
}
