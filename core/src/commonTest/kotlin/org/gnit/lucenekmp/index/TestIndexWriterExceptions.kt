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

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.FileNotFoundException
import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper.FakeIOException
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CloseableThreadLocal
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.jdkport.Thread
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking

private val testIndexWriterExceptionsLogger = KotlinLogging.logger {}

// @SuppressCodecs("SimpleText") // too slow here
@OptIn(ExperimentalAtomicApi::class)
class TestIndexWriterExceptions : LuceneTestCase() {
    companion object {
        // Stage names used by FailOnlyInCommit
        private const val PREPARE_STAGE = "prepareCommit"
        private const val FINISH_STAGE = "finishCommit"
        // Stage names and error message used by FailOnTermVectors
        private const val TV_INIT_STAGE = "initTermVectorsWriter"
        private const val TV_AFTER_INIT_STAGE = "finishDocument"
        private const val TV_EXC_MSG = "FOTV"
    }


    /** Fake OOM error used in place of java.lang.OutOfMemoryError for KMP common code. */
    private class FakeOOME(msg: String) : Error(msg)

    /* DocCopyIterator is a Java record in upstream; ported as a class with companion object for static FieldTypes */
    private class DocCopyIterator(val doc: Document, val count: Int) : Iterable<Document> {
        /* private field types */
        companion object {
            val custom1 = FieldType(TextField.TYPE_NOT_STORED)
            val custom2 = FieldType()
            val custom3 = FieldType()
            val custom4 = FieldType(StringField.TYPE_NOT_STORED)
            val custom5 = FieldType(TextField.TYPE_STORED)

            init {
                custom1.setStoreTermVectors(true)
                custom1.setStoreTermVectorPositions(true)
                custom1.setStoreTermVectorOffsets(true)

                custom2.setStored(true)
                custom2.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)

                custom3.setStored(true)

                custom4.setStoreTermVectors(true)
                custom4.setStoreTermVectorPositions(true)
                custom4.setStoreTermVectorOffsets(true)

                custom5.setStoreTermVectors(true)
                custom5.setStoreTermVectorPositions(true)
                custom5.setStoreTermVectorOffsets(true)
            }
        }

        override fun iterator(): Iterator<Document> {
            return object : Iterator<Document> {
                var upto = 0

                override fun hasNext(): Boolean {
                    return upto < count
                }

                override fun next(): Document {
                    upto++
                    return doc
                }
            }
        }
    }

    private inner class IndexerThread(val i: Int, val writer: IndexWriter) {
        val name: String = "Indexer $i"
        val r: Random = Random(random().nextLong())

        @Volatile
        var failure: Throwable? = null

        fun run() {
            val doc = Document()

            doc.add(newTextField(r, "content1", "aaa bbb ccc ddd", Field.Store.YES))
            doc.add(newField(r, "content6", "aaa bbb ccc ddd", DocCopyIterator.custom1))
            doc.add(newField(r, "content2", "aaa bbb ccc ddd", DocCopyIterator.custom2))
            doc.add(newField(r, "content3", "aaa bbb ccc ddd", DocCopyIterator.custom3))

            doc.add(newTextField(r, "content4", "aaa bbb ccc ddd", Field.Store.NO))
            doc.add(newStringField(r, "content5", "aaa bbb ccc ddd", Field.Store.NO))
            doc.add(NumericDocValuesField("numericdv", 5))
            doc.add(BinaryDocValuesField("binarydv", BytesRef("hello")))
            doc.add(SortedDocValuesField("sorteddv", BytesRef("world")))
            doc.add(SortedSetDocValuesField("sortedsetdv", BytesRef("hellllo")))
            doc.add(SortedSetDocValuesField("sortedsetdv", BytesRef("again")))
            doc.add(SortedNumericDocValuesField("sortednumericdv", 10))
            doc.add(SortedNumericDocValuesField("sortednumericdv", 5))

            doc.add(newField(r, "content7", "aaa bbb ccc ddd", DocCopyIterator.custom4))

            val idField = newField(r, "id", "", DocCopyIterator.custom2)
            doc.add(idField)

            val maxIterations = 250
            var iterations = 0
            // Top-level catch ensures no exception escapes on Kotlin/Native (where
            // an uncaught coroutine exception crashes the process).
            try {
                do {
                    if (VERBOSE) {
                        println("$name: TEST: IndexerThread: cycle")
                    }
                    doFail.set(true)
                    val id = "" + r.nextInt(50)
                    idField.setStringValue(id)
                    val idTerm = Term("id", id)
                    try {
                        if (r.nextBoolean()) {
                            writer.updateDocuments(idTerm, DocCopyIterator(doc, TestUtil.nextInt(r, 1, 20)))
                        } else {
                            writer.updateDocument(idTerm, doc)
                        }
                    } catch (re: RuntimeException) {
                        if (VERBOSE) {
                            println("$name: EXC: $re")
                        }
                        try {
                            // Use non-concurrent (single-thread) checkIndex to avoid coroutine
                            // worker pool exhaustion when called from multiple threads concurrently.
                            TestUtil.checkIndex(
                                writer.getDirectory(),
                                CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS,
                                false,
                                false,
                                null
                            )
                        } catch (ioe: IOException) {
                            println("$name: unexpected exception1")
                            failure = ioe
                            break
                        } catch (t: Throwable) {
                            // Catch-all to prevent native crash from escaped coroutine exception
                            println("$name: unexpected exception1b")
                            failure = t
                            break
                        }
                    } catch (t: Throwable) {
                        println("$name: unexpected exception2")
                        failure = t
                        break
                    }

                    doFail.set(false)

                    // After a possible exception (above) I should be able
                    // to add a new document without hitting an
                    // exception:
                    try {
                        writer.updateDocument(idTerm, doc)
                    } catch (t: Throwable) {
                        println("$name: unexpected exception3")
                        failure = t
                        break
                    }
                } while (++iterations < maxIterations)
            } catch (t: Throwable) {
                // Final safety net: ensure no exception escapes run() on native
                if (failure == null) failure = t
            }
        }
    }

    // doFail is a CloseableThreadLocal<Boolean>; in Java this is ThreadLocal<Thread> per-thread.
    // Each thread sets its own "should fail" flag independently.
    val doFail = CloseableThreadLocal<Boolean>()

    private inner class TestPoint1 : RandomIndexWriter.TestPoint {
        val r: Random = Random(random().nextLong())

        override fun apply(name: String) {
            if (doFail.get() == true && !name.equals("startDoFlush") && r.nextInt(40) == 17) {
                if (VERBOSE) {
                    println("NOW FAIL: $name")
                }
                throw RuntimeException("intentionally failing at $name")
            }
        }
    }

    @Test
    fun testRandomExceptions() {
        if (VERBOSE) {
            println("\nTEST: start testRandomExceptions")
        }
        val dir = newDirectory()

        val analyzer = MockAnalyzer(random())
        analyzer.setEnableChecks(
            false
        ) // disable workflow checking as we forcefully close() in exceptional cases.

        val writer =
            RandomIndexWriter.mockIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(analyzer)
                    .setRAMBufferSizeMB(0.1)
                    .setMergeScheduler(ConcurrentMergeScheduler()),
                TestPoint1()
            )
        (writer.config.mergeScheduler as ConcurrentMergeScheduler).setSuppressExceptions()
        // writer.setMaxBufferedDocs(10);
        if (VERBOSE) {
            println("TEST: initial commit")
        }
        writer.commit()

        val thread = IndexerThread(0, writer)
        thread.run()
        if (thread.failure != null) {
            println(thread.failure)
            fail("thread ${thread.name}: hit unexpected failure")
        }

        if (VERBOSE) {
            println("TEST: commit after thread start")
        }
        writer.commit()

        try {
            writer.close()
        } catch (t: Throwable) {
            println("exception during close:")
            writer.rollback()
        }

        // Confirm that when doc hits exception partway through tokenization, it's deleted:
        val r2 = DirectoryReader.open(dir)
        val count = r2.docFreq(Term("content4", "aaa"))
        val count2 = r2.docFreq(Term("content4", "ddd"))
        assertEquals(count, count2)
        r2.close()

        dir.close()
    }

    @Test
    fun testRandomExceptionsThreads() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        analyzer.setEnableChecks(
            false
        ) // disable workflow checking as we forcefully close() in exceptional cases.
        val writer =
            RandomIndexWriter.mockIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(analyzer)
                    .setRAMBufferSizeMB(0.2)
                    .setMergeScheduler(ConcurrentMergeScheduler()),
                TestPoint1()
            )
        (writer.config.mergeScheduler as ConcurrentMergeScheduler).setSuppressExceptions()
        // writer.setMaxBufferedDocs(10);
        writer.commit()

        val NUM_THREADS = 4

        val threads = Array(NUM_THREADS) { i -> IndexerThread(i, writer) }
        val platformThreads = Array(NUM_THREADS) { i -> Thread { threads[i].run() } }
        for (i in 0 until NUM_THREADS) {
            platformThreads[i].start()
        }

        for (i in 0 until NUM_THREADS) platformThreads[i].join()

        for (i in 0 until NUM_THREADS)
            if (threads[i].failure != null) {
                fail("thread ${threads[i].name}: hit unexpected failure")
            }

        writer.commit()

        try {
            writer.close()
        } catch (t: Throwable) {
            println("exception during close:")
            writer.rollback()
        }

        // Confirm that when doc hits exception partway through tokenization, it's deleted:
        val r2 = DirectoryReader.open(dir)
        val count = r2.docFreq(Term("content4", "aaa"))
        val count2 = r2.docFreq(Term("content4", "ddd"))
        assertEquals(count, count2)
        r2.close()

        dir.close()
    }

    // LUCENE-1198
    private class TestPoint2 : RandomIndexWriter.TestPoint {
        var doFail = false

        override fun apply(name: String) {
            if (doFail && name.equals("DocumentsWriterPerThread addDocuments start"))
                throw RuntimeException("intentionally failing")
        }
    }

    private val CRASH_FAIL_MESSAGE = "I'm experiencing problems"

    private inner class CrashingFilter(val fieldName: String, input: TokenStream) :
        TokenFilter(input) {
        var count = 0

        override fun incrementToken(): Boolean {
            if (fieldName.equals("crash") && count++ >= 4) throw IOException(CRASH_FAIL_MESSAGE)
            return input.incrementToken()
        }

        override fun reset() {
            super.reset()
            count = 0
        }
    }

    @Test
    fun testExceptionDocumentsWriterInit() {
        val dir = newDirectory()
        val testPoint = TestPoint2()
        val w =
            RandomIndexWriter.mockIndexWriter(
                random(), dir, newIndexWriterConfig(MockAnalyzer(random())), testPoint
            )
        val doc = Document()
        doc.add(newTextField("field", "a field", Field.Store.YES))
        w.addDocument(doc)

        testPoint.doFail = true
        assertFailsWith<RuntimeException> {
            w.addDocument(doc)
        }

        w.close()
        dir.close()
    }

    // LUCENE-1208
    @Test
    fun testExceptionJustBeforeFlush() {
        val dir = newDirectory()

        val doCrash = AtomicBoolean(false)

        val analyzer = object : Analyzer(PER_FIELD_REUSE_STRATEGY) {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                tokenizer.enableChecks =
                    false // disable workflow checking as we forcefully close() in exceptional cases.
                var stream: TokenStream = tokenizer
                if (doCrash.load()) {
                    stream = CrashingFilter(fieldName, stream)
                }
                return TokenStreamComponents(tokenizer, stream)
            }
        }

        val w =
            RandomIndexWriter.mockIndexWriter(
                random(), dir, newIndexWriterConfig(analyzer).setMaxBufferedDocs(2), TestPoint1()
            )
        val doc = Document()
        doc.add(newTextField("field", "a field", Field.Store.YES))
        w.addDocument(doc)

        val crashDoc = Document()
        crashDoc.add(newTextField("crash", "do it on token 4", Field.Store.YES))
        doCrash.store(true)
        assertFailsWith<IOException> {
            w.addDocument(crashDoc)
        }

        w.addDocument(doc)
        w.close()
        dir.close()
    }

    private class TestPoint3 : RandomIndexWriter.TestPoint {
        var doFail = false
        var failed = false

        override fun apply(name: String) {
            if (doFail && name.equals("startMergeInit")) {
                failed = true
                throw RuntimeException("intentionally failing")
            }
        }
    }

    // LUCENE-1210
    @Test
    fun testExceptionOnMergeInit() {
        val dir = newDirectory()
        val conf =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setMergePolicy(newLogMergePolicy())
        val cms = ConcurrentMergeScheduler()
        cms.setSuppressExceptions()
        conf.setMergeScheduler(cms)
        (conf.mergePolicy as LogMergePolicy).mergeFactor = 2
        (conf.mergePolicy as LogMergePolicy).targetSearchConcurrency = 1
        val testPoint = TestPoint3()
        val w = RandomIndexWriter.mockIndexWriter(random(), dir, conf, testPoint)
        testPoint.doFail = true
        val doc = Document()
        doc.add(newTextField("field", "a field", Field.Store.YES))
        for (i in 0 until 10) {
            try {
                w.addDocument(doc)
            } catch (re: RuntimeException) {
                break
            }
        }

        try {
            (w.config.mergeScheduler as ConcurrentMergeScheduler).let { runBlocking { it.sync() } }
        } catch (ise: IllegalStateException) {
            // OK: merge exc causes tragedy
        }
        assertTrue(testPoint.failed)
        w.close()
        dir.close()
    }

    // LUCENE-1072
    @Test
    fun testExceptionFromTokenStream() {
        val dir = newDirectory()
        val conf =
            newIndexWriterConfig(
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer = MockTokenizer(MockTokenizer.SIMPLE, true)
                        tokenizer.enableChecks =
                            false // disable workflow checking as we forcefully close() in exceptional
                        // cases.
                        return TokenStreamComponents(
                            tokenizer,
                            object : TokenFilter(tokenizer) {
                                private var count = 0

                                override fun incrementToken(): Boolean {
                                    if (count++ == 5) {
                                        throw IOException()
                                    }
                                    return input.incrementToken()
                                }

                                override fun reset() {
                                    super.reset()
                                    this.count = 0
                                }
                            }
                        )
                    }
                }
            )
        conf.setMaxBufferedDocs(max(3, conf.maxBufferedDocs))
        conf.setMergePolicy(NoMergePolicy.INSTANCE)

        val writer = IndexWriter(dir, conf)

        val brokenDoc = Document()
        val contents = "aa bb cc dd ee ff gg hh ii jj kk"
        brokenDoc.add(newTextField("content", contents, Field.Store.NO))
        assertFailsWith<Exception> {
            writer.addDocument(brokenDoc)
        }

        // Make sure we can add another normal document
        var doc = Document()
        doc.add(newTextField("content", "aa bb cc dd", Field.Store.NO))
        writer.addDocument(doc)

        // Make sure we can add another normal document
        doc = Document()
        doc.add(newTextField("content", "aa bb cc dd", Field.Store.NO))
        writer.addDocument(doc)

        writer.close()
        val reader = DirectoryReader.open(dir)
        val t = Term("content", "aa")
        assertEquals(3, reader.docFreq(t))

        // Make sure the doc that hit the exception was marked
        // as deleted:
        val tdocs =
            TestUtil.docs(random(), reader, t.field(), BytesRef(t.text()), null, 0)!!

        val liveDocs: Bits? = MultiBits.getLiveDocs(reader)
        var count2 = 0
        while (tdocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            if (liveDocs == null || liveDocs.get(tdocs.docID())) {
                count2++
            }
        }
        assertEquals(2, count2)

        assertEquals(0, reader.docFreq(Term("content", "gg")))
        reader.close()
        dir.close()
    }

    private inner class FailOnlyOnFlush : MockDirectoryWrapper.Failure() {
        var count = 0

        override fun eval(dir: MockDirectoryWrapper) {
            if (doFail) {
                if (dir.flushStage == "flush" && count++ >= 30) {
                    doFail = false
                    throw IOException("now failing during flush")
                }
            }
        }
    }

    // make sure an aborting exception closes the writer:
    @Test
    fun testDocumentsWriterAbort() {
        val dir = newMockDirectory()
        val failure = FailOnlyOnFlush()
        failure.setDoFail()
        dir.failOn(failure)

        val writer =
            IndexWriter(
                dir, newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(2)
            )
        val doc = Document()
        val contents = "aa bb cc dd ee ff gg hh ii jj kk"
        doc.add(newTextField("content", contents, Field.Store.NO))
        var hitError = false
        writer.addDocument(doc)

        assertFailsWith<IOException> {
            writer.addDocument(doc)
        }

        // only one flush should fail:
        assertFalse(hitError)
        hitError = true
        assertTrue(writer.isDeleterClosed())
        assertTrue(writer.isClosed())
        assertFalse(DirectoryReader.indexExists(dir))

        dir.close()
    }

    @Test
    fun testDocumentsWriterExceptions() {
        val analyzer = object : Analyzer(PER_FIELD_REUSE_STRATEGY) {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                tokenizer.enableChecks =
                    false // disable workflow checking as we forcefully close() in exceptional cases.
                return TokenStreamComponents(tokenizer, CrashingFilter(fieldName, tokenizer))
            }
        }

        for (i in 0 until 2) {
            if (VERBOSE) {
                println("TEST: cycle i=$i")
            }
            val dir = newDirectory()
            val writer =
                IndexWriter(dir, newIndexWriterConfig(analyzer).setMergePolicy(newLogMergePolicy()))

            // don't allow a sudden merge to clean up the deleted
            // doc below:
            val lmp = writer.config.mergePolicy as LogMergePolicy
            lmp.mergeFactor = max(lmp.mergeFactor, 5)

            val doc = Document()
            doc.add(newField("contents", "here are some contents", DocCopyIterator.custom5))
            writer.addDocument(doc)
            writer.addDocument(doc)
            doc.add(newField("crash", "this should crash after 4 terms", DocCopyIterator.custom5))
            doc.add(newField("other", "this will not get indexed", DocCopyIterator.custom5))
            try {
                writer.addDocument(doc)
                fail("did not hit expected exception")
            } catch (ioe: IOException) {
                if (VERBOSE) {
                    println("TEST: hit expected exception")
                }
            }

            if (0 == i) {
                val doc2 = Document()
                doc2.add(newField("contents", "here are some contents", DocCopyIterator.custom5))
                writer.addDocument(doc2)
                writer.addDocument(doc2)
            }
            writer.close()

            if (VERBOSE) {
                println("TEST: open reader")
            }
            val reader = DirectoryReader.open(dir)
            if (i == 0) {
                val expected = 5
                assertEquals(expected, reader.docFreq(Term("contents", "here")))
                assertEquals(expected, reader.maxDoc())
                var numDel = 0
                val liveDocs: Bits? = MultiBits.getLiveDocs(reader)
                assertNotNull(liveDocs)
                val storedFields = reader.storedFields()
                val termVectors = reader.termVectors()
                for (j in 0 until reader.maxDoc()) {
                    if (!liveDocs.get(j)) numDel++
                    else {
                        storedFields.document(j)
                        termVectors.get(j)
                    }
                }
                assertEquals(1, numDel)
            }
            reader.close()

            val writer2 = IndexWriter(dir, newIndexWriterConfig(analyzer).setMaxBufferedDocs(10))
            val doc3 = Document()
            doc3.add(newField("contents", "here are some contents", DocCopyIterator.custom5))
            for (j in 0 until 17) writer2.addDocument(doc3)
            writer2.forceMerge(1)
            writer2.close()

            val reader2 = DirectoryReader.open(dir)
            val expected = 19 + (1 - i) * 2
            assertEquals(expected, reader2.docFreq(Term("contents", "here")))
            assertEquals(expected, reader2.maxDoc())
            var numDel2 = 0
            assertNull(MultiBits.getLiveDocs(reader2))
            val storedFields2 = reader2.storedFields()
            val termVectors2 = reader2.termVectors()
            for (j in 0 until reader2.maxDoc()) {
                storedFields2.document(j)
                termVectors2.get(j)
            }
            reader2.close()
            assertEquals(0, numDel2)

            dir.close()
        }
    }

    @Test
    fun testDocumentsWriterExceptionFailOneDoc() {
        val analyzer = object : Analyzer(PER_FIELD_REUSE_STRATEGY) {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                tokenizer.enableChecks =
                    false // disable workflow checking as we forcefully close() in exceptional cases.
                return TokenStreamComponents(tokenizer, CrashingFilter(fieldName, tokenizer))
            }
        }
        for (i in 0 until 10) {
            val dir = newDirectory()
            val writer = IndexWriter(
                dir,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(-1)
                    .setRAMBufferSizeMB(if (random().nextBoolean()) 0.00001 else Int.MAX_VALUE.toDouble())
                    .setMergePolicy(
                        object : FilterMergePolicy(NoMergePolicy.INSTANCE) {
                            override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                                return true
                            }
                        }
                    )
            )
            val doc = Document()
            doc.add(newField("contents", "here are some contents", DocCopyIterator.custom5))
            writer.addDocument(doc)
            doc.add(newField("crash", "this should crash after 4 terms", DocCopyIterator.custom5))
            doc.add(newField("other", "this will not get indexed", DocCopyIterator.custom5))
            assertFailsWith<IOException> {
                writer.addDocument(doc)
            }
            writer.commit()
            val reader = DirectoryReader.open(dir)
            assertEquals(2, reader.docFreq(Term("contents", "here")))
            assertEquals(2, reader.maxDoc())
            assertEquals(1, reader.numDocs())
            reader.close()
            writer.close()
            dir.close()
        }
    }

    @Test
    fun testDocumentsWriterExceptionThreads() {
        val analyzer = object : Analyzer(PER_FIELD_REUSE_STRATEGY) {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                tokenizer.enableChecks =
                    false // disable workflow checking as we forcefully close() in exceptional cases.
                return TokenStreamComponents(tokenizer, CrashingFilter(fieldName, tokenizer))
            }
        }

        val NUM_THREAD = 3
        val NUM_ITER = atLeast(10)

        for (i in 0 until 2) {
            val dir = newDirectory()
            run {
                val writer = IndexWriter(
                    dir,
                    newIndexWriterConfig(analyzer)
                        .setMaxBufferedDocs(Int.MAX_VALUE)
                        .setRAMBufferSizeMB(-1.0) // we don't want to flush automatically
                        .setMergePolicy(
                            object : FilterMergePolicy(NoMergePolicy.INSTANCE) {
                                // don't use a merge policy here they depend on the DWPThreadPool and its
                                // max thread states etc.
                                // we also need to keep fully deleted segments since otherwise we clean up
                                // fully deleted ones and if we
                                // flush the one that has only the failed document the docFreq checks will
                                // be off below.
                                override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                                    return true
                                }
                            }
                        )
                )

                val finalI = i

                val threadFailure = AtomicReference<Throwable?>(null)
                val threads = Array(NUM_THREAD) { _ ->
                    Thread {
                        try {
                            for (iter in 0 until NUM_ITER) {
                                val doc = Document()
                                doc.add(
                                    newField(
                                        "contents", "here are some contents", DocCopyIterator.custom5
                                    )
                                )
                                writer.addDocument(doc)
                                writer.addDocument(doc)
                                doc.add(
                                    newField(
                                        "crash",
                                        "this should crash after 4 terms",
                                        DocCopyIterator.custom5
                                    )
                                )
                                doc.add(
                                    newField(
                                        "other", "this will not get indexed", DocCopyIterator.custom5
                                    )
                                )
                                assertFailsWith<IOException> {
                                    writer.addDocument(doc)
                                }

                                if (0 == finalI) {
                                    val extraDoc = Document()
                                    extraDoc.add(
                                        newField(
                                            "contents",
                                            "here are some contents",
                                            DocCopyIterator.custom5
                                        )
                                    )
                                    writer.addDocument(extraDoc)
                                    writer.addDocument(extraDoc)
                                }
                            }
                        } catch (t: Throwable) {
                            // On native, calling fail() inside a coroutine would throw AssertionError
                            // and crash the process. Capture the exception and report after join().
                            println("ERROR: hit unexpected exception: $t")
                            threadFailure.compareAndSet(null, t)
                        }
                    }
                }
                for (t in threads) t.start()
                for (t in threads) t.join()

                // Report any thread failure after all threads complete (safe on native)
                val tf = threadFailure.load()
                if (tf != null) fail("Thread hit unexpected exception: $tf")

                writer.close()
            }

            val reader = DirectoryReader.open(dir)
            val expected = (3 + (1 - i) * 2) * NUM_THREAD * NUM_ITER
            assertEquals(expected, reader.docFreq(Term("contents", "here")), "i=$i")
            assertEquals(expected, reader.maxDoc())
            var numDel = 0
            val liveDocs: Bits? = MultiBits.getLiveDocs(reader)
            assertNotNull(liveDocs)
            val storedFields = reader.storedFields()
            val termVectors = reader.termVectors()
            for (j in 0 until reader.maxDoc()) {
                if (!liveDocs.get(j)) numDel++
                else {
                    storedFields.document(j)
                    termVectors.get(j)
                }
            }
            reader.close()

            assertEquals(NUM_THREAD * NUM_ITER, numDel)

            val writer2 = IndexWriter(dir, newIndexWriterConfig(analyzer).setMaxBufferedDocs(10))
            val doc = Document()
            doc.add(newField("contents", "here are some contents", DocCopyIterator.custom5))
            for (j in 0 until 17) writer2.addDocument(doc)
            writer2.forceMerge(1)
            writer2.close()

            val reader2 = DirectoryReader.open(dir)
            val expected2 = expected + 17 - NUM_THREAD * NUM_ITER
            assertEquals(expected2, reader2.docFreq(Term("contents", "here")))
            assertEquals(expected2, reader2.maxDoc())
            assertNull(MultiBits.getLiveDocs(reader2))
            val storedFields2 = reader2.storedFields()
            val termVectors2 = reader2.termVectors()
            for (j in 0 until reader2.maxDoc()) {
                storedFields2.document(j)
                termVectors2.get(j)
            }
            reader2.close()

            dir.close()
        }
    }

    // Throws IOException during MockDirectoryWrapper.sync
    private inner class FailOnlyInSync : MockDirectoryWrapper.Failure() {
        var didFail = false

        override fun eval(dir: MockDirectoryWrapper) {
            if (doFail && didFail == false && dir.isSyncing) {
                didFail = true
                testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.inject-sync-failure didFail=$didFail" }
                if (VERBOSE) {
                    println("TEST: now throw exc:")
                }
                throw IOException("now failing on purpose during sync")
            }
        }
    }

    // TODO: these are also in TestIndexWriter... add a simple doc-writing method
    // like this to LuceneTestCase?
    private fun addDoc(writer: IndexWriter) {
        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        writer.addDocument(doc)
    }

    // LUCENE-1044: test exception during sync
    @Test
    fun testExceptionDuringSync() {
        val dir = newMockDirectory()
        val failure = FailOnlyInSync()
        dir.failOn(failure)
        testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.start" }

        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setMergeScheduler(ConcurrentMergeScheduler())
                    .setMergePolicy(newLogMergePolicy(5))
            )

        for (i in 0 until 23) {
            testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.addDoc.start i=$i" }
            addDoc(writer)
            testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.addDoc.done i=$i" }
            if ((i - 1) % 2 == 0) {
                failure.setDoFail()
                testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.commit.start i=$i" }
                try {
                    writer.commit()
                    testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.commit.done i=$i result=success" }
                } catch (ioe: IOException) {
                    // expected
                    testIndexWriterExceptionsLogger.debug(ioe) { "phase=testExceptionDuringSync.commit.done i=$i result=expected-ioexception" }
                } finally {
                    failure.clearDoFail()
                    testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.commit.finally i=$i didFail=${failure.didFail}" }
                }
            }
        }
        testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.cms.sync.start didFail=${failure.didFail}" }
        runBlocking { (writer.config.mergeScheduler as ConcurrentMergeScheduler).sync() }
        testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.cms.sync.done didFail=${failure.didFail}" }
        assertTrue(failure.didFail)
        testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.writer.close.start" }
        writer.close()
        testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.writer.close.done" }

        testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.reader.open.start" }
        val reader = DirectoryReader.open(dir)
        testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.reader.open.done numDocs=${reader.numDocs()}" }
        assertEquals(23, reader.numDocs())
        reader.close()
        dir.close()
        testIndexWriterExceptionsLogger.debug { "phase=testExceptionDuringSync.done" }
    }

    private inner class FailOnlyInCommit(
        private val dontFailDuringGlobalFieldMap: Boolean,
        private val dontFailDuringSyncMetadata: Boolean,
        private val stage: String
    ) : MockDirectoryWrapper.Failure() {

        var failOnCommit = false
        var failOnDeleteFile = false
        var failOnSyncMetadata = false

        override fun eval(dir: MockDirectoryWrapper) {
            var isCommit = dir.commitStage == stage
            val isDelete = dir.isDeletingFile
            val isSyncMetadata = dir.isSyncingMetaData
            val isInGlobalFieldMap = dir.isWritingGlobalFieldMap
            if (isInGlobalFieldMap && dontFailDuringGlobalFieldMap) {
                isCommit = false
            }
            if (isSyncMetadata && dontFailDuringSyncMetadata) {
                isCommit = false
            }
            if (isCommit) {
                if (!isDelete) {
                    failOnCommit = true
                    failOnSyncMetadata = isSyncMetadata
                    throw RuntimeException("now fail first")
                } else {
                    failOnDeleteFile = true
                    throw IOException("now fail during delete")
                }
            }
        }
    }

    @Test
    fun testExceptionsDuringCommit() {
        val failures = arrayOf(
            // LUCENE-1214
            FailOnlyInCommit(
                false,
                true,
                PREPARE_STAGE
            ), // fail during global field map is written
            FailOnlyInCommit(
                true, false, PREPARE_STAGE
            ), // fail during sync metadata
            FailOnlyInCommit(
                true, true, PREPARE_STAGE
            ), // fail after global field map is written
            FailOnlyInCommit(
                false, true, FINISH_STAGE
            ) // fail while running finishCommit
        )

        for (failure in failures) {
            val dir = newMockDirectory()
            dir.setFailOnCreateOutput(false)
            val fileCount = dir.listAll().size
            val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
            val doc = Document()
            doc.add(newTextField("field", "a field", Field.Store.YES))
            w.addDocument(doc)
            dir.failOn(failure)
            assertFailsWith<RuntimeException> {
                w.close()
            }
            assertTrue(
                failure.failOnCommit && (failure.failOnDeleteFile || failure.failOnSyncMetadata),
                "failOnCommit=${failure.failOnCommit} failOnDeleteFile=${failure.failOnDeleteFile} failOnSyncMetadata=${failure.failOnSyncMetadata}"
            )
            w.rollback()
            val files = dir.listAll()
            assertTrue(
                files.size == fileCount
                    || (files.size == fileCount + 1
                    && files.toList().contains(IndexWriter.WRITE_LOCK_NAME))
            )
            dir.close()
        }
    }

    @Test
    fun testForceMergeExceptions() {
        val startDir = newDirectory()
        val conf =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setMergePolicy(newLogMergePolicy())
        (conf.mergePolicy as LogMergePolicy).mergeFactor = 100
        val w = IndexWriter(startDir, conf)
        for (i in 0 until 27) {
            addDoc(w)
        }
        w.close()

        val iter = if (TEST_NIGHTLY) 200 else 10
        for (i in 0 until iter) {
            if (VERBOSE) {
                println("\nTEST: iter $i")
            }
            val dir = MockDirectoryWrapper(random(), TestUtil.ramCopyOf(startDir))
            val conf2 =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergeScheduler(ConcurrentMergeScheduler())
            (conf2.mergeScheduler as ConcurrentMergeScheduler).setSuppressExceptions()
            val w2 = IndexWriter(dir, conf2)
            dir.randomIOExceptionRate = 0.5
            try {
                w2.forceMerge(1)
            } catch (ise: IllegalStateException) {
                // expected
            } catch (ioe: IOException) {
                if (ioe.cause == null) {
                    fail("forceMerge threw IOException without root cause")
                }
            }
            dir.randomIOExceptionRate = 0.0
            // System.out.println("TEST: now close IW");
            try {
                w2.close()
            } catch (ise: IllegalStateException) {
                // ok
            }
            dir.close()
        }
        startDir.close()
    }

    // LUCENE-1429
    @Test
    fun testOutOfMemoryErrorCausesCloseToFail() {
        val thrown = AtomicBoolean(false)
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setInfoStream(
                        object : InfoStream() {
                            override fun message(component: String, message: String) {
                                if (message.startsWith("now flush at close")
                                    && thrown.compareAndSet(false, true)
                                ) {
                                    throw FakeOOME("fake OOME at $message")
                                }
                            }

                            override fun isEnabled(component: String): Boolean {
                                return true
                            }

                            override fun close() {}
                        }
                    )
            )

        assertFailsWith<Error> {
            writer.close()
        }

        // throws IllegalStateEx w/o bug fix
        writer.close()
        dir.close()
    }

    /** If IW hits OOME during indexing, it should refuse to commit any further changes.  */
    @Test
    fun testOutOfMemoryErrorRollback() {
        val thrown = AtomicBoolean(false)
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setInfoStream(
                        object : InfoStream() {
                            override fun message(component: String, message: String) {
                                if (message.contains("startFullFlush")
                                    && thrown.compareAndSet(false, true)
                                ) {
                                    throw FakeOOME("fake OOME at $message")
                                }
                            }

                            override fun isEnabled(component: String): Boolean {
                                return true
                            }

                            override fun close() {}
                        }
                    )
            )
        writer.addDocument(Document())

        assertFailsWith<Error> {
            writer.commit()
        }

        try {
            writer.close()
        } catch (ok: IllegalArgumentException) {
            // ok
        }

        assertFailsWith<AlreadyClosedException> {
            writer.addDocument(Document())
        }

        // IW should have done rollback() during close, since it hit OOME, and so no index should exist:
        assertFalse(DirectoryReader.indexExists(dir))

        dir.close()
    }

    // LUCENE-1347
    private class TestPoint4 : RandomIndexWriter.TestPoint {
        var doFail = false

        override fun apply(name: String) {
            if (doFail && name.equals("rollback before checkpoint"))
                throw RuntimeException("intentionally failing")
        }
    }

    // LUCENE-1347
    @Test
    fun testRollbackExceptionHang() {
        val dir = newDirectory()
        val testPoint = TestPoint4()
        val w =
            RandomIndexWriter.mockIndexWriter(
                random(), dir, newIndexWriterConfig(MockAnalyzer(random())), testPoint
            )

        addDoc(w)
        testPoint.doFail = true
        assertFailsWith<RuntimeException> {
            w.rollback()
        }

        testPoint.doFail = false
        w.rollback()
        dir.close()
    }

    // LUCENE-1044: Simulate checksum error in segments_N
    @Test
    fun testSegmentsChecksumError() {
        val dir = newDirectory() as BaseDirectoryWrapper
        dir.checkIndexOnClose = false // we corrupt the index

        var writer: IndexWriter?

        writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        // add 100 documents
        for (i in 0 until 100) {
            addDoc(writer)
        }

        // close
        writer.close()

        val gen = SegmentInfos.getLastCommitGeneration(dir)
        assertTrue(gen > 0, "segment generation should be > 0 but got $gen")

        val segmentsFileName = SegmentInfos.getLastCommitSegmentsFileName(dir)!!
        val `in` = dir.openInput(segmentsFileName, IOContext.READONCE)
        val out =
            dir.createOutput(
                IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", 1 + gen)!!,
                newIOContext(random())
            )
        out.copyBytes(`in`, `in`.length() - 1)
        val b = `in`.readByte()
        out.writeByte((1 + b).toByte())
        out.close()
        `in`.close()

        assertFailsWith<CorruptIndexException> {
            DirectoryReader.open(dir)
        }

        dir.close()
    }

    // Simulate a corrupt index by removing last byte of
    // latest segments file and make sure we get an
    // IOException trying to open the index:
    @Test
    fun testSimulatedCorruptIndex1() {
        val dir = newDirectory() as BaseDirectoryWrapper
        dir.checkIndexOnClose = false // we are corrupting it!

        var writer: IndexWriter?

        writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        // add 100 documents
        for (i in 0 until 100) {
            addDoc(writer)
        }

        // close
        writer.close()

        val gen = SegmentInfos.getLastCommitGeneration(dir)
        assertTrue(gen > 0, "segment generation should be > 0 but got $gen")

        val fileNameIn = SegmentInfos.getLastCommitSegmentsFileName(dir)!!
        val fileNameOut =
            IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", 1 + gen)!!
        val `in` = dir.openInput(fileNameIn, IOContext.READONCE)
        val out = dir.createOutput(fileNameOut, newIOContext(random()))
        val length = `in`.length()
        for (i in 0 until length - 1) {
            out.writeByte(`in`.readByte())
        }
        `in`.close()
        out.close()
        dir.deleteFile(fileNameIn)

        assertFailsWith<Exception> {
            DirectoryReader.open(dir)
        }

        dir.close()
    }

    // Simulate a corrupt index by removing one of the
    // files and make sure we get an IOException trying to
    // open the index:
    @Test
    fun testSimulatedCorruptIndex2() {
        val dir = newDirectory() as BaseDirectoryWrapper
        dir.checkIndexOnClose = false // we are corrupting it!
        var writer: IndexWriter?

        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(newLogMergePolicy(true))
                    .setUseCompoundFile(true)
            )
        val lmp = writer.config.mergePolicy
        // Force creation of CFS:
        lmp.noCFSRatio = 1.0
        lmp.maxCFSSegmentSizeMB = Double.POSITIVE_INFINITY

        // add 100 documents
        for (i in 0 until 100) {
            addDoc(writer)
        }

        // close
        writer.close()

        val gen = SegmentInfos.getLastCommitGeneration(dir)
        assertTrue(gen > 0, "segment generation should be > 0 but got $gen")

        var corrupted = false
        val sis = SegmentInfos.readLatestCommit(dir)
        for (si in sis) {
            assertTrue(si.info.useCompoundFile)
            val victims = ArrayList<String>(si.info.files())
            victims.shuffle(random())
            dir.deleteFile(victims[0])
            corrupted = true
            break
        }

        assertTrue(corrupted, "failed to find cfs file to remove: ")

        assertFailsWith<Exception> {
            DirectoryReader.open(dir)
        }

        dir.close()
    }

    @Test
    fun testTermVectorExceptions() {
        val failures = arrayOf(
            FailOnTermVectors(TV_AFTER_INIT_STAGE),
            FailOnTermVectors(TV_INIT_STAGE),
        )
        val num = atLeast(1)
        iters@ for (j in 0 until num) {
            for (failure in failures) {
                val dir = newMockDirectory()
                val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
                dir.failOn(failure)
                val numDocs = 10 + random().nextInt(30)
                for (i in 0 until numDocs) {
                    val doc = Document()
                    // random TV
                    val field = newTextField(random(), "field", "a field", Field.Store.YES)
                    doc.add(field)
                    try {
                        w.addDocument(doc)
                        assertFalse(field.fieldType().storeTermVectors())
                    } catch (e: RuntimeException) {
                        assertTrue(e.message!!.startsWith(TV_EXC_MSG))
                        // This is an aborting exception, so writer is closed:
                        assertTrue(w.isDeleterClosed())
                        assertTrue(w.isClosed())
                        dir.close()
                        continue@iters
                    }
                    if (random().nextInt(20) == 0) {
                        w.commit()
                        TestUtil.checkIndex(dir)
                    }
                }
                val document = Document()
                document.add(TextField("field", "a field", Field.Store.YES))
                w.addDocument(document)

                for (i in 0 until numDocs) {
                    val doc = Document()
                    val field = newTextField(random(), "field", "a field", Field.Store.YES)
                    doc.add(field)
                    // random TV
                    try {
                        w.addDocument(doc)
                        assertFalse(field.fieldType().storeTermVectors())
                    } catch (e: RuntimeException) {
                        assertTrue(e.message!!.startsWith(TV_EXC_MSG))
                    }
                    if (random().nextInt(20) == 0) {
                        w.commit()
                        TestUtil.checkIndex(dir)
                    }
                }
                val document2 = Document()
                document2.add(TextField("field", "a field", Field.Store.YES))
                w.addDocument(document2)
                w.close()
                val reader = DirectoryReader.open(dir)
                assertTrue(reader.numDocs() > 0)
                SegmentInfos.readLatestCommit(dir)
                for (context in reader.leaves()) {
                    assertFalse(context.reader().fieldInfos.hasTermVectors())
                }
                reader.close()
                dir.close()
            }
        }
    }

    private inner class FailOnTermVectors(private val stage: String) :
        MockDirectoryWrapper.Failure() {

        override fun eval(dir: MockDirectoryWrapper) {
            if ((stage == TV_AFTER_INIT_STAGE && dir.isInTermVectorsFinishDocument)
                || dir.termVectorsStage == stage
            ) {
                throw RuntimeException(TV_EXC_MSG)
            }
        }
    }

    @Test
    fun testAddDocsNonAbortingException() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val numDocs1 = random().nextInt(25)
        for (docCount in 0 until numDocs1) {
            val doc = Document()
            doc.add(newTextField("content", "good content", Field.Store.NO))
            w.addDocument(doc)
        }

        val docs = ArrayList<Document>()
        for (docCount in 0 until 7) {
            val doc = Document()
            docs.add(doc)
            doc.add(newStringField("id", "$docCount", Field.Store.NO))
            doc.add(newTextField("content", "silly content $docCount", Field.Store.NO))
            if (docCount == 4) {
                val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                tokenizer.setReader(StringReader("crash me on the 4th token"))
                tokenizer.enableChecks =
                    false // disable workflow checking as we forcefully close() in exceptional cases.
                val f =
                    Field("crash", CrashingFilter("crash", tokenizer), TextField.TYPE_NOT_STORED)
                doc.add(f)
            }
        }

        val expected = assertFailsWith<IOException> {
            w.addDocuments(docs)
        }
        assertEquals(CRASH_FAIL_MESSAGE, expected.message)

        val numDocs2 = random().nextInt(25)
        for (docCount in 0 until numDocs2) {
            val doc = Document()
            doc.add(newTextField("content", "good content", Field.Store.NO))
            w.addDocument(doc)
        }

        val r = w.getReader(true, false)
        w.close()

        val s = newSearcher(r)
        var pq = PhraseQuery("content", "silly", "good")
        assertEquals(0, s.count(pq))

        pq = PhraseQuery("content", "good", "content")
        assertEquals(numDocs1 + numDocs2, s.count(pq))
        r.close()
        dir.close()
    }

    @Test
    fun testUpdateDocsNonAbortingException() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val numDocs1 = random().nextInt(25)
        for (docCount in 0 until numDocs1) {
            val doc = Document()
            doc.add(newTextField("content", "good content", Field.Store.NO))
            w.addDocument(doc)
        }

        // Use addDocs (no exception) to get docs in the index:
        val docs = ArrayList<Document>()
        val numDocs2 = random().nextInt(25)
        for (docCount in 0 until numDocs2) {
            val doc = Document()
            docs.add(doc)
            doc.add(newStringField("subid", "subs", Field.Store.NO))
            doc.add(newStringField("id", "$docCount", Field.Store.NO))
            doc.add(newTextField("content", "silly content $docCount", Field.Store.NO))
        }
        w.addDocuments(docs)

        val numDocs3 = random().nextInt(25)
        for (docCount in 0 until numDocs3) {
            val doc = Document()
            doc.add(newTextField("content", "good content", Field.Store.NO))
            w.addDocument(doc)
        }

        docs.clear()
        val limit = TestUtil.nextInt(random(), 2, 25)
        val crashAt = random().nextInt(limit)
        for (docCount in 0 until limit) {
            val doc = Document()
            docs.add(doc)
            doc.add(newStringField("id", "$docCount", Field.Store.NO))
            doc.add(newTextField("content", "silly content $docCount", Field.Store.NO))
            if (docCount == crashAt) {
                val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                tokenizer.setReader(StringReader("crash me on the 4th token"))
                tokenizer.enableChecks =
                    false // disable workflow checking as we forcefully close() in exceptional cases.
                val f =
                    Field("crash", CrashingFilter("crash", tokenizer), TextField.TYPE_NOT_STORED)
                doc.add(f)
            }
        }

        val expected = assertFailsWith<IOException> {
            w.updateDocuments(Term("subid", "subs"), docs)
        }
        assertEquals(CRASH_FAIL_MESSAGE, expected.message)

        val numDocs4 = random().nextInt(25)
        for (docCount in 0 until numDocs4) {
            val doc = Document()
            doc.add(newTextField("content", "good content", Field.Store.NO))
            w.addDocument(doc)
        }

        val r = w.getReader(true, false)
        w.close()

        val s = newSearcher(r)
        var pq = PhraseQuery("content", "silly", "content")
        assertEquals(numDocs2, s.count(pq))

        pq = PhraseQuery("content", "good", "content")
        assertEquals(numDocs1 + numDocs3 + numDocs4, s.count(pq))
        r.close()
        dir.close()
    }

    /** test a null string value doesn't abort the entire segment  */
    @Test
    fun testNullStoredField() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        val iw = IndexWriter(dir, IndexWriterConfig(analyzer))
        // add good document
        val doc = Document()
        iw.addDocument(doc)
        assertFailsWith<Exception> {
            // set to null value
            val value: String? = null
            doc.add(StoredField("foo", value as String))
            iw.addDocument(doc)
        }

        assertNull(iw.getTragicException())
        iw.close()
        // make sure we see our good doc
        val r = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs())
        r.close()
        dir.close()
    }

    /** test a null string value doesn't abort the entire segment  */
    @Test
    fun testNullStoredFieldReuse() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        val iw = IndexWriter(dir, IndexWriterConfig(analyzer))
        // add good document
        val doc = Document()
        val theField = StoredField("foo", "hello", StoredField.TYPE)
        doc.add(theField)
        iw.addDocument(doc)
        assertFailsWith<Exception> {
            // set to null value
            theField.setStringValue(null as String)
            iw.addDocument(doc)
        }

        assertNull(iw.getTragicException())
        iw.close()
        // make sure we see our good doc
        val r = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs())
        r.close()
        dir.close()
    }

    /** test a null byte[] value doesn't abort the entire segment  */
    @Test
    fun testNullStoredBytesField() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        val iw = IndexWriter(dir, IndexWriterConfig(analyzer))
        // add good document
        val doc = Document()
        iw.addDocument(doc)

        assertFailsWith<Exception> {
            // set to null value
            val v: ByteArray? = null
            val theField = StoredField("foo", v as ByteArray)
            doc.add(theField)
            iw.addDocument(doc)
        }

        assertNull(iw.getTragicException())
        iw.close()
        // make sure we see our good doc
        val r = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs())
        r.close()
        dir.close()
    }

    /** test a null byte[] value doesn't abort the entire segment  */
    @Test
    fun testNullStoredBytesFieldReuse() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        val iw = IndexWriter(dir, IndexWriterConfig(analyzer))
        // add good document
        val doc = Document()
        val theField = StoredField("foo", BytesRef("hello").bytes)
        doc.add(theField)
        iw.addDocument(doc)
        assertFailsWith<Exception> {
            // set to null value
            val v: ByteArray? = null
            theField.setBytesValue(v as ByteArray)
            iw.addDocument(doc)
        }

        assertNull(iw.getTragicException())
        iw.close()
        // make sure we see our good doc
        val r = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs())
        r.close()
        dir.close()
    }

    /** test a null bytesref value doesn't abort the entire segment  */
    @Test
    fun testNullStoredBytesRefField() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        val iw = IndexWriter(dir, IndexWriterConfig(analyzer))
        // add good document
        val doc = Document()
        iw.addDocument(doc)

        assertFailsWith<Exception> {
            // set to null value
            val v: BytesRef? = null
            val theField = StoredField("foo", v as BytesRef)
            doc.add(theField)
            iw.addDocument(doc)
            fail("didn't get expected exception")
        }

        assertNull(iw.getTragicException())
        iw.close()
        // make sure we see our good doc
        val r = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs())
        r.close()
        dir.close()
    }

    /** test a null bytesref value doesn't abort the entire segment  */
    @Test
    fun testNullStoredBytesRefFieldReuse() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        val iw = IndexWriter(dir, IndexWriterConfig(analyzer))
        // add good document
        val doc = Document()
        val theField = StoredField("foo", BytesRef("hello"))
        doc.add(theField)
        iw.addDocument(doc)
        assertFailsWith<Exception> {
            // set to null value
            val v: BytesRef? = null
            theField.setBytesValue(v as BytesRef)
            iw.addDocument(doc)
            fail("didn't get expected exception")
        }

        assertNull(iw.getTragicException())
        iw.close()
        // make sure we see our good doc
        val r = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs())
        r.close()
        dir.close()
    }

    /** test a null data input value doesn't abort the entire segment  */
    @Test
    fun testNullStoredDataInputField() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        val iw = IndexWriter(dir, IndexWriterConfig(analyzer))
        // add good document
        val doc = Document()
        iw.addDocument(doc)

        assertFailsWith<Exception> {
            // set to null value
            val v: StoredFieldDataInput? = null
            val theField = StoredField("foo", v as StoredFieldDataInput)
            doc.add(theField)
            iw.addDocument(doc)
            fail("didn't get expected exception")
        }

        assertNull(iw.getTragicException())
        iw.close()
        // make sure we see our good doc
        val r = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs())
        r.close()
        dir.close()
    }

    @Test
    fun testCrazyPositionIncrementGap() {
        val dir = newDirectory()
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                return TokenStreamComponents(MockTokenizer(MockTokenizer.KEYWORD, false))
            }

            override fun getPositionIncrementGap(fieldName: String?): Int {
                return -2
            }
        }
        val iw = IndexWriter(dir, IndexWriterConfig(analyzer))
        // add good document
        val doc = Document()
        iw.addDocument(doc)
        assertFailsWith<IllegalArgumentException> {
            doc.add(newTextField("foo", "bar", Field.Store.NO))
            doc.add(newTextField("foo", "bar", Field.Store.NO))
            iw.addDocument(doc)
        }

        assertNull(iw.getTragicException())
        iw.close()

        // make sure we see our good doc
        val r = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs())
        r.close()
        dir.close()
    }

    // TODO: we could also check isValid, to catch "broken" bytesref values, might be too much?

    inner class UOEDirectory : FilterDirectory(ByteBuffersDirectory()) {
        var doFail = false

        override fun openInput(name: String, context: IOContext): IndexInput {
            if (doFail && name.startsWith("segments_")) {
                if (callStackContainsAnyOf("readCommit", "readLatestCommit")) {
                    throw UnsupportedOperationException("expected UOE")
                }
            }
            return super.openInput(name, context)
        }
    }

    @Test
    fun testExceptionOnCtor() {
        val uoe = UOEDirectory()
        val d = MockDirectoryWrapper(random(), uoe)
        val iw = IndexWriter(d, newIndexWriterConfig())
        iw.addDocument(Document())
        iw.close()
        uoe.doFail = true
        assertFailsWith<UnsupportedOperationException> {
            IndexWriter(d, newIndexWriterConfig())
        }

        uoe.doFail = false
        d.close()
    }

    // See LUCENE-4870 TooManyOpenFiles errors are thrown as
    // FNFExceptions which can trigger data loss.
    @Test
    fun testTooManyFileException() {

        // Create failure that throws Too many open files exception randomly
        val failure = object : MockDirectoryWrapper.Failure() {

            override fun reset(): MockDirectoryWrapper.Failure {
                doFail = false
                return this
            }

            override fun eval(dir: MockDirectoryWrapper) {
                if (doFail) {
                    if (random().nextBoolean()) {
                        throw FileNotFoundException("some/file/name.ext (Too many open files)")
                    }
                }
            }
        }

        val dir = newMockDirectory()
        // The exception is only thrown on open input
        dir.setFailOnOpenInput(true)
        dir.failOn(failure)

        // Create an index with one document
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        var iw = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(StringField("foo", "bar", Field.Store.NO))
        iw.addDocument(doc) // add a document
        iw.commit()
        var ir = DirectoryReader.open(dir)
        assertEquals(1, ir.numDocs())
        ir.close()
        iw.close()

        // Open and close the index a few times
        for (i in 0 until 10) {
            failure.setDoFail()
            val iwc2 = IndexWriterConfig(MockAnalyzer(random()))
            try {
                iw = IndexWriter(dir, iwc2)
            } catch (ex: AssertionError) {
                // This is fine: we tripped IW's assert that all files it's about to fsync do exist:
                assertTrue(ex.message!!.matches(Regex("file .* does not exist; files=\\[.*\\]")))
            } catch (ex: CorruptIndexException) {
                // Exceptions are fine - we are running out of file handlers here
                continue
            } catch (ex: FileNotFoundException) {
                continue
            } catch (ex: NoSuchFileException) {
                continue
            }
            failure.clearDoFail()
            iw.close()
            ir = DirectoryReader.open(dir)
            assertEquals(1, ir.numDocs(), "lost document after iteration: $i")
            ir.close()
        }

        // Check if document is still there
        failure.clearDoFail()
        ir = DirectoryReader.open(dir)
        assertEquals(1, ir.numDocs())
        ir.close()

        dir.close()
    }

    // kind of slow, but omits positions, so just CPU
    // @Nightly
    @Test
    fun testTooManyTokens() {
        val dir = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        doc.add(
            Field(
                "foo",
                object : TokenStream() {
                    val termAtt = addAttribute(CharTermAttribute::class)
                    val posIncAtt = addAttribute(PositionIncrementAttribute::class)
                    val termFreqAtt =
                        addAttribute(org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute::class)
                    var num: Long = 0

                    override fun incrementToken(): Boolean {
                        if (num == 20L) { // TODO reduced from Int.MAX_VALUE + 1 to 20 for dev speed, using large termFrequency to still trigger overflow
                            return false
                        }
                        clearAttributes()
                        if (num == 0L) {
                            posIncAtt.setPositionIncrement(1)
                        } else {
                            posIncAtt.setPositionIncrement(0)
                        }
                        termFreqAtt.termFrequency = Int.MAX_VALUE / 19 + 1 // TODO set to trigger Math.addExact overflow in ~10 tokens for dev speed (original: default=1)
                        termAtt.append("a")
                        num++
                        if (VERBOSE && num % 1000000 == 0L) {
                            println("indexed: $num")
                        }
                        return true
                    }
                },
                ft
            )
        )

        val expected = assertFailsWith<IllegalArgumentException> {
            iw.addDocument(doc)
        }
        assertTrue(expected.message!!.contains("too many tokens"))

        iw.close()
        dir.close()
    }

    @Test
    fun testExceptionDuringRollback() {
        // currently: fail in two different places
        val messageToFailOn =
            if (random().nextBoolean()) "rollback: done finish merges" else "rollback before checkpoint"

        // infostream that throws exception during rollback
        val evilInfoStream =
            object : InfoStream() {
                override fun message(component: String, message: String) {
                    if (messageToFailOn.equals(message)) {
                        throw RuntimeException("BOOM!")
                    }
                }

                override fun isEnabled(component: String): Boolean {
                    return true
                }

                override fun close() {}
            }

        val dir = newMockDirectory() // we want to ensure we don't leak any locks or file handles
        val iwc = IndexWriterConfig()
        iwc.setInfoStream(evilInfoStream)
        // TODO: cutover to RandomIndexWriter.mockIndexWriter?
        val iw =
            object : IndexWriter(dir, iwc) {
                override fun isEnableTestPoints(): Boolean {
                    return true
                }
            }

        val doc = Document()
        for (i in 0 until 10) {
            iw.addDocument(doc)
        }
        iw.commit()

        iw.addDocument(doc)

        // pool readers
        val r = DirectoryReader.open(iw)

        // sometimes sneak in a pending commit: we don't want to leak a file handle to that segments_N
        if (random().nextBoolean()) {
            iw.prepareCommit()
        }

        val expected = assertFailsWith<RuntimeException> {
            iw.rollback()
        }
        assertEquals("BOOM!", expected.message)

        r.close()

        // even though we hit exception: we are closed, no locks or files held, index in good state
        assertTrue(iw.isClosed())
        dir.obtainLock(IndexWriter.WRITE_LOCK_NAME).close()

        val r2 = DirectoryReader.open(dir)
        assertEquals(10, r2.maxDoc())
        r2.close()

        // no leaks
        dir.close()
    }

    @Test
    fun testRandomExceptionDuringRollback() {
        // fail in random places on i/o
        val numIters = RANDOM_MULTIPLIER * 75
        for (iter in 0 until numIters) {
            val dir = newMockDirectory()
            dir.failOn(
                object : MockDirectoryWrapper.Failure() {
                    override fun eval(dir: MockDirectoryWrapper) {
                        if (random().nextInt(10) != 0) {
                            return
                        }
                        if (dir.rollbackStage == "rollbackInternal") {
                            if (VERBOSE) {
                                println("TEST: now fail; exc:")
                            }
                            throw FakeIOException()
                        }
                    }
                }
            )

            val iwc = IndexWriterConfig()
            val iw = IndexWriter(dir, iwc)
            val doc = Document()
            for (i in 0 until 10) {
                iw.addDocument(doc)
            }
            iw.commit()

            iw.addDocument(doc)

            // pool readers
            val r = DirectoryReader.open(iw)

            // sometimes sneak in a pending commit: we don't want to leak a file handle to that segments_N
            if (random().nextBoolean()) {
                iw.prepareCommit()
            }

            try {
                iw.rollback()
            } catch (expected: FakeIOException) {
                // ok, we randomly hit exc here
            }

            r.close()

            // even though we hit exception: we are closed, no locks or files held, index in good state
            assertTrue(iw.isClosed())
            dir.obtainLock(IndexWriter.WRITE_LOCK_NAME).close()

            val r2 = DirectoryReader.open(dir)
            assertEquals(10, r2.maxDoc())
            r2.close()

            // no leaks
            dir.close()
        }
    }

    // TODO: can be super slow in pathological cases (merge config?)
    // @Nightly
    @Test
    fun testMergeExceptionIsTragic() {
        val dir = newMockDirectory()
        val didFail = AtomicBoolean(false)
        dir.failOn(
            object : MockDirectoryWrapper.Failure() {
                override fun eval(dir: MockDirectoryWrapper) {
                    if (random().nextInt(10) != 0) {
                        return
                    }
                    if (didFail.load()) {
                        // Already failed
                        return
                    }

                    if (dir.mergeStage == "merge") {
                        if (VERBOSE) {
                            println("TEST: now fail; exc:")
                        }
                        didFail.store(true)
                        throw FakeIOException()
                    }
                }
            }
        )

        val iwc = newIndexWriterConfig()
        val mp = iwc.mergePolicy
        if (mp is TieredMergePolicy) {
            val tmp = mp
            if (tmp.maxMergedSegmentMB < 0.2) {
                tmp.setMaxMergedSegmentMB(0.2)
            }
        }
        val ms = iwc.mergeScheduler
        if (ms is ConcurrentMergeScheduler) {
            ms.setSuppressExceptions()
        }
        val w = IndexWriter(dir, iwc)

        while (true) {
            try {
                val doc = Document()
                doc.add(newStringField("field", "string", Field.Store.NO))
                w.addDocument(doc)
                if (random().nextInt(10) == 7) {
                    // Flush new segment:
                    DirectoryReader.open(w).close()
                }
            } catch (ace: AlreadyClosedException) {
                // OK: e.g. CMS hit the exc in BG thread and closed the writer
                break
            } catch (fioe: FakeIOException) {
                // OK: e.g. SMS hit the exception
                break
            } catch (ise: IllegalStateException) {
                // OK: Merge-on-refresh refuses to run because IndexWriter hit a tragedy
                break
            }
        }

        assertNotNull(w.getTragicException())
        assertFalse(w.isOpen())
        assertTrue(didFail.load())

        if (ms is ConcurrentMergeScheduler) {
            // Sneaky: CMS's merge thread will be concurrently rolling back IW due
            // to the tragedy, with this main thread, so we have to wait here
            // to ensure the rollback has finished, else MDW still sees open files:
            runBlocking { ms.sync() }
        }

        dir.close()
    }

    @Test
    fun testOnlyRollbackOnceOnException() {
        val once = AtomicBoolean(false)
        val stream = object : InfoStream() {
            override fun message(component: String, message: String) {
                if ("TP".equals(component) && "rollback before checkpoint".equals(message)) {
                    if (once.compareAndSet(false, true)) {
                        throw RuntimeException("boom")
                    } else {
                        throw AssertionError("has been rolled back twice")
                    }
                }
            }

            override fun isEnabled(component: String): Boolean {
                return "TP".equals(component)
            }

            override fun close() {}
        }
        val dir = newDirectory()
        try {
            val writer =
                object : IndexWriter(dir, newIndexWriterConfig().setInfoStream(stream)) {
                    override fun isEnableTestPoints(): Boolean {
                        return true
                    }
                }
            writer.rollback()
            fail()
        } catch (e: RuntimeException) {
            assertEquals("boom", e.message)
            assertEquals(
                0,
                e.suppressedExceptions.size,
                "has suppressed exceptions: ${e.suppressedExceptions}"
            )
            assertNull(e.cause)
        }
        dir.close()
    }

    @Test
    fun testExceptionOnSyncMetadata() {
        val dir = newMockDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig().setCommitOnClose(false))
        writer.commit()
        val maybeFailDelete = AtomicBoolean(false)
        val failDelete = { random().nextBoolean() && maybeFailDelete.load() }
        dir.failOn(
            object : MockDirectoryWrapper.Failure() {
                override fun eval(dir: MockDirectoryWrapper) {
                    if (dir.isSyncingMetaData && dir.commitStage == "finishCommit") {
                        throw RuntimeException("boom")
                    } else if (failDelete()
                        && dir.rollbackStage == "rollbackInternal"
                        && dir.deleteStage == "deleteFiles"
                    ) {
                        throw RuntimeException("bang")
                    }
                }
            }
        )
        for (i in 0 until 5) {
            val doc = Document()
            doc.add(newStringField("id", i.toString(), Field.Store.NO))
            doc.add(NumericDocValuesField("dv", i.toLong()))
            doc.add(BinaryDocValuesField("dv2", BytesRef(i.toString())))
            doc.add(SortedDocValuesField("dv3", BytesRef(i.toString())))
            doc.add(SortedSetDocValuesField("dv4", BytesRef(i.toString())))
            doc.add(SortedSetDocValuesField("dv4", BytesRef((i - 1).toString())))
            doc.add(SortedNumericDocValuesField("dv5", i.toLong()))
            doc.add(SortedNumericDocValuesField("dv5", (i - 1).toLong()))
            doc.add(
                newTextField(
                    "text1", TestUtil.randomAnalysisString(random(), 20, true), Field.Store.NO
                )
            )
            // ensure we store something
            doc.add(StoredField("stored1", "foo"))
            doc.add(StoredField("stored1", "bar"))
            // ensure we get some payloads
            doc.add(
                newTextField(
                    "text_payloads", TestUtil.randomAnalysisString(random(), 6, true), Field.Store.NO
                )
            )
            // ensure we get some vectors
            val ft = FieldType(TextField.TYPE_NOT_STORED)
            ft.setStoreTermVectors(true)
            doc.add(newField("text_vectors", TestUtil.randomAnalysisString(random(), 6, true), ft))
            doc.add(IntPoint("point", random().nextInt()))
            doc.add(IntPoint("point2d", random().nextInt(), random().nextInt()))
            writer.addDocument(Document())
        }
        try {
            writer.commit()
            fail()
        } catch (e: RuntimeException) {
            assertEquals("boom", e.message)
        }
        try {
            maybeFailDelete.store(true)
            writer.rollback()
        } catch (e: RuntimeException) {
            assertEquals("bang", e.message)
        }
        maybeFailDelete.store(false)
        assertTrue(writer.isClosed())
        assertTrue(DirectoryReader.indexExists(dir))
        DirectoryReader.open(dir).close()
        dir.close()
    }

    @Test
    fun testExceptionJustBeforeFlushWithPointValues() {
        val dir = newDirectory()
        val analyzer = object : Analyzer(PER_FIELD_REUSE_STRATEGY) {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                tokenizer.enableChecks =
                    false // disable workflow checking as we forcefully close() in exceptional cases.
                val stream: TokenStream = CrashingFilter(fieldName, tokenizer)
                return TokenStreamComponents(tokenizer, stream)
            }
        }
        val iwc =
            newIndexWriterConfig(analyzer).setCommitOnClose(false).setMaxBufferedDocs(3)
        val mp = iwc.mergePolicy
        iwc.setMergePolicy(
            SoftDeletesRetentionMergePolicy("soft_delete", { MatchAllDocsQuery() }, mp)
        )
        val w = RandomIndexWriter.mockIndexWriter(dir, iwc, random())
        val newdoc = Document()
        newdoc.add(newTextField("crash", "do it on token 4", Field.Store.NO))
        newdoc.add(IntPoint("int", 42))
        assertFailsWith<IOException> { w.addDocument(newdoc) }
        val r = w.getReader(false, false)
        val onlyReader = getOnlyLeafReader(r)
        // we mark the failed doc as deleted
        assertEquals(onlyReader.numDeletedDocs(), 1)
        // there are not points values (rather than an empty set of values)
        assertNull(onlyReader.getPointValues("field"))
        onlyReader.close()
        w.close()
        dir.close()
    }
}
