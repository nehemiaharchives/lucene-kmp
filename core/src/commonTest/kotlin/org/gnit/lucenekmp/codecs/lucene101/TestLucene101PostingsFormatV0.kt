package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter
import org.gnit.lucenekmp.tests.index.BasePostingsFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

class TestLucene101PostingsFormatV0 : BasePostingsFormatTestCase() {
    override val codec: Codec
        get() =
            TestUtil.alwaysPostingsFormat(
                Lucene101PostingsFormat(
                    Lucene90BlockTreeTermsWriter.DEFAULT_MIN_BLOCK_SIZE,
                    Lucene90BlockTreeTermsWriter.DEFAULT_MAX_BLOCK_SIZE,
                    Lucene101PostingsFormat.VERSION_START
                )
            )

    // tests inherited from BasePostingsFormatTestCase
    @Test
    @Throws(Exception::class)
    override fun testDocsOnly() = super.testDocsOnly()

    @Test
    @Throws(Exception::class)
    override fun testDocsAndFreqs() = super.testDocsAndFreqs()

    @Test
    @Throws(Exception::class)
    override fun testDocsAndFreqsAndPositions() = super.testDocsAndFreqsAndPositions()

    @Test
    @Throws(Exception::class)
    override fun testDocsAndFreqsAndPositionsAndPayloads() = super.testDocsAndFreqsAndPositionsAndPayloads()

    @Test
    @Throws(Exception::class)
    override fun testDocsAndFreqsAndPositionsAndOffsets() = super.testDocsAndFreqsAndPositionsAndOffsets()

    @Test
    @Throws(Exception::class)
    override fun testDocsAndFreqsAndPositionsAndOffsetsAndPayloads() =
        super.testDocsAndFreqsAndPositionsAndOffsetsAndPayloads()

    @Test
    @Throws(Exception::class)
    override fun testRandom() = super.testRandom()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumReuse() = super.testPostingsEnumReuse()

    @Test
    @Throws(Exception::class)
    override fun testJustEmptyField() = super.testJustEmptyField()

    @Test
    @Throws(Exception::class)
    override fun testEmptyFieldAndEmptyTerm() = super.testEmptyFieldAndEmptyTerm()

    @Test
    @Throws(Exception::class)
    override fun testDidntWantFreqsButAskedAnyway() = super.testDidntWantFreqsButAskedAnyway()

    @Test
    @Throws(Exception::class)
    override fun testAskForPositionsWhenNotThere() = super.testAskForPositionsWhenNotThere()

    @Test
    @Throws(Exception::class)
    override fun testGhosts() = super.testGhosts()

    @Test
    @Throws(Exception::class)
    override fun testDisorder() = super.testDisorder()

    @Test
    @Throws(Exception::class)
    override fun testBinarySearchTermLeaf() = super.testBinarySearchTermLeaf()

    @Test
    @Throws(Exception::class)
    override fun testLevel2Ghosts() = super.testLevel2Ghosts()

    @Test
    @Throws(Exception::class)
    override fun testInvertedWrite() = super.testInvertedWrite()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumDocsOnly() = super.testPostingsEnumDocsOnly()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumFreqs() = super.testPostingsEnumFreqs()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumPositions() = super.testPostingsEnumPositions()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumOffsets() = super.testPostingsEnumOffsets()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumPayloads() = super.testPostingsEnumPayloads()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumAll() = super.testPostingsEnumAll()

    @Test
    override fun testLineFileDocs() = super.testLineFileDocs()

    @Test
    @Throws(Exception::class)
    override fun testMismatchedFields() = super.testMismatchedFields()
}
