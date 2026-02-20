package org.gnit.lucenekmp.codecs.lucene90

import kotlin.test.Test

/** Test the merge instance of the Lucene90 stored fields format. */
class TestLucene90StoredFieldsFormatMergeInstance : TestLucene90StoredFieldsFormat() {
    override fun shouldTestMergeInstance(): Boolean {
        return true
    }

    // tests inherited from TestLucene90StoredFieldsFormat
    @Test
    override fun testSkipRedundantPrefetches() = super.testSkipRedundantPrefetches()

    @Test
    override fun testRandomStoredFields() = super.testRandomStoredFields()

    @Test
    override fun testStoredFieldsOrder() = super.testStoredFieldsOrder()

    @Test
    override fun testBinaryFieldOffsetLength() = super.testBinaryFieldOffsetLength()

    @Test
    override fun testNumericField() = super.testNumericField()

    @Test
    override fun testIndexedBit() = super.testIndexedBit()

    @Test
    override fun testReadSkip() = super.testReadSkip()

    @Test
    override fun testEmptyDocs() = super.testEmptyDocs()

    @Test
    override fun testConcurrentReads() = super.testConcurrentReads()

    @Test
    override fun testWriteReadMerge() = super.testWriteReadMerge()

    @Test
    override fun testMergeFilterReader() = super.testMergeFilterReader()

    @Test
    override fun testBigDocuments() = super.testBigDocuments()

    @Test
    override fun testBulkMergeWithDeletes() = super.testBulkMergeWithDeletes()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()

    @Test
    override fun testRandomStoredFieldsWithIndexSort() = super.testRandomStoredFieldsWithIndexSort()

    @Test
    override fun testLineFileDocs() = super.testLineFileDocs()

}
