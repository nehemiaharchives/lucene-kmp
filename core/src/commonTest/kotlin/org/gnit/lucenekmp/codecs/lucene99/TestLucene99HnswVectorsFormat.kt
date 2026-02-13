package org.gnit.lucenekmp.codecs.lucene99

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.tests.index.BaseKnnVectorsFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.SameThreadExecutorService
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestLucene99HnswVectorsFormat : BaseKnnVectorsFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.getDefaultCodec()

    @Test
    fun testToString() {
        val customCodec: FilterCodec =
            object : FilterCodec("foo", default) {
                override fun knnVectorsFormat(): KnnVectorsFormat {
                    return Lucene99HnswVectorsFormat(10, 20)
                }
            }
        val expectedPrefix =
            "Lucene99HnswVectorsFormat(name=Lucene99HnswVectorsFormat, maxConn=10, beamWidth=20, flatVectorFormat=Lucene99FlatVectorsFormat(vectorsScorer="
        val expectedSuffix = "()))"
        val defaultScorerName = "DefaultFlatVectorScorer"
        val memSegScorerName = "Lucene99MemorySegmentFlatVectorsScorer"
        val defaultScorer = "$expectedPrefix${defaultScorerName}$expectedSuffix"
        val memSegScorer = "$expectedPrefix${memSegScorerName}$expectedSuffix"
        val actual = customCodec.knnVectorsFormat().toString()
        assertTrue(actual == defaultScorer || actual == memSegScorer, "Unexpected format: $actual")
    }

    @Test
    fun testLimits() {
        assertFailsWith<IllegalArgumentException> { Lucene99HnswVectorsFormat(-1, 20) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswVectorsFormat(0, 20) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswVectorsFormat(20, 0) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswVectorsFormat(20, -1) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswVectorsFormat(513, 20) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswVectorsFormat(20, 3201) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswVectorsFormat(20, 100, 1, SameThreadExecutorService()) }
    }

    // tests inherited from BaseKnnVectorsFormatTestCase

    @Test
    override fun testFieldConstructor() = super.testFieldConstructor()

    @Test
    override fun testFieldConstructorExceptions() = super.testFieldConstructorExceptions()

    @Test
    override fun testFieldSetValue() = super.testFieldSetValue()

    @Test
    override fun testIllegalDimChangeTwoDocs() = super.testIllegalDimChangeTwoDocs()

    @Test
    override fun testIllegalSimilarityFunctionChange() = super.testIllegalSimilarityFunctionChange()

    @Test
    override fun testIllegalDimChangeTwoWriters() = super.testIllegalDimChangeTwoWriters()

    @Test
    override fun testMergingWithDifferentKnnFields() = super.testMergingWithDifferentKnnFields()

    @Test
    override fun testMergingWithDifferentByteKnnFields() = super.testMergingWithDifferentByteKnnFields()

    @Test
    override fun testWriterRamEstimate() = super.testWriterRamEstimate()

    @Test
    override fun testIllegalSimilarityFunctionChangeTwoWriters() = super.testIllegalSimilarityFunctionChangeTwoWriters()

    @Test
    override fun testAddIndexesDirectory0() = super.testAddIndexesDirectory0()

    @Test
    override fun testAddIndexesDirectory1() = super.testAddIndexesDirectory1()

    @Test
    override fun testAddIndexesDirectory01() = super.testAddIndexesDirectory01()

    @Test
    override fun testIllegalDimChangeViaAddIndexesDirectory() = super.testIllegalDimChangeViaAddIndexesDirectory()

    @Test
    override fun testIllegalSimilarityFunctionChangeViaAddIndexesDirectory() = super.testIllegalSimilarityFunctionChangeViaAddIndexesDirectory()

    @Test
    override fun testIllegalDimChangeViaAddIndexesCodecReader() = super.testIllegalDimChangeViaAddIndexesCodecReader()

    @Test
    override fun testIllegalSimilarityFunctionChangeViaAddIndexesCodecReader() = super.testIllegalSimilarityFunctionChangeViaAddIndexesCodecReader()

    @Test
    override fun testIllegalDimChangeViaAddIndexesSlowCodecReader() = super.testIllegalDimChangeViaAddIndexesSlowCodecReader()

    @Test
    override fun testIllegalSimilarityFunctionChangeViaAddIndexesSlowCodecReader() = super.testIllegalSimilarityFunctionChangeViaAddIndexesSlowCodecReader()

    @Test
    override fun testIllegalMultipleValues() = super.testIllegalMultipleValues()

    @Test
    override fun testIllegalDimensionTooLarge() = super.testIllegalDimensionTooLarge()

    @Test
    override fun testIllegalEmptyVector() = super.testIllegalEmptyVector()

    @Test
    override fun testDifferentCodecs1() = super.testDifferentCodecs1()

    @Test
    override fun testDifferentCodecs2() = super.testDifferentCodecs2()

    @Test
    override fun testInvalidKnnVectorFieldUsage() = super.testInvalidKnnVectorFieldUsage()

    @Test
    override fun testDeleteAllVectorDocs() = super.testDeleteAllVectorDocs()

    @Test
    override fun testKnnVectorFieldMissingFromOneSegment() = super.testKnnVectorFieldMissingFromOneSegment()

    @Test
    override fun testSparseVectors() = super.testSparseVectors()

    @Test
    override fun testFloatVectorScorerIteration() = super.testFloatVectorScorerIteration()

    @Test
    override fun testByteVectorScorerIteration() = super.testByteVectorScorerIteration()

    @Test
    override fun testEmptyFloatVectorData() = super.testEmptyFloatVectorData()

    @Test
    override fun testEmptyByteVectorData() = super.testEmptyByteVectorData()

    @Test
    override fun testIndexedValueNotAliased() = super.testIndexedValueNotAliased()

    @Test
    override fun testSortedIndex() = super.testSortedIndex()

    @Test
    override fun testSortedIndexBytes() = super.testSortedIndexBytes()

    @Test
    override fun testIndexMultipleKnnVectorFields() = super.testIndexMultipleKnnVectorFields()

    @Test
    override fun testRandom() = super.testRandom()

    @Test
    override fun testSearchWithVisitedLimit() = super.testSearchWithVisitedLimit()

    @Test
    override fun testRandomWithUpdatesAndGraph() = super.testRandomWithUpdatesAndGraph()

    @Test
    override fun testRandomBytes() = super.testRandomBytes()

    @Test
    override fun testCheckIndexIncludesVectors() = super.testCheckIndexIncludesVectors()

    @Test
    override fun testSimilarityFunctionIdentifiers() = super.testSimilarityFunctionIdentifiers()

    @Test
    override fun testVectorEncodingOrdinals() = super.testVectorEncodingOrdinals()

    @Test
    override fun testAdvance() = super.testAdvance()

    @Test
    override fun testVectorValuesReportCorrectDocs() = super.testVectorValuesReportCorrectDocs()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()

    @Test
    override fun testRecall() = super.testRecall()
}
