package org.gnit.lucenekmp.codecs.lucene90

import kotlin.test.Test

/** Tests Lucene90DocValuesFormat's merge instance.  */
class TestLucene90DocValuesFormatMergeInstance : TestLucene90DocValuesFormat() {
    override fun shouldTestMergeInstance(): Boolean {
        return true
    }

    // tests inherited from TestLucene90DocValuesFormat
    @Test
    override fun testUniqueValuesCompression() = super.testUniqueValuesCompression()

    @Test
    override fun testDateCompression() = super.testDateCompression()

    @Test
    override fun testSingleBigValueCompression() = super.testSingleBigValueCompression()

    @Test
    override fun testSortedMergeAwayAllValuesWithSkipper() = super.testSortedMergeAwayAllValuesWithSkipper()

    @Test
    override fun testSortedSetMergeAwayAllValuesWithSkipper() = super.testSortedSetMergeAwayAllValuesWithSkipper()

    @Test
    override fun testNumberMergeAwayAllValuesWithSkipper() = super.testNumberMergeAwayAllValuesWithSkipper()

    @Test
    override fun testSortedNumberMergeAwayAllValuesWithSkipper() = super.testSortedNumberMergeAwayAllValuesWithSkipper()

    @Test
    override fun testSortedMergeAwayAllValuesLargeSegmentWithSkipper() = super.testSortedMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testSortedSetMergeAwayAllValuesLargeSegmentWithSkipper() = super.testSortedSetMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testNumericMergeAwayAllValuesLargeSegmentWithSkipper() = super.testNumericMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testSortedNumericMergeAwayAllValuesLargeSegmentWithSkipper() = super.testSortedNumericMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testNumericDocValuesWithSkipperSmall() = super.testNumericDocValuesWithSkipperSmall()

    @Test
    override fun testNumericDocValuesWithSkipperMedium() = super.testNumericDocValuesWithSkipperMedium()

    @Test
    override fun testNumericDocValuesWithSkipperBig() = super.testNumericDocValuesWithSkipperBig()

    @Test
    override fun testSortedNumericDocValuesWithSkipperSmall() = super.testSortedNumericDocValuesWithSkipperSmall()

    @Test
    override fun testSortedNumericDocValuesWithSkipperMedium() = super.testSortedNumericDocValuesWithSkipperMedium()

    @Test
    override fun testSortedNumericDocValuesWithSkipperBig() = super.testSortedNumericDocValuesWithSkipperBig()

    @Test
    override fun testSortedDocValuesWithSkipperSmall() = super.testSortedDocValuesWithSkipperSmall()

    @Test
    override fun testSortedDocValuesWithSkipperMedium() = super.testSortedDocValuesWithSkipperMedium()

    @Test
    override fun testSortedDocValuesWithSkipperBig() = super.testSortedDocValuesWithSkipperBig()

    @Test
    override fun testSortedSetDocValuesWithSkipperSmall() = super.testSortedSetDocValuesWithSkipperSmall()

    @Test
    override fun testSortedSetDocValuesWithSkipperMedium() = super.testSortedSetDocValuesWithSkipperMedium()

    @Test
    override fun testSortedSetDocValuesWithSkipperBig() = super.testSortedSetDocValuesWithSkipperBig()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()

    @Test
    override fun testOneNumber() = super.testOneNumber()

    @Test
    override fun testOneFloat() = super.testOneFloat()

    @Test
    override fun testTwoNumbers() = super.testTwoNumbers()

    @Test
    override fun testTwoBinaryValues() = super.testTwoBinaryValues()

    @Test
    override fun testVariouslyCompressibleBinaryValues() = super.testVariouslyCompressibleBinaryValues()

    @Test
    override fun testTwoFieldsMixed() = super.testTwoFieldsMixed()

    @Test
    override fun testThreeFieldsMixed() = super.testThreeFieldsMixed()

    @Test
    override fun testThreeFieldsMixed2() = super.testThreeFieldsMixed2()

    @Test
    override fun testTwoDocumentsNumeric() = super.testTwoDocumentsNumeric()

    @Test
    override fun testTwoDocumentsMerged() = super.testTwoDocumentsMerged()

    @Test
    override fun testBigNumericRange() = super.testBigNumericRange()

    @Test
    override fun testBigNumericRange2() = super.testBigNumericRange2()

    @Test
    override fun testBytes() = super.testBytes()

    @Test
    override fun testBytesTwoDocumentsMerged() = super.testBytesTwoDocumentsMerged()

    @Test
    override fun testBytesMergeAwayAllValues() = super.testBytesMergeAwayAllValues()

    @Test
    override fun testSortedBytes() = super.testSortedBytes()

    @Test
    override fun testSortedBytesTwoDocuments() = super.testSortedBytesTwoDocuments()

    @Test
    override fun testSortedBytesThreeDocuments() = super.testSortedBytesThreeDocuments()

    @Test
    override fun testSortedBytesTwoDocumentsMerged() = super.testSortedBytesTwoDocumentsMerged()

    @Test
    override fun testSortedMergeAwayAllValues() = super.testSortedMergeAwayAllValues()

    @Test
    override fun testBytesWithNewline() = super.testBytesWithNewline()

    @Test
    override fun testMissingSortedBytes() = super.testMissingSortedBytes()

    @Test
    override fun testSortedTermsEnum() = super.testSortedTermsEnum()

    @Test
    override fun testEmptySortedBytes() = super.testEmptySortedBytes()

    @Test
    override fun testEmptyBytes() = super.testEmptyBytes()

    @Test
    override fun testVeryLargeButLegalBytes() = super.testVeryLargeButLegalBytes()

    @Test
    override fun testVeryLargeButLegalSortedBytes() = super.testVeryLargeButLegalSortedBytes()

    @Test
    override fun testCodecUsesOwnBytes() = super.testCodecUsesOwnBytes()

    @Test
    override fun testCodecUsesOwnSortedBytes() = super.testCodecUsesOwnSortedBytes()

    @Test
    override fun testDocValuesSimple() = super.testDocValuesSimple()

    @Test
    override fun testRandomSortedBytes() = super.testRandomSortedBytes()

    @Test
    override fun testBooleanNumericsVsStoredFields() = super.testBooleanNumericsVsStoredFields()

    @Test
    override fun testSparseBooleanNumericsVsStoredFields() = super.testSparseBooleanNumericsVsStoredFields()

    @Test
    override fun testByteNumericsVsStoredFields() = super.testByteNumericsVsStoredFields()

    @Test
    override fun testSparseByteNumericsVsStoredFields() = super.testSparseByteNumericsVsStoredFields()

    @Test
    override fun testShortNumericsVsStoredFields() = super.testShortNumericsVsStoredFields()

    @Test
    override fun testSparseShortNumericsVsStoredFields() = super.testSparseShortNumericsVsStoredFields()

    @Test
    override fun testIntNumericsVsStoredFields() = super.testIntNumericsVsStoredFields()

    @Test
    override fun testSparseIntNumericsVsStoredFields() = super.testSparseIntNumericsVsStoredFields()

    @Test
    override fun testLongNumericsVsStoredFields() = super.testLongNumericsVsStoredFields()

    @Test
    override fun testSparseLongNumericsVsStoredFields() = super.testSparseLongNumericsVsStoredFields()

    @Test
    override fun testBinaryFixedLengthVsStoredFields() = super.testBinaryFixedLengthVsStoredFields()

    @Test
    override fun testSparseBinaryFixedLengthVsStoredFields() = super.testSparseBinaryFixedLengthVsStoredFields()

    @Test
    override fun testBinaryVariableLengthVsStoredFields() = super.testBinaryVariableLengthVsStoredFields()

    @Test
    override fun testSparseBinaryVariableLengthVsStoredFields() = super.testSparseBinaryVariableLengthVsStoredFields()

    @Test
    override fun testSortedFixedLengthVsStoredFields() = super.testSortedFixedLengthVsStoredFields()

    @Test
    override fun testSparseSortedFixedLengthVsStoredFields() = super.testSparseSortedFixedLengthVsStoredFields()

    @Test
    override fun testSortedVariableLengthVsStoredFields() = super.testSortedVariableLengthVsStoredFields()

    @Test
    override fun testSparseSortedVariableLengthVsStoredFields() = super.testSparseSortedVariableLengthVsStoredFields()

    @Test
    override fun testSortedSetOneValue() = super.testSortedSetOneValue()

    @Test
    override fun testSortedSetTwoFields() = super.testSortedSetTwoFields()

    @Test
    override fun testSortedSetTwoDocumentsMerged() = super.testSortedSetTwoDocumentsMerged()

    @Test
    override fun testSortedSetTwoValues() = super.testSortedSetTwoValues()

    @Test
    override fun testSortedSetTwoValuesUnordered() = super.testSortedSetTwoValuesUnordered()

    @Test
    override fun testSortedSetThreeValuesTwoDocs() = super.testSortedSetThreeValuesTwoDocs()

    @Test
    override fun testSortedSetTwoDocumentsLastMissing() = super.testSortedSetTwoDocumentsLastMissing()

    @Test
    override fun testSortedSetTwoDocumentsLastMissingMerge() = super.testSortedSetTwoDocumentsLastMissingMerge()

    @Test
    override fun testSortedSetTwoDocumentsFirstMissing() = super.testSortedSetTwoDocumentsFirstMissing()

    @Test
    override fun testSortedSetTwoDocumentsFirstMissingMerge() = super.testSortedSetTwoDocumentsFirstMissingMerge()

    @Test
    override fun testSortedSetMergeAwayAllValues() = super.testSortedSetMergeAwayAllValues()

    @Test
    override fun testSortedSetTermsEnum() = super.testSortedSetTermsEnum()

    @Test
    override fun testSortedSetFixedLengthVsStoredFields() = super.testSortedSetFixedLengthVsStoredFields()

    @Test
    override fun testSortedNumericsSingleValuedVsStoredFields() = super.testSortedNumericsSingleValuedVsStoredFields()

    @Test
    override fun testSortedNumericsSingleValuedMissingVsStoredFields() = super.testSortedNumericsSingleValuedMissingVsStoredFields()

    @Test
    override fun testSortedNumericsMultipleValuesVsStoredFields() = super.testSortedNumericsMultipleValuesVsStoredFields()

    @Test
    override fun testSortedNumericsFewUniqueSetsVsStoredFields() = super.testSortedNumericsFewUniqueSetsVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthVsStoredFields() = super.testSortedSetVariableLengthVsStoredFields()

    @Test
    override fun testSortedSetFixedLengthSingleValuedVsStoredFields() = super.testSortedSetFixedLengthSingleValuedVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthSingleValuedVsStoredFields() = super.testSortedSetVariableLengthSingleValuedVsStoredFields()

    @Test
    override fun testSortedSetFixedLengthFewUniqueSetsVsStoredFields() = super.testSortedSetFixedLengthFewUniqueSetsVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthFewUniqueSetsVsStoredFields() = super.testSortedSetVariableLengthFewUniqueSetsVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthManyValuesPerDocVsStoredFields() = super.testSortedSetVariableLengthManyValuesPerDocVsStoredFields()

    @Test
    override fun testSortedSetFixedLengthManyValuesPerDocVsStoredFields() = super.testSortedSetFixedLengthManyValuesPerDocVsStoredFields()

    @Test
    override fun testGCDCompression() = super.testGCDCompression()

    @Test
    override fun testSparseGCDCompression() = super.testSparseGCDCompression()

    @Test
    override fun testZeros() = super.testZeros()

    @Test
    override fun testSparseZeros() = super.testSparseZeros()

    @Test
    override fun testZeroOrMin() = super.testZeroOrMin()

    @Test
    override fun testTwoNumbersOneMissing() = super.testTwoNumbersOneMissing()

    @Test
    override fun testTwoNumbersOneMissingWithMerging() = super.testTwoNumbersOneMissingWithMerging()

    @Test
    override fun testThreeNumbersOneMissingWithMerging() = super.testThreeNumbersOneMissingWithMerging()

    @Test
    override fun testTwoBytesOneMissing() = super.testTwoBytesOneMissing()

    @Test
    override fun testTwoBytesOneMissingWithMerging() = super.testTwoBytesOneMissingWithMerging()

    @Test
    override fun testThreeBytesOneMissingWithMerging() = super.testThreeBytesOneMissingWithMerging()

    @Test
    override fun testThreads() = super.testThreads()

    @Test
    override fun testThreads2() = super.testThreads2()

    @Test
    override fun testThreads3() = super.testThreads3()

    @Test
    override fun testEmptyBinaryValueOnPageSizes() = super.testEmptyBinaryValueOnPageSizes()

    @Test
    override fun testOneSortedNumber() = super.testOneSortedNumber()

    @Test
    override fun testOneSortedNumberOneMissing() = super.testOneSortedNumberOneMissing()

    @Test
    override fun testNumberMergeAwayAllValues() = super.testNumberMergeAwayAllValues()

    @Test
    override fun testTwoSortedNumber() = super.testTwoSortedNumber()

    @Test
    override fun testTwoSortedNumberSameValue() = super.testTwoSortedNumberSameValue()

    @Test
    override fun testTwoSortedNumberOneMissing() = super.testTwoSortedNumberOneMissing()

    @Test
    override fun testSortedNumberMerge() = super.testSortedNumberMerge()

    @Test
    override fun testSortedNumberMergeAwayAllValues() = super.testSortedNumberMergeAwayAllValues()

    @Test
    override fun testSortedEnumAdvanceIndependently() = super.testSortedEnumAdvanceIndependently()

    @Test
    override fun testSortedSetEnumAdvanceIndependently() = super.testSortedSetEnumAdvanceIndependently()

    @Test
    override fun testSortedMergeAwayAllValuesLargeSegment() = super.testSortedMergeAwayAllValuesLargeSegment()

    @Test
    override fun testSortedSetMergeAwayAllValuesLargeSegment() = super.testSortedSetMergeAwayAllValuesLargeSegment()

    @Test
    override fun testNumericMergeAwayAllValuesLargeSegment() = super.testNumericMergeAwayAllValuesLargeSegment()

    @Test
    override fun testSortedNumericMergeAwayAllValuesLargeSegment() = super.testSortedNumericMergeAwayAllValuesLargeSegment()

    @Test
    override fun testBinaryMergeAwayAllValuesLargeSegment() = super.testBinaryMergeAwayAllValuesLargeSegment()

    @Test
    override fun testRandomAdvanceNumeric() = super.testRandomAdvanceNumeric()

    @Test
    override fun testRandomAdvanceBinary() = super.testRandomAdvanceBinary()

    @Test
    override fun testHighOrdsSortedSetDV() = super.testHighOrdsSortedSetDV()

}
