/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestPendingSoftDeletes : TestPendingDeletes() {

    override fun newPendingDeletes(commitInfo: SegmentCommitInfo): PendingDeletes {
        return PendingSoftDeletes("_soft_deletes", commitInfo)
    }

    @Test
    fun testHardDeleteSoftDeleted() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig()
                    .setSoftDeletesField("_soft_deletes")
                    // make sure all docs will end up in the same segment
                    .setMaxBufferedDocs(10)
                    .setMergePolicy(NoMergePolicy.INSTANCE)
                    .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
            )
        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("_soft_deletes", 1))
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "2"), doc, NumericDocValuesField("_soft_deletes", 1))
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "2"), doc, NumericDocValuesField("_soft_deletes", 1))
        writer.commit()
        val reader = DirectoryReader.open(dir)
        assertEquals(1, reader.leaves().size)
        val segmentReader = reader.leaves()[0].reader() as SegmentReader
        val segmentInfo = segmentReader.segmentInfo
        val pendingSoftDeletes = newPendingDeletes(segmentInfo)
        pendingSoftDeletes.onNewReader(segmentReader, segmentInfo)
        assertEquals(0, pendingSoftDeletes.numPendingDeletes())
        assertEquals(1, pendingSoftDeletes.delCount)
        assertTrue(checkNotNull(pendingSoftDeletes.liveDocs).get(0))
        assertFalse(checkNotNull(pendingSoftDeletes.liveDocs).get(1))
        assertTrue(checkNotNull(pendingSoftDeletes.liveDocs).get(2))
        assertNull(pendingSoftDeletes.hardLiveDocs)
        assertTrue(pendingSoftDeletes.delete(1))
        assertEquals(0, pendingSoftDeletes.numPendingDeletes())
        assertEquals(-1, pendingSoftDeletes.pendingDeleteCount) // transferred the delete
        assertEquals(1, pendingSoftDeletes.delCount)
        IOUtils.close(reader, writer, dir)
    }

    @Test
    fun testDeleteSoft() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig()
                    .setSoftDeletesField("_soft_deletes")
                    // make sure all docs will end up in the same segment
                    .setMaxBufferedDocs(10)
                    .setMergePolicy(NoMergePolicy.INSTANCE)
                    .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
            )
        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("_soft_deletes", 1))
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "2"), doc, NumericDocValuesField("_soft_deletes", 1))
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "2"), doc, NumericDocValuesField("_soft_deletes", 1))
        writer.commit()
        var reader = DirectoryReader.open(dir)
        assertEquals(1, reader.leaves().size)
        var segmentReader = reader.leaves()[0].reader() as SegmentReader
        var segmentInfo = segmentReader.segmentInfo
        var pendingSoftDeletes = newPendingDeletes(segmentInfo)
        pendingSoftDeletes.onNewReader(segmentReader, segmentInfo)
        assertEquals(0, pendingSoftDeletes.numPendingDeletes())
        assertEquals(1, pendingSoftDeletes.delCount)
        assertTrue(checkNotNull(pendingSoftDeletes.liveDocs).get(0))
        assertFalse(checkNotNull(pendingSoftDeletes.liveDocs).get(1))
        assertTrue(checkNotNull(pendingSoftDeletes.liveDocs).get(2))
        assertNull(pendingSoftDeletes.hardLiveDocs)
        // pass reader again
        val liveDocs = pendingSoftDeletes.liveDocs
        pendingSoftDeletes.onNewReader(segmentReader, segmentInfo)
        assertEquals(0, pendingSoftDeletes.numPendingDeletes())
        assertEquals(1, pendingSoftDeletes.delCount)
        assertSame(liveDocs, pendingSoftDeletes.liveDocs)

        // now apply a hard delete
        writer.deleteDocuments(Term("id", "1"))
        writer.commit()
        IOUtils.close(reader)
        reader = DirectoryReader.open(dir)
        assertEquals(1, reader.leaves().size)
        segmentReader = reader.leaves()[0].reader() as SegmentReader
        segmentInfo = segmentReader.segmentInfo
        pendingSoftDeletes = newPendingDeletes(segmentInfo)
        pendingSoftDeletes.onNewReader(segmentReader, segmentInfo)
        assertEquals(0, pendingSoftDeletes.numPendingDeletes())
        assertEquals(2, pendingSoftDeletes.delCount)
        assertFalse(checkNotNull(pendingSoftDeletes.liveDocs).get(0))
        assertFalse(checkNotNull(pendingSoftDeletes.liveDocs).get(1))
        assertTrue(checkNotNull(pendingSoftDeletes.liveDocs).get(2))
        assertNotNull(pendingSoftDeletes.hardLiveDocs)
        assertFalse(checkNotNull(pendingSoftDeletes.hardLiveDocs).get(0))
        assertTrue(checkNotNull(pendingSoftDeletes.hardLiveDocs).get(1))
        assertTrue(checkNotNull(pendingSoftDeletes.hardLiveDocs).get(2))
        IOUtils.close(reader, writer, dir)
    }

    @Test
    fun testApplyUpdates() {
        val dir = ByteBuffersDirectory()
        val si =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "test",
                10,
                false,
                false,
                Codec.default,
                mutableMapOf(),
                StringHelper.randomId(),
                HashMap(),
                null
            )
        val commitInfo = SegmentCommitInfo(si, 0, 0, -1, -1, -1, StringHelper.randomId())
        val writer = IndexWriter(dir, newIndexWriterConfig())
        for (i in 0..<si.maxDoc()) {
            writer.addDocument(Document())
        }
        writer.forceMerge(1)
        writer.commit()
        val reader = DirectoryReader.open(writer)
        assertEquals(1, reader.leaves().size)
        val segmentReader = reader.leaves()[0].reader() as SegmentReader
        val deletes = newPendingDeletes(commitInfo)
        deletes.onNewReader(segmentReader, commitInfo)
        reader.close()
        writer.close()
        var fieldInfo =
            FieldInfo(
                "_soft_deletes",
                1,
                false,
                false,
                false,
                IndexOptions.NONE,
                DocValuesType.NUMERIC,
                DocValuesSkipIndexType.NONE,
                0,
                emptyMap(),
                0,
                0,
                0,
                0,
                VectorEncoding.FLOAT32,
                VectorSimilarityFunction.EUCLIDEAN,
                true,
                false
            )
        var docsDeleted = listOf(1, 3, 7, 8, DocIdSetIterator.NO_MORE_DOCS)
        var updates = listOf(singleUpdate(docsDeleted, 10, true))
        for (update in updates) {
            deletes.onDocValuesUpdate(fieldInfo, update.iterator())
        }
        assertEquals(0, deletes.numPendingDeletes())
        assertEquals(4, deletes.delCount)
        assertTrue(checkNotNull(deletes.liveDocs).get(0))
        assertFalse(checkNotNull(deletes.liveDocs).get(1))
        assertTrue(checkNotNull(deletes.liveDocs).get(2))
        assertFalse(checkNotNull(deletes.liveDocs).get(3))
        assertTrue(checkNotNull(deletes.liveDocs).get(4))
        assertTrue(checkNotNull(deletes.liveDocs).get(5))
        assertTrue(checkNotNull(deletes.liveDocs).get(6))
        assertFalse(checkNotNull(deletes.liveDocs).get(7))
        assertFalse(checkNotNull(deletes.liveDocs).get(8))
        assertTrue(checkNotNull(deletes.liveDocs).get(9))

        docsDeleted = listOf(1, 2, DocIdSetIterator.NO_MORE_DOCS)
        updates = listOf(singleUpdate(docsDeleted, 10, true))
        fieldInfo =
            FieldInfo(
                "_soft_deletes",
                1,
                false,
                false,
                false,
                IndexOptions.NONE,
                DocValuesType.NUMERIC,
                DocValuesSkipIndexType.NONE,
                1,
                emptyMap(),
                0,
                0,
                0,
                0,
                VectorEncoding.FLOAT32,
                VectorSimilarityFunction.EUCLIDEAN,
                true,
                false
            )
        for (update in updates) {
            deletes.onDocValuesUpdate(fieldInfo, update.iterator())
        }
        assertEquals(0, deletes.numPendingDeletes())
        assertEquals(5, deletes.delCount)
        assertTrue(checkNotNull(deletes.liveDocs).get(0))
        assertFalse(checkNotNull(deletes.liveDocs).get(1))
        assertFalse(checkNotNull(deletes.liveDocs).get(2))
        assertFalse(checkNotNull(deletes.liveDocs).get(3))
        assertTrue(checkNotNull(deletes.liveDocs).get(4))
        assertTrue(checkNotNull(deletes.liveDocs).get(5))
        assertTrue(checkNotNull(deletes.liveDocs).get(6))
        assertFalse(checkNotNull(deletes.liveDocs).get(7))
        assertFalse(checkNotNull(deletes.liveDocs).get(8))
        assertTrue(checkNotNull(deletes.liveDocs).get(9))
    }

    @Test
    fun testUpdateAppliedOnlyOnce() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig()
                    .setSoftDeletesField("_soft_deletes")
                    .setMaxBufferedDocs(3) // make sure we write one segment
                    .setMergePolicy(NoMergePolicy.INSTANCE) // prevent deletes from triggering merges
                    .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
            )
        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("_soft_deletes", 1))
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "2"), doc, NumericDocValuesField("_soft_deletes", 1))
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "2"), doc, NumericDocValuesField("_soft_deletes", 1))
        writer.commit()
        val reader = DirectoryReader.open(dir)
        assertEquals(1, reader.leaves().size)
        val segmentReader = reader.leaves()[0].reader() as SegmentReader
        val segmentInfo = segmentReader.segmentInfo
        val deletes: PendingDeletes = newPendingDeletes(segmentInfo)
        deletes.onNewReader(segmentReader, segmentInfo)
        val fieldInfo =
            FieldInfo(
                "_soft_deletes",
                1,
                false,
                false,
                false,
                IndexOptions.NONE,
                DocValuesType.NUMERIC,
                DocValuesSkipIndexType.NONE,
                segmentInfo.getNextWriteDocValuesGen(),
                emptyMap(),
                0,
                0,
                0,
                0,
                VectorEncoding.FLOAT32,
                VectorSimilarityFunction.EUCLIDEAN,
                true,
                false
            )
        val docsDeleted = listOf(1, DocIdSetIterator.NO_MORE_DOCS)
        val updates = listOf(singleUpdate(docsDeleted, 3, true))
        for (update in updates) {
            deletes.onDocValuesUpdate(fieldInfo, update.iterator())
        }
        assertEquals(0, deletes.numPendingDeletes())
        assertEquals(1, deletes.delCount)
        assertTrue(checkNotNull(deletes.liveDocs).get(0))
        assertFalse(checkNotNull(deletes.liveDocs).get(1))
        assertTrue(checkNotNull(deletes.liveDocs).get(2))
        val liveDocs = deletes.liveDocs
        deletes.onNewReader(segmentReader, segmentInfo)
        // no changes we don't apply updates twice
        assertSame(liveDocs, deletes.liveDocs)
        assertTrue(checkNotNull(deletes.liveDocs).get(0))
        assertFalse(checkNotNull(deletes.liveDocs).get(1))
        assertTrue(checkNotNull(deletes.liveDocs).get(2))
        assertEquals(0, deletes.numPendingDeletes())
        assertEquals(1, deletes.delCount)
        IOUtils.close(reader, writer, dir)
    }

    @Test
    fun testResetOnUpdate() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig()
                    .setSoftDeletesField("_soft_deletes")
                    .setMaxBufferedDocs(3) // make sure we write one segment
                    .setMergePolicy(NoMergePolicy.INSTANCE) // prevent deletes from triggering merges
                    .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
            )
        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("_soft_deletes", 1))
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "2"), doc, NumericDocValuesField("_soft_deletes", 1))
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "2"), doc, NumericDocValuesField("_soft_deletes", 1))
        writer.commit()
        val reader = DirectoryReader.open(dir)
        assertEquals(1, reader.leaves().size)
        val segmentReader = reader.leaves()[0].reader() as SegmentReader
        val segmentInfo = segmentReader.segmentInfo
        val deletes: PendingDeletes = newPendingDeletes(segmentInfo)
        deletes.onNewReader(segmentReader, segmentInfo)
        var fieldInfo =
            FieldInfo(
                "_soft_deletes",
                1,
                false,
                false,
                false,
                IndexOptions.NONE,
                DocValuesType.NUMERIC,
                DocValuesSkipIndexType.NONE,
                segmentInfo.getNextWriteDocValuesGen(),
                emptyMap(),
                0,
                0,
                0,
                0,
                VectorEncoding.FLOAT32,
                VectorSimilarityFunction.EUCLIDEAN,
                true,
                false
            )
        var updates =
            listOf(singleUpdate(listOf(0, 1, DocIdSetIterator.NO_MORE_DOCS), 3, false))
        for (update in updates) {
            deletes.onDocValuesUpdate(fieldInfo, update.iterator())
        }
        assertEquals(0, deletes.numPendingDeletes())
        assertTrue(checkNotNull(deletes.liveDocs).get(0))
        assertTrue(checkNotNull(deletes.liveDocs).get(1))
        assertTrue(checkNotNull(deletes.liveDocs).get(2))
        val liveDocs = deletes.liveDocs
        deletes.onNewReader(segmentReader, segmentInfo)
        // no changes we keep this update
        assertSame(liveDocs, deletes.liveDocs)
        assertTrue(checkNotNull(deletes.liveDocs).get(0))
        assertTrue(checkNotNull(deletes.liveDocs).get(1))
        assertTrue(checkNotNull(deletes.liveDocs).get(2))
        assertEquals(0, deletes.numPendingDeletes())

        segmentInfo.advanceDocValuesGen()
        fieldInfo =
            FieldInfo(
                "_soft_deletes",
                1,
                false,
                false,
                false,
                IndexOptions.NONE,
                DocValuesType.NUMERIC,
                DocValuesSkipIndexType.NONE,
                segmentInfo.getNextWriteDocValuesGen(),
                emptyMap(),
                0,
                0,
                0,
                0,
                VectorEncoding.FLOAT32,
                VectorSimilarityFunction.EUCLIDEAN,
                true,
                false
            )
        updates = listOf(singleUpdate(listOf(1, DocIdSetIterator.NO_MORE_DOCS), 3, true))
        for (update in updates) {
            deletes.onDocValuesUpdate(fieldInfo, update.iterator())
        }
        // no changes we keep this update
        assertNotSame(liveDocs, deletes.liveDocs)
        assertTrue(checkNotNull(deletes.liveDocs).get(0))
        assertFalse(checkNotNull(deletes.liveDocs).get(1))
        assertTrue(checkNotNull(deletes.liveDocs).get(2))
        assertEquals(0, deletes.numPendingDeletes())
        assertEquals(1, deletes.delCount)
        IOUtils.close(reader, writer, dir)
    }

    private fun singleUpdate(
        docsChanged: List<Int>,
        maxDoc: Int,
        hasValue: Boolean
    ): DocValuesFieldUpdates {
        return object : DocValuesFieldUpdates(maxDoc, 0, "_soft_deletes", DocValuesType.NUMERIC) {
            override fun add(doc: Int, value: Long) {
                throw UnsupportedOperationException()
            }

            override fun add(doc: Int, value: BytesRef) {
                throw UnsupportedOperationException()
            }

            override fun add(docId: Int, iterator: Iterator) {
                throw UnsupportedOperationException()
            }

            override fun iterator(): Iterator {
                return object : Iterator() {
                    private val iter = docsChanged.iterator()
                    private var doc = -1

                    override fun nextDoc(): Int {
                        doc = iter.next()
                        return doc
                    }

                    override fun longValue(): Long {
                        return 1
                    }

                    override fun binaryValue(): BytesRef {
                        throw UnsupportedOperationException()
                    }

                    override fun docID(): Int {
                        return doc
                    }

                    override fun delGen(): Long {
                        return 0
                    }

                    override fun hasValue(): Boolean {
                        return hasValue
                    }
                }
            }
        }
    }

    // tests inherited from TestPendingDeletes

    @Test
    override fun testDeleteDoc() = super.testDeleteDoc()

    @Test
    override fun testWriteLiveDocs() = super.testWriteLiveDocs()

    @Test
    override fun testIsFullyDeleted() = super.testIsFullyDeleted()
}
