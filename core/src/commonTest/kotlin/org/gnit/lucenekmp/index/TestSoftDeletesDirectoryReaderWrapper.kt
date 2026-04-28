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

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestSoftDeletesDirectoryReaderWrapper : LuceneTestCase() {

    @Test
    fun testDropFullyDeletedSegments() {
        val indexWriterConfig = newIndexWriterConfig()
        val softDeletesField = "soft_delete"
        indexWriterConfig.setSoftDeletesField(softDeletesField)
        indexWriterConfig.setMergePolicy(
            SoftDeletesRetentionMergePolicy(
                softDeletesField,
                { MatchAllDocsQuery() },
                NoMergePolicy.INSTANCE
            )
        )
        newDirectory().use { dir ->
            IndexWriter(dir, indexWriterConfig).use { writer ->
                var doc = Document()
                doc.add(StringField("id", "1", Field.Store.YES))
                doc.add(StringField("version", "1", Field.Store.YES))
                writer.addDocument(doc)
                writer.commit()
                doc = Document()
                doc.add(StringField("id", "2", Field.Store.YES))
                doc.add(StringField("version", "1", Field.Store.YES))
                writer.addDocument(doc)
                writer.commit()

                SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), softDeletesField).use { reader ->
                    assertEquals(2, reader.leaves().size)
                    assertEquals(2, reader.numDocs())
                    assertEquals(2, reader.maxDoc())
                    assertEquals(0, reader.numDeletedDocs())
                }
                writer.updateDocValues(Term("id", "1"), NumericDocValuesField(softDeletesField, 1))
                writer.commit()
                SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(writer), softDeletesField).use { reader ->
                    assertEquals(1, reader.numDocs())
                    assertEquals(1, reader.maxDoc())
                    assertEquals(0, reader.numDeletedDocs())
                    assertEquals(1, reader.leaves().size)
                }
                SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), softDeletesField).use { reader ->
                    assertEquals(1, reader.numDocs())
                    assertEquals(1, reader.maxDoc())
                    assertEquals(0, reader.numDeletedDocs())
                    assertEquals(1, reader.leaves().size)
                }

                DirectoryReader.open(dir).use { reader ->
                    assertEquals(2, reader.numDocs())
                    assertEquals(2, reader.maxDoc())
                    assertEquals(0, reader.numDeletedDocs())
                    assertEquals(2, reader.leaves().size)
                }
            }
        }
    }

    @Test
    fun testReuseUnchangedLeafReader() {
        val dir = newDirectory()
        val indexWriterConfig = newIndexWriterConfig()
        val softDeletesField = "soft_delete"
        indexWriterConfig.setSoftDeletesField(softDeletesField)
        indexWriterConfig.setMergePolicy(NoMergePolicy.INSTANCE)
        val writer = IndexWriter(dir, indexWriterConfig)

        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        doc.add(StringField("version", "1", Field.Store.YES))
        writer.addDocument(doc)
        writer.commit()
        var reader: DirectoryReader =
            SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), softDeletesField)
        assertEquals(2, reader.numDocs())
        assertEquals(2, reader.maxDoc())
        assertEquals(0, reader.numDeletedDocs())

        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "2", Field.Store.YES))
        writer.softUpdateDocument(
            Term("id", "1"),
            doc,
            NumericDocValuesField("soft_delete", 1)
        )

        doc = Document()
        doc.add(StringField("id", "3", Field.Store.YES))
        doc.add(StringField("version", "1", Field.Store.YES))
        writer.addDocument(doc)
        writer.commit()

        var newReader = DirectoryReader.openIfChanged(reader)
        assertNotNull(newReader)
        assertNotSame(newReader, reader)
        reader.close()
        reader = newReader
        assertEquals(3, reader.numDocs())
        assertEquals(4, reader.maxDoc())
        assertEquals(1, reader.numDeletedDocs())

        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "3", Field.Store.YES))
        writer.softUpdateDocument(
            Term("id", "1"),
            doc,
            NumericDocValuesField("soft_delete", 1)
        )
        writer.commit()

        newReader = DirectoryReader.openIfChanged(reader)
        assertNotNull(newReader)
        assertNotSame(newReader, reader)
        assertEquals(3, newReader.sequentialSubReaders.size)
        assertEquals(2, reader.sequentialSubReaders.size)
        assertSame(reader.sequentialSubReaders[0], newReader.sequentialSubReaders[0])
        assertNotSame(reader.sequentialSubReaders[1], newReader.sequentialSubReaders[1])
        assertTrue(isWrapped(reader.sequentialSubReaders[0]))
        // last one has no soft deletes
        assertFalse(isWrapped(reader.sequentialSubReaders[1]))

        assertTrue(isWrapped(newReader.sequentialSubReaders[0]))
        assertTrue(isWrapped(newReader.sequentialSubReaders[1]))
        // last one has no soft deletes
        assertFalse(isWrapped(newReader.sequentialSubReaders[2]))
        reader.close()
        reader = newReader
        assertEquals(3, reader.numDocs())
        assertEquals(5, reader.maxDoc())
        assertEquals(2, reader.numDeletedDocs())
        IOUtils.close(reader, writer, dir)
    }

    private fun isWrapped(reader: LeafReader): Boolean {
        return reader is FilterLeafReader || reader is FilterCodecReader
    }

    @Test
    fun testMixSoftAndHardDeletes() {
        val dir = newDirectory()
        val indexWriterConfig = newIndexWriterConfig()
        val softDeletesField = "soft_delete"
        indexWriterConfig.setSoftDeletesField(softDeletesField)
        val writer = IndexWriter(dir, indexWriterConfig)
        val uniqueDocs = HashSet<Int>()
        for (i in 0..<100) {
            val docId = random().nextInt(5)
            uniqueDocs.add(docId)
            val doc = Document()
            doc.add(StringField("id", docId.toString(), Field.Store.YES))
            if (docId % 2 == 0) {
                writer.updateDocument(Term("id", docId.toString()), doc)
            } else {
                writer.softUpdateDocument(
                    Term("id", docId.toString()),
                    doc,
                    NumericDocValuesField(softDeletesField, 0)
                )
            }
        }

        writer.commit()
        writer.close()
        val reader = SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), softDeletesField)
        assertEquals(uniqueDocs.size, reader.numDocs())
        val searcher = IndexSearcher(reader)
        for (docId in uniqueDocs) {
            assertEquals(1, searcher.count(TermQuery(Term("id", docId.toString()))))
        }

        IOUtils.close(reader, dir)
    }

    @Test
    fun testReaderCacheKey() {
        val dir = newDirectory()
        val indexWriterConfig = newIndexWriterConfig()
        val softDeletesField = "soft_delete"
        indexWriterConfig.setSoftDeletesField(softDeletesField)
        indexWriterConfig.setMergePolicy(NoMergePolicy.INSTANCE)
        val writer = IndexWriter(dir, indexWriterConfig)

        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        doc.add(StringField("version", "1", Field.Store.YES))
        writer.addDocument(doc)
        writer.commit()
        var reader: DirectoryReader =
            SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), softDeletesField)
        val readerCacheHelper = checkNotNull(reader.leaves()[0].reader().readerCacheHelper)
        val dirReaderCacheHelper = checkNotNull(reader.readerCacheHelper)
        var leafCalled = 0
        var dirCalled = 0
        runBlocking {
            readerCacheHelper.addClosedListener { key ->
                leafCalled++
                assertSame(key, readerCacheHelper.key)
            }
            dirReaderCacheHelper.addClosedListener { key ->
                dirCalled++
                assertSame(key, dirReaderCacheHelper.key)
            }
        }
        assertEquals(2, reader.numDocs())
        assertEquals(2, reader.maxDoc())
        assertEquals(0, reader.numDeletedDocs())

        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "2", Field.Store.YES))
        writer.softUpdateDocument(
            Term("id", "1"),
            doc,
            NumericDocValuesField("soft_delete", 1)
        )

        doc = Document()
        doc.add(StringField("id", "3", Field.Store.YES))
        doc.add(StringField("version", "1", Field.Store.YES))
        writer.addDocument(doc)
        writer.commit()
        assertEquals(0, leafCalled)
        assertEquals(0, dirCalled)
        val newReader = DirectoryReader.openIfChanged(reader)
        assertNotNull(newReader)
        assertEquals(0, leafCalled)
        assertEquals(0, dirCalled)
        assertNotSame(newReader.readerCacheHelper!!.key, reader.readerCacheHelper!!.key)
        assertNotSame(newReader, reader)
        reader.close()
        reader = newReader
        assertEquals(1, dirCalled)
        assertEquals(1, leafCalled)
        IOUtils.close(reader, writer, dir)
    }

    @Test
    fun testAvoidWrappingReadersWithoutSoftDeletes() {
        val iwc = newIndexWriterConfig()
        val softDeletesField = "soft_deletes"
        iwc.setSoftDeletesField(softDeletesField)
        val mergePolicy = iwc.mergePolicy
        iwc.setMergePolicy(
            SoftDeletesRetentionMergePolicy(softDeletesField, { MatchAllDocsQuery() }, mergePolicy)
        )
        newDirectory().use { dir ->
            IndexWriter(dir, iwc).use { writer ->
                val numDocs = 1 + random().nextInt(10)
                for (i in 0..<numDocs) {
                    val doc = Document()
                    val docId = i.toString()
                    doc.add(StringField("id", docId, Field.Store.YES))
                    writer.addDocument(doc)
                }
                val numDeletes = 1 + random().nextInt(5)
                for (i in 0..<numDeletes) {
                    val doc = Document()
                    val docId = random().nextInt(numDocs).toString()
                    doc.add(StringField("id", docId, Field.Store.YES))
                    writer.softUpdateDocument(
                        Term("id", docId),
                        doc,
                        NumericDocValuesField(softDeletesField, 0)
                    )
                }
                writer.flush()
                DirectoryReader.open(writer).use { reader ->
                    val wrapped = SoftDeletesDirectoryReaderWrapper(reader, softDeletesField)
                    var expectedNumDeletes = 0
                    for (i in 0..<wrapped.leaves().size) {
                        expectedNumDeletes += wrapped.leaves()[i].reader().numDeletedDocs()
                    }
                    assertEquals(numDocs, wrapped.numDocs())
                    assertEquals(expectedNumDeletes, wrapped.numDeletedDocs())
                    wrapped.close()
                }
                writer.config.setMergePolicy(
                    SoftDeletesRetentionMergePolicy(softDeletesField, { MatchNoDocsQuery() }, mergePolicy)
                )
                writer.forceMerge(1)
                DirectoryReader.open(writer).use { reader ->
                    for (leafContext in reader.leaves()) {
                        assertTrue(leafContext.reader() is SegmentReader)
                        val segmentReader = leafContext.reader() as SegmentReader
                        assertNull(segmentReader.liveDocs)
                        assertNull(segmentReader.hardLiveDocs)
                    }
                    val wrapped = SoftDeletesDirectoryReaderWrapper(reader, softDeletesField)
                    assertEquals(numDocs, wrapped.numDocs())
                    assertEquals(0, wrapped.numDeletedDocs())
                    for (leaf in wrapped.leaves()) {
                        assertTrue(leaf.reader() is SegmentReader)
                    }
                    wrapped.close()
                }
            }
        }
    }
}

