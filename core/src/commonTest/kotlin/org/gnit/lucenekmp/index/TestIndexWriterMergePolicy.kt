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
import okio.Path
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.MockIndexWriterEventListener
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.util.IOFunction
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

// @HandleLimitFS.MaxOpenHandles(limit = HandleLimitFS.MaxOpenHandles.MAX_OPEN_FILES * 2)
// Some of these tests are too intense for SimpleText
// @LuceneTestCase.SuppressCodecs("SimpleText")
@OptIn(ExperimentalAtomicApi::class)
class TestIndexWriterMergePolicy : LuceneTestCase() {

    /**
     * A less sophisticated version of LogDocMergePolicy, only for testing the interaction between
     * IndexWriter and the MergePolicy.
     */
    private class MockMergePolicy : MergePolicy() {
        private var mergeFactor = 10

        fun getMergeFactor(): Int {
            return mergeFactor
        }

        fun setMergeFactor(mergeFactor: Int) {
            this.mergeFactor = mergeFactor
        }

        @Throws(IOException::class)
        override fun findMerges(
            mergeTrigger: MergeTrigger?,
            segmentInfos: SegmentInfos?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            val segments = mutableListOf<SegmentCommitInfo>()
            for (sci in segmentInfos!!) {
                segments.add(sci)
            }
            var spec: MergeSpecification? = null
            var start = 0
            while (start <= segments.size - mergeFactor) {
                val startDocCount = segments[start].info.maxDoc()
                // Now search for the right-most segment that could be merged with the start segment
                var end = start + 1
                for (i in segments.size - 1 downTo start + 1) {
                    val docCount = segments[i].info.maxDoc()
                    if (docCount.toLong() * mergeFactor > startDocCount &&
                        docCount < mergeFactor.toLong() * startDocCount) {
                        end = i + 1
                        break
                    }
                }

                // Now record a merge if possible
                if (start + mergeFactor <= end) {
                    if (spec == null) {
                        spec = MergeSpecification()
                    }
                    spec.add(OneMerge(segments.subList(start, start + mergeFactor).toMutableList()))
                    start += mergeFactor
                } else {
                    start++
                }
            }
            return spec
        }

        @Throws(IOException::class)
        override fun findForcedMerges(
            segmentInfos: SegmentInfos?,
            maxSegmentCount: Int,
            segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            return null
        }

        @Throws(IOException::class)
        override fun findForcedDeletesMerges(
            segmentInfos: SegmentInfos?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            return null
        }
    }

    // Test the normal case
    @Test
    @Throws(IOException::class)
    fun testNormalCase() {
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(10)
                    .setMergePolicy(MockMergePolicy())
            ).use { writer ->
                for (i in 0..<100) {
                    addDoc(writer)
                    checkInvariants(writer)
                }
            }
        }
    }

    // Test to see if there is over merge
    @Test
    @Throws(IOException::class)
    fun testNoOverMerge() {
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(10)
                    .setMergePolicy(MockMergePolicy())
            ).use { writer ->
                var noOverMerge = false
                for (i in 0..<100) {
                    addDoc(writer)
                    checkInvariants(writer)
                    if (writer.getNumBufferedDocuments() + writer.getSegmentCount() >= 18) {
                        noOverMerge = true
                    }
                }
                assertTrue(noOverMerge)
            }
        }
    }

    // Test the case where flush is forced after every addDoc
    @Test
    @Throws(IOException::class)
    fun testForceFlush() {
        newDirectory().use { dir ->
            val mp = MockMergePolicy()
            mp.setMergeFactor(10)
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(10)
                    .setMergePolicy(mp)
            ).use { writer ->
                for (i in 0..<100) {
                    addDoc(writer)
                    writer.flush()
                }
            }
        }
    }

    // Test the case where mergeFactor changes
    @Test
    @Throws(IOException::class)
    fun testMergeFactorChange() {
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(10)
                    .setMergePolicy(MockMergePolicy())
                    .setMergeScheduler(SerialMergeScheduler())
            ).use { writer ->
                for (i in 0..<250) {
                    addDoc(writer)
                    checkInvariants(writer)
                }

                (writer.config.mergePolicy as MockMergePolicy).setMergeFactor(5)

                // merge policy only fixes segments on levels where merges
                // have been triggered, so check invariants after all adds
                for (i in 0..<10) {
                    addDoc(writer)
                }
                checkInvariants(writer)
            }
        }
    }

    // Test the case where both mergeFactor and maxBufferedDocs change
    @Nightly
    @Test
    @Throws(IOException::class)
    fun testMaxBufferedDocsChange() {
        newDirectory().use { dir ->
            var writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(101)
                        .setMergePolicy(MockMergePolicy())
                        .setMergeScheduler(SerialMergeScheduler())
                )

            // leftmost* segment has 1 doc
            // rightmost* segment has 100 docs
            for (i in 1..100) {
                for (j in 0..<i) {
                    addDoc(writer)
                    checkInvariants(writer)
                }
                writer.close()
                writer =
                    IndexWriter(
                        dir,
                        newIndexWriterConfig(MockAnalyzer(random()))
                            .setOpenMode(OpenMode.APPEND)
                            .setMaxBufferedDocs(101)
                            .setMergePolicy(MockMergePolicy())
                            .setMergeScheduler(SerialMergeScheduler())
                    )
            }

            writer.close()
            val ldmp = MockMergePolicy()
            ldmp.setMergeFactor(10)
            writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setOpenMode(OpenMode.APPEND)
                        .setMaxBufferedDocs(10)
                        .setMergePolicy(ldmp)
                        .setMergeScheduler(SerialMergeScheduler())
                )

            // merge policy only fixes segments on levels where merges
            // have been triggered, so check invariants after all adds
            for (i in 0..<100) {
                addDoc(writer)
            }
            checkInvariants(writer)

            for (i in 100..<1000) {
                addDoc(writer)
            }
            writer.commit()
            writer.waitForMerges()
            writer.commit()
            checkInvariants(writer)

            writer.close()
        }
    }

    // Test the case where a merge results in no doc at all
    @Test
    @Throws(IOException::class)
    fun testMergeDocCount0() {
        newDirectory().use { dir ->
            var ldmp = MockMergePolicy()
            ldmp.setMergeFactor(100)
            var writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(10)
                        .setMergePolicy(ldmp)
                )

            for (i in 0..<250) {
                addDoc(writer)
                checkInvariants(writer)
            }
            writer.close()

            // delete some docs without merging
            writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMergePolicy(NoMergePolicy.INSTANCE)
                )
            writer.deleteDocuments(Term("content", "aaa"))
            writer.close()

            ldmp = MockMergePolicy()
            ldmp.setMergeFactor(5)
            writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setOpenMode(OpenMode.APPEND)
                        .setMaxBufferedDocs(10)
                        .setMergePolicy(ldmp)
                        .setMergeScheduler(ConcurrentMergeScheduler())
                )

            // merge factor is changed, so check invariants after all adds
            for (i in 0..<10) {
                addDoc(writer)
            }
            writer.commit()
            writer.waitForMerges()
            writer.commit()
            checkInvariants(writer)
            assertEquals(10, writer.getDocStats().maxDoc)

            writer.close()
        }
    }

    private fun addDoc(writer: IndexWriter) {
        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        writer.addDocument(doc)
    }

    private fun checkInvariants(writer: IndexWriter) {
        writer.waitForMerges()
        val maxBufferedDocs = writer.config.maxBufferedDocs
        val mergeFactor = (writer.config.mergePolicy as MockMergePolicy).getMergeFactor()

        val ramSegmentCount = writer.getNumBufferedDocuments()
        assertTrue(ramSegmentCount < maxBufferedDocs)

        val segmentCount = writer.getSegmentCount()
        var lowerBound = Int.MAX_VALUE
        for (i in 0..<segmentCount) {
            lowerBound = min(lowerBound, writer.maxDoc(i))
        }
        val upperBound = lowerBound * mergeFactor

        var segmentsAcrossLevels = 0
        while (segmentsAcrossLevels < segmentCount) {
            var segmentsOnCurrentLevel = 0
            for (i in 0..<segmentCount) {
                val docCount = writer.maxDoc(i)
                if (docCount >= lowerBound && docCount < upperBound) {
                    segmentsOnCurrentLevel++
                }
            }

            assertTrue(segmentsOnCurrentLevel < mergeFactor)
            segmentsAcrossLevels += segmentsOnCurrentLevel
        }
    }

    companion object {
        private const val EPSILON = 1E-14
    }

    @Test
    fun testSetters() {
        assertSetters(LogByteSizeMergePolicy())
        assertSetters(MockMergePolicy())
    }

    // Test basic semantics of merge on commit
    @Test
    @Throws(IOException::class)
    fun testMergeOnCommit() {
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE)
            ).use { firstWriter ->
                for (i in 0..<5) {
                    TestIndexWriter.addDoc(firstWriter)
                    firstWriter.flush()
                }
                DirectoryReader.open(firstWriter).use { firstReader ->
                    assertEquals(5, firstReader.leaves().size)
                }
            }

            val iwc =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(MergeOnXMergePolicy(newMergePolicy(), MergeTrigger.COMMIT))
                    .setMaxFullFlushMergeWaitMillis(Int.MAX_VALUE.toLong())

            IndexWriter(dir, iwc).use { writerWithMergePolicy ->
                // No changes. Refresh doesn't trigger a merge.
                DirectoryReader.open(writerWithMergePolicy).use { unmergedReader ->
                    assertEquals(5, unmergedReader.leaves().size)
                }

                writerWithMergePolicy.commit() // Do merge on commit.
                assertEquals(1, writerWithMergePolicy.getSegmentCount())

                DirectoryReader.open(writerWithMergePolicy).use { mergedReader ->
                    assertEquals(1, mergedReader.leaves().size)
                }

                DirectoryReader.open(writerWithMergePolicy).use { reader ->
                    val searcher = IndexSearcher(reader)
                    assertEquals(5, reader.numDocs())
                    assertEquals(5, searcher.count(MatchAllDocsQuery()))
                }
            }
        }
    }

    // Test basic semantics of merge on commit and events recording invocation
    @Test
    @Throws(IOException::class)
    fun testMergeOnCommitWithEventListener() {
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE)
            ).use { firstWriter ->
                for (i in 0..<5) {
                    TestIndexWriter.addDoc(firstWriter)
                    firstWriter.flush()
                }
                DirectoryReader.open(firstWriter).use { firstReader ->
                    assertEquals(5, firstReader.leaves().size)
                }
            }

            val eventListener = MockIndexWriterEventListener()

            val iwc =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(MergeOnXMergePolicy(newMergePolicy(), MergeTrigger.COMMIT))
                    .setMaxFullFlushMergeWaitMillis(Int.MAX_VALUE.toLong())
                    .setIndexWriterEventListener(eventListener)

            IndexWriter(dir, iwc).use { writerWithMergePolicy ->
                DirectoryReader.open(writerWithMergePolicy).use { unmergedReader ->
                    assertEquals(5, unmergedReader.leaves().size)
                }

                assertFalse(eventListener.isEventsRecorded())
                writerWithMergePolicy.commit()
                assertEquals(1, writerWithMergePolicy.getSegmentCount())
                assertTrue(eventListener.isEventsRecorded())
            }
        }
    }

    private fun assertSetters(lmp: MergePolicy) {
        lmp.maxCFSSegmentSizeMB = 2.0
        assertEquals(2.0, lmp.maxCFSSegmentSizeMB, EPSILON)

        lmp.maxCFSSegmentSizeMB = Double.POSITIVE_INFINITY
        assertEquals(
            Long.MAX_VALUE / 1024.0 / 1024.0,
            lmp.maxCFSSegmentSizeMB,
            EPSILON * Long.MAX_VALUE
        )

        lmp.maxCFSSegmentSizeMB = Long.MAX_VALUE / 1024.0 / 1024.0
        assertEquals(
            Long.MAX_VALUE / 1024.0 / 1024.0,
            lmp.maxCFSSegmentSizeMB,
            EPSILON * Long.MAX_VALUE
        )

        expectThrows(IllegalArgumentException::class) {
            lmp.maxCFSSegmentSizeMB = -2.0
        }

        // TODO: Add more checks for other non-double setters!
    }

    @Test
    @Throws(Exception::class)
    fun testCarryOverNewDeletesOnCommit() {
        newDirectory().use { directory ->
            val useSoftDeletes = random().nextBoolean()
            val waitForMerge = CountDownLatch(1)
            val waitForUpdate = CountDownLatch(1)
            val writer =
                object : IndexWriter(
                    directory,
                    newIndexWriterConfig()
                        .setMergePolicy(
                            MergeOnXMergePolicy(NoMergePolicy.INSTANCE, MergeTrigger.COMMIT)
                        )
                        .setMaxFullFlushMergeWaitMillis(30_000)
                        .setSoftDeletesField("soft_delete")
                        .setMaxBufferedDocs(Int.MAX_VALUE)
                        .setRAMBufferSizeMB(100.0)
                        .setMergeScheduler(ConcurrentMergeScheduler())
                ) {
                    override fun merge(merge: MergePolicy.OneMerge) {
                        waitForMerge.countDown()
                        try {
                            waitForUpdate.await()
                        } catch (e: Exception) {
                            throw AssertionError(e)
                        }
                        super.merge(merge)
                    }
                }
            writer.use {
                val d1 = Document().apply { add(StringField("id", "1", Field.Store.NO)) }
                val d2 = Document().apply { add(StringField("id", "2", Field.Store.NO)) }
                val d3 = Document().apply { add(StringField("id", "3", Field.Store.NO)) }
                writer.addDocument(d1)
                writer.flush()
                writer.addDocument(d2)
                val addThreeDocs = random().nextBoolean()
                var expectedNumDocs = 2
                if (addThreeDocs) {
                    expectedNumDocs = 3
                    writer.addDocument(d3)
                }
                val t = Thread {
                    try {
                        waitForMerge.await()
                        if (useSoftDeletes) {
                            writer.softUpdateDocument(
                                Term("id", "2"),
                                d2,
                                NumericDocValuesField("soft_delete", 1)
                            )
                        } else {
                            writer.updateDocument(Term("id", "2"), d2)
                        }
                        writer.flush()
                    } catch (e: Exception) {
                        throw AssertionError(e)
                    } finally {
                        waitForUpdate.countDown()
                    }
                }
                t.start()
                writer.commit()
                t.join()
                SoftDeletesDirectoryReaderWrapper(
                    DirectoryReader.open(directory),
                    "soft_delete"
                ).use { open ->
                    assertEquals(expectedNumDocs, open.numDocs())
                    assertEquals(expectedNumDocs, open.maxDoc(), "we should not have any deletes")
                }

                DirectoryReader.open(writer).use { open ->
                    assertEquals(expectedNumDocs, open.numDocs())
                    assertEquals(expectedNumDocs + 1, open.maxDoc(), "we should not have one delete")
                }
            }
        }
    }

    /**
     * This test makes sure we release the merge readers on abort. MDW will fail if it can't close all
     * files
     */
    @Test
    @Throws(Exception::class)
    fun testAbortMergeOnCommit() {
        abortMergeOnX(false)
    }

    @Test
    @Throws(Exception::class)
    fun testAbortMergeOnGetReader() {
        abortMergeOnX(true)
    }

    @Throws(Exception::class)
    fun abortMergeOnX(useGetReader: Boolean) {
        newDirectory().use { directory ->
            val waitForMerge = CountDownLatch(1)
            val waitForDeleteAll = CountDownLatch(1)
            val writer =
                IndexWriter(
                    directory,
                    newIndexWriterConfig()
                        .setMergePolicy(
                            MergeOnXMergePolicy(
                                newMergePolicy(),
                                if (useGetReader) MergeTrigger.GET_READER else MergeTrigger.COMMIT
                            )
                        )
                        .setMaxFullFlushMergeWaitMillis(30_000)
                        .setMergeScheduler(
                            object : SerialMergeScheduler() {
                                override suspend fun merge(
                                    mergeSource: MergeSource,
                                    trigger: MergeTrigger
                                ) {
                                    waitForMerge.countDown()
                                    try {
                                        waitForDeleteAll.await()
                                    } catch (e: Exception) {
                                        throw AssertionError(e)
                                    }
                                    super.merge(mergeSource, trigger)
                                }
                            }
                        )
                )
            writer.use {
                val d1 = Document().apply { add(StringField("id", "1", Field.Store.NO)) }
                val d2 = Document().apply { add(StringField("id", "2", Field.Store.NO)) }
                writer.addDocument(d1)
                writer.flush()
                writer.addDocument(d2)
                val t = Thread {
                    var success = false
                    try {
                        if (useGetReader) {
                            DirectoryReader.open(writer).close()
                        } else {
                            writer.commit()
                        }
                        success = true
                    } catch (e: IOException) {
                        throw AssertionError(e)
                    } finally {
                        if (!success) {
                            waitForMerge.countDown()
                        }
                    }
                }
                t.start()
                waitForMerge.await()
                writer.deleteAll()
                waitForDeleteAll.countDown()
                t.join()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testForceMergeWhileGetReader() {
        newDirectory().use { directory ->
            val waitForMerge = CountDownLatch(1)
            val waitForForceMergeCalled = CountDownLatch(1)
            val writer =
                IndexWriter(
                    directory,
                    newIndexWriterConfig()
                        .setMergePolicy(
                            MergeOnXMergePolicy(newMergePolicy(), MergeTrigger.GET_READER)
                        )
                        .setMaxFullFlushMergeWaitMillis(30_000)
                        .setMergeScheduler(
                            object : SerialMergeScheduler() {
                                override suspend fun merge(
                                    mergeSource: MergeSource,
                                    trigger: MergeTrigger
                                ) {
                                    waitForMerge.countDown()
                                    try {
                                        waitForForceMergeCalled.await()
                                    } catch (e: Exception) {
                                        throw AssertionError(e)
                                    }
                                    super.merge(mergeSource, trigger)
                                }
                            }
                        )
                )
            writer.use {
                val d1 = Document().apply { add(StringField("id", "1", Field.Store.NO)) }
                writer.addDocument(d1)
                writer.flush()
                val d2 = Document().apply { add(StringField("id", "2", Field.Store.NO)) }
                writer.addDocument(d2)
                val t = Thread {
                    try {
                        DirectoryReader.open(writer).use { reader ->
                            assertEquals(2, reader.maxDoc())
                        }
                    } catch (e: IOException) {
                        throw AssertionError(e)
                    }
                }
                t.start()
                waitForMerge.await()
                val d3 = Document().apply { add(StringField("id", "3", Field.Store.NO)) }
                writer.addDocument(d3)
                waitForForceMergeCalled.countDown()
                writer.forceMerge(1)
                t.join()
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testFailAfterMergeCommitted() {
        newDirectory().use { directory ->
            val mergeAndFail = AtomicBoolean(false)
            val writer =
                object : IndexWriter(
                    directory,
                    newIndexWriterConfig()
                        .setMergePolicy(
                            MergeOnXMergePolicy(NoMergePolicy.INSTANCE, MergeTrigger.GET_READER)
                        )
                        .setMaxFullFlushMergeWaitMillis(30_000)
                        .setMergeScheduler(SerialMergeScheduler())
                ) {
                    override fun doAfterFlush() {
                        if (mergeAndFail.load() && hasPendingMerges()) {
                            executeMerge(MergeTrigger.GET_READER)
                            throw RuntimeException("boom")
                        }
                    }
                }
            writer.use {
                val d1 = Document().apply { add(StringField("id", "1", Field.Store.NO)) }
                writer.addDocument(d1)
                writer.flush()
                val d2 = Document().apply { add(StringField("id", "2", Field.Store.NO)) }
                writer.addDocument(d2)
                writer.flush()
                mergeAndFail.store(true)
                try {
                    DirectoryReader.open(writer).use { reader ->
                        assertNotNull(reader)
                        fail()
                    }
                } catch (e: RuntimeException) {
                    assertEquals("boom", e.message)
                } finally {
                    mergeAndFail.store(false)
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testStressUpdateSameDocumentWithMergeOnGetReader() {
        stressUpdateSameDocumentWithMergeOnX(true)
    }

    @Test
    @Throws(Exception::class)
    fun testStressUpdateSameDocumentWithMergeOnCommit() {
        stressUpdateSameDocumentWithMergeOnX(false)
    }


    @Throws(Exception::class)
    fun stressUpdateSameDocumentWithMergeOnX(useGetReader: Boolean) {
        val testStart = kotlin.time.TimeSource.Monotonic.markNow()
        val dirStart = kotlin.time.TimeSource.Monotonic.markNow()
        newDirectory().use { directory ->
            val dirMs = dirStart.elapsedNow().inWholeMilliseconds
            val writerStart = kotlin.time.TimeSource.Monotonic.markNow()
            RandomIndexWriter(
                random(),
                directory,
                newIndexWriterConfig()
                    .setMergePolicy(
                        MergeOnXMergePolicy(
                            newMergePolicy(),
                            if (useGetReader) MergeTrigger.GET_READER else MergeTrigger.COMMIT
                        )
                    )
                    .setMaxFullFlushMergeWaitMillis((10 + random().nextInt(2000)).toLong())
                    .setSoftDeletesField("soft_delete")
                    .setMergeScheduler(ConcurrentMergeScheduler())
            ).use { writer ->
                val writerMs = writerStart.elapsedNow().inWholeMilliseconds
                val docStart = kotlin.time.TimeSource.Monotonic.markNow()
                val d1 = Document().apply { add(StringField("id", "1", Field.Store.NO)) }
                val docMs = docStart.elapsedNow().inWholeMilliseconds
                val updateStart = kotlin.time.TimeSource.Monotonic.markNow()
                writer.updateDocument(Term("id", "1"), d1)
                val updateMs = updateStart.elapsedNow().inWholeMilliseconds
                val commitStart = kotlin.time.TimeSource.Monotonic.markNow()
                writer.commit()
                val commitMs = commitStart.elapsedNow().inWholeMilliseconds
                val setupMs = testStart.elapsedNow().inWholeMilliseconds
                println(">>> substep dir=$dirMs writer=$writerMs doc=$docMs update=$updateMs commit=$commitMs setup_total=$setupMs")

                val iters = AtomicInteger(100 + random().nextInt(if (TEST_NIGHTLY) 5000 else 1000))
                val numFullFlushes = AtomicInteger(10 + random().nextInt(if (TEST_NIGHTLY) 500 else 100))
                val done = AtomicBoolean(false)
                println(">>> iters=${iters.load()} flushes=${numFullFlushes.load()}")
                
                val threads = Array(1 + random().nextInt(4)) {
                    Thread {
                        try {
                            while (iters.decrementAndFetch() > 0 || numFullFlushes.load() > 0) {
                                writer.updateDocument(Term("id", "1"), d1)
                                if (random().nextBoolean()) {
                                    writer.addDocument(Document())
                                }
                            }
                        } catch (e: Exception) {
                            throw AssertionError(e)
                        } finally {
                            done.store(true)
                        }
                    }
                }
                for (t in threads) {
                    t.start()
                }
                val loopStart = kotlin.time.TimeSource.Monotonic.markNow()
                var opCount = 0
                try {
                    while (!done.load()) {
                        if (useGetReader) {
                            writer.getReader(true, false).use { reader ->
                                assertEquals(
                                    1L,
                                    IndexSearcher(reader)
                                        .search(TermQuery(Term("id", "1")), 10)
                                        .totalHits.value
                                )
                            }
                        } else {
                            if (random().nextBoolean()) {
                                writer.commit()
                            }
                            DirectoryReader.open(directory).use { delegate ->
                                SoftDeletesDirectoryReaderWrapper(delegate, "___soft_deletes").use { open ->
                                    assertEquals(
                                        1L,
                                        IndexSearcher(open)
                                            .search(TermQuery(Term("id", "1")), 10)
                                            .totalHits.value
                                    )
                                }
                            }
                        }
                        numFullFlushes.decrementAndFetch()
                        opCount++
                    }
                    val loopMs = loopStart.elapsedNow().inWholeMilliseconds
                    println(">>> main_loop_ms=$loopMs ops=$opCount")
                } finally {
                    while (numFullFlushes.load() > 0) {
                        numFullFlushes.decrementAndFetch()
                    }
                    for (t in threads) {
                        t.join()
                    }
                    val totalMs = testStart.elapsedNow().inWholeMilliseconds
                    println(">>> TOTAL_ms=$totalMs")
                }
            }
        }
    }


    // Test basic semantics of merge on getReader
    @Test
    @Throws(IOException::class)
    fun testMergeOnGetReader() {
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE)
            ).use { firstWriter ->
                for (i in 0..<5) {
                    TestIndexWriter.addDoc(firstWriter)
                    firstWriter.flush()
                }
                DirectoryReader.open(firstWriter).use { firstReader ->
                    assertEquals(5, firstReader.leaves().size)
                }
            }

            val iwc =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(MergeOnXMergePolicy(newMergePolicy(), MergeTrigger.GET_READER))
                    .setMaxFullFlushMergeWaitMillis(Int.MAX_VALUE.toLong())

            IndexWriter(dir, iwc).use { writerWithMergePolicy ->
                DirectoryReader.open(dir).use { unmergedReader ->
                    assertEquals(5, unmergedReader.leaves().size)
                }

                TestIndexWriter.addDoc(writerWithMergePolicy)
                DirectoryReader.open(writerWithMergePolicy).use { mergedReader ->
                    assertEquals(1, mergedReader.leaves().size)
                }
            }
        }
    }

    private class MergeOnXMergePolicy(
        `in`: MergePolicy,
        private val trigger: MergeTrigger
    ) : FilterMergePolicy(`in`) {
        override fun findFullFlushMerges(
            mergeTrigger: MergeTrigger,
            segmentInfos: SegmentInfos,
            mergeContext: MergeContext
        ): MergeSpecification? {
            // Optimize down to a single segment on commit
            if (mergeTrigger == trigger && segmentInfos.size() > 1) {
                val nonMergingSegments = mutableListOf<SegmentCommitInfo>()
                for (sci in segmentInfos) {
                    if (!mergeContext.mergingSegments.contains(sci)) {
                        nonMergingSegments.add(sci)
                    }
                }
                if (nonMergingSegments.size > 1) {
                    val mergeSpecification = MergeSpecification()
                    mergeSpecification.add(OneMerge(nonMergingSegments))
                    return mergeSpecification
                }
            }
            return null
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSetDiagnostics() {
        val logMp = newLogMergePolicy(4)
        logMp.targetSearchConcurrency = 1
        val myMergePolicy =
            object : FilterMergePolicy(logMp) {
                override fun findMerges(
                    mergeTrigger: MergeTrigger?,
                    segmentInfos: SegmentInfos?,
                    mergeContext: MergeContext?
                ): MergeSpecification? {
                    return wrapSpecification(super.findMerges(mergeTrigger, segmentInfos, mergeContext))
                }

                override fun findFullFlushMerges(
                    mergeTrigger: MergeTrigger,
                    segmentInfos: SegmentInfos,
                    mergeContext: MergeContext
                ): MergeSpecification? {
                    return wrapSpecification(super.findFullFlushMerges(mergeTrigger, segmentInfos, mergeContext))
                }

                private fun wrapSpecification(spec: MergeSpecification?): MergeSpecification? {
                    if (spec == null) {
                        return null
                    }
                    val newSpec = MergeSpecification()
                    for (merge in spec.merges) {
                        newSpec.add(
                            object : OneMerge(merge) {
                                override fun setMergeInfo(info: SegmentCommitInfo) {
                                    super.setMergeInfo(info)
                                    info.info.addDiagnostics(
                                        mutableMapOf("merge_policy" to "my_merge_policy")
                                    )
                                }
                            }
                        )
                    }
                    return newSpec
                }
            }
        val dir = newDirectory()
        var w: IndexWriter? = null
        try {
            w = IndexWriter(
                dir,
                newIndexWriterConfig().setMergePolicy(myMergePolicy).setMaxBufferedDocs(2)
            )
            val doc = Document()
            for (i in 0..<20) {
                w.addDocument(doc)
            }
            w.close()
            w = null
            val si = SegmentInfos.readLatestCommit(dir)
            var hasOneMergedSegment = false
            for (sci in si) {
                if (IndexWriter.SOURCE_MERGE == sci.info.diagnostics[IndexWriter.SOURCE]) {
                    assertEquals("my_merge_policy", sci.info.diagnostics["merge_policy"])
                    hasOneMergedSegment = true
                }
            }
            assertTrue(hasOneMergedSegment)
        } finally {
            w?.close()
            dir.close()
        }
    }

    private class MockAssertFileExistIndexInput(
        private val name: String,
        private val delegate: IndexInput,
        private val filePath: Path
    ) : IndexInput("MockAssertFileExistIndexInput(name=$name delegate=$delegate)") {

        private fun checkFileExist() {
            if (!Files.getFileSystem().exists(filePath)) {
                throw NoSuchFileException(filePath.toString())
            }
        }

        override fun close() {
            delegate.close()
        }

        override fun clone(): MockAssertFileExistIndexInput {
            return MockAssertFileExistIndexInput(name, delegate.clone(), filePath)
        }

        override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
            checkFileExist()
            val slice = delegate.slice(sliceDescription, offset, length)
            return MockAssertFileExistIndexInput(sliceDescription, slice, filePath)
        }

        override val filePointer: Long
            get() = delegate.filePointer

        override fun seek(pos: Long) {
            checkFileExist()
            delegate.seek(pos)
        }

        override fun length(): Long {
            return delegate.length()
        }

        override fun readByte(): Byte {
            checkFileExist()
            return delegate.readByte()
        }

        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            checkFileExist()
            delegate.readBytes(b, offset, len)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testForceMergeDVUpdateFileWithConcurrentFlush() {
        val waitForInitMergeReader = CountDownLatch(1)
        val waitForDVUpdate = CountDownLatch(1)
        val waitForMergeFinished = CountDownLatch(1)

        val path = createTempDir("testForceMergeDVUpdateFileWithConcurrentFlush")
        val mockDirectory =
            object : FilterDirectory(newFSDirectory(path)) {
                override fun openInput(name: String, context: IOContext): IndexInput {
                    val indexInput = super.openInput(name, context)
                    return MockAssertFileExistIndexInput(name, indexInput, path.resolve(name))
                }
            }

        val mockMergePolicy =
            OneMergeWrappingMergePolicy(
                SoftDeletesRetentionMergePolicy(
                    "soft_delete",
                    { MatchAllDocsQuery() },
                    object : LogMergePolicy() {
                        override fun size(
                            info: SegmentCommitInfo,
                            mergeContext: MergeContext
                        ): Long {
                            return sizeDocs(info, mergeContext)
                        }

                        override fun findMerges(
                            mergeTrigger: MergeTrigger?,
                            segmentInfos: SegmentInfos?,
                            mergeContext: MergeContext?
                        ): MergeSpecification? {
                            // only allow force merge
                            return null
                        }
                    }
                )
            ) { merge ->
                object : MergePolicy.OneMerge(merge.segments) {
                    override fun initMergeReaders(
                        readerFactory: IOFunction<SegmentCommitInfo, MergePolicy.MergeReader>
                    ) {
                        super.initMergeReaders(readerFactory)
                        waitForInitMergeReader.countDown()
                    }

                    override fun wrapForMerge(reader: CodecReader): CodecReader {
                        try {
                            waitForDVUpdate.await()
                        } catch (e: Exception) {
                            throw AssertionError(e)
                        }
                        return super.wrapForMerge(reader)
                    }
                }
            }

        IndexWriter(
            mockDirectory,
            newIndexWriterConfig()
                .setMergePolicy(mockMergePolicy)
                .setSoftDeletesField("soft_delete")
        ).use { writer ->
            var doc = Document()
            doc.add(StringField("id", "1", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            writer.addDocument(doc)
            writer.flush()
            doc = Document()
            doc.add(StringField("id", "2", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            writer.addDocument(doc)
            doc = Document()
            doc.add(StringField("id", "2", Field.Store.YES))
            doc.add(StringField("version", "2", Field.Store.YES))
            var field: Field = NumericDocValuesField("soft_delete", 1)
            writer.softUpdateDocument(Term("id", "2"), doc, field)
            writer.flush()

            val t = Thread {
                try {
                    writer.forceMerge(1)
                } catch (e: Throwable) {
                    throw AssertionError(e)
                } finally {
                    waitForMergeFinished.countDown()
                }
            }
            t.start()
            waitForInitMergeReader.await()

            doc = Document()
            doc.add(StringField("id", "2", Field.Store.YES))
            doc.add(StringField("version", "3", Field.Store.YES))
            field = NumericDocValuesField("soft_delete", 1)
            writer.softUpdateDocument(Term("id", "2"), doc, field)
            writer.flush()

            waitForDVUpdate.countDown()
            waitForMergeFinished.await()
        }
        mockDirectory.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMergeDVUpdateFileOnGetReaderWithConcurrentFlush() {
        val waitForInitMergeReader = CountDownLatch(1)
        val waitForDVUpdate = CountDownLatch(1)

        val path = createTempDir("testMergeDVUpdateFileOnGetReaderWithConcurrentFlush")
        val mockDirectory =
            object : FilterDirectory(newFSDirectory(path)) {
                override fun openInput(name: String, context: IOContext): IndexInput {
                    val indexInput = super.openInput(name, context)
                    return MockAssertFileExistIndexInput(name, indexInput, path.resolve(name))
                }
            }

        IndexWriter(
            mockDirectory,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE)
        ).use { firstWriter ->
            var doc = Document()
            doc.add(StringField("id", "1", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            firstWriter.addDocument(doc)
            firstWriter.flush()
            doc = Document()
            doc.add(StringField("id", "2", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            firstWriter.addDocument(doc)
            doc = Document()
            doc.add(StringField("id", "2", Field.Store.YES))
            doc.add(StringField("version", "2", Field.Store.YES))
            val field: Field = NumericDocValuesField("soft_delete", 1)
            firstWriter.softUpdateDocument(Term("id", "2"), doc, field)
            firstWriter.flush()
            DirectoryReader.open(firstWriter).use { firstReader ->
                assertEquals(2, firstReader.leaves().size)
            }
        }

        val mockConcurrentMergeScheduler =
            object : ConcurrentMergeScheduler() {
                override suspend fun merge(mergeSource: MergeSource, trigger: MergeTrigger) {
                    waitForInitMergeReader.countDown()
                    try {
                        waitForDVUpdate.await()
                    } catch (e: Exception) {
                        throw AssertionError(e)
                    }
                    super.merge(mergeSource, trigger)
                }
            }

        val iwc =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(MergeOnXMergePolicy(newMergePolicy(), MergeTrigger.GET_READER))
                .setMaxFullFlushMergeWaitMillis(Int.MAX_VALUE.toLong())
                .setMergeScheduler(mockConcurrentMergeScheduler)

        val writerWithMergePolicy = IndexWriter(mockDirectory, iwc)

        val t = Thread {
            try {
                waitForInitMergeReader.await()

                val updateDoc = Document()
                updateDoc.add(StringField("id", "2", Field.Store.YES))
                updateDoc.add(StringField("version", "3", Field.Store.YES))
                val softDeleteField: Field = NumericDocValuesField("soft_delete", 1)
                writerWithMergePolicy.softUpdateDocument(
                    Term("id", "2"),
                    updateDoc,
                    softDeleteField
                )
                val reader = DirectoryReader.open(writerWithMergePolicy, true, false)
                reader.close()

                waitForDVUpdate.countDown()
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }
        t.start()

        DirectoryReader.open(writerWithMergePolicy).use { mergedReader ->
            assertEquals(1, mergedReader.leaves().size)
        }

        writerWithMergePolicy.close()
        mockDirectory.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMergeDVUpdateFileOnCommitWithConcurrentFlush() {
        val waitForInitMergeReader = CountDownLatch(1)
        val waitForDVUpdate = CountDownLatch(1)

        val path = createTempDir("testMergeDVUpdateFileOnCommitWithConcurrentFlush")
        val mockDirectory =
            object : FilterDirectory(newFSDirectory(path)) {
                override fun openInput(name: String, context: IOContext): IndexInput {
                    val indexInput = super.openInput(name, context)
                    return MockAssertFileExistIndexInput(name, indexInput, path.resolve(name))
                }
            }

        IndexWriter(
            mockDirectory,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE)
        ).use { firstWriter ->
            var doc = Document()
            doc.add(StringField("id", "1", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            firstWriter.addDocument(doc)
            firstWriter.flush()
            doc = Document()
            doc.add(StringField("id", "2", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            firstWriter.addDocument(doc)
            doc = Document()
            doc.add(StringField("id", "2", Field.Store.YES))
            doc.add(StringField("version", "2", Field.Store.YES))
            val field: Field = NumericDocValuesField("soft_delete", 1)
            firstWriter.softUpdateDocument(Term("id", "2"), doc, field)
            firstWriter.flush()
            DirectoryReader.open(firstWriter).use { firstReader ->
                assertEquals(2, firstReader.leaves().size)
            }
        }

        val mockConcurrentMergeScheduler =
            object : ConcurrentMergeScheduler() {
                override suspend fun merge(mergeSource: MergeSource, trigger: MergeTrigger) {
                    waitForInitMergeReader.countDown()
                    try {
                        waitForDVUpdate.await()
                    } catch (e: Exception) {
                        throw AssertionError(e)
                    }
                    super.merge(mergeSource, trigger)
                }
            }

        val iwc =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(MergeOnXMergePolicy(newMergePolicy(), MergeTrigger.COMMIT))
                .setMaxFullFlushMergeWaitMillis(Int.MAX_VALUE.toLong())
                .setMergeScheduler(mockConcurrentMergeScheduler)

        val writerWithMergePolicy = IndexWriter(mockDirectory, iwc)

        val t = Thread {
            try {
                waitForInitMergeReader.await()

                val updateDoc = Document()
                updateDoc.add(StringField("id", "2", Field.Store.YES))
                updateDoc.add(StringField("version", "3", Field.Store.YES))
                val softDeleteField: Field = NumericDocValuesField("soft_delete", 1)
                writerWithMergePolicy.softUpdateDocument(
                    Term("id", "2"),
                    updateDoc,
                    softDeleteField
                )
                val reader = DirectoryReader.open(writerWithMergePolicy, true, false)
                reader.close()

                waitForDVUpdate.countDown()
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }
        t.start()

        writerWithMergePolicy.commit()
        assertEquals(2, writerWithMergePolicy.getSegmentCount())

        writerWithMergePolicy.close()
        mockDirectory.close()
    }

    @Test
    @Throws(Exception::class)
    fun testForceMergeWithPendingHardAndSoftDeleteFile() {
        val path = createTempDir("testForceMergeWithPendingHardAndSoftDeleteFile")
        val mockDirectory =
            object : FilterDirectory(newFSDirectory(path)) {
                override fun openInput(name: String, context: IOContext): IndexInput {
                    val indexInput = super.openInput(name, context)
                    return MockAssertFileExistIndexInput(name, indexInput, path.resolve(name))
                }
            }

        val mockMergePolicy =
            OneMergeWrappingMergePolicy(
                object : TieredMergePolicy() {
                    override fun findMerges(
                        mergeTrigger: MergeTrigger?,
                        segmentInfos: SegmentInfos?,
                        mergeContext: MergeContext?
                    ): MergeSpecification? {
                        // only allow force merge
                        return null
                    }
                }
            ) { merge ->
                MergePolicy.OneMerge(merge.segments)
            }

        IndexWriter(
            mockDirectory,
            newIndexWriterConfig().setMergePolicy(mockMergePolicy)
        ).use { writer ->
            var doc = Document()
            doc.add(StringField("id", "1", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            writer.addDocument(doc)
            writer.commit()

            doc = Document()
            doc.add(StringField("id", "2", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            writer.addDocument(doc)

            doc = Document()
            doc.add(StringField("id", "3", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            writer.addDocument(doc)

            doc = Document()
            doc.add(StringField("id", "4", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            writer.addDocument(doc)

            doc = Document()
            doc.add(StringField("id", "5", Field.Store.YES))
            doc.add(StringField("version", "1", Field.Store.YES))
            writer.addDocument(doc)
            writer.commit()

            doc = Document()
            doc.add(StringField("id", "2", Field.Store.YES))
            doc.add(StringField("version", "2", Field.Store.YES))
            writer.updateDocument(Term("id", "2"), doc)
            writer.commit()

            doc = Document()
            doc.add(StringField("id", "3", Field.Store.YES))
            doc.add(StringField("version", "2", Field.Store.YES))
            writer.updateDocument(Term("id", "3"), doc)

            doc = Document()
            doc.add(StringField("id", "4", Field.Store.YES))
            doc.add(StringField("version", "2", Field.Store.YES))
            val field: Field = NumericDocValuesField("soft_delete", 1)
            writer.softUpdateDocument(Term("id", "4"), doc, field)

            val reader = writer.getReader(true, false)
            reader.close()
            writer.commit()

            writer.forceMerge(1)
        }
        mockDirectory.close()
    }
}
