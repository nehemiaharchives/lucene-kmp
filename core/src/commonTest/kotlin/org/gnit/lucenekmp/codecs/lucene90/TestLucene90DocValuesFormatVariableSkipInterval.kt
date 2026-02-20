package org.gnit.lucenekmp.codecs.lucene90

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.tests.index.BaseDocValuesFormatTestCase
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Tests Lucene90DocValuesFormat with custom skipper interval size */
class TestLucene90DocValuesFormatVariableSkipInterval : BaseDocValuesFormatTestCase() {
    override val codec: Codec
        get() {
            // small interval size to test with many intervals
            return TestUtil.alwaysDocValuesFormat(Lucene90DocValuesFormat(random().nextInt(4, 16)))
        }

    @Test
    fun testSkipIndexIntervalSize() {
        val ex =
            expectThrows(IllegalArgumentException::class) {
                Lucene90DocValuesFormat(random().nextInt(Int.MIN_VALUE, 2))
            }
        assertTrue(ex.message!!.contains("skipIndexIntervalSize must be > 1"))
    }

    @Test
    @Throws(IOException::class)
    fun testSkipperAllEqualValue() {
        val config: IndexWriterConfig = IndexWriterConfig().setCodec(codec)
        newDirectory().use { directory ->
            RandomIndexWriter(random(), directory, config).use { writer ->
                val numDocs: Int = atLeast(100)
                for (i in 0..<numDocs) {
                    val doc = Document()
                    doc.add(NumericDocValuesField.indexedField("dv", 0L))
                    writer.addDocument(doc)
                }
                writer.forceMerge(1)
                writer.reader.use { reader ->
                    assertEquals(1, reader.leaves().size)
                    val skipper: DocValuesSkipper? = reader.leaves()[0].reader().getDocValuesSkipper("dv")
                    assertNotNull(skipper)
                    skipper.advance(0)
                    assertEquals(0L, skipper.minValue(0))
                    assertEquals(0L, skipper.maxValue(0))
                    assertEquals(numDocs.toLong(), skipper.docCount(0).toLong())
                    skipper.advance(skipper.maxDocID(0) + 1)
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())
                }
            }
        }
    }

    // break on different value
    @Test
    @Throws(IOException::class)
    fun testSkipperFewValuesSorted() {
        val config: IndexWriterConfig = IndexWriterConfig().setCodec(codec)
        val reverse = random().nextBoolean()
        config.setIndexSort(Sort(SortField("dv", SortField.Type.LONG, reverse)))
        newDirectory().use { directory ->
            RandomIndexWriter(random(), directory, config).use { writer ->
                val intervals = random().nextInt(2, 10)
                val numDocs = IntArray(intervals)
                for (i in 0..<intervals) {
                    numDocs[i] = random().nextInt(10) + 16
                    for (j in 0..<numDocs[i]) {
                        val doc = Document()
                        doc.add(NumericDocValuesField.indexedField("dv", i.toLong()))
                        writer.addDocument(doc)
                    }
                }
                writer.forceMerge(1)
                writer.reader.use { reader ->
                    assertEquals(1, reader.leaves().size)
                    val skipper: DocValuesSkipper? = reader.leaves()[0].reader().getDocValuesSkipper("dv")
                    assertNotNull(skipper)
                    assertEquals(numDocs.sum().toLong(), skipper.docCount().toLong())
                    skipper.advance(0)
                    if (reverse) {
                        for (i in intervals - 1 downTo 0) {
                            assertEquals(i.toLong(), skipper.minValue(0))
                            assertEquals(i.toLong(), skipper.maxValue(0))
                            assertEquals(numDocs[i].toLong(), skipper.docCount(0).toLong())
                            skipper.advance(skipper.maxDocID(0) + 1)
                        }
                    } else {
                        for (i in 0..<intervals) {
                            assertEquals(i.toLong(), skipper.minValue(0))
                            assertEquals(i.toLong(), skipper.maxValue(0))
                            assertEquals(numDocs[i].toLong(), skipper.docCount(0).toLong())
                            skipper.advance(skipper.maxDocID(0) + 1)
                        }
                    }
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())
                }
            }
        }
    }

    // break on empty doc values
    @Test
    @Throws(IOException::class)
    fun testSkipperAllEqualValueWithGaps() {
        val config: IndexWriterConfig = IndexWriterConfig().setCodec(codec)
        config.setIndexSort(Sort(SortField("sort", SortField.Type.LONG, false)))
        newDirectory().use { directory ->
            RandomIndexWriter(random(), directory, config).use { writer ->
                val gaps = random().nextInt(2, 10)
                val numDocs = IntArray(gaps)
                var totaldocs = 0L
                for (i in 0..<gaps) {
                    numDocs[i] = random().nextInt(10) + 16
                    for (j in 0..<numDocs[i]) {
                        val doc = Document()
                        doc.add(NumericDocValuesField("sort", totaldocs++))
                        doc.add(SortedNumericDocValuesField.indexedField("dv", 0L))
                        writer.addDocument(doc)
                    }
                    // add doc with empty "dv"
                    val doc = Document()
                    doc.add(NumericDocValuesField("sort", totaldocs++))
                    writer.addDocument(doc)
                }
                writer.forceMerge(1)
                writer.reader.use { reader ->
                    assertEquals(1, reader.leaves().size)
                    val skipper: DocValuesSkipper? = reader.leaves()[0].reader().getDocValuesSkipper("dv")
                    assertNotNull(skipper)
                    assertEquals(numDocs.sum().toLong(), skipper.docCount().toLong())
                    skipper.advance(0)
                    for (i in 0..<gaps) {
                        assertEquals(0L, skipper.minValue(0))
                        assertEquals(0L, skipper.maxValue(0))
                        assertEquals(numDocs[i].toLong(), skipper.docCount(0).toLong())
                        skipper.advance(skipper.maxDocID(0) + 1)
                    }
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())
                }
            }
        }
    }

    // break on multi-values
    @Test
    @Throws(IOException::class)
    fun testSkipperAllEqualValueWithMultiValues() {
        val config: IndexWriterConfig = IndexWriterConfig().setCodec(codec)
        config.setIndexSort(Sort(SortField("sort", SortField.Type.LONG, false)))
        newDirectory().use { directory ->
            RandomIndexWriter(random(), directory, config).use { writer ->
                val gaps = random().nextInt(2, 10)
                val numDocs = IntArray(gaps)
                var totaldocs = 0L
                for (i in 0..<gaps) {
                    val docs = random().nextInt(10) + 16
                    numDocs[i] += docs
                    for (j in 0..<docs) {
                        val doc = Document()
                        doc.add(NumericDocValuesField("sort", totaldocs++))
                        doc.add(SortedNumericDocValuesField.indexedField("dv", 0L))
                        writer.addDocument(doc)
                    }
                    if (i != gaps - 1) {
                        // add doc with mutivalues
                        val doc = Document()
                        doc.add(NumericDocValuesField("sort", totaldocs++))
                        doc.add(SortedNumericDocValuesField.indexedField("dv", 0L))
                        doc.add(SortedNumericDocValuesField.indexedField("dv", 0L))
                        writer.addDocument(doc)
                        numDocs[i + 1] = 1
                    }
                }
                writer.forceMerge(1)
                writer.reader.use { reader ->
                    assertEquals(1, reader.leaves().size)
                    val skipper: DocValuesSkipper? = reader.leaves()[0].reader().getDocValuesSkipper("dv")
                    assertNotNull(skipper)
                    assertEquals(numDocs.sum().toLong(), skipper.docCount().toLong())
                    skipper.advance(0)
                    for (i in 0..<gaps) {
                        assertEquals(0L, skipper.minValue(0))
                        assertEquals(0L, skipper.maxValue(0))
                        assertEquals(numDocs[i].toLong(), skipper.docCount(0).toLong())
                        skipper.advance(skipper.maxDocID(0) + 1)
                    }
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())
                }
            }
        }
    }

    // tests inherited from BaseDocValuesFormatTestCase and LegacyBaseDocValuesFormatTestCase
    @Test
    override fun testBigNumericRange() = super.testBigNumericRange()

    @Test
    override fun testBigNumericRange2() = super.testBigNumericRange2()

    @Test
    override fun testBinaryFixedLengthVsStoredFields() = super.testBinaryFixedLengthVsStoredFields()

    @Test
    override fun testBinaryMergeAwayAllValuesLargeSegment() = super.testBinaryMergeAwayAllValuesLargeSegment()

    @Test
    override fun testBinaryVariableLengthVsStoredFields() = super.testBinaryVariableLengthVsStoredFields()

    @Test
    override fun testBooleanNumericsVsStoredFields() = super.testBooleanNumericsVsStoredFields()

    @Test
    override fun testByteNumericsVsStoredFields() = super.testByteNumericsVsStoredFields()

    @Test
    override fun testBytes() = super.testBytes()

    @Test
    override fun testBytesMergeAwayAllValues() = super.testBytesMergeAwayAllValues()

    @Test
    override fun testBytesTwoDocumentsMerged() = super.testBytesTwoDocumentsMerged()

    @Test
    override fun testBytesWithNewline() = super.testBytesWithNewline()

    @Test
    override fun testCodecUsesOwnBytes() = super.testCodecUsesOwnBytes()

    @Test
    override fun testCodecUsesOwnSortedBytes() = super.testCodecUsesOwnSortedBytes()

    @Test
    override fun testDocValuesSimple() = super.testDocValuesSimple()

    @Test
    override fun testEmptyBinaryValueOnPageSizes() = super.testEmptyBinaryValueOnPageSizes()

    @Test
    override fun testEmptyBytes() = super.testEmptyBytes()

    @Test
    override fun testEmptySortedBytes() = super.testEmptySortedBytes()

    @Test
    override fun testGCDCompression() = super.testGCDCompression()

    @Test
    override fun testHighOrdsSortedSetDV() = super.testHighOrdsSortedSetDV()

    @Test
    override fun testIntNumericsVsStoredFields() = super.testIntNumericsVsStoredFields()

    @Test
    override fun testLongNumericsVsStoredFields() = super.testLongNumericsVsStoredFields()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()

    @Test
    override fun testMissingSortedBytes() = super.testMissingSortedBytes()

    @Test
    override fun testNumberMergeAwayAllValues() = super.testNumberMergeAwayAllValues()

    @Test
    override fun testNumberMergeAwayAllValuesWithSkipper() = super.testNumberMergeAwayAllValuesWithSkipper()

    @Test
    override fun testNumericDocValuesWithSkipperBig() = super.testNumericDocValuesWithSkipperBig()

    @Test
    override fun testNumericDocValuesWithSkipperMedium() = super.testNumericDocValuesWithSkipperMedium()

    @Test
    override fun testNumericDocValuesWithSkipperSmall() = super.testNumericDocValuesWithSkipperSmall()

    @Test
    override fun testNumericMergeAwayAllValuesLargeSegment() = super.testNumericMergeAwayAllValuesLargeSegment()

    @Test
    override fun testNumericMergeAwayAllValuesLargeSegmentWithSkipper() = super.testNumericMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testOneFloat() = super.testOneFloat()

    @Test
    override fun testOneNumber() = super.testOneNumber()

    @Test
    override fun testOneSortedNumber() = super.testOneSortedNumber()

    @Test
    override fun testOneSortedNumberOneMissing() = super.testOneSortedNumberOneMissing()

    @Test
    override fun testRandomAdvanceBinary() = super.testRandomAdvanceBinary()

    @Test
    override fun testRandomAdvanceNumeric() = super.testRandomAdvanceNumeric()

    @Test
    override fun testRandomSortedBytes() = super.testRandomSortedBytes()

    @Test
    override fun testShortNumericsVsStoredFields() = super.testShortNumericsVsStoredFields()

    @Test
    override fun testSortedBytes() = super.testSortedBytes()

    @Test
    override fun testSortedBytesThreeDocuments() = super.testSortedBytesThreeDocuments()

    @Test
    override fun testSortedBytesTwoDocuments() = super.testSortedBytesTwoDocuments()

    @Test
    override fun testSortedBytesTwoDocumentsMerged() = super.testSortedBytesTwoDocumentsMerged()

    @Test
    override fun testSortedDocValuesWithSkipperBig() = super.testSortedDocValuesWithSkipperBig()

    @Test
    override fun testSortedDocValuesWithSkipperMedium() = super.testSortedDocValuesWithSkipperMedium()

    @Test
    override fun testSortedDocValuesWithSkipperSmall() = super.testSortedDocValuesWithSkipperSmall()

    @Test
    override fun testSortedEnumAdvanceIndependently() = super.testSortedEnumAdvanceIndependently()

    @Test
    override fun testSortedFixedLengthVsStoredFields() = super.testSortedFixedLengthVsStoredFields()

    @Test
    override fun testSortedMergeAwayAllValues() = super.testSortedMergeAwayAllValues()

    @Test
    override fun testSortedMergeAwayAllValuesLargeSegment() = super.testSortedMergeAwayAllValuesLargeSegment()

    @Test
    override fun testSortedMergeAwayAllValuesLargeSegmentWithSkipper() = super.testSortedMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testSortedMergeAwayAllValuesWithSkipper() = super.testSortedMergeAwayAllValuesWithSkipper()

    @Test
    override fun testSortedNumberMerge() = super.testSortedNumberMerge()

    @Test
    override fun testSortedNumberMergeAwayAllValues() = super.testSortedNumberMergeAwayAllValues()

    @Test
    override fun testSortedNumberMergeAwayAllValuesWithSkipper() = super.testSortedNumberMergeAwayAllValuesWithSkipper()

    @Test
    override fun testSortedNumericDocValuesWithSkipperBig() = super.testSortedNumericDocValuesWithSkipperBig()

    @Test
    override fun testSortedNumericDocValuesWithSkipperMedium() = super.testSortedNumericDocValuesWithSkipperMedium()

    @Test
    override fun testSortedNumericDocValuesWithSkipperSmall() = super.testSortedNumericDocValuesWithSkipperSmall()

    @Test
    override fun testSortedNumericMergeAwayAllValuesLargeSegment() = super.testSortedNumericMergeAwayAllValuesLargeSegment()

    @Test
    override fun testSortedNumericMergeAwayAllValuesLargeSegmentWithSkipper() = super.testSortedNumericMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testSortedNumericsFewUniqueSetsVsStoredFields() = super.testSortedNumericsFewUniqueSetsVsStoredFields()

    @Test
    override fun testSortedNumericsMultipleValuesVsStoredFields() = super.testSortedNumericsMultipleValuesVsStoredFields()

    @Test
    override fun testSortedNumericsSingleValuedMissingVsStoredFields() = super.testSortedNumericsSingleValuedMissingVsStoredFields()

    @Test
    override fun testSortedNumericsSingleValuedVsStoredFields() = super.testSortedNumericsSingleValuedVsStoredFields()

    @Test
    override fun testSortedSetDocValuesWithSkipperBig() = super.testSortedSetDocValuesWithSkipperBig()

    @Test
    override fun testSortedSetDocValuesWithSkipperMedium() = super.testSortedSetDocValuesWithSkipperMedium()

    @Test
    override fun testSortedSetDocValuesWithSkipperSmall() = super.testSortedSetDocValuesWithSkipperSmall()

    @Test
    override fun testSortedSetEnumAdvanceIndependently() = super.testSortedSetEnumAdvanceIndependently()

    @Test
    override fun testSortedSetFixedLengthFewUniqueSetsVsStoredFields() = super.testSortedSetFixedLengthFewUniqueSetsVsStoredFields()

    @Test
    override fun testSortedSetFixedLengthManyValuesPerDocVsStoredFields() = super.testSortedSetFixedLengthManyValuesPerDocVsStoredFields()

    @Test
    override fun testSortedSetFixedLengthSingleValuedVsStoredFields() = super.testSortedSetFixedLengthSingleValuedVsStoredFields()

    @Test
    override fun testSortedSetFixedLengthVsStoredFields() = super.testSortedSetFixedLengthVsStoredFields()

    @Test
    override fun testSortedSetMergeAwayAllValues() = super.testSortedSetMergeAwayAllValues()

    @Test
    override fun testSortedSetMergeAwayAllValuesLargeSegment() = super.testSortedSetMergeAwayAllValuesLargeSegment()

    @Test
    override fun testSortedSetMergeAwayAllValuesLargeSegmentWithSkipper() = super.testSortedSetMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testSortedSetMergeAwayAllValuesWithSkipper() = super.testSortedSetMergeAwayAllValuesWithSkipper()

    @Test
    override fun testSortedSetOneValue() = super.testSortedSetOneValue()

    @Test
    override fun testSortedSetTermsEnum() = super.testSortedSetTermsEnum()

    @Test
    override fun testSortedSetThreeValuesTwoDocs() = super.testSortedSetThreeValuesTwoDocs()

    @Test
    override fun testSortedSetTwoDocumentsFirstMissing() = super.testSortedSetTwoDocumentsFirstMissing()

    @Test
    override fun testSortedSetTwoDocumentsFirstMissingMerge() = super.testSortedSetTwoDocumentsFirstMissingMerge()

    @Test
    override fun testSortedSetTwoDocumentsLastMissing() = super.testSortedSetTwoDocumentsLastMissing()

    @Test
    override fun testSortedSetTwoDocumentsLastMissingMerge() = super.testSortedSetTwoDocumentsLastMissingMerge()

    @Test
    override fun testSortedSetTwoDocumentsMerged() = super.testSortedSetTwoDocumentsMerged()

    @Test
    override fun testSortedSetTwoFields() = super.testSortedSetTwoFields()

    @Test
    override fun testSortedSetTwoValues() = super.testSortedSetTwoValues()

    @Test
    override fun testSortedSetTwoValuesUnordered() = super.testSortedSetTwoValuesUnordered()

    @Test
    override fun testSortedSetVariableLengthFewUniqueSetsVsStoredFields() = super.testSortedSetVariableLengthFewUniqueSetsVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthManyValuesPerDocVsStoredFields() = super.testSortedSetVariableLengthManyValuesPerDocVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthSingleValuedVsStoredFields() = super.testSortedSetVariableLengthSingleValuedVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthVsStoredFields() = super.testSortedSetVariableLengthVsStoredFields()

    @Test
    override fun testSortedTermsEnum() = super.testSortedTermsEnum()

    @Test
    override fun testSortedVariableLengthVsStoredFields() = super.testSortedVariableLengthVsStoredFields()

    @Test
    override fun testSparseBinaryFixedLengthVsStoredFields() = super.testSparseBinaryFixedLengthVsStoredFields()

    @Test
    override fun testSparseBinaryVariableLengthVsStoredFields() = super.testSparseBinaryVariableLengthVsStoredFields()

    @Test
    override fun testSparseBooleanNumericsVsStoredFields() = super.testSparseBooleanNumericsVsStoredFields()

    @Test
    override fun testSparseByteNumericsVsStoredFields() = super.testSparseByteNumericsVsStoredFields()

    @Test
    override fun testSparseGCDCompression() = super.testSparseGCDCompression()

    @Test
    override fun testSparseIntNumericsVsStoredFields() = super.testSparseIntNumericsVsStoredFields()

    @Test
    override fun testSparseLongNumericsVsStoredFields() = super.testSparseLongNumericsVsStoredFields()

    @Test
    override fun testSparseShortNumericsVsStoredFields() = super.testSparseShortNumericsVsStoredFields()

    @Test
    override fun testSparseSortedFixedLengthVsStoredFields() = super.testSparseSortedFixedLengthVsStoredFields()

    @Test
    override fun testSparseSortedVariableLengthVsStoredFields() = super.testSparseSortedVariableLengthVsStoredFields()

    @Test
    override fun testSparseZeros() = super.testSparseZeros()

    @Test
    override fun testThreads() = super.testThreads()

    @Test
    override fun testThreads2() = super.testThreads2()

    @Test
    override fun testThreads3() = super.testThreads3()

    @Test
    override fun testThreeBytesOneMissingWithMerging() = super.testThreeBytesOneMissingWithMerging()

    @Test
    override fun testThreeFieldsMixed() = super.testThreeFieldsMixed()

    @Test
    override fun testThreeFieldsMixed2() = super.testThreeFieldsMixed2()

    @Test
    override fun testThreeNumbersOneMissingWithMerging() = super.testThreeNumbersOneMissingWithMerging()

    @Test
    override fun testTwoBinaryValues() = super.testTwoBinaryValues()

    @Test
    override fun testTwoBytesOneMissing() = super.testTwoBytesOneMissing()

    @Test
    override fun testTwoBytesOneMissingWithMerging() = super.testTwoBytesOneMissingWithMerging()

    @Test
    override fun testTwoDocumentsMerged() = super.testTwoDocumentsMerged()

    @Test
    override fun testTwoDocumentsNumeric() = super.testTwoDocumentsNumeric()

    @Test
    override fun testTwoFieldsMixed() = super.testTwoFieldsMixed()

    @Test
    override fun testTwoNumbers() = super.testTwoNumbers()

    @Test
    override fun testTwoNumbersOneMissing() = super.testTwoNumbersOneMissing()

    @Test
    override fun testTwoNumbersOneMissingWithMerging() = super.testTwoNumbersOneMissingWithMerging()

    @Test
    override fun testTwoSortedNumber() = super.testTwoSortedNumber()

    @Test
    override fun testTwoSortedNumberOneMissing() = super.testTwoSortedNumberOneMissing()

    @Test
    override fun testTwoSortedNumberSameValue() = super.testTwoSortedNumberSameValue()

    @Test
    override fun testVariouslyCompressibleBinaryValues() = super.testVariouslyCompressibleBinaryValues()

    @Test
    override fun testVeryLargeButLegalBytes() = super.testVeryLargeButLegalBytes()

    @Test
    override fun testVeryLargeButLegalSortedBytes() = super.testVeryLargeButLegalSortedBytes()

    @Test
    override fun testZeroOrMin() = super.testZeroOrMin()

    @Test
    override fun testZeros() = super.testZeros()

}
