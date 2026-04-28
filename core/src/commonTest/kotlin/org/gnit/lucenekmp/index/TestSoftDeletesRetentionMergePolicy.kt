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

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Collections
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.search.FieldExistsQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.SearcherFactory
import org.gnit.lucenekmp.search.SearcherManager
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestSoftDeletesRetentionMergePolicy : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testForceMergeFullyDeleted() {
        val dir = newDirectory()
        val letItGo = AtomicBoolean(false)
        val policy: MergePolicy =
            SoftDeletesRetentionMergePolicy(
                "soft_delete",
                { if (letItGo.load()) MatchNoDocsQuery() else MatchAllDocsQuery() },
                LogDocMergePolicy()
            )
        val indexWriterConfig =
            newIndexWriterConfig().setMergePolicy(policy).setSoftDeletesField("soft_delete")
        val writer = IndexWriter(dir, indexWriterConfig)

        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(NumericDocValuesField("soft_delete", 1))
        writer.addDocument(doc)
        writer.commit()
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        doc.add(NumericDocValuesField("soft_delete", 1))
        writer.addDocument(doc)
        var reader = DirectoryReader.open(writer)
        run {
            assertEquals(2, reader.leaves().size)
            val segmentReader = reader.leaves()[0].reader() as SegmentReader
            assertTrue(policy.keepFullyDeletedSegment { segmentReader })
            assertEquals(0, policy.numDeletesToMerge(segmentReader.segmentInfo, 0) { segmentReader })
        }
        run {
            val segmentReader = reader.leaves()[1].reader() as SegmentReader
            assertTrue(policy.keepFullyDeletedSegment { segmentReader })
            assertEquals(0, policy.numDeletesToMerge(segmentReader.segmentInfo, 0) { segmentReader })
            writer.forceMerge(1)
            reader.close()
        }
        reader = DirectoryReader.open(writer)
        run {
            assertEquals(1, reader.leaves().size)
            val segmentReader = reader.leaves()[0].reader() as SegmentReader
            assertEquals(2, reader.maxDoc())
            assertTrue(policy.keepFullyDeletedSegment { segmentReader })
            assertEquals(0, policy.numDeletesToMerge(segmentReader.segmentInfo, 0) { segmentReader })
        }
        writer.forceMerge(1) // make sure we don't merge this
        assertNull(DirectoryReader.openIfChanged(reader))

        writer.forceMergeDeletes() // make sure we don't merge this
        assertNull(DirectoryReader.openIfChanged(reader))
        letItGo.store(true)
        writer.forceMergeDeletes() // make sure we don't merge this
        val directoryReader = DirectoryReader.openIfChanged(reader)
        assertNotNull(directoryReader)
        assertEquals(0, directoryReader.numDeletedDocs())
        assertEquals(0, directoryReader.maxDoc())
        IOUtils.close(directoryReader, reader, writer, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testKeepFullyDeletedSegments() {
        val dir = newDirectory()
        val indexWriterConfig = newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
        val writer = IndexWriter(dir, indexWriterConfig)

        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(NumericDocValuesField("soft_delete", 1))
        writer.addDocument(doc)
        val reader = DirectoryReader.open(writer)
        assertEquals(1, reader.leaves().size)
        val policy: MergePolicy =
            SoftDeletesRetentionMergePolicy(
                "soft_delete",
                { FieldExistsQuery("keep_around") },
                NoMergePolicy.INSTANCE
            )
        assertFalse(policy.keepFullyDeletedSegment { reader.leaves()[0].reader() as SegmentReader })
        reader.close()

        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(NumericDocValuesField("keep_around", 1))
        doc.add(NumericDocValuesField("soft_delete", 1))
        writer.addDocument(doc)

        val reader1 = DirectoryReader.open(writer)
        assertEquals(2, reader1.leaves().size)
        assertFalse(policy.keepFullyDeletedSegment { reader1.leaves()[0].reader() as SegmentReader })

        assertTrue(policy.keepFullyDeletedSegment { reader1.leaves()[1].reader() as SegmentReader })

        IOUtils.close(reader1, writer, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testFieldBasedRetention() {
        val dir = newDirectory()
        val indexWriterConfig = newIndexWriterConfig()
        val now = org.gnit.lucenekmp.jdkport.System.currentTimeMillis()
        val time24HoursAgo = now - 24L * 60L * 60L * 1000L
        val softDeletesField = "soft_delete"
        val docsOfLast24Hours: () -> Query =
            {
                LongPoint.newRangeQuery("creation_date", time24HoursAgo, now)
            }
        indexWriterConfig.setMergePolicy(
            SoftDeletesRetentionMergePolicy(softDeletesField, docsOfLast24Hours, LogDocMergePolicy())
        )
        indexWriterConfig.setSoftDeletesField(softDeletesField)
        val writer = IndexWriter(dir, indexWriterConfig)

        val time28HoursAgo = now - 28L * 60L * 60L * 1000L
        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "1", Field.Store.YES))
        doc.add(LongPoint("creation_date", time28HoursAgo))
        writer.addDocument(doc)

        writer.flush()
        val time26HoursAgo = now - 26L * 60L * 60L * 1000L
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "2", Field.Store.YES))
        doc.add(LongPoint("creation_date", time26HoursAgo))
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("soft_delete", 1))

        if (random().nextBoolean()) {
            writer.flush()
        }
        val time23HoursAgo = now - 23L * 60L * 60L * 1000L
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "3", Field.Store.YES))
        doc.add(LongPoint("creation_date", time23HoursAgo))
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("soft_delete", 1))

        if (random().nextBoolean()) {
            writer.flush()
        }
        val time12HoursAgo = now - 12L * 60L * 60L * 1000L
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "4", Field.Store.YES))
        doc.add(LongPoint("creation_date", time12HoursAgo))
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("soft_delete", 1))

        if (random().nextBoolean()) {
            writer.flush()
        }
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "5", Field.Store.YES))
        doc.add(LongPoint("creation_date", now))
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("soft_delete", 1))

        if (random().nextBoolean()) {
            writer.flush()
        }
        writer.forceMerge(1)
        val reader = DirectoryReader.open(writer)
        assertEquals(1, reader.numDocs())
        assertEquals(3, reader.maxDoc())
        val versions = HashSet<String>()
        versions.add(reader.storedFields().document(0, mutableSetOf("version")).get("version")!!)
        versions.add(reader.storedFields().document(1, mutableSetOf("version")).get("version")!!)
        versions.add(reader.storedFields().document(2, mutableSetOf("version")).get("version")!!)
        assertTrue(versions.contains("5"))
        assertTrue(versions.contains("4"))
        assertTrue(versions.contains("3"))
        IOUtils.close(reader, writer, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testKeepAllDocsAcrossMerges() {
        val dir = newDirectory()
        val indexWriterConfig = newIndexWriterConfig()
        indexWriterConfig.setMergePolicy(
            SoftDeletesRetentionMergePolicy(
                "soft_delete",
                { MatchAllDocsQuery() },
                LogDocMergePolicy()
            )
        )
        indexWriterConfig.setSoftDeletesField("soft_delete")
        val writer = IndexWriter(dir, indexWriterConfig)

        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("soft_delete", 1))

        writer.commit()
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("soft_delete", 1))

        writer.commit()
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(NumericDocValuesField("soft_delete", 1)) // already deleted
        writer.softUpdateDocument(Term("id", "1"), doc, NumericDocValuesField("soft_delete", 1))
        writer.commit()
        var reader = DirectoryReader.open(writer)
        assertEquals(0, reader.numDocs())
        assertEquals(3, reader.maxDoc())
        assertEquals(0, writer.getDocStats().numDocs)
        assertEquals(3, writer.getDocStats().maxDoc)
        assertEquals(3, reader.leaves().size)
        reader.close()
        writer.forceMerge(1)
        reader = DirectoryReader.open(writer)
        assertEquals(0, reader.numDocs())
        assertEquals(3, reader.maxDoc())
        assertEquals(0, writer.getDocStats().numDocs)
        assertEquals(3, writer.getDocStats().maxDoc)
        assertEquals(1, reader.leaves().size)
        IOUtils.close(reader, writer, dir)
    }

    /** tests soft deletes that carry over deleted documents on merge for history rentention. */
    @Test
    @Throws(IOException::class)
    fun testSoftDeleteWithRetention() {
        val seqIds = AtomicInteger(0)
        val dir = newDirectory()
        val indexWriterConfig = newIndexWriterConfig()
        indexWriterConfig.setMergePolicy(
            SoftDeletesRetentionMergePolicy(
                "soft_delete",
                { IntPoint.newRangeQuery("seq_id", seqIds.load() - 50, Int.MAX_VALUE) },
                indexWriterConfig.mergePolicy
            )
        )
        indexWriterConfig.setSoftDeletesField("soft_delete")
        val writer = IndexWriter(dir, indexWriterConfig)
        val threads = arrayOfNulls<Thread>(2 + random().nextInt(3))
        val startLatch = CountDownLatch(1)
        val started = CountDownLatch(threads.size)
        val updateSeveralDocs = random().nextBoolean()
        val ids = Collections.synchronizedSet(HashSet<String>())
        for (i in threads.indices) {
            threads[i] =
                Thread {
                    try {
                        started.countDown()
                        startLatch.await()
                        for (d in 0 until 100) {
                            val id = random().nextInt(10).toString()
                            val seqId = seqIds.incrementAndFetch()
                            if (updateSeveralDocs) {
                                val doc = Document()
                                doc.add(StringField("id", id, Field.Store.YES))
                                doc.add(IntPoint("seq_id", seqId))
                                writer.softUpdateDocuments(
                                    Term("id", id),
                                    listOf(doc, doc),
                                    NumericDocValuesField("soft_delete", 1)
                                )
                            } else {
                                val doc = Document()
                                doc.add(StringField("id", id, Field.Store.YES))
                                doc.add(IntPoint("seq_id", seqId))
                                writer.softUpdateDocument(
                                    Term("id", id),
                                    doc,
                                    NumericDocValuesField("soft_delete", 1)
                                )
                            }
                            if (rarely()) {
                                writer.flush()
                            }
                            ids.add(id)
                        }
                    } catch (e: Exception) {
                        throw AssertionError(e)
                    }
                }
            threads[i]!!.start()
        }
        started.await()
        startLatch.countDown()

        for (i in threads.indices) {
            threads[i]!!.join()
        }
        var reader = DirectoryReader.open(writer)
        var searcher = IndexSearcher(reader)
        for (id in ids) {
            val topDocs = searcher.search(TermQuery(Term("id", id)), 10)
            if (updateSeveralDocs) {
                assertEquals(2L, topDocs.totalHits.value)
                assertEquals(kotlin.math.abs(topDocs.scoreDocs[0].doc - topDocs.scoreDocs[1].doc), 1)
            } else {
                assertEquals(1L, topDocs.totalHits.value)
            }
        }
        writer.addDocument(Document()) // add a dummy doc to trigger a segment here
        writer.flush()
        writer.forceMerge(1)
        val oldReader = reader
        val maybeReader = DirectoryReader.openIfChanged(reader, writer)
        if (maybeReader != null) {
            oldReader.close()
            assertNotSame(oldReader, maybeReader)
            reader = maybeReader
        } else {
            reader = oldReader
        }
        assertEquals(1, reader.leaves().size)
        val leafReaderContext = reader.leaves()[0]
        val leafReader = leafReaderContext.reader()
        searcher =
            IndexSearcher(
                object : FilterLeafReader(leafReader) {
                    override val coreCacheHelper: IndexReader.CacheHelper?
                        get() = leafReader.coreCacheHelper

                    override val readerCacheHelper: IndexReader.CacheHelper?
                        get() = leafReader.readerCacheHelper

                    override val liveDocs: Bits?
                        get() = null

                    override fun numDocs(): Int {
                        return maxDoc()
                    }
                }
            )
        val seqId = searcher.search(IntPoint.newRangeQuery("seq_id", seqIds.load() - 50, Int.MAX_VALUE), 10)
        assertTrue(seqId.totalHits.value >= 50, "${seqId.totalHits.value} hits")
        searcher = IndexSearcher(reader)
        for (id in ids) {
            if (updateSeveralDocs) {
                assertEquals(2L, searcher.search(TermQuery(Term("id", id)), 10).totalHits.value)
            } else {
                assertEquals(1L, searcher.search(TermQuery(Term("id", id)), 10).totalHits.value)
            }
        }
        IOUtils.close(reader, writer, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testForceMergeDeletes() {
        val dir = newDirectory()
        val config = newIndexWriterConfig().setSoftDeletesField("soft_delete")
        config.setMergePolicy(
            newMergePolicy(random(), false)
        ) // no mock MP it might not select segments for force merge
        if (random().nextBoolean()) {
            config.setMergePolicy(
                SoftDeletesRetentionMergePolicy(
                    "soft_delete",
                    { MatchNoDocsQuery() },
                    config.mergePolicy
                )
            )
        }
        val writer = IndexWriter(dir, config)
        // The first segment includes d1 and d2
        for (i in 0 until 2) {
            val d = Document()
            d.add(StringField("id", i.toString(), Field.Store.YES))
            writer.addDocument(d)
        }
        writer.flush()
        // The second segment includes only the tombstone
        val tombstone = Document()
        tombstone.add(NumericDocValuesField("soft_delete", 1))
        writer.softUpdateDocument(Term("id", "1"), tombstone, NumericDocValuesField("soft_delete", 1))
        writer.flush(false, true) // flush pending updates but don't trigger a merge, we run forceMergeDeletes below
        // Now we have have two segments - both having soft-deleted documents.
        // We expect any MP to merge these segments into one segment
        // when calling forceMergeDeletes.
        writer.forceMergeDeletes(true)
        assertEquals(1, writer.cloneSegmentInfos().size())
        assertEquals(1, writer.getDocStats().numDocs)
        assertEquals(1, writer.getDocStats().maxDoc)
        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDropFullySoftDeletedSegment() {
        val dir = newDirectory()
        val softDelete = if (random().nextBoolean()) null else "soft_delete"
        val config = newIndexWriterConfig()
        if (softDelete != null) {
            config.setSoftDeletesField(softDelete)
        }
        config.setMergePolicy(newMergePolicy(random(), true))
        if (softDelete != null && random().nextBoolean()) {
            config.setMergePolicy(
                SoftDeletesRetentionMergePolicy(
                    softDelete,
                    { MatchNoDocsQuery() },
                    config.mergePolicy
                )
            )
        }
        val writer = IndexWriter(dir, config)
        for (i in 0 until 2) {
            val d = Document()
            d.add(StringField("id", i.toString(), Field.Store.YES))
            writer.addDocument(d)
        }
        writer.flush()
        assertEquals(1, writer.cloneSegmentInfos().size())

        if (softDelete != null) {
            // the newly created segment should be dropped as it is fully deleted (i.e. only contains
            // deleted docs).
            if (random().nextBoolean()) {
                val tombstone = Document()
                tombstone.add(NumericDocValuesField(softDelete, 1))
                writer.softUpdateDocument(
                    Term("id", "1"),
                    tombstone,
                    NumericDocValuesField(softDelete, 1)
                )
            } else {
                val doc = Document()
                doc.add(StringField("id", 1.toString(), Field.Store.YES))
                if (random().nextBoolean()) {
                    writer.softUpdateDocument(
                        Term("id", "1"),
                        doc,
                        NumericDocValuesField(softDelete, 1)
                    )
                } else {
                    writer.addDocument(doc)
                }
                writer.updateDocValues(Term("id", "1"), NumericDocValuesField(softDelete, 1))
            }
        } else {
            val d = Document()
            d.add(StringField("id", "1", Field.Store.YES))
            writer.addDocument(d)
            writer.deleteDocuments(Term("id", "1"))
        }
        writer.commit()
        val reader = DirectoryReader.open(writer)
        assertEquals(1, reader.numDocs())
        reader.close()
        assertEquals(1, writer.cloneSegmentInfos().size())

        writer.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSoftDeleteWhileMergeSurvives() {
        val dir = newDirectory()
        val softDelete = "soft_delete"
        val config = newIndexWriterConfig().setSoftDeletesField(softDelete)
        val update = AtomicBoolean(true)
        config.setReaderPooling(true)
        config.setMergePolicy(
            SoftDeletesRetentionMergePolicy(
                "soft_delete",
                { FieldExistsQuery("keep") },
                LogDocMergePolicy()
            )
        )
        val writer = IndexWriter(dir, config)
        writer.config.setMergedSegmentWarmer(
            IndexWriter.IndexReaderWarmer { _ ->
                if (update.compareAndSet(true, false)) {
                    try {
                        writer.softUpdateDocument(
                            Term("id", "0"),
                            Document(),
                            NumericDocValuesField(softDelete, 1),
                            NumericDocValuesField("keep", 1)
                        )
                        writer.commit()
                    } catch (e: IOException) {
                        throw AssertionError(e)
                    }
                }
            }
        )

        val preExistingDeletes = random().nextBoolean()
        for (i in 0 until 2) {
            val d = Document()
            d.add(StringField("id", i.toString(), Field.Store.YES))
            if (preExistingDeletes && random().nextBoolean()) {
                writer.addDocument(d) // randomly add a preexisting hard-delete that we don't carry over
                writer.deleteDocuments(Term("id", i.toString()))
                d.add(NumericDocValuesField("keep", 1))
                writer.addDocument(d)
            } else {
                d.add(NumericDocValuesField("keep", 1))
                writer.addDocument(d)
            }
            writer.flush()
        }
        writer.forceMerge(1)
        writer.commit()
        assertFalse(update.load())
        val open = DirectoryReader.open(dir)
        assertEquals(0, open.numDeletedDocs())
        assertEquals(3, open.maxDoc())
        IOUtils.close(open, writer, dir)
    }

    /*
     * This test is trying to hard-delete a particular document while the segment is merged which is already soft-deleted
     * This requires special logic inside IndexWriter#carryOverHardDeletes since docMaps are not created for this document.
     */
    @Test
    @Throws(IOException::class)
    fun testDeleteDocWhileMergeThatIsSoftDeleted() {
        val dir = newDirectory()
        val softDelete = "soft_delete"
        val config = newIndexWriterConfig().setSoftDeletesField(softDelete)
        val delete = AtomicBoolean(true)
        config.setReaderPooling(true)
        config.setMergePolicy(LogDocMergePolicy())
        val writer = IndexWriter(dir, config)
        var d = Document()
        d.add(StringField("id", "0", Field.Store.YES))
        writer.addDocument(d)
        d = Document()
        d.add(StringField("id", "1", Field.Store.YES))
        writer.addDocument(d)
        if (random().nextBoolean()) {
            // randomly run with a preexisting hard delete
            d = Document()
            d.add(StringField("id", "2", Field.Store.YES))
            writer.addDocument(d)
            writer.deleteDocuments(Term("id", "2"))
        }

        writer.flush()
        val reader = DirectoryReader.open(writer)
        writer.softUpdateDocument(Term("id", "0"), Document(), NumericDocValuesField(softDelete, 1))
        writer.flush()
        writer.config.setMergedSegmentWarmer(
            IndexWriter.IndexReaderWarmer { _ ->
                if (delete.compareAndSet(true, false)) {
                    try {
                        val seqNo = writer.tryDeleteDocument(reader, 0)
                        assertTrue(seqNo != -1L, "seqId was -1")
                    } catch (e: IOException) {
                        throw AssertionError(e)
                    }
                }
            }
        )
        writer.forceMerge(1)
        assertEquals(2, writer.getDocStats().numDocs)
        assertEquals(2, writer.getDocStats().maxDoc)
        assertFalse(delete.load())
        IOUtils.close(reader, writer, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testUndeleteDocument() {
        val dir = newDirectory()
        val softDelete = "soft_delete"
        val config =
            newIndexWriterConfig()
                .setSoftDeletesField(softDelete)
                .setMergePolicy(
                    SoftDeletesRetentionMergePolicy(
                        "soft_delete",
                        { MatchAllDocsQuery() },
                        LogDocMergePolicy()
                    )
                )
        config.setReaderPooling(true)
        config.setMergePolicy(LogDocMergePolicy())
        val writer = IndexWriter(dir, config)
        var d = Document()
        d.add(StringField("id", "0", Field.Store.YES))
        d.add(StringField("seq_id", "0", Field.Store.YES))
        writer.addDocument(d)
        d = Document()
        d.add(StringField("id", "1", Field.Store.YES))
        writer.addDocument(d)
        writer.updateDocValues(Term("id", "0"), NumericDocValuesField("soft_delete", 1))
        DirectoryReader.open(writer).use { reader ->
            assertEquals(2, reader.maxDoc())
            assertEquals(1, reader.numDocs())
        }
        doUpdate(Term("id", "0"), writer, NumericDocValuesField("soft_delete", null))
        DirectoryReader.open(writer).use { reader ->
            assertEquals(2, reader.maxDoc())
            assertEquals(2, reader.numDocs())
        }
        IOUtils.close(writer, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testMergeSoftDeleteAndHardDelete() {
        val dir = newDirectory()
        val softDelete = "soft_delete"
        val config =
            newIndexWriterConfig()
                .setSoftDeletesField(softDelete)
                .setMergePolicy(
                    SoftDeletesRetentionMergePolicy(
                        "soft_delete",
                        { MatchAllDocsQuery() },
                        LogDocMergePolicy()
                    )
                )
        config.setReaderPooling(true)
        val writer = IndexWriter(dir, config)
        var d = Document()
        d.add(StringField("id", "0", Field.Store.YES))
        writer.addDocument(d)
        d = Document()
        d.add(StringField("id", "1", Field.Store.YES))
        d.add(NumericDocValuesField("soft_delete", 1))
        writer.addDocument(d)
        DirectoryReader.open(writer).use { reader ->
            assertEquals(2, reader.maxDoc())
            assertEquals(1, reader.numDocs())
        }
        while (true) {
            val reader = DirectoryReader.open(writer)
            try {
                val topDocs = IndexSearcher(IncludeSoftDeletesWrapper(reader)).search(TermQuery(Term("id", "1")), 1)
                assertEquals(1L, topDocs.totalHits.value)
                if (writer.tryDeleteDocument(reader, topDocs.scoreDocs[0].doc) > 0) {
                    break
                }
            } finally {
                reader.close()
            }
        }
        writer.forceMergeDeletes(true)
        assertEquals(1, writer.cloneSegmentInfos().size())
        val si = writer.cloneSegmentInfos().info(0)
        assertEquals(0, si.getSoftDelCount()) // hard-delete should supersede the soft-delete
        assertEquals(0, si.delCount)
        assertEquals(1, si.info.maxDoc())
        IOUtils.close(writer, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testSoftDeleteWithTryUpdateDocValue() {
        val dir = newDirectory()
        val config =
            newIndexWriterConfig()
                .setSoftDeletesField("soft_delete")
                .setMergePolicy(
                    SoftDeletesRetentionMergePolicy(
                        "soft_delete",
                        { MatchAllDocsQuery() },
                        newLogMergePolicy()
                    )
                )
        val writer = IndexWriter(dir, config)
        val sm = SearcherManager(writer, SearcherFactory())
        val d = Document()
        d.add(StringField("id", "0", Field.Store.YES))
        writer.addDocument(d)
        sm.maybeRefreshBlocking()
        doUpdate(
            Term("id", "0"),
            writer,
            NumericDocValuesField("soft_delete", 1),
            NumericDocValuesField("other-field", 1)
        )
        sm.maybeRefreshBlocking()
        assertEquals(1, writer.cloneSegmentInfos().size())
        val si = writer.cloneSegmentInfos().info(0)
        assertEquals(1, si.getSoftDelCount())
        assertEquals(1, si.info.maxDoc())
        IOUtils.close(sm, writer, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testMixedSoftDeletesAndHardDeletes() {
        val dir = newDirectory()
        val softDeletesField = "soft-deletes"
        val config =
            newIndexWriterConfig()
                .setMaxBufferedDocs(2 + random().nextInt(50))
                .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                .setSoftDeletesField(softDeletesField)
                .setMergePolicy(
                    SoftDeletesRetentionMergePolicy(
                        softDeletesField,
                        { MatchAllDocsQuery() },
                        newMergePolicy()
                    )
                )
        val writer = IndexWriter(dir, config)
        val numDocs = 10 + random().nextInt(100)
        val liveDocs = HashSet<String>()
        for (i in 0 until numDocs) {
            val id = i.toString()
            val doc = Document()
            doc.add(StringField("id", id, Field.Store.YES))
            writer.addDocument(doc)
            liveDocs.add(id)
        }
        for (i in 0 until numDocs) {
            if (random().nextBoolean()) {
                val id = i.toString()
                if (random().nextBoolean() && liveDocs.contains(id)) {
                    doUpdate(Term("id", id), writer, NumericDocValuesField(softDeletesField, 1))
                } else {
                    val doc = Document()
                    doc.add(StringField("id", "v$id", Field.Store.YES))
                    writer.softUpdateDocument(
                        Term("id", id),
                        doc,
                        NumericDocValuesField(softDeletesField, 1)
                    )
                    liveDocs.add("v$id")
                }
            }
            if (random().nextBoolean() && liveDocs.isEmpty().not()) {
                val delId = RandomPicks.randomFrom(random(), liveDocs)
                if (random().nextBoolean()) {
                    doDelete(Term("id", delId), writer)
                } else {
                    writer.deleteDocuments(Term("id", delId))
                }
                liveDocs.remove(delId)
            }
        }
        val unwrapped = DirectoryReader.open(writer)
        try {
            val reader = IncludeSoftDeletesWrapper(unwrapped)
            try {
                assertEquals(liveDocs.size, reader.numDocs())
            } finally {
                reader.close()
            }
        } finally {
        }
        writer.commit()
        IOUtils.close(writer, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testRewriteRetentionQuery() {
        val dir = newDirectory()
        val config =
            newIndexWriterConfig()
                .setSoftDeletesField("soft_deletes")
                .setMergePolicy(
                    SoftDeletesRetentionMergePolicy(
                        "soft_deletes",
                        { PrefixQuery(Term("id", "foo")) },
                        newMergePolicy()
                    )
                )
        val writer = IndexWriter(dir, config)

        var d = Document()
        d.add(StringField("id", "foo-1", Field.Store.YES))
        writer.addDocument(d)
        d = Document()
        d.add(StringField("id", "foo-2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "foo-1"), d, NumericDocValuesField("soft_deletes", 1))

        d = Document()
        d.add(StringField("id", "bar-1", Field.Store.YES))
        writer.addDocument(d)
        d.add(StringField("id", "bar-2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "bar-1"), d, NumericDocValuesField("soft_deletes", 1))

        writer.forceMerge(1)
        assertEquals(2, writer.getDocStats().numDocs) // foo-2, bar-2
        assertEquals(3, writer.getDocStats().maxDoc) // foo-1, foo-2, bar-2
        IOUtils.close(writer, dir)
    }

    companion object {
        @Throws(IOException::class)
        fun doUpdate(doc: Term, writer: IndexWriter, vararg fields: Field) {
            var seqId = -1L
            do { // retry if we just committing a merge
                val reader = DirectoryReader.open(writer)
                try {
                    val wrappedReader = IncludeSoftDeletesWrapper(reader)
                    try {
                        val topDocs = IndexSearcher(wrappedReader).search(TermQuery(doc), 10)
                        assertEquals(1L, topDocs.totalHits.value)
                        val theDoc = topDocs.scoreDocs[0].doc
                        seqId = writer.tryUpdateDocValue(reader, theDoc, *fields)
                    } finally {
                        wrappedReader.close()
                    }
                } finally {
                }
            } while (seqId == -1L)
        }

        @Throws(IOException::class)
        fun doDelete(doc: Term, writer: IndexWriter) {
            var seqId: Long
            do { // retry if we just committing a merge
                val reader = DirectoryReader.open(writer)
                try {
                    val wrappedReader = IncludeSoftDeletesWrapper(reader)
                    try {
                        val topDocs = IndexSearcher(wrappedReader).search(TermQuery(doc), 10)
                        assertEquals(1L, topDocs.totalHits.value)
                        val theDoc = topDocs.scoreDocs[0].doc
                        seqId = writer.tryDeleteDocument(reader, theDoc)
                    } finally {
                        wrappedReader.close()
                    }
                } finally {
                }
            } while (seqId == -1L)
        }
    }

    private class IncludeSoftDeletesSubReaderWrapper : FilterDirectoryReader.SubReaderWrapper() {
        override fun wrap(reader: LeafReader): LeafReader {
            var mutableReader = reader
            while (mutableReader is FilterLeafReader) {
                mutableReader = mutableReader.delegate
            }
            val hardLiveDocs = (mutableReader as SegmentReader).hardLiveDocs
            val numDocs =
                if (hardLiveDocs == null) {
                    mutableReader.maxDoc()
                } else {
                    var bits = 0
                    for (i in 0 until hardLiveDocs.length()) {
                        if (hardLiveDocs.get(i)) {
                            bits++
                        }
                    }
                    bits
                }
            return object : FilterLeafReader(mutableReader) {
                override fun numDocs(): Int {
                    return numDocs
                }

                override val liveDocs: Bits?
                    get() = hardLiveDocs

                override val coreCacheHelper: IndexReader.CacheHelper?
                    get() = null

                override val readerCacheHelper: IndexReader.CacheHelper?
                    get() = null
            }
        }
    }

    private class IncludeSoftDeletesWrapper(inReader: DirectoryReader) :
        FilterDirectoryReader(inReader, IncludeSoftDeletesSubReaderWrapper()) {

        @Throws(IOException::class)
        override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
            return IncludeSoftDeletesWrapper(`in`)
        }

        override val readerCacheHelper: CacheHelper?
            get() = null
    }
}

