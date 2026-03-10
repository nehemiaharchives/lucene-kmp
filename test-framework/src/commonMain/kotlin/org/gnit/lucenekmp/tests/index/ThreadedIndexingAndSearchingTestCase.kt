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
package org.gnit.lucenekmp.tests.index

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.BinaryPoint
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.IntField
import org.gnit.lucenekmp.document.KeywordField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.SegmentReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.internal.tests.IndexWriterAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.Executors
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.util.FailOnNonBulkMergesInfoStream
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// TODO
//   - mix in forceMerge, addIndexes
//   - randomly mix in non-congruent docs

/** Utility class that spawns multiple indexing and searching threads. */
@OptIn(ExperimentalAtomicApi::class)
abstract class ThreadedIndexingAndSearchingTestCase : LuceneTestCase() {

    private companion object {
        val INDEX_WRITER_ACCESS: IndexWriterAccess = TestSecrets.getIndexWriterAccess()
    }

    protected val failed = AtomicBoolean(false)
    protected val addCount = AtomicInt(0)
    protected val delCount = AtomicInt(0)
    protected val packCount = AtomicInt(0)

    protected lateinit var dir: Directory
    protected lateinit var writer: IndexWriter

    private class SubDocs(val packID: String, val subIDs: MutableList<String>) {
        var deleted: Boolean = false
    }

    // Called per-search
    @Throws(Exception::class)
    protected abstract fun getCurrentSearcher(): IndexSearcher

    @Throws(Exception::class)
    protected abstract fun getFinalSearcher(): IndexSearcher

    @Throws(Exception::class)
    protected open fun releaseSearcher(s: IndexSearcher) {}

    // Called once to run searching
    @Throws(Exception::class)
    protected abstract fun doSearching(es: ExecutorService?, maxIterations: Int)

    protected open fun getDirectory(inp: Directory): Directory {
        return inp
    }

    @Throws(Exception::class)
    protected open fun updateDocuments(id: Term, docs: List<Iterable<IndexableField>>) {
        writer.updateDocuments(id, docs)
    }

    @Throws(Exception::class)
    protected open fun addDocuments(id: Term, docs: List<Iterable<IndexableField>>) {
        writer.addDocuments(docs)
    }

    @Throws(Exception::class)
    protected open fun addDocument(id: Term, doc: Iterable<IndexableField>) {
        writer.addDocument(doc)
    }

    @Throws(Exception::class)
    protected open fun updateDocument(term: Term, doc: Iterable<IndexableField>) {
        writer.updateDocument(term, doc)
    }

    @Throws(Exception::class)
    protected open fun deleteDocuments(term: Term) {
        writer.deleteDocuments(term)
    }

    protected open fun doAfterIndexingThreadDone() {}

    private fun launchIndexingThreads(
        docs: LineFileDocs,
        numThreads: Int,
        maxIterations: Int,
        delIDs: MutableSet<String>,
        delPackIDs: MutableSet<String>,
        allSubDocs: MutableList<SubDocs>,
        sharedStateLock: ReentrantLock
    ): Array<Thread> {
        val threads = arrayOfNulls<Thread>(numThreads)
        for (thread in 0 until numThreads) {
            threads[thread] = object : Thread() {
                override fun run() {
                    // TODO: would be better if this were cross thread, so that we make sure one thread
                    // deleting anothers added docs works:
                    val toDeleteIDs = mutableListOf<String>()
                    val toDeleteSubDocs = mutableListOf<SubDocs>()
                    var iterations = 0
                    while (++iterations < maxIterations && !failed.load()) {
                        try {

                            // Occasional longish pause if running
                            // nightly
                            if (LuceneTestCase.TEST_NIGHTLY && random().nextInt(6) == 3) {
                                if (VERBOSE) {
                                    println(Thread.currentThread().getName() + ": now long sleep")
                                }
                                Thread.sleep(TestUtil.nextInt(random(), 50, 500).toLong())
                            }

                            // Rate limit ingest rate:
                            if (random().nextInt(7) == 5) {
                                Thread.sleep(TestUtil.nextInt(random(), 1, 10).toLong())
                                if (VERBOSE) {
                                    println(Thread.currentThread().getName() + ": done sleep")
                                }
                            }

                            val doc = docs.nextDoc() ?: break

                            // Maybe add randomly named field
                            val addedField: String?
                            if (random().nextBoolean()) {
                                addedField = "extra" + random().nextInt(40)
                                doc.add(newTextField(addedField, "a random field", Field.Store.YES))
                            } else {
                                addedField = null
                            }

                            if (random().nextBoolean()) {

                                if (random().nextBoolean()) {
                                    // Add/update doc block:
                                    val packID: String
                                    val delSubDocs: SubDocs?
                                    if (toDeleteSubDocs.size > 0 && random().nextBoolean()) {
                                        delSubDocs = toDeleteSubDocs[random().nextInt(toDeleteSubDocs.size)]
                                        assert(!delSubDocs.deleted)
                                        toDeleteSubDocs.remove(delSubDocs)
                                        // Update doc block, replacing prior packID
                                        packID = delSubDocs.packID
                                    } else {
                                        delSubDocs = null
                                        // Add doc block, using new packID
                                        packID = packCount.fetchAndIncrement().toString()
                                    }

                                    val packIDField = newStringField("packID", packID, Field.Store.YES)
                                    val docIDs = mutableListOf<String>()
                                    val subDocs = SubDocs(packID, docIDs)
                                    val docsList = mutableListOf<Document>()

                                    sharedStateLock.withLock {
                                        allSubDocs.add(subDocs)
                                    }
                                    doc.add(packIDField)
                                    docsList.add(cloneDocument(doc))
                                    docIDs.add(doc.get("docid")!!)

                                    val maxDocCount = TestUtil.nextInt(random(), 1, 10)
                                    while (docsList.size < maxDocCount) {
                                        val nextDoc = docs.nextDoc() ?: break
                                        docsList.add(cloneDocument(nextDoc))
                                        docIDs.add(nextDoc.get("docid")!!)
                                    }
                                    addCount.addAndFetch(docsList.size)

                                    val packIDTerm = Term("packID", packID)

                                    if (delSubDocs != null) {
                                        delSubDocs.deleted = true
                                        sharedStateLock.withLock {
                                            delIDs.addAll(delSubDocs.subIDs)
                                        }
                                        delCount.addAndFetch(delSubDocs.subIDs.size)
                                        if (VERBOSE) {
                                            println(
                                                Thread.currentThread().getName() +
                                                        ": update pack packID=" +
                                                        delSubDocs.packID +
                                                        " count=" +
                                                        docsList.size +
                                                        " docs=" +
                                                        docIDs
                                            )
                                        }
                                        updateDocuments(packIDTerm, docsList)
                                    } else {
                                        if (VERBOSE) {
                                            println(
                                                Thread.currentThread().getName() +
                                                        ": add pack packID=" +
                                                        packID +
                                                        " count=" +
                                                        docsList.size +
                                                        " docs=" +
                                                        docIDs
                                            )
                                        }
                                        addDocuments(packIDTerm, docsList)
                                    }
                                    doc.removeField("packID")

                                    if (random().nextInt(5) == 2) {
                                        if (VERBOSE) {
                                            println(Thread.currentThread().getName() + ": buffer del id:" + packID)
                                        }
                                        toDeleteSubDocs.add(subDocs)
                                    }

                                } else {
                                    // Add single doc
                                    val docid = doc.get("docid")!!
                                    if (VERBOSE) {
                                        println(Thread.currentThread().getName() + ": add doc docid:" + docid)
                                    }
                                    addDocument(Term("docid", docid), doc)
                                    addCount.fetchAndIncrement()

                                    if (random().nextInt(5) == 3) {
                                        if (VERBOSE) {
                                            println(
                                                Thread.currentThread().getName() +
                                                        ": buffer del id:" +
                                                        doc.get("docid")
                                            )
                                        }
                                        toDeleteIDs.add(docid)
                                    }
                                }
                            } else {

                                // Update single doc, but we never re-use
                                // and ID so the delete will never
                                // actually happen:
                                if (VERBOSE) {
                                    println(Thread.currentThread().getName() + ": update doc id:" + doc.get("docid"))
                                }
                                val docid = doc.get("docid")!!
                                updateDocument(Term("docid", docid), doc)
                                addCount.fetchAndIncrement()

                                if (random().nextInt(5) == 3) {
                                    if (VERBOSE) {
                                        println(
                                            Thread.currentThread().getName() +
                                                    ": buffer del id:" +
                                                    doc.get("docid")
                                        )
                                    }
                                    toDeleteIDs.add(docid)
                                }
                            }

                            if (random().nextInt(30) == 17) {
                                if (VERBOSE) {
                                    println(
                                        Thread.currentThread().getName() +
                                                ": apply " +
                                                toDeleteIDs.size +
                                                " deletes"
                                    )
                                }
                                for (id in toDeleteIDs) {
                                    if (VERBOSE) {
                                        println(Thread.currentThread().getName() + ": del term=id:" + id)
                                    }
                                    deleteDocuments(Term("docid", id))
                                }
                                val count = delCount.addAndFetch(toDeleteIDs.size)
                                if (VERBOSE) {
                                    println(Thread.currentThread().getName() + ": tot $count deletes")
                                }
                                sharedStateLock.withLock {
                                    delIDs.addAll(toDeleteIDs)
                                }
                                toDeleteIDs.clear()

                                for (subDocs in toDeleteSubDocs) {
                                    assert(!subDocs.deleted)
                                    sharedStateLock.withLock {
                                        delPackIDs.add(subDocs.packID)
                                    }
                                    deleteDocuments(Term("packID", subDocs.packID))
                                    subDocs.deleted = true
                                    if (VERBOSE) {
                                        println(
                                            Thread.currentThread().getName() +
                                                    ": del subs: " +
                                                    subDocs.subIDs +
                                                    " packID=" +
                                                    subDocs.packID
                                        )
                                    }
                                    sharedStateLock.withLock {
                                        delIDs.addAll(subDocs.subIDs)
                                    }
                                    delCount.addAndFetch(subDocs.subIDs.size)
                                }
                                toDeleteSubDocs.clear()
                            }
                            if (addedField != null) {
                                doc.removeField(addedField)
                            }
                        } catch (t: Throwable) {
                            println(Thread.currentThread().getName() + ": hit exc")
                            t.printStackTrace()
                            failed.store(true)
                            throw RuntimeException(t)
                        }
                    }
                    if (VERBOSE) {
                        println(Thread.currentThread().getName() + ": indexing done")
                    }

                    doAfterIndexingThreadDone()
                }
            }
            threads[thread]!!.start()
        }

        @Suppress("UNCHECKED_CAST")
        return threads as Array<Thread>
    }

    @Throws(Exception::class)
    protected open fun runSearchThreads(maxIterations: Int) {
        val numThreads = if (TEST_NIGHTLY) TestUtil.nextInt(random(), 1, 5) else 2
        val searchThreads = arrayOfNulls<Thread>(numThreads)
        val totHits = AtomicLong(0L)

        // silly starting guess:
        val totTermCount = AtomicInt(100)

        // TODO: we should enrich this to do more interesting searches
        for (thread in 0 until searchThreads.size) {
            searchThreads[thread] = object : Thread() {
                override fun run() {
                    if (VERBOSE) {
                        println(Thread.currentThread().getName() + ": launch search thread")
                    }
                    var iterations = 0
                    while (++iterations < maxIterations && !failed.load()) {
                        try {
                            val s = getCurrentSearcher()
                            try {
                                // Verify 1) IW is correctly setting
                                // diagnostics, and 2) segment warming for
                                // merged segments is actually happening:
                                val leaves: List<LeafReaderContext> = s.indexReader.leaves()
                                for (sub in leaves) {
                                    val segReader = sub.reader() as SegmentReader
                                    val diagnostics: Map<String, String> = segReader.segmentInfo.info.diagnostics
                                    assertNotNull(diagnostics)
                                    val source = diagnostics["source"]
                                    assertNotNull(source)
                                    if (source == "merge") {
                                        assertTrue(
                                            !assertMergedSegmentsWarmed ||
                                                    warmedLock.withLock { warmed.containsKey(segReader.segmentInfo.info) },
                                            "sub reader $sub wasn't warmed: warmed=$warmed diagnostics=$diagnostics si=${segReader.segmentInfo}"
                                        )
                                    }
                                }
                                if (s.indexReader.numDocs() > 0) {
                                    smokeTestSearcher(s)
                                    val terms = MultiTerms.getTerms(s.indexReader, "body")
                                    if (terms == null) {
                                        continue
                                    }
                                    val termsEnum = terms.iterator()
                                    var seenTermCount = 0
                                    val shift: Int
                                    val trigger: Int
                                    if (totTermCount.load() < 30) {
                                        shift = 0
                                        trigger = 1
                                    } else {
                                        trigger = totTermCount.load() / 30
                                        shift = random().nextInt(trigger)
                                    }
                                    var iters = 0
                                    while (++iters < maxIterations) {
                                        val term: BytesRef? = termsEnum.next()
                                        if (term == null) {
                                            totTermCount.store(seenTermCount)
                                            break
                                        }
                                        seenTermCount++
                                        // search 30 terms
                                        if ((seenTermCount + shift) % trigger == 0) {
                                            // if (VERBOSE) {
                                            // println(Thread.currentThread().getName() + " now search body:" + term.utf8ToString())
                                            // }
                                            totHits.addAndFetch(runQuery(s, TermQuery(Term("body", term))))
                                        }
                                    }
                                    // if (VERBOSE) {
                                    // println(Thread.currentThread().getName() + ": search done")
                                    // }
                                }
                            } finally {
                                releaseSearcher(s)
                            }
                        } catch (t: Throwable) {
                            println(Thread.currentThread().getName() + ": hit exc")
                            failed.store(true)
                            t.printStackTrace()
                            throw RuntimeException(t)
                        }
                    }
                }
            }
            searchThreads[thread]!!.start()
        }

        for (thread in searchThreads) {
            thread!!.join()
        }

        if (VERBOSE) {
            println("TEST: DONE search: totHits=$totHits")
        }
    }

    @Throws(Exception::class)
    protected open fun doAfterWriter(es: ExecutorService?) {}

    @Throws(Exception::class)
    protected open fun doClose() {}

    protected open var assertMergedSegmentsWarmed: Boolean = true

    // Normally WeakHashMap but KMP has no WeakHashMap; use a regular map guarded by a lock.
    // Key is SegmentInfo (stands in for the core object) since SEGMENT_READER_ACCESS not yet ported.
    private val warmedLock = ReentrantLock()
    private val warmed: MutableMap<Any, Boolean> = mutableMapOf()

    @Throws(Exception::class)
    fun runTest(testName: String) {

        failed.store(false)
        addCount.store(0)
        delCount.store(0)
        packCount.store(0)

        val t0 = System.nanoTime()

        val random = random()
        val docs = LineFileDocs(random)
        val tempDir = createTempDir(testName)
        dir = getDirectory(newMockFSDirectory(tempDir)) // some subclasses rely on this being MDW
        if (dir is BaseDirectoryWrapper) {
            (dir as BaseDirectoryWrapper)
                .checkIndexOnClose = false // don't double-checkIndex, we do it ourselves.
        }
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val conf: IndexWriterConfig = newIndexWriterConfig(analyzer).setCommitOnClose(false)
        conf.setInfoStream(FailOnNonBulkMergesInfoStream())
        if (conf.mergePolicy is MockRandomMergePolicy) {
            (conf.mergePolicy as MockRandomMergePolicy).doNonBulkMerges = false
        }

        // ensureSaneIWCOnNightly(conf) -- not yet ported

        conf.setMergedSegmentWarmer { reader ->
            if (VERBOSE) {
                println("TEST: now warm merged reader=$reader")
            }
            // Store segment info as a proxy for the core (SEGMENT_READER_ACCESS not yet ported)
            val coreKey = (reader as SegmentReader).segmentInfo.info
            warmedLock.withLock { warmed[coreKey] = true }
            val maxDoc = reader.maxDoc()
            val liveDocs = reader.liveDocs
            var sum = 0
            val inc = maxOf(1, maxDoc / 50)
            val storedFields = reader.storedFields()
            var docID = 0
            while (docID < maxDoc) {
                if (liveDocs == null || liveDocs.get(docID)) {
                    val doc = storedFields.document(docID)
                    sum += doc.getFields().size
                }
                docID += inc
            }

            val searcher = newSearcher(reader, false)
            sum += searcher.search(TermQuery(Term("body", "united")), 10).totalHits.value.toInt()

            if (VERBOSE) {
                println("TEST: warm visited $sum fields")
            }
        }

        // VERBOSE: omit PrintStreamInfoStream(System.out) since System.out is not available in KMP

        writer = IndexWriter(dir, conf)
        // TestUtil.reduceOpenFiles(writer) -- not yet ported

        val es: ExecutorService? =
            if (random().nextBoolean())
                null
            else
                Executors.newCachedThreadPool(NamedThreadFactory(testName))

        doAfterWriter(es)

        val NUM_INDEX_THREADS = TestUtil.nextInt(random(), 2, 4)

        val MAX_ITERATIONS = if (LuceneTestCase.TEST_NIGHTLY) 200 else 10 * RANDOM_MULTIPLIER // TODO reduced to 10*RANDOM_MULTIPLIER for dev speed

        val sharedStateLock = ReentrantLock()
        val delIDs: MutableSet<String> = mutableSetOf()
        val delPackIDs: MutableSet<String> = mutableSetOf()
        val allSubDocs: MutableList<SubDocs> = mutableListOf()

        val indexThreads =
            launchIndexingThreads(
                docs, NUM_INDEX_THREADS, MAX_ITERATIONS, delIDs, delPackIDs, allSubDocs, sharedStateLock
            )

        if (VERBOSE) {
            println(
                "TEST: DONE start $NUM_INDEX_THREADS indexing threads [${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)} ms]"
            )
        }

        // Let index build up a bit
        Thread.sleep(100)

        doSearching(es, MAX_ITERATIONS)

        if (VERBOSE) {
            println(
                "TEST: all searching done [${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)} ms]"
            )
        }

        for (thread in indexThreads) {
            thread.join()
        }

        val delIDsSnapshot = sharedStateLock.withLock { delIDs.toSet() }
        val delPackIDsSnapshot = sharedStateLock.withLock { delPackIDs.toSet() }
        val allSubDocsSnapshot = sharedStateLock.withLock { allSubDocs.toList() }

        if (VERBOSE) {
            println(
                "TEST: done join indexing threads [${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)} ms]; addCount=$addCount delCount=$delCount"
            )
        }

        val s = getFinalSearcher()
        if (VERBOSE) {
            println("TEST: finalSearcher=$s")
        }

        assertFalse(failed.load())

        var doFail = false

        // Verify: make sure delIDs are in fact deleted:
        for (id in delIDsSnapshot) {
            val hits: TopDocs = s.search(TermQuery(Term("docid", id)), 1)
            if (hits.totalHits.value != 0L) {
                println(
                    "doc id=$id is supposed to be deleted, but got ${hits.totalHits.value} hits; first docID=${hits.scoreDocs[0].doc}"
                )
                doFail = true
            }
        }

        // Verify: make sure delPackIDs are in fact deleted:
        for (id in delPackIDsSnapshot) {
            val hits: TopDocs = s.search(TermQuery(Term("packID", id)), 1)
            if (hits.totalHits.value != 0L) {
                println("packID=$id is supposed to be deleted, but got ${hits.totalHits.value} matches")
                doFail = true
            }
        }

        // Verify: make sure each group of sub-docs are still in docID order:
        for (subDocs in allSubDocsSnapshot) {
            var hits: TopDocs = s.search(TermQuery(Term("packID", subDocs.packID)), 20)
            val storedFields = s.storedFields()
            if (!subDocs.deleted) {
                // We sort by relevance but the scores should be identical so sort falls back to by docID:
                if (hits.totalHits.value != subDocs.subIDs.size.toLong()) {
                    println(
                        "packID=${subDocs.packID}: expected ${subDocs.subIDs.size} hits but got ${hits.totalHits.value}"
                    )
                    doFail = true
                } else {
                    var lastDocID = -1
                    var startDocID = -1
                    for (scoreDoc in hits.scoreDocs) {
                        val docID = scoreDoc.doc
                        if (lastDocID != -1) {
                            assertEquals(1 + lastDocID, docID)
                        } else {
                            startDocID = docID
                        }
                        lastDocID = docID
                        val doc = storedFields.document(docID)
                        assertEquals(subDocs.packID, doc.get("packID"))
                    }

                    lastDocID = startDocID - 1
                    for (subID in subDocs.subIDs) {
                        hits = s.search(TermQuery(Term("docid", subID)), 1)
                        assertEquals(1, hits.totalHits.value.toInt())
                        val docID = hits.scoreDocs[0].doc
                        if (lastDocID != -1) {
                            assertEquals(1 + lastDocID, docID)
                        }
                        lastDocID = docID
                    }
                }
            } else {
                // Pack was deleted -- make sure its docs are
                // deleted.  We can't verify packID is deleted
                // because we can re-use packID for update:
                for (subID in subDocs.subIDs) {
                    assertEquals(0, s.search(TermQuery(Term("docid", subID)), 1).totalHits.value.toInt())
                }
            }
        }

        // Verify: make sure all not-deleted docs are in fact
        // not deleted:
        val endID = docs.nextDoc()!!.get("docid")!!.toInt()
        docs.close()

        for (id in 0 until endID) {
            val stringID = "$id"
            if (!delIDsSnapshot.contains(stringID)) {
                val hits: TopDocs = s.search(TermQuery(Term("docid", stringID)), 1)
                if (hits.totalHits.value != 1L) {
                    println(
                        "doc id=$stringID is not supposed to be deleted, but got hitCount=${hits.totalHits.value}; delIDs=$delIDsSnapshot"
                    )
                    doFail = true
                }
            }
        }
        assertFalse(doFail)

        assertEquals(
            addCount.load() - delCount.load(),
            s.indexReader.numDocs(),
            "index=${INDEX_WRITER_ACCESS.segString(writer)} addCount=$addCount delCount=$delCount"
        )
        releaseSearcher(s)

        writer.commit()

        assertEquals(
            addCount.load() - delCount.load(),
            writer.getDocStats().numDocs.toInt(),
            "index=${INDEX_WRITER_ACCESS.segString(writer)} addCount=$addCount delCount=$delCount"
        )

        doClose()

        try {
            writer.commit()
        } finally {
            writer.close()
        }

        // Cannot close until after writer is closed because
        // writer has merged segment warmer that uses IS to run
        // searches, and that IS may be using this es!
        if (es != null) {
            es.shutdown()
            runBlocking { es.awaitTermination(1, TimeUnit.SECONDS) }
        }

        TestUtil.checkIndex(dir)
        dir.close()

        if (VERBOSE) {
            println("TEST: done [${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)} ms]")
        }
    }

    @Throws(Exception::class)
    private fun runQuery(s: IndexSearcher, q: Query): Long {
        s.search(q, 10)
        val hitCount =
            s.search(q, 10, Sort(SortField("titleDV", SortField.Type.STRING)))
                .totalHits
                .value
        val dvSort = Sort(SortField("titleDV", SortField.Type.STRING))
        val hitCount2 = s.search(q, 10, dvSort).totalHits.value
        assertEquals(hitCount, hitCount2)
        return hitCount
    }

    @Throws(Exception::class)
    protected open fun smokeTestSearcher(s: IndexSearcher) {
        runQuery(s, TermQuery(Term("body", "united")))
        runQuery(s, TermQuery(Term("titleTokenized", "states")))
        val pq = PhraseQuery("body", "united", "states")
        runQuery(s, pq)
    }

    /**
     * Simple clone of a Document for use in indexing threads.
     */
    private fun cloneDocument(doc: Document): Document {
        val doc2 = Document()
        for (f in doc.getFields()) {
            val field1 = f as Field
            val field2: Field
            val dvType = field1.fieldType().docValuesType()
            val dimCount = field1.fieldType().pointDimensionCount()
            if (f is KeywordField) {
                field2 =
                    KeywordField(
                        f.name(),
                        f.stringValue()!!,
                        if (f.fieldType().stored()) Field.Store.YES else Field.Store.NO
                    )
            } else if (f is IntField) {
                field2 =
                    IntField(
                        f.name(),
                        f.numericValue()!!.toInt(),
                        if (f.fieldType().stored()) Field.Store.YES else Field.Store.NO
                    )
            } else if (dvType != DocValuesType.NONE) {
                field2 =
                    when (dvType) {
                        DocValuesType.NUMERIC -> NumericDocValuesField(field1.name(), field1.numericValue()!!.toLong())
                        DocValuesType.BINARY -> BinaryDocValuesField(field1.name(), field1.binaryValue())
                        DocValuesType.SORTED -> SortedDocValuesField(field1.name(), field1.binaryValue()!!)
                        DocValuesType.NONE, DocValuesType.SORTED_SET, DocValuesType.SORTED_NUMERIC ->
                            throw IllegalStateException("unknown Type: $dvType")
                    }
            } else if (dimCount != 0) {
                val br = field1.binaryValue()!!
                val bytes = ByteArray(br.length)
                System.arraycopy(br.bytes, br.offset, bytes, 0, br.length)
                field2 = BinaryPoint(field1.name(), bytes, field1.fieldType())
            } else {
                field2 = Field(field1.name(), field1.stringValue()!!, field1.fieldType())
            }
            doc2.add(field2)
        }

        return doc2
    }
}
