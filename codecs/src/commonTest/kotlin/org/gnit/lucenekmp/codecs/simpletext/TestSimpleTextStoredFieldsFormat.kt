package org.gnit.lucenekmp.codecs.simpletext

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BaseStoredFieldsFormatTestCase
import kotlin.test.Test

class TestSimpleTextStoredFieldsFormat : BaseStoredFieldsFormatTestCase() {
    override val codec: Codec
        get() = SimpleTextCodec()

    // tests inherited from BaseStoredFieldsFormatTestCase

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

}
