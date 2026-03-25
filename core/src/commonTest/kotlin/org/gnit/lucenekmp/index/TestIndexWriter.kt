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
import okio.FileNotFoundException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.jdkport.Collections
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.RejectedExecutionException
import org.gnit.lucenekmp.jdkport.Semaphore
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.printStackTrace
import org.gnit.lucenekmp.jdkport.updateAndGet
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.SearcherFactory
import org.gnit.lucenekmp.search.SearcherManager
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.LockObtainFailedException
import org.gnit.lucenekmp.store.MMapDirectory
import org.gnit.lucenekmp.store.NIOFSDirectory
import org.gnit.lucenekmp.store.NoLockFactory
import org.gnit.lucenekmp.store.SimpleFSLockFactory
import org.gnit.lucenekmp.tests.index.SuppressingConcurrentMergeScheduler
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.mockfile.ExtrasFS
import org.gnit.lucenekmp.tests.mockfile.WindowsFS
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Constants
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.SetOnce
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.ThreadInterruptedException
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.StringField
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.random.Random
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TestIndexWriter : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testDocCount() {
        val dir = newDirectory()

        var writer: IndexWriter
        var reader: IndexReader

        writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        // add 100 documents
        for (i in 0 until 100) {
            addDocWithIndex(writer, i)
            if (random().nextBoolean()) {
                writer.commit()
            }
        }
        var docStats = writer.getDocStats()
        assertEquals(100, docStats.maxDoc)
        assertEquals(100, docStats.numDocs)
        writer.close()

        // delete 40 documents
        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(
                        object : FilterMergePolicy(NoMergePolicy.INSTANCE) {
                            override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                                return true
                            }
                        }
                    )
            )

        for (i in 0 until 40) {
            writer.deleteDocuments(Term("id", "$i"))
            if (random().nextBoolean()) {
                writer.commit()
            }
        }
        writer.flush()
        docStats = writer.getDocStats()
        assertEquals(100, docStats.maxDoc)
        assertEquals(60, docStats.numDocs)
        writer.close()

        reader = DirectoryReader.open(dir)
        assertEquals(60, reader.numDocs())
        reader.close()

        // merge the index down and check that the new doc count is correct
        writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        assertEquals(60, writer.getDocStats().numDocs)
        writer.forceMerge(1)
        docStats = writer.getDocStats()
        assertEquals(60, docStats.maxDoc)
        assertEquals(60, docStats.numDocs)
        writer.close()

        // check that the index reader gives the same numbers.
        reader = DirectoryReader.open(dir)
        assertEquals(60, reader.maxDoc())
        assertEquals(60, reader.numDocs())
        reader.close()

        // make sure opening a new index for create over this existing one works correctly:
        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.CREATE)
            )
        docStats = writer.getDocStats()
        assertEquals(0, docStats.maxDoc)
        assertEquals(0, docStats.numDocs)
        writer.close()
        dir.close()
    }

    // Make sure we can open an index for create even when a
    // reader holds it open (this fails pre lock-less commits on Windows):
    @Test
    @Throws(IOException::class)
    fun testCreateWithReader() {
        val dir = newDirectory()

        // add one document & close writer
        var writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        addDoc(writer)
        writer.close()

        // now open reader:
        val reader = DirectoryReader.open(dir)
        assertEquals(1, reader.numDocs(), "should be one document")

        // now open index for create:
        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.CREATE)
            )
        assertEquals(0, writer.getDocStats().maxDoc, "should be zero documents")
        addDoc(writer)
        writer.close()

        assertEquals(1, reader.numDocs(), "should be one document")
        val reader2 = DirectoryReader.open(dir)
        assertEquals(1, reader2.numDocs(), "should be one document")
        reader.close()
        reader2.close()

        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testChangesAfterClose() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        addDoc(writer)

        // close
        writer.close()
        expectThrows(AlreadyClosedException::class) { addDoc(writer) }

        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testIndexNoDocuments() {
        val dir = newDirectory()
        var writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        writer.commit()
        writer.close()

        var reader = DirectoryReader.open(dir)
        assertEquals(0, reader.maxDoc())
        assertEquals(0, reader.numDocs())
        reader.close()

        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND)
            )
        writer.commit()
        writer.close()

        reader = DirectoryReader.open(dir)
        assertEquals(0, reader.maxDoc())
        assertEquals(0, reader.numDocs())
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSmallRAMBuffer() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setRAMBufferSizeMB(0.000001)
                    .setMergePolicy(newLogMergePolicy(10))
            )
        var lastNumSegments = getSegmentCount(dir)
        for (j in 0 until 9) {
            val doc = Document()
            doc.add(newField("field", "aaa$j", storedTextType))
            writer.addDocument(doc)
            // Verify that with a tiny RAM buffer we see new segment after every doc
            val numSegments = getSegmentCount(dir)
            assertTrue(numSegments > lastNumSegments)
            lastNumSegments = numSegments
        }
        writer.close()
        dir.close()
    }

    // Make sure it's OK to change RAM buffer size and maxBufferedDocs in a write session
    @Test
    @Throws(IOException::class)
    fun testChangingRAMBuffer() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        writer.config.setMaxBufferedDocs(10)
        writer.config.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())

        var lastFlushCount = -1
        for (j in 1 until 52) {
            val doc = Document()
            doc.add(Field("field", "aaa$j", storedTextType))
            writer.addDocument(doc)
            runBlocking {
                val mergeScheduler = writer.config.mergeScheduler
                if (mergeScheduler is ConcurrentMergeScheduler) {
                    mergeScheduler.sync()
                }
            }
            val flushCount = writer.getFlushCount()
            if (j == 1) {
                lastFlushCount = flushCount
            } else if (j < 10) {
                // No new files should be created
                assertEquals(flushCount, lastFlushCount)
            } else if (10 == j) {
                assertTrue(flushCount > lastFlushCount)
                lastFlushCount = flushCount
                writer.config.setRAMBufferSizeMB(0.000001)
                writer.config.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            } else if (j < 20) {
                assertTrue(flushCount > lastFlushCount)
                lastFlushCount = flushCount
            } else if (20 == j) {
                writer.config.setRAMBufferSizeMB(16.0)
                writer.config.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
                lastFlushCount = flushCount
            } else if (j < 30) {
                assertEquals(flushCount, lastFlushCount)
            } else if (30 == j) {
                writer.config.setRAMBufferSizeMB(0.000001)
                writer.config.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            } else if (j < 40) {
                assertTrue(flushCount > lastFlushCount)
                lastFlushCount = flushCount
            } else if (40 == j) {
                writer.config.setMaxBufferedDocs(10)
                writer.config.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                lastFlushCount = flushCount
            } else if (j < 50) {
                assertEquals(flushCount, lastFlushCount)
                writer.config.setMaxBufferedDocs(10)
                writer.config.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
            } else if (50 == j) {
                assertTrue(flushCount > lastFlushCount)
            }
        }
        writer.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEnablingNorms() {
        val dir = newDirectory()
        var writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(10)
            )
        // Enable norms for only 1 doc, pre flush
        val customType = FieldType(TextField.TYPE_STORED)
        customType.setOmitNorms(true)
        for (j in 0 until 10) {
            val doc = Document()
            val f =
                if (j != 8) {
                    newField("field", "aaa", customType)
                } else {
                    newField("field", "aaa", storedTextType)
                }
            doc.add(f)
            writer.addDocument(doc)
        }
        writer.close()

        val searchTerm = Term("field", "aaa")

        var reader = DirectoryReader.open(dir)
        val searcher: IndexSearcher = newSearcher(reader)
        val hits: Array<ScoreDoc> = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
        assertEquals(10, hits.size)
        reader.close()

        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setMaxBufferedDocs(10)
            )
        // Enable norms for only 1 doc, post flush
        for (j in 0 until 27) {
            val doc = Document()
            val f =
                if (j != 26) {
                    newField("field", "aaa", customType)
                } else {
                    newField("field", "aaa", storedTextType)
                }
            doc.add(f)
            writer.addDocument(doc)
        }
        writer.close()
        reader = DirectoryReader.open(dir)
        val searcher2: IndexSearcher = newSearcher(reader)
        val hits2: Array<ScoreDoc> = searcher2.search(TermQuery(searchTerm), 1000).scoreDocs
        assertEquals(27, hits2.size)
        reader.close()

        reader = DirectoryReader.open(dir)
        reader.close()

        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testHighFreqTerm() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setRAMBufferSizeMB(0.01)
            )
        // Massive doc that has 128 K a's
        val b = StringBuilder(1024 * 1024)
        for (i in 0 until 4096) {
            b.append(" a a a a a a a a")
            b.append(" a a a a a a a a")
            b.append(" a a a a a a a a")
            b.append(" a a a a a a a a")
        }
        val doc = Document()
        val customType = FieldType(TextField.TYPE_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        doc.add(newField("field", b.toString(), customType))
        writer.addDocument(doc)
        writer.close()

        val reader = DirectoryReader.open(dir)
        assertEquals(1, reader.maxDoc())
        assertEquals(1, reader.numDocs())
        val t = Term("field", "a")
        assertEquals(1, reader.docFreq(t))
        val td =
            TestUtil.docs(random(), reader, "field", newBytesRef("a"), null, PostingsEnum.FREQS.toInt())
        td!!.nextDoc()
        assertEquals(128 * 1024, td.freq())
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFlushWithNoMerging() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy(10))
            )
        val doc = Document()
        val customType = FieldType(TextField.TYPE_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        doc.add(newField("field", "aaa", customType))
        for (i in 0 until 19) {
            writer.addDocument(doc)
        }
        writer.flush(false, true)
        // Since we flushed w/o allowing merging we should now have 10 segments
        assertEquals(10, writer.getSegmentCount())
        writer.close()
        dir.close()
    }

    // Make sure we can flush segment w/ norms, then add empty doc (no norms) and flush
    @Test
    @Throws(IOException::class)
    fun testEmptyDocAfterFlushingRealDoc() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val customType = FieldType(TextField.TYPE_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        doc.add(newField("field", "aaa", customType))
        writer.addDocument(doc)
        writer.commit()
        if (VERBOSE) {
            println("\nTEST: now add empty doc")
        }
        writer.addDocument(Document())
        writer.close()
        val reader = DirectoryReader.open(dir)
        assertEquals(2, reader.numDocs())
        reader.close()
        dir.close()
    }

    /**
     * Test that no NullPointerException will be raised, when adding one document with a single, empty
     * field and term vectors enabled.
     */
    @Test
    @Throws(IOException::class)
    fun testBadSegment() {
        val dir = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        val document = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        document.add(newField("tvtest", "", customType))
        iw.addDocument(document)
        iw.close()
        dir.close()
    }

    // LUCENE-1036
    @Test
    @Throws(IOException::class)
    fun testMaxThreadPriority() {
        val pri = Thread.currentThread().getPriority()
        try {
            val dir = newDirectory()
            val conf =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy())
            (conf.mergePolicy as LogMergePolicy).mergeFactor = 2
            val iw = IndexWriter(dir, conf)
            val document = Document()
            val customType = FieldType(TextField.TYPE_NOT_STORED)
            customType.setStoreTermVectors(true)
            document.add(newField("tvtest", "a b c", customType))
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY)
            for (i in 0 until 4) {
                iw.addDocument(document)
            }
            iw.close()
            dir.close()
        } finally {
            Thread.currentThread().setPriority(pri)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testVariableSchema() {
        val dir = newDirectory()
        for (i in 0 until 20) {
            if (VERBOSE) {
                println("TEST: iter=$i")
            }
            var writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(2)
                        .setMergePolicy(newLogMergePolicy())
                )
            // LogMergePolicy lmp = (LogMergePolicy) writer.getConfig().getMergePolicy();
            // lmp.setMergeFactor(2);
            // lmp.setNoCFSRatio(0.0);
            val doc = Document()
            val contents = "aa bb cc dd ee ff gg hh ii jj kk"

            val customType = FieldType(TextField.TYPE_STORED)
            if (i == 7) {
                // Add empty docs here
                doc.add(newTextField("content3", "", Field.Store.NO))
            } else {
                val type: FieldType
                if (i % 2 == 0) {
                    doc.add(newField("content4", contents, customType))
                    type = customType
                } else {
                    type = TextField.TYPE_NOT_STORED
                }
                doc.add(newTextField("content1", contents, Field.Store.NO))
                doc.add(newField("content3", "", customType))
                doc.add(newField("content5", "", type))
            }

            for (j in 0 until 4) {
                writer.addDocument(doc)
            }

            writer.close()

            if (0 == i % 4) {
                writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
                // LogMergePolicy lmp2 = (LogMergePolicy) writer.getConfig().getMergePolicy();
                // lmp2.setNoCFSRatio(0.0);
                writer.forceMerge(1)
                writer.close()
            }
        }
        dir.close()
    }

    // LUCENE-1084: test unlimited field length
    @Test
    @Throws(IOException::class)
    fun testUnlimitedMaxFieldLength() {
        val dir = newDirectory()

        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        val doc = Document()
        doc.add(newTextField("field", " a".repeat(10_000) + " x", Field.Store.NO))
        writer.addDocument(doc)
        writer.close()

        val reader = DirectoryReader.open(dir)
        val t = Term("field", "x")
        assertEquals(1, reader.docFreq(t))
        reader.close()
        dir.close()
    }

    // LUCENE-1179
    @Test
    @Throws(IOException::class)
    fun testEmptyFieldName() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(newTextField("", "a b c", Field.Store.NO))
        writer.addDocument(doc)
        writer.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyFieldNameTerms() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(newTextField("", "a b c", Field.Store.NO))
        writer.addDocument(doc)
        writer.close()
        val reader = DirectoryReader.open(dir)
        val subreader = getOnlyLeafReader(reader)
        val te = subreader.terms("")!!.iterator()
        assertEquals(newBytesRef("a"), te.next())
        assertEquals(newBytesRef("b"), te.next())
        assertEquals(newBytesRef("c"), te.next())
        assertNull(te.next())
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyFieldNameWithEmptyTerm() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(newStringField("", "", Field.Store.NO))
        doc.add(newStringField("", "a", Field.Store.NO))
        doc.add(newStringField("", "b", Field.Store.NO))
        doc.add(newStringField("", "c", Field.Store.NO))
        writer.addDocument(doc)
        writer.close()
        val reader = DirectoryReader.open(dir)
        val subreader = getOnlyLeafReader(reader)
        val te = subreader.terms("")!!.iterator()
        assertEquals(newBytesRef(""), te.next())
        assertEquals(newBytesRef("a"), te.next())
        assertEquals(newBytesRef("b"), te.next())
        assertEquals(newBytesRef("c"), te.next())
        assertNull(te.next())
        reader.close()
        dir.close()
    }

    // LUCENE-1222
    @Test
    @Throws(IOException::class)
    fun testDoBeforeAfterFlush() {
        val dir = newDirectory()
        val w = MockIndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val customType = FieldType(TextField.TYPE_STORED)
        doc.add(newField("field", "a field", customType))
        w.addDocument(doc)
        w.commit()
        assertTrue(w.beforeWasCalled)
        assertTrue(w.afterWasCalled)
        w.beforeWasCalled = false
        w.afterWasCalled = false
        w.deleteDocuments(Term("field", "field"))
        w.commit()
        assertTrue(w.beforeWasCalled)
        assertTrue(w.afterWasCalled)
        w.close()

        val ir = DirectoryReader.open(dir)
        assertEquals(0, ir.numDocs())
        ir.close()

        dir.close()
    }

    // LUCENE-1255
    @Test
    @Throws(Throwable::class)
    fun testNegativePositions() {
        val tokens =
            object : TokenStream() {
                val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
                val posIncrAtt: PositionIncrementAttribute =
                    addAttribute(PositionIncrementAttribute::class)

                val terms = listOf("a", "b", "c").iterator()
                var first = true

                override fun incrementToken(): Boolean {
                    if (!terms.hasNext()) {
                        return false
                    }
                    clearAttributes()
                    termAtt.append(terms.next())
                    posIncrAtt.setPositionIncrement(if (first) 0 else 1)
                    first = false
                    return true
                }
            }

        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(TextField("field", tokens))
        expectThrows(IllegalArgumentException::class) { w.addDocument(doc) }

        w.close()
        dir.close()
    }

    // LUCENE-2529
    @Test
    @Throws(Exception::class)
    fun testPositionIncrementGapEmptyField() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        analyzer.setPositionIncrementGap(100)
        val w = IndexWriter(dir, newIndexWriterConfig(analyzer))
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        val f = newField("field", "", customType)
        val f2 = newField("field", "crunch man", customType)
        doc.add(f)
        doc.add(f2)
        w.addDocument(doc)
        w.close()

        val r = DirectoryReader.open(dir)
        val tpv = r.termVectors().get(0, "field")!!
        val termsEnum = tpv.iterator()
        assertNotNull(termsEnum.next())
        var dpEnum = termsEnum.postings(null, PostingsEnum.ALL.toInt())
        assertNotNull(dpEnum)
        assertTrue(dpEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(1, dpEnum.freq())
        assertEquals(100, dpEnum.nextPosition())

        assertNotNull(termsEnum.next())
        dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())
        assertNotNull(dpEnum)
        assertTrue(dpEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(1, dpEnum.freq())
        assertEquals(101, dpEnum.nextPosition())
        assertNull(termsEnum.next())

        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeadlock() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(2)
            )
        val doc = Document()

        val customType = FieldType(TextField.TYPE_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)

        doc.add(newField("content", "aaa bbb ccc ddd eee fff ggg hhh iii", customType))
        writer.addDocument(doc)
        writer.addDocument(doc)
        writer.addDocument(doc)
        writer.commit()
        // index has 2 segments

        val dir2 = newDirectory()
        val writer2 = IndexWriter(dir2, newIndexWriterConfig(MockAnalyzer(random())))
        writer2.addDocument(doc)
        writer2.close()

        val r1 = DirectoryReader.open(dir2)
        TestUtil.addIndexesSlowly(writer, r1, r1)
        writer.close()

        val r3 = DirectoryReader.open(dir)
        assertEquals(5, r3.numDocs())
        r3.close()

        r1.close()

        dir2.close()
        dir.close()
    }

    private class IndexerThreadInterrupt(id: Int) : Thread() {
        @Volatile
        var failed = false

        @Volatile
        var finish = false

        @Volatile
        var allowInterrupt = false

        val random = Random(LuceneTestCase.random().nextLong())
        val adder: Directory
        val bytesLog = ByteArrayOutputStream()
        val log = PrintStream(bytesLog, true, StandardCharsets.UTF_8)
        val id = id

        init {
            adder = MockDirectoryWrapper(random, ByteBuffersDirectory())
            val conf = newIndexWriterConfig(random, MockAnalyzer(random))
            if (conf.mergeScheduler is ConcurrentMergeScheduler) {
                conf.setMergeScheduler(
                    object : SuppressingConcurrentMergeScheduler() {
                        override fun isOK(t: Throwable): Boolean {
                            return t is AlreadyClosedException ||
                                (t is IllegalStateException &&
                                    t.message!!.contains("this writer hit an unrecoverable error"))
                        }
                    }
                )
            }
            val w = IndexWriter(adder, conf)
            var doc = Document()
            doc.add(newStringField(random, "id", "500", Field.Store.NO))
            doc.add(newField(random, "field", "some prepackaged text contents", storedTextType))
            doc.add(BinaryDocValuesField("binarydv", newBytesRef("500")))
            doc.add(NumericDocValuesField("numericdv", 500))
            doc.add(SortedDocValuesField("sorteddv", newBytesRef("500")))
            doc.add(SortedSetDocValuesField("sortedsetdv", newBytesRef("one")))
            doc.add(SortedSetDocValuesField("sortedsetdv", newBytesRef("two")))
            doc.add(SortedNumericDocValuesField("sortednumericdv", 4))
            doc.add(SortedNumericDocValuesField("sortednumericdv", 3))
            w.addDocument(doc)
            doc = Document()
            doc.add(newStringField(random, "id", "501", Field.Store.NO))
            doc.add(newField(random, "field", "some more contents", storedTextType))
            doc.add(BinaryDocValuesField("binarydv", newBytesRef("501")))
            doc.add(NumericDocValuesField("numericdv", 501))
            doc.add(SortedDocValuesField("sorteddv", newBytesRef("501")))
            doc.add(SortedSetDocValuesField("sortedsetdv", newBytesRef("two")))
            doc.add(SortedSetDocValuesField("sortedsetdv", newBytesRef("three")))
            doc.add(SortedNumericDocValuesField("sortednumericdv", 6))
            doc.add(SortedNumericDocValuesField("sortednumericdv", 1))
            w.addDocument(doc)
            w.deleteDocuments(Term("id", "500"))
            w.close()
        }

        override fun run() {
            val dir = MockDirectoryWrapper(random, ByteBuffersDirectory())
            dir.useSlowOpenClosers = true
            dir.setThrottling(MockDirectoryWrapper.Throttling.SOMETIMES)

            var w: IndexWriter? = null
            while (!finish) {
                try {
                    while (!finish) {
                        if (w != null) {
                            try {
                                w.close()
                            } catch (_: AlreadyClosedException) {
                                // OK
                            }
                            w = null
                        }
                        val conf =
                            newIndexWriterConfig(random, MockAnalyzer(random)).setMaxBufferedDocs(2)
                        if (conf.mergeScheduler is ConcurrentMergeScheduler) {
                            conf.setMergeScheduler(
                                object : SuppressingConcurrentMergeScheduler() {
                                    override fun isOK(t: Throwable): Boolean {
                                        return t is AlreadyClosedException ||
                                            t is RejectedExecutionException ||
                                            (t is IllegalStateException &&
                                                t.message!!.contains("this writer hit an unrecoverable error"))
                                    }
                                }
                            )
                        }
                        w = IndexWriter(dir, conf)

                        val doc = Document()
                        val idField = newStringField(random, "id", "", Field.Store.NO)
                        val binaryDVField = BinaryDocValuesField("binarydv", newBytesRef())
                        val numericDVField = NumericDocValuesField("numericdv", 0)
                        val sortedDVField = SortedDocValuesField("sorteddv", newBytesRef())
                        val sortedSetDVField = SortedSetDocValuesField("sortedsetdv", newBytesRef())
                        doc.add(idField)
                        doc.add(newField(random, "field", "some text contents", storedTextType))
                        doc.add(binaryDVField)
                        doc.add(numericDVField)
                        doc.add(sortedDVField)
                        doc.add(sortedSetDVField)
                        for (i in 0 until 100) {
                            val id = i.toString()
                            idField.setStringValue(id)
                            binaryDVField.setBytesValue(newBytesRef(id))
                            numericDVField.setLongValue(i.toLong())
                            sortedDVField.setBytesValue(newBytesRef(id))
                            sortedSetDVField.setBytesValue(newBytesRef(id))
                            val action = random.nextInt(100)
                            if (action == 17) {
                                w.addIndexes(adder)
                            } else if (action % 30 == 0) {
                                w.deleteAll()
                            } else if (action % 2 == 0) {
                                w.updateDocument(Term("id", id), doc)
                            } else {
                                w.addDocument(doc)
                            }
                            if (random.nextInt(3) == 0) {
                                var r: IndexReader? = null
                                try {
                                    r = DirectoryReader.open(w, random.nextBoolean(), false)
                                    if (random.nextBoolean() && r.maxDoc() > 0) {
                                        val docid = random.nextInt(r.maxDoc())
                                        w.tryDeleteDocument(r, docid)
                                    }
                                } finally {
                                    IOUtils.closeWhileHandlingException(r)
                                }
                            }
                            if (i % 10 == 0) {
                                w.commit()
                            }
                            if (random.nextInt(50) == 0) {
                                w.forceMerge(1)
                            }
                        }
                        w.close()
                        w = null
                        DirectoryReader.open(dir).close()
                        allowInterrupt = true
                    }
                } catch (re: ThreadInterruptedException) {
                    log.println("TEST thread $id: got interrupt")
                    re.printStackTrace(log)
                    val e = re.cause
                    assertTrue(e is InterruptedException)
                    if (finish) {
                        break
                    }
                } catch (t: Throwable) {
                    log.println("thread $id FAILED; unexpected exception")
                    t.printStackTrace(log)
                    listIndexFiles(log, dir)
                    failed = true
                    break
                }
            }

            if (VERBOSE) {
                log.println("TEST: thread $id: now finish failed=$failed")
            }
            if (!failed) {
                if (VERBOSE) {
                    log.println("TEST: thread $id: now rollback")
                }
                Thread.interrupted()
                if (w != null) {
                    try {
                        w.rollback()
                    } catch (ioe: IOException) {
                        throw RuntimeException(ioe)
                    }
                }

                try {
                    TestUtil.checkIndex(dir)
                } catch (e: Exception) {
                    failed = true
                    log.println("thread $id: CheckIndex FAILED: unexpected exception")
                    e.printStackTrace(log)
                    listIndexFiles(log, dir)
                }
                try {
                    val r = DirectoryReader.open(dir)
                    r.close()
                } catch (e: Exception) {
                    failed = true
                    log.println("thread $id: DirectoryReader.open FAILED: unexpected exception")
                    e.printStackTrace(log)
                    listIndexFiles(log, dir)
                }
            }
            try {
                IOUtils.close(dir)
            } catch (e: IOException) {
                failed = true
                throw RuntimeException("thread $id", e)
            }
            try {
                IOUtils.close(adder)
            } catch (e: IOException) {
                failed = true
                throw RuntimeException("thread $id", e)
            }
        }

        private fun listIndexFiles(log: PrintStream, dir: Directory) {
            try {
                log.println("index files: ${dir.listAll().contentToString()}")
            } catch (ioe: IOException) {
                log.println("failed to index files:")
                ioe.printStackTrace(log)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testThreadInterruptDeadlock() {
        val t = IndexerThreadInterrupt(1)
        t.setDaemon(true)
        t.start()

        assertTrue(
            ThreadInterruptedException(InterruptedException()).cause is InterruptedException
        )

        val numInterrupts = atLeast(100)
        var i = 0
        while (i < numInterrupts) {
            Thread.sleep(10)
            if (t.allowInterrupt) {
                i++
                t.interrupt()
            }
            if (!t.isAlive()) {
                break
            }
        }
        t.finish = true
        t.join()
        if (t.failed) {
            fail(t.bytesLog.toString(StandardCharsets.UTF_8))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIndexStoreCombos() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        var b = ByteArray(50)
        for (i in 0 until 50) {
            b[i] = (i + 77).toByte()
        }

        val doc = Document()

        val customType = FieldType(StoredField.TYPE)
        customType.setTokenized(true)

        val field1 = MockTokenizer(MockTokenizer.WHITESPACE, false)
        val f =
            object : Field("binary", b, 10, 17, customType) {
                override fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream {
                    return field1
                }
            }
        customType.setIndexOptions(IndexOptions.DOCS)
        field1.setReader(StringReader("doc1field1"))

        val customType2 = FieldType(TextField.TYPE_STORED)

        val field2 = MockTokenizer(MockTokenizer.WHITESPACE, false)
        val f2 =
            object : Field("string", "value", customType2) {
                override fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream {
                    return field2
                }
            }

        field2.setReader(StringReader("doc1field2"))
        doc.add(f)
        doc.add(f2)
        w.addDocument(doc)

        field1.setReader(StringReader("doc2field1"))
        field2.setReader(StringReader("doc2field2"))
        w.addDocument(doc)

        w.commit()

        field1.setReader(StringReader("doc3field1"))
        field2.setReader(StringReader("doc3field2"))

        w.addDocument(doc)
        w.commit()
        w.forceMerge(1)
        w.close()

        val ir = DirectoryReader.open(dir)
        val storedFields = ir.storedFields()
        val doc2 = storedFields.document(0)
        val f3: IndexableField = doc2.getField("binary")!!
        b = f3.binaryValue()!!.bytes
        assertNotNull(b)
        assertEquals(17, b.size)
        assertEquals(87, b[0].toInt())

        assertNotNull(storedFields.document(0).getField("binary")!!.binaryValue())
        assertNotNull(storedFields.document(1).getField("binary")!!.binaryValue())
        assertNotNull(storedFields.document(2).getField("binary")!!.binaryValue())

        assertEquals("value", storedFields.document(0).get("string"))
        assertEquals("value", storedFields.document(1).get("string"))
        assertEquals("value", storedFields.document(2).get("string"))

        assertTrue(
            TestUtil.docs(random(), ir, "binary", newBytesRef("doc1field1"), null, PostingsEnum.NONE.toInt())!!
                .nextDoc() != DocIdSetIterator.NO_MORE_DOCS
        )
        assertTrue(
            TestUtil.docs(random(), ir, "binary", newBytesRef("doc2field1"), null, PostingsEnum.NONE.toInt())!!
                .nextDoc() != DocIdSetIterator.NO_MORE_DOCS
        )
        assertTrue(
            TestUtil.docs(random(), ir, "binary", newBytesRef("doc3field1"), null, PostingsEnum.NONE.toInt())!!
                .nextDoc() != DocIdSetIterator.NO_MORE_DOCS
        )
        assertTrue(
            TestUtil.docs(random(), ir, "string", newBytesRef("doc1field2"), null, PostingsEnum.NONE.toInt())!!
                .nextDoc() != DocIdSetIterator.NO_MORE_DOCS
        )
        assertTrue(
            TestUtil.docs(random(), ir, "string", newBytesRef("doc2field2"), null, PostingsEnum.NONE.toInt())!!
                .nextDoc() != DocIdSetIterator.NO_MORE_DOCS
        )
        assertTrue(
            TestUtil.docs(random(), ir, "string", newBytesRef("doc3field2"), null, PostingsEnum.NONE.toInt())!!
                .nextDoc() != DocIdSetIterator.NO_MORE_DOCS
        )

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Throwable::class)
    fun testNoDocsIndex() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        writer.addDocument(Document())
        writer.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteUnusedFiles() {
        assumeFalse("test relies on exact filenames", Codec.default.name == "SimpleText")

        for (iter in 0..1) {
            val path = createTempDir()
            val provider = WindowsFS(Files.getFileSystem())
            val previousFileSystem = Files.getFileSystem()
            Files.setFileSystem(provider.getFileSystem())
            try {
                val indexPath = provider.wrapPath(path)

                val dir: FSDirectory =
                    if (Constants.WINDOWS) {
                        MMapDirectory(indexPath)
                    } else {
                        NIOFSDirectory(indexPath)
                    }

                val mergePolicy = newLogMergePolicy(true)

                // This test expects all of its segments to be in CFS
                mergePolicy.noCFSRatio = 1.0
                mergePolicy.maxCFSSegmentSizeMB = Double.POSITIVE_INFINITY

                val w =
                    IndexWriter(
                        dir,
                        newIndexWriterConfig(MockAnalyzer(random()))
                            .setMergePolicy(mergePolicy)
                            .setUseCompoundFile(true)
                    )
                val doc = Document()
                doc.add(newTextField("field", "go", Field.Store.NO))
                w.addDocument(doc)
                val r =
                    if (iter == 0) {
                        DirectoryReader.open(w)
                    } else {
                        w.commit()
                        DirectoryReader.open(dir)
                    }

                assertTrue(Files.getFileSystem().exists(indexPath.resolve("_0.cfs")))
                assertTrue(Files.getFileSystem().exists(indexPath.resolve("_0.cfe")))
                assertTrue(Files.getFileSystem().exists(indexPath.resolve("_0.si")))
                if (iter == 1) {
                    assertTrue(Files.getFileSystem().exists(indexPath.resolve("segments_1")))
                } else {
                    assertFalse(Files.getFileSystem().exists(indexPath.resolve("segments_1")))
                }
                w.addDocument(doc)
                w.forceMerge(1)
                if (iter == 1) {
                    w.commit()
                }
                val r2 = DirectoryReader.openIfChanged(r)
                assertNotNull(r2)
                assertTrue(r !== r2)

                assertTrue(Files.getFileSystem().exists(indexPath.resolve("_0.cfs")))
                w.deleteUnusedFiles()

                assertTrue(Files.getFileSystem().exists(indexPath.resolve("_0.cfs")))

                r.close()
                if (iter == 0) {
                    assertFalse(Files.getFileSystem().exists(indexPath.resolve("_0.cfs")))
                } else {
                    dir.deletePendingFiles()
                    assertFalse(Files.getFileSystem().exists(indexPath.resolve("_0.cfs")))
                }

                w.close()
                r2.close()

                dir.close()
            } finally {
                Files.setFileSystem(previousFileSystem)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteUnusedFiles2() {
        // Validates that iw.deleteUnusedFiles() also deletes unused index commits
        // in case a deletion policy which holds onto commits is used.
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(
                        SnapshotDeletionPolicy(KeepOnlyLastCommitDeletionPolicy())
                    )
            )
        val sdp = writer.config.indexDeletionPolicy as SnapshotDeletionPolicy

        // First commit
        var doc = Document()

        val customType = FieldType(TextField.TYPE_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)

        doc.add(newField("c", "val", customType))
        writer.addDocument(doc)
        writer.commit()
        assertEquals(1, DirectoryReader.listCommits(dir).size)

        // Keep that commit
        val id = sdp.snapshot()

        // Second commit - now KeepOnlyLastCommit cannot delete the prev commit.
        doc = Document()
        doc.add(newField("c", "val", customType))
        writer.addDocument(doc)
        writer.commit()
        assertEquals(2, DirectoryReader.listCommits(dir).size)

        // Should delete the unreferenced commit
        sdp.release(id)
        writer.deleteUnusedFiles()
        assertEquals(1, DirectoryReader.listCommits(dir).size)

        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyFSDirWithNoLock() {
        // Tests that if FSDir is opened w/ a NoLockFactory (or SingleInstanceLF),
        // then IndexWriter ctor succeeds. Previously (LUCENE-2386) it failed
        // when listAll() was called in IndexFileDeleter.
        val dir = newFSDirectory(createTempDir("emptyFSDirNoLock"), NoLockFactory.INSTANCE)
        IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random()))).close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyDirRollback() {
        // TODO: generalize this test
        assumeFalse("test makes assumptions about file counts", Codec.default.name == "SimpleText")

        // Tests that if IW is created over an empty Directory, some documents are
        // indexed, flushed (but not committed) and then IW rolls back, then no
        // files are left in the Directory.

        val dir = newDirectory()

        val origFiles = dir.listAll()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy())
                    .setUseCompoundFile(false)
            )
        val files = dir.listAll()

        // Creating over empty dir should not create any files, or, at most the write.lock file
        val extraFileCount = files.size - origFiles.size
        if (extraFileCount == 1) {
            assertTrue(files.contains(IndexWriter.WRITE_LOCK_NAME))
        } else {
            Arrays.sort(origFiles)
            Arrays.sort(files)
            assertTrue(origFiles.contentEquals(files))
        }

        var doc = Document()
        val customType = FieldType(TextField.TYPE_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        // create as many files as possible
        doc.add(newField("c", "val", customType))
        writer.addDocument(doc)

        // Adding just one document does not call flush yet.
        var computedExtraFileCount = 0
        for (file in dir.listAll()) {
            if (IndexWriter.WRITE_LOCK_NAME == file ||
                file.startsWith(IndexFileNames.SEGMENTS) ||
                IndexFileNames.CODEC_FILE_PATTERN.matches(file)
            ) {
                if (file.lastIndexOf('.') < 0 ||
                    !listOf("fdm", "fdt", "tvm", "tvd", "tmp").contains(
                        file.substring(file.lastIndexOf('.') + 1)
                    )
                ) {
                    ++computedExtraFileCount
                }
            }
        }
        assertEquals(
            extraFileCount,
            computedExtraFileCount,
            "only the stored and term vector files should exist in the directory"
        )

        doc = Document()
        doc.add(newField("c", "val", customType))
        writer.addDocument(doc)

        // The second document should cause a flush.
        assertTrue(
            dir.listAll().size > 5 + extraFileCount,
            "flush should have occurred and files should have been created"
        )

        // After rollback, IW should remove all files
        writer.rollback()
        var allFiles = dir.listAll()
        assertEquals(
            origFiles.size + extraFileCount,
            allFiles.size,
            "no files should exist in the directory after rollback"
        )

        // Since we rolled-back above, that close should be a no-op
        writer.close()
        allFiles = dir.listAll()
        assertEquals(
            origFiles.size + extraFileCount,
            allFiles.size,
            "expected a no-op close after IW.rollback()"
        )
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNoUnwantedTVFiles() {
        val dir = newDirectory()
        val indexWriter =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setRAMBufferSizeMB(0.01)
                    .setMergePolicy(newLogMergePolicy())
            )
        indexWriter.config.mergePolicy.noCFSRatio = 0.0

        var big =
            "alskjhlaksjghlaksjfhalksvjepgjioefgjnsdfjgefgjhelkgjhqewlrkhgwlekgrhwelkgjhwelkgrhwlkejg"
        big += big + big + big

        val customType = FieldType(TextField.TYPE_STORED)
        customType.setOmitNorms(true)
        val customType2 = FieldType(TextField.TYPE_STORED)
        customType2.setTokenized(false)
        val customType3 = FieldType(TextField.TYPE_STORED)
        customType3.setTokenized(false)
        customType3.setOmitNorms(true)

        for (i in 0 until 2) {
            val doc = Document()
            doc.add(Field("id", "$i$big", customType3))
            doc.add(Field("str", "$i$big", customType2))
            doc.add(Field("str2", "$i$big", storedTextType))
            doc.add(Field("str3", "$i$big", customType))
            indexWriter.addDocument(doc)
        }

        indexWriter.close()

        TestUtil.checkIndex(dir)

        assertNoUnreferencedFiles(dir, "no tv files")
        val r0 = DirectoryReader.open(dir)
        for (ctx in r0.leaves()) {
            val sr = ctx.reader() as SegmentReader
            assertFalse(sr.fieldInfos.hasTermVectors())
        }

        r0.close()
        dir.close()
    }

    /** Make sure we skip wicked long terms. */
    @Test
    @Throws(IOException::class)
    fun testWickedLongTerm() {
        var dir = newDirectory()
        val w = RandomIndexWriter(random(), dir, StringSplitAnalyzer())

        val chars = CharArray(IndexWriter.MAX_TERM_LENGTH)
        Arrays.fill(chars, 'x')
        val hugeDoc = Document()
        val bigTerm = chars.concatToString()

        // This contents produces a too-long term:
        val contents = "abc xyz x${bigTerm} another term"
        hugeDoc.add(TextField("content", contents, Field.Store.NO))
        expectThrows(IllegalArgumentException::class) { w.addDocument(hugeDoc) }

        // Make sure we can add another normal document
        var doc = Document()
        doc.add(TextField("content", "abc bbb ccc", Field.Store.NO))
        w.addDocument(doc)

        // So we remove the deleted doc:
        w.forceMerge(1)

        var reader = w.reader
        w.close()

        // Make sure all terms < max size were indexed
        assertEquals(1, reader.docFreq(Term("content", "abc")))
        assertEquals(1, reader.docFreq(Term("content", "bbb")))
        assertEquals(0, reader.docFreq(Term("content", "term")))

        // Make sure the doc that has the massive term is NOT in the index:
        assertEquals(1, reader.numDocs(), "document with wicked long term is in the index!")

        reader.close()
        dir.close()
        dir = newDirectory()

        // Make sure we can add a document with exactly the maximum length term,
        // and search on that term:
        doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setTokenized(false)
        val contentField = Field("content", "", customType)
        doc.add(contentField)

        val iwc = newIndexWriterConfig()
        iwc.codec = TestUtil.getDefaultCodec()

        val w2 = RandomIndexWriter(random(), dir, iwc)

        contentField.setStringValue("other")
        w2.addDocument(doc)

        contentField.setStringValue("term")
        w2.addDocument(doc)

        contentField.setStringValue(bigTerm)
        w2.addDocument(doc)

        contentField.setStringValue("zzz")
        w2.addDocument(doc)

        reader = w2.reader
        w2.close()
        assertEquals(1, reader.docFreq(Term("content", bigTerm)))

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteAllNRTLeftoverFiles() {
        val d = MockDirectoryWrapper(random(), ByteBuffersDirectory())
        val w = IndexWriter(d, IndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        for (i in 0 until 20) {
            for (j in 0 until 100) {
                w.addDocument(doc)
            }
            w.commit()
            DirectoryReader.open(w).close()

            w.deleteAll()
            w.commit()
            // Make sure we accumulate no files except for empty segments_N and segments.gen:
            assertTrue(d.listAll().size <= 2)
        }

        w.close()
        d.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNRTReaderVersion() {
        val d = MockDirectoryWrapper(random(), ByteBuffersDirectory())
        val w = IndexWriter(d, IndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(newStringField("id", "0", Field.Store.YES))
        w.addDocument(doc)
        var r = DirectoryReader.open(w)
        val version = r.version
        r.close()

        w.addDocument(doc)
        r = DirectoryReader.open(w)
        val version2 = r.version
        r.close()
        assert(version2 > version)

        w.deleteDocuments(Term("id", "0"))
        r = DirectoryReader.open(w)
        w.close()
        val version3 = r.version
        r.close()
        assert(version3 > version2)
        d.close()
    }

    @Test
    @Throws(Exception::class)
    fun testWhetherDeleteAllDeletesWriteLock() {
        // Must use SimpleFSLockFactory...
        // NativeFSLockFactory somehow "knows" a lock is held against write.lock
        // even if you remove that file:
        val d =
            newFSDirectory(
                createTempDir("TestIndexWriter.testWhetherDeleteAllDeletesWriteLock"),
                SimpleFSLockFactory.INSTANCE
            )
        val w1 = RandomIndexWriter(random(), d)
        w1.deleteAll()
        expectThrows(LockObtainFailedException::class) {
            RandomIndexWriter(random(), d, IndexWriterConfig())
        }

        w1.close()
        d.close()
    }

    @Test
    @Throws(IOException::class)
    fun testHasBlocksMergeFullyDelSegments() {
        val documentSupplier = {
            val doc = Document()
            doc.add(StringField("foo", "bar", Field.Store.NO))
            doc
        }
        newDirectory().use { dir ->
            IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { writer ->
                val docs = mutableListOf<Document>()
                docs.add(documentSupplier())
                docs.add(documentSupplier())
                writer.updateDocuments(Term("foo", "bar"), docs)
                writer.commit()
                if (random().nextBoolean()) {
                    writer.updateDocuments(Term("foo", "bar"), docs)
                    writer.commit()
                }
                writer.updateDocument(Term("foo", "bar"), documentSupplier())
                if (random().nextBoolean()) {
                    writer.forceMergeDeletes(true)
                } else {
                    writer.forceMerge(1, true)
                }
                writer.commit()
                DirectoryReader.open(dir).use { reader ->
                    assertEquals(1, reader.leaves().size)
                    assertFalse((reader.leaves()[0].reader().metaData.hasBlocks), "hasBlocks should be cleared")
                }
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSingleDocsDoNotTriggerHasBlocks() {
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                IndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(Int.MAX_VALUE)
                    .setRAMBufferSizeMB(100.0)
            ).use { w ->
                val docs = random().nextInt(1, 100)
                for (i in 0 until docs) {
                    val doc = Document()
                    doc.add(StringField("id", "$i", Field.Store.NO))
                    w.addDocuments(listOf(doc))
                }
                w.commit()
                var si = w.cloneSegmentInfos()
                assertEquals(1, si.size())
                assertFalse(si.asList()[0].info.hasBlocks)

                val doc = Document()
                doc.add(StringField("id", "XXX", Field.Store.NO))
                w.addDocuments(listOf(doc, doc))
                w.commit()
                si = w.cloneSegmentInfos()
                assertEquals(2, si.size())
                assertFalse(si.asList()[0].info.hasBlocks)
                assertTrue(si.asList()[1].info.hasBlocks)
                w.forceMerge(1)

                w.commit()
                si = w.cloneSegmentInfos()
                assertEquals(1, si.size())
                assertTrue(si.asList()[0].info.hasBlocks)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testCarryOverHasBlocks() {
        newDirectory().use { dir ->
            IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                val docs = mutableListOf<Document>()
                docs.add(Document())
                w.updateDocuments(Term("foo", "bar"), docs)
                w.commit()
                DirectoryReader.open(dir).use { reader ->
                    var segmentInfo = (reader.leaves()[0].reader() as SegmentReader).segmentInfo
                    assertFalse(segmentInfo.info.hasBlocks)
                }

                docs.add(Document()) // now we have 2 docs
                w.updateDocuments(Term("foo", "bar"), docs)
                w.commit()
                DirectoryReader.open(dir).use { reader ->
                    assertEquals(2, reader.leaves().size)
                    var segmentInfo = (reader.leaves()[0].reader() as SegmentReader).segmentInfo
                    assertFalse(segmentInfo.info.hasBlocks, "codec: ${segmentInfo.info.codec}")
                    segmentInfo = (reader.leaves()[1].reader() as SegmentReader).segmentInfo
                    assertTrue(segmentInfo.info.hasBlocks, "codec: ${segmentInfo.info.codec}")
                }
                w.forceMerge(1, true)
                w.commit()
                DirectoryReader.open(dir).use { reader ->
                    assertEquals(1, reader.leaves().size)
                    val segmentInfo = (reader.leaves()[0].reader() as SegmentReader).segmentInfo
                    assertTrue(segmentInfo.info.hasBlocks, "codec: ${segmentInfo.info.codec}")
                }
                w.commit()
            }
        }
    }

    // LUCENE-3872
    @Test
    @Throws(Exception::class)
    fun testPrepareCommitThenClose() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        w.prepareCommit()
        expectThrows(IllegalStateException::class) { w.close() }
        w.commit()
        w.close()
        val r = DirectoryReader.open(dir)
        assertEquals(0, r.maxDoc())
        r.close()
        dir.close()
    }

    // LUCENE-3872
    @Test
    @Throws(Exception::class)
    fun testPrepareCommitThenRollback() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        w.prepareCommit()
        w.rollback()
        assertFalse(DirectoryReader.indexExists(dir))
        dir.close()
    }

    // LUCENE-3872
    @Test
    @Throws(Exception::class)
    fun testPrepareCommitThenRollback2() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        w.commit()
        w.addDocument(Document())
        w.prepareCommit()
        w.rollback()
        assertTrue(DirectoryReader.indexExists(dir))
        val r = DirectoryReader.open(dir)
        assertEquals(0, r.maxDoc())
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDontInvokeAnalyzerForUnAnalyzedFields() {
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    throw IllegalStateException("don't invoke me!")
                }

                override fun getPositionIncrementGap(fieldName: String?): Int {
                    throw IllegalStateException("don't invoke me!")
                }

                override fun getOffsetGap(fieldName: String?): Int {
                    throw IllegalStateException("don't invoke me!")
                }
            }
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(analyzer))
        val doc = Document()
        val customType = FieldType(StringField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        val f = newField("field", "abcd", customType)
        doc.add(f)
        doc.add(f)
        val f2 = newField("field", "", customType)
        doc.add(f2)
        doc.add(f)
        w.addDocument(doc)
        w.close()
        dir.close()
    }

    @Test
    @Throws(Throwable::class)
    fun testOtherFiles() {
        val dir = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        iw.addDocument(Document())
        iw.close()
        try {
            // Create my own random file:
            val out = dir.createOutput("myrandomfile", newIOContext(random()))
            out.writeByte(42.toByte())
            out.close()

            IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random()))).close()

            assertTrue(slowFileExists(dir, "myrandomfile"))
        } finally {
            dir.close()
        }
    }

    // LUCENE-3849
    @Test
    @Throws(Exception::class)
    fun testStopwordsPosIncHole() {
        val dir = newDirectory()
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer = MockTokenizer()
                    val stream = MockTokenFilter(tokenizer, MockTokenFilter.ENGLISH_STOPSET)
                    return TokenStreamComponents(tokenizer, stream)
                }
            }
        val iw = RandomIndexWriter(random(), dir, a)
        val doc = Document()
        doc.add(TextField("body", "just a", Field.Store.NO))
        doc.add(TextField("body", "test of gaps", Field.Store.NO))
        iw.addDocument(doc)
        val ir = iw.reader
        iw.close()
        val `is` = newSearcher(ir)
        val builder = PhraseQuery.Builder()
        builder.add(Term("body", "just"), 0)
        builder.add(Term("body", "test"), 2)
        val pq = builder.build()
        // body:"just ? test"
        val totalHits = `is`.search(pq, 5).totalHits.value.toInt()
        assertEquals(1, totalHits)
        ir.close()
        dir.close()
    }

    // LUCENE-3849
    @Test
    @Throws(Exception::class)
    fun testStopwordsPosIncHole2() {
        // use two stopfilters for testing here
        val dir = newDirectory()
        val secondSet = Automata.makeString("foobar")
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer = MockTokenizer()
                    var stream: TokenStream = MockTokenFilter(tokenizer, MockTokenFilter.ENGLISH_STOPSET)
                    stream = MockTokenFilter(stream, CharacterRunAutomaton(secondSet))
                    return TokenStreamComponents(tokenizer, stream)
                }
            }
        val iw = RandomIndexWriter(random(), dir, a)
        val doc = Document()
        doc.add(TextField("body", "just a foobar", Field.Store.NO))
        doc.add(TextField("body", "test of gaps", Field.Store.NO))
        iw.addDocument(doc)
        val ir = iw.reader
        iw.close()
        val `is` = newSearcher(ir)
        val builder = PhraseQuery.Builder()
        builder.add(Term("body", "just"), 0)
        builder.add(Term("body", "test"), 3)
        val pq = builder.build()
        // body:"just ? ? test"
        val totalHits = `is`.search(pq, 5).totalHits.value.toInt()
        assertEquals(1, totalHits)
        ir.close()
        dir.close()
    }

    // LUCENE-4575
    @Test
    @Throws(Exception::class)
    fun testCommitWithUserDataOnly() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        writer.commit() // first commit to complete IW create transaction.

        // this should store the commit data, even though no other changes were made
        writer.setLiveCommitData(hashMapOf("key" to "value").entries)
        writer.commit()

        var r = DirectoryReader.open(dir)
        assertEquals("value", r.indexCommit.userData["key"])
        r.close()

        // now check setCommitData and prepareCommit/commit sequence
        writer.setLiveCommitData(hashMapOf("key" to "value1").entries)
        writer.prepareCommit()
        writer.setLiveCommitData(hashMapOf("key" to "value2").entries)
        writer.commit() // should commit the first commitData only, per protocol

        r = DirectoryReader.open(dir)
        assertEquals("value1", r.indexCommit.userData["key"])
        r.close()

        // now should commit the second commitData - there was a bug where
        // IndexWriter.finishCommit overrode the second commitData
        writer.commit()
        r = DirectoryReader.open(dir)
        assertEquals(
            "value2",
            r.indexCommit.userData["key"],
            "IndexWriter.finishCommit may have overridden the second commitData"
        )
        r.close()

        writer.close()
        dir.close()
    }

    private fun getLiveCommitData(writer: IndexWriter): MutableMap<String, String> {
        val data = HashMap<String, String>()
        val iter = writer.getLiveCommitData()
        if (iter != null) {
            for (ent in iter) {
                data[ent.key] = ent.value
            }
        }
        return data
    }

    @Test
    @Throws(Exception::class)
    fun testGetCommitData() {
        val dir = newDirectory()
        var writer = IndexWriter(dir, IndexWriterConfig())
        writer.setLiveCommitData(hashMapOf("key" to "value").entries)
        assertEquals("value", getLiveCommitData(writer)["key"])
        writer.close()

        // validate that it's also visible when opening a new IndexWriter
        writer = IndexWriter(dir, IndexWriterConfig().setOpenMode(OpenMode.APPEND))
        assertEquals("value", getLiveCommitData(writer)["key"])
        writer.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testGetCommitDataFromOldSnapshot() {
        val dir = newDirectory()
        var writer = IndexWriter(dir, newSnapshotIndexWriterConfig(MockAnalyzer(random())))
        writer.setLiveCommitData(hashMapOf("key" to "value").entries)
        assertEquals("value", getLiveCommitData(writer)["key"])
        writer.commit()
        // Snapshot this commit to open later
        val indexCommit = (writer.config.indexDeletionPolicy as SnapshotDeletionPolicy).snapshot()
        writer.close()

        // Modify the commit data and commit on close so the most recent commit data is different
        writer = IndexWriter(dir, newSnapshotIndexWriterConfig(MockAnalyzer(random())))
        writer.setLiveCommitData(hashMapOf("key" to "value2").entries)
        assertEquals("value2", getLiveCommitData(writer)["key"])
        writer.close()

        // validate that when opening writer from older snapshotted index commit,
        // the old commit data is visible
        writer =
            IndexWriter(
                dir,
                newSnapshotIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.APPEND)
                    .setIndexCommit(indexCommit)
            )
        assertEquals("value", getLiveCommitData(writer)["key"])
        writer.close()

        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNullAnalyzer() {
        val dir = newDirectory()
        val iwConf =
            newIndexWriterConfig(
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        throw NullPointerException()
                    }
                }
            )
        val iw = RandomIndexWriter(random(), dir, iwConf)

        // add 3 good docs
        for (i in 0 until 3) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.NO))
            iw.addDocument(doc)
        }

        // add broken doc
        expectThrows(NullPointerException::class) {
            val broke = Document()
            val type = FieldType(TextField.TYPE_STORED)
            type.setStoreTermVectors(true)
            type.setStoreTermVectorPositions(true)
            type.setStoreTermVectorOffsets(true)
            type.freeze()
            broke.add(Field("test", "broken", type))
            iw.addDocument(broke)
        }

        // ensure good docs are still ok
        val ir = iw.reader
        assertEquals(3, ir.numDocs())
        ir.close()
        iw.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNullDocument() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)

        // add 3 good docs
        for (i in 0 until 3) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.NO))
            iw.addDocument(doc)
        }

        // add broken doc
        expectThrows(NullPointerException::class) { iw.addDocument(null as Iterable<IndexableField>) }

        // ensure good docs are still ok
        val ir = iw.reader
        assertEquals(3, ir.numDocs())

        ir.close()
        iw.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNullDocuments() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)

        // add 3 good docs
        for (i in 0 until 3) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.NO))
            iw.addDocument(doc)
        }

        // add broken doc block
        expectThrows(NullPointerException::class) {
            iw.addDocuments(null as Iterable<Iterable<IndexableField>>)
        }

        // ensure good docs are still ok
        val ir = iw.reader
        assertEquals(3, ir.numDocs())

        ir.close()
        iw.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testIterableFieldThrowsException() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val iters = atLeast(100)
        var docCount = 0
        var docId = 0
        val liveIds = HashSet<String>()
        for (i in 0 until iters) {
            val numDocs = atLeast(4)
            for (j in 0 until numDocs) {
                val id = docId++.toString()
                val fields = ArrayList<IndexableField>()
                fields.add(StringField("id", id, Field.Store.YES))
                fields.add(StringField("foo", TestUtil.randomSimpleString(random()), Field.Store.NO))
                docId++

                var success = false
                try {
                    w.addDocument(RandomFailingIterable(fields, random()))
                    success = true
                } catch (e: RuntimeException) {
                    assertEquals("boom", e.message)
                } finally {
                    if (success) {
                        docCount++
                        liveIds.add(id)
                    }
                }
            }
        }
        val reader = DirectoryReader.open(w)
        assertEquals(docCount, reader.numDocs())
        val leaves = reader.leaves()
        for (leafReaderContext in leaves) {
            val ar = leafReaderContext.reader()
            val liveDocs = ar.liveDocs
            val maxDoc = ar.maxDoc()
            val storedFields = ar.storedFields()
            for (i in 0 until maxDoc) {
                if (liveDocs == null || liveDocs.get(i)) {
                    assertTrue(liveIds.remove(storedFields.document(i).get("id")))
                }
            }
        }
        assertTrue(liveIds.isEmpty())
        w.close()
        IOUtils.close(reader, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testIterableThrowsException() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val iters = atLeast(100)
        var docCount = 0
        var docId = 0
        val liveIds = HashSet<String>()
        for (i in 0 until iters) {
            val numDocs = atLeast(4)
            for (j in 0 until numDocs) {
                val id = docId++.toString()
                val fields = ArrayList<IndexableField>()
                fields.add(StringField("id", id, Field.Store.YES))
                fields.add(StringField("foo", TestUtil.randomSimpleString(random()), Field.Store.NO))
                docId++

                var success = false
                try {
                    w.addDocument(RandomFailingIterable(fields, random()))
                    success = true
                } catch (e: RuntimeException) {
                    assertEquals("boom", e.message)
                } finally {
                    if (success) {
                        docCount++
                        liveIds.add(id)
                    }
                }
            }
        }
        val reader = DirectoryReader.open(w)
        assertEquals(docCount, reader.numDocs())
        val leaves = reader.leaves()
        for (leafReaderContext in leaves) {
            val ar = leafReaderContext.reader()
            val liveDocs = ar.liveDocs
            val maxDoc = ar.maxDoc()
            val storedFields = ar.storedFields()
            for (i in 0 until maxDoc) {
                if (liveDocs == null || liveDocs.get(i)) {
                    assertTrue(liveIds.remove(storedFields.document(i).get("id")))
                }
            }
        }
        assertTrue(liveIds.isEmpty())
        w.close()
        IOUtils.close(reader, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testIterableThrowsException2() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val expected =
            expectThrows(Exception::class) {
                w.addDocuments(
                    object : Iterable<Document> {
                        override fun iterator(): MutableIterator<Document> {
                            return object : MutableIterator<Document> {
                                override fun hasNext(): Boolean {
                                    return true
                                }

                                override fun next(): Document {
                                    throw RuntimeException("boom")
                                }

                                override fun remove() {
                                    assert(false)
                                }
                            }
                        }
                    }
                )
            }
        assertEquals("boom", expected.message)

        w.close()
        IOUtils.close(dir)
    }

    private class RandomFailingIterable<T>(
        private val list: Iterable<out T>,
        random: Random
    ) : Iterable<T> {
        private val failOn = random.nextInt(5)

        override fun iterator(): MutableIterator<T> {
            val docIter = list.iterator()
            return object : MutableIterator<T> {
                var count = 0

                override fun hasNext(): Boolean {
                    return docIter.hasNext()
                }

                override fun next(): T {
                    if (count == failOn) {
                        throw RuntimeException("boom")
                    }
                    count++
                    return docIter.next()
                }

                override fun remove() {
                    throw UnsupportedOperationException()
                }
            }
        }
    }

    // LUCENE-2727/LUCENE-2812/LUCENE-4738:
    @Test
    @Throws(Exception::class)
    fun testCorruptFirstCommit() {
        for (i in 0 until 6) {
            val dir = newDirectory()

            // Create a corrupt first commit:
            dir.createOutput(
                IndexFileNames.fileNameFromGeneration(IndexFileNames.PENDING_SEGMENTS, "", 0)!!,
                IOContext.DEFAULT
            ).close()

            val iwc = newIndexWriterConfig(MockAnalyzer(random()))
            val mode = i / 2
            if (mode == 0) {
                iwc.setOpenMode(OpenMode.CREATE)
            } else if (mode == 1) {
                iwc.setOpenMode(OpenMode.APPEND)
            } else if (mode == 2) {
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND)
            }

            if (VERBOSE) {
                println("\nTEST: i=$i")
            }

            try {
                if ((i and 1) == 0) {
                    IndexWriter(dir, iwc).close()
                } else {
                    IndexWriter(dir, iwc).rollback()
                }
            } catch (ioe: IOException) {
                // OpenMode.APPEND should throw an exception since no index exists:
                if (mode == 0) {
                    // Unexpected
                    throw ioe
                }
            }

            if (VERBOSE) {
                println("  at close: " + dir.listAll().contentToString())
            }

            if (mode != 0) {
                dir.checkIndexOnClose = false
            }

            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testHasUncommittedChanges() {
        val dir = newDirectory()
        var writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    // Disable merging to simplify this test, otherwise a commit might trigger
                    // uncommitted merges.
                    .setMergePolicy(NoMergePolicy.INSTANCE)
            )
        assertTrue(
            writer.hasUncommittedChanges()
        ) // this will be true because a commit will create an empty
        // index
        var doc = Document()
        doc.add(newTextField("myfield", "a b c", Field.Store.NO))
        writer.addDocument(doc)
        assertTrue(writer.hasUncommittedChanges())

        // Must commit, waitForMerges, commit again, to be
        // certain that hasUncommittedChanges returns false:
        writer.commit()
        writer.waitForMerges()
        writer.commit()
        assertFalse(writer.hasUncommittedChanges())
        writer.addDocument(doc)
        assertTrue(writer.hasUncommittedChanges())
        writer.commit()
        doc = Document()
        doc.add(newStringField("id", "xyz", Field.Store.YES))
        writer.addDocument(doc)
        assertTrue(writer.hasUncommittedChanges())

        writer.commit()
        assertFalse(writer.hasUncommittedChanges())
        writer.deleteDocuments(Term("id", "xyz"))
        assertTrue(writer.hasUncommittedChanges())

        writer.commit()
        assertFalse(writer.hasUncommittedChanges())
        writer.close()

        writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        assertFalse(writer.hasUncommittedChanges())
        writer.addDocument(doc)
        assertTrue(writer.hasUncommittedChanges())

        writer.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    fun testMergeAllDeleted() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        val keepFullyDeletedSegments = AtomicBoolean(false)
        iwc.setMergePolicy(
            object : FilterMergePolicy(iwc.mergePolicy) {
                override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                    return keepFullyDeletedSegments.load()
                }
            }
        )
        val iwRef = SetOnce<IndexWriter>()
        val evilWriter =
            RandomIndexWriter.mockIndexWriter(
                random(),
                dir,
                iwc,
                object : RandomIndexWriter.TestPoint {
                    override fun apply(message: String) {
                        if ("startCommitMerge" == message) {
                            keepFullyDeletedSegments.store(false)
                        } else if ("startMergeInit" == message) {
                            keepFullyDeletedSegments.store(true)
                        }
                    }
                }
            )
        iwRef.set(evilWriter)
        for (i in 0 until 1000) {
            addDoc(evilWriter)
            if (random().nextInt(17) == 0) {
                evilWriter.commit()
            }
        }
        evilWriter.deleteDocuments(MatchAllDocsQuery())
        evilWriter.forceMerge(1)
        evilWriter.close()
        dir.close()
    }

    // LUCENE-5239
    @Test
    @Throws(Exception::class)
    fun testDeleteSameTermAcrossFields() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(TextField("a", "foo", Field.Store.NO))
        w.addDocument(doc)

        // Should not delete the document;
        // with LUCENE-5239 the "foo" from the 2nd delete term would incorrectly match field a's "foo":
        w.deleteDocuments(Term("a", "xxx"))
        w.deleteDocuments(Term("b", "foo"))
        val r = DirectoryReader.open(w)
        w.close()

        // Make sure document was not (incorrectly) deleted:
        assertEquals(1, r.numDocs())
        r.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testHasUncommittedChangesAfterException() {
        val analyzer = MockAnalyzer(random())

        val directory = newDirectory()
        // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!
        val iwc = newIndexWriterConfig(analyzer)
        iwc.setMergePolicy(newLogMergePolicy())
        val iwriter = IndexWriter(directory, iwc)
        val doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo!")))
        doc.add(SortedDocValuesField("dv", newBytesRef("bar!")))
        expectThrows(IllegalArgumentException::class) { iwriter.addDocument(doc) }

        iwriter.commit()
        assertFalse(iwriter.hasUncommittedChanges())
        iwriter.close()
        directory.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDoubleClose() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo!")))
        w.addDocument(doc)
        w.close()
        // Close again should have no effect
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRollbackThenClose() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo!")))
        w.addDocument(doc)
        w.rollback()
        // Close after rollback should have no effect
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testCloseThenRollback() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo!")))
        w.addDocument(doc)
        w.close()
        // Rollback after close should have no effect
        w.rollback()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testCloseWhileMergeIsRunning() {
        val dir = newDirectory()

        val mergeStarted = CountDownLatch(1)
        val closeStarted = CountDownLatch(1)

        val iwc =
            newIndexWriterConfig(random(), MockAnalyzer(random())).setCommitOnClose(false)
        val mp = LogDocMergePolicy()
        mp.mergeFactor = 2
        iwc.setMergePolicy(mp)
        iwc.setInfoStream(
            object : InfoStream() {
                override fun isEnabled(component: String): Boolean {
                    return true
                }

                override fun message(component: String, message: String) {
                    if (message == "rollback") {
                        closeStarted.countDown()
                    }
                }

                override fun close() {}
            }
        )

        iwc.setMergeScheduler(
            object : ConcurrentMergeScheduler() {
                override fun doMerge(mergeSource: MergeSource, merge: MergePolicy.OneMerge) {
                    mergeStarted.countDown()
                    try {
                        closeStarted.await()
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw RuntimeException(ie)
                    }
                    super.doMerge(mergeSource, merge)
                }

                override fun close() {}
            }
        )
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo!")))
        w.addDocument(doc)
        w.commit()
        w.addDocument(doc)
        w.commit()
        w.close()
        dir.close()
    }

    /** Make sure that close waits for any still-running commits. */
    @Test
    @Throws(Exception::class)
    fun testCloseDuringCommit() {
        val startCommit = CountDownLatch(1)
        val finishCommit = CountDownLatch(1)

        val dir = newDirectory()
        val iwc = IndexWriterConfig()
        // use an InfoStream that "takes a long time" to commit
        val iw =
            RandomIndexWriter.mockIndexWriter(
                random(),
                dir,
                iwc,
                object : RandomIndexWriter.TestPoint {
                    override fun apply(message: String) {
                        if (message == "finishStartCommit") {
                            startCommit.countDown()
                            try {
                                Thread.sleep(10)
                            } catch (ie: InterruptedException) {
                                throw ThreadInterruptedException(ie)
                            }
                        }
                    }
                }
            )
        object : Thread() {
            override fun run() {
                try {
                    iw.commit()
                    finishCommit.countDown()
                } catch (ioe: IOException) {
                    throw RuntimeException(ioe)
                }
            }
        }.start()
        startCommit.await()
        try {
            iw.close()
        } catch (_: IllegalStateException) {
            // OK, but not required (depends on thread scheduling)
        }
        finishCommit.await()
        iw.close()
        dir.close()
    }

    // LUCENE-5895:

    /** Make sure we see ids per segment and per commit. */
    @Test
    @Throws(Exception::class)
    fun testIds() {
        val d = newDirectory()
        val w = IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random())))
        w.addDocument(Document())
        w.close()

        val sis = SegmentInfos.readLatestCommit(d)
        val id1 = sis.getId()
        assertNotNull(id1)
        assertEquals(StringHelper.ID_LENGTH, id1.size)

        val id2 = sis.info(0).info.getId()
        val sciId2 = sis.info(0).getId()
        assertNotNull(id2)
        assertNotNull(sciId2)
        assertEquals(StringHelper.ID_LENGTH, id2.size)
        assertEquals(StringHelper.ID_LENGTH, sciId2.size)

        // Make sure CheckIndex includes id output:
        val bos = ByteArrayOutputStream(1024)
        val checker = CheckIndex(d)
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS)
        checker.setInfoStream(PrintStream(bos, false, StandardCharsets.UTF_8), false)
        val indexStatus = checker.checkIndex(null)
        val s = bos.toString(StandardCharsets.UTF_8)
        checker.close()
        // Make sure CheckIndex didn't fail
        assertTrue(indexStatus.clean, s)

        // Commit id is always stored:
        assertTrue(
            s.contains("id=" + StringHelper.idToString(id1)),
            "missing id=" + StringHelper.idToString(id1) + " in:\n" + s
        )

        assertTrue(
            s.contains("id=" + StringHelper.idToString(id1)),
            "missing id=" + StringHelper.idToString(id1) + " in:\n" + s
        )
        d.close()

        val ids = HashSet<String>()
        for (i in 0 until 100000) {
            val id = StringHelper.idToString(StringHelper.randomId())
            assertFalse(ids.contains(id), "id=$id i=$i")
            ids.add(id)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyNorm() {
        val d = newDirectory()
        val w = IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(TextField("foo", CannedTokenStream()))
        w.addDocument(doc)
        w.commit()
        w.close()
        val r = DirectoryReader.open(d)
        val norms = getOnlyLeafReader(r).getNormValues("foo")!!
        assertEquals(0, norms.nextDoc())
        assertEquals(0, norms.longValue())
        r.close()
        d.close()
    }

    @Test
    @Throws(Exception::class)
    fun testManySeparateThreads() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMaxBufferedDocs(1000)
        val w = IndexWriter(dir, iwc)
        // Index 100 docs, each from a new thread, but always only 1 thread is in IW at once:
        for (i in 0 until 100) {
            val thread =
                object : Thread() {
                    override fun run() {
                        val doc = Document()
                        doc.add(newStringField("foo", "bar", Field.Store.NO))
                        try {
                            w.addDocument(doc)
                        } catch (ioe: IOException) {
                            throw RuntimeException(ioe)
                        }
                    }
                }
            thread.start()
            thread.join()
        }
        w.close()

        val r = DirectoryReader.open(dir)
        assertEquals(1, r.leaves().size)
        r.close()
        dir.close()
    }

    // LUCENE-6505
    @Test
    @Throws(Exception::class)
    fun testNRTSegmentsFile() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        // creates segments_1
        w.commit()

        // newly opened NRT reader should see gen=1 segments file
        val r = DirectoryReader.open(w)
        assertEquals(1L, r.indexCommit.generation)
        assertEquals("segments_1", r.indexCommit.segmentsFileName)

        // newly opened non-NRT reader should see gen=1 segments file
        val r2 = DirectoryReader.open(dir)
        assertEquals(1L, r2.indexCommit.generation)
        assertEquals("segments_1", r2.indexCommit.segmentsFileName)
        r2.close()

        // make a change and another commit
        w.addDocument(Document())
        w.commit()
        val r3 = DirectoryReader.openIfChanged(r)
        r.close()
        assertNotNull(r3)

        // reopened NRT reader should see gen=2 segments file
        assertEquals(2L, r3.indexCommit.generation)
        assertEquals("segments_2", r3.indexCommit.segmentsFileName)
        r3.close()

        // newly opened non-NRT reader should see gen=2 segments file
        val r4 = DirectoryReader.open(dir)
        assertEquals(2L, r4.indexCommit.generation)
        assertEquals("segments_2", r4.indexCommit.segmentsFileName)
        r4.close()

        w.close()
        dir.close()
    }

    // LUCENE-6505
    @Test
    @Throws(Exception::class)
    fun testNRTAfterCommit() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        w.commit()

        w.addDocument(Document())
        val r = DirectoryReader.open(w)
        w.commit()

        // commit even with no other changes counts as a "change" that NRT reader reopen will see:
        val r2 = DirectoryReader.open(dir)
        assertNotNull(r2)
        assertEquals(2L, r2.indexCommit.generation)
        assertEquals("segments_2", r2.indexCommit.segmentsFileName)

        IOUtils.close(r, r2, w, dir)
    }

    // LUCENE-6505
    @Test
    @Throws(Exception::class)
    fun testNRTAfterSetUserDataWithoutCommit() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        w.commit()

        val r = DirectoryReader.open(w)
        val m = hashMapOf("foo" to "bar")
        w.setLiveCommitData(m.entries)

        // setLiveCommitData with no other changes should count as an NRT change:
        val r2 = DirectoryReader.openIfChanged(r)
        assertNotNull(r2)

        IOUtils.close(r2, r, w, dir)
    }

    // LUCENE-6505
    @Test
    @Throws(Exception::class)
    fun testNRTAfterSetUserDataWithCommit() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        w.commit()

        val r = DirectoryReader.open(w)
        val m = hashMapOf("foo" to "bar")
        w.setLiveCommitData(m.entries)
        w.commit()
        // setLiveCommitData and also commit, with no other changes, should count as an NRT change:
        val r2 = DirectoryReader.openIfChanged(r)
        assertNotNull(r2)
        IOUtils.close(r, r2, w, dir)
    }

    // LUCENE-6523
    @Test
    @Throws(Exception::class)
    fun testCommitImmediatelyAfterNRTReopen() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        w.commit()

        w.addDocument(Document())

        val r = DirectoryReader.open(w)
        w.commit()

        assertFalse(r.isCurrent)

        val r2 = DirectoryReader.openIfChanged(r)
        assertNotNull(r2)
        // segments_N should have changed:
        assertFalse(
            r2.indexCommit.segmentsFileName == r.indexCommit.segmentsFileName,
            "expected segments_N to change after reopen"
        )
        IOUtils.close(r, r2, w, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testPendingDeleteDVGeneration() {
        // irony: currently we don't emulate windows well enough to work on windows!
        assumeFalse("windows is not supported", Constants.WINDOWS)

        val path = createTempDir()

        // Use WindowsFS to prevent open files from being deleted:
        val provider = WindowsFS(Files.getFileSystem())
        val root = provider.wrapPath(path)

        // MMapDirectory doesn't work because it closes its file handles after mapping!
        val toClose = ArrayList<AutoCloseable>()
        NIOFSDirectory(root).use { dir ->
            AutoCloseable { IOUtils.close(toClose) }.use { closeable ->
                assertNotNull(closeable)
                var iwc =
                    IndexWriterConfig(MockAnalyzer(random()))
                        .setUseCompoundFile(false)
                        .setMergePolicy(NoMergePolicy.INSTANCE) // avoid merging away the randomFile
                        .setMaxBufferedDocs(2)
                        .setRAMBufferSizeMB(-1.0)
                var w = IndexWriter(dir, iwc)
                var d = Document()
                d.add(StringField("id", "1", Field.Store.YES))
                d.add(NumericDocValuesField("nvd", 1))
                w.addDocument(d)
                d = Document()
                d.add(StringField("id", "2", Field.Store.YES))
                d.add(NumericDocValuesField("nvd", 2))
                w.addDocument(d)
                w.flush()
                d = Document()
                d.add(StringField("id", "1", Field.Store.YES))
                d.add(NumericDocValuesField("nvd", 1))
                w.updateDocument(Term("id", "1"), d)
                w.commit()
                val files = HashSet(dir.listAll().asList())
                val numIters = 10 + random().nextInt(50)
                for (i in 0 until numIters) {
                    if (random().nextBoolean()) {
                        d = Document()
                        d.add(StringField("id", "1", Field.Store.YES))
                        d.add(NumericDocValuesField("nvd", 1))
                        w.updateDocument(Term("id", "1"), d)
                    } else if (random().nextBoolean()) {
                        w.deleteDocuments(Term("id", "2"))
                    } else {
                        w.updateNumericDocValue(Term("id", "1"), "nvd", 2)
                    }
                    w.prepareCommit()
                    val newFiles = ArrayList(dir.listAll().asList())
                    newFiles.removeAll(files)
                    val randomFile = RandomPicks.randomFrom(random(), newFiles)
                    toClose.add(dir.openInput(randomFile, IOContext.DEFAULT))
                    w.rollback()
                    iwc =
                        IndexWriterConfig(MockAnalyzer(random()))
                            .setUseCompoundFile(false)
                            .setMergePolicy(NoMergePolicy.INSTANCE)
                            .setMaxBufferedDocs(2)
                            .setRAMBufferSizeMB(-1.0)
                    w = IndexWriter(dir, iwc)
                    expectThrows(NoSuchFileException::class) { dir.deleteFile(randomFile) }
                }
                w.close()
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testPendingDeletionsRollbackWithReader() {
        // irony: currently we don't emulate Windows well enough to work on Windows!
        assumeFalse("Windows is not supported", Constants.WINDOWS)

        val path = createTempDir()

        // Use WindowsFS to prevent open files from being deleted:
        val provider = WindowsFS(Files.getFileSystem())
        val root = provider.wrapPath(path)
        NIOFSDirectory(root).use { _dir ->
            val dir = object : FilterDirectory(_dir) {}

            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            var w = IndexWriter(dir, iwc)
            val d = Document()
            d.add(StringField("id", "1", Field.Store.YES))
            d.add(NumericDocValuesField("numval", 1))
            w.addDocument(d)
            w.commit()
            w.addDocument(d)
            w.flush()
            val reader = DirectoryReader.open(w)
            w.rollback()

            // try-delete superfluous files (some will fail due to windows-fs)
            val iwc2 = IndexWriterConfig(MockAnalyzer(random()))
            IndexWriter(dir, iwc2).close()

            // test that we can index on top of pending deletions
            val iwc3 = IndexWriterConfig(MockAnalyzer(random()))
            w = IndexWriter(dir, iwc3)
            w.addDocument(d)
            w.commit()

            reader.close()
            w.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testWithPendingDeletions() {
        // irony: currently we don't emulate Windows well enough to work on Windows!
        assumeFalse("Windows is not supported", Constants.WINDOWS)

        val path = createTempDir()

        // Use WindowsFS to prevent open files from being deleted:
        val provider = WindowsFS(Files.getFileSystem())
        val root = provider.wrapPath(path)
        var indexCommit: IndexCommit
        var reader: DirectoryReader
        // MMapDirectory doesn't work because it closes its file handles after mapping!
        NIOFSDirectory(root).use { dir ->
            var iwc =
                IndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE)
            var w = IndexWriter(dir, iwc)
            w.commit()
            reader = DirectoryReader.open(w)
            // we pull this commit to open it again later to check that we fail if a future file delete is
            // pending
            indexCommit = reader.indexCommit
            w.close()
            w =
                IndexWriter(
                    dir,
                    IndexWriterConfig(MockAnalyzer(random()))
                        .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE)
                )
            w.addDocument(Document())
            w.close()
            val `in` = dir.openInput("segments_2", IOContext.DEFAULT)
            dir.deleteFile("segments_2")
            assertTrue(dir.pendingDeletions.size > 0)

            // make sure we get NoSuchFileException if we try to delete and already-pending-delete file:
            expectThrows(NoSuchFileException::class) { dir.deleteFile("segments_2") }

            IndexWriter(
                dir,
                IndexWriterConfig(MockAnalyzer(random())).setIndexCommit(indexCommit)
            ).use { writer ->
                writer.addDocument(Document())
                writer.commit()
                assertEquals(1, writer.getDocStats().maxDoc)
                // now check that we moved to 3
                dir.openInput("segments_3", IOContext.DEFAULT).close()
            }
            reader.close()
            `in`.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testPendingDeletesAlreadyWrittenFiles() {
        val path = createTempDir()
        // irony: currently we don't emulate Windows well enough to work on Windows!
        assumeFalse("Windows is not supported", Constants.WINDOWS)

        // Use WindowsFS to prevent open files from being deleted:
        val provider = WindowsFS(Files.getFileSystem())
        val root = provider.wrapPath(path)
        // MMapDirectory doesn't work because it closes its file handles after mapping!
        NIOFSDirectory(root).use { dir ->
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val w = IndexWriter(dir, iwc)
            w.commit()
            val `in` = dir.openInput("segments_1", IOContext.DEFAULT)
            w.addDocument(Document())
            w.close()

            assertTrue(dir.pendingDeletions.size > 0)

            // make sure we get NoSuchFileException if we try to delete and already-pending-delete file:
            expectThrows(NoSuchFileException::class) { dir.deleteFile("segments_1") }
            IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).close()
            `in`.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testLeftoverTempFiles() {
        val dir = newDirectory()
        var iwc = IndexWriterConfig(MockAnalyzer(random()))
        var w = IndexWriter(dir, iwc)
        w.close()

        val out = dir.createTempOutput("_0", "bkd", IOContext.DEFAULT)
        val tempName = out.name!!
        out.close()
        iwc = IndexWriterConfig(MockAnalyzer(random()))
        w = IndexWriter(dir, iwc)

        // Make sure IW deleted the unref'd file:
        try {
            dir.openInput(tempName, IOContext.DEFAULT)
            fail("did not hit exception")
        } catch (_: FileNotFoundException) {
            // expected
        } catch (_: NoSuchFileException) {
            // expected
        }
        w.close()
        dir.close()
    }

    // requires running tests with biggish heap
    @Ignore
    @Test
    @Throws(IOException::class)
    fun testMassiveField() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)

        val b = StringBuilder()
        while (b.length <= IndexWriter.MAX_STORED_STRING_LENGTH) {
            b.append("x ")
        }

        val doc = Document()
        // doc.add(new TextField("big", b.toString(), Field.Store.YES));
        doc.add(StoredField("big", b.toString()))
        val e =
            expectThrows(IllegalArgumentException::class) {
                w.addDocument(doc)
            }
        assertEquals(
            "stored field \"big\" is too large (${b.length} characters) to store",
            e.message
        )

        // make sure writer is still usable:
        val doc2 = Document()
        doc2.add(StringField("id", "foo", Field.Store.YES))
        w.addDocument(doc2)

        val r = DirectoryReader.open(w)
        assertEquals(1, r.numDocs())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRecordsIndexCreatedVersion() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.commit()
        w.close()
        assertEquals(
            org.gnit.lucenekmp.util.Version.LATEST.major,
            SegmentInfos.readLatestCommit(dir).indexCreatedVersionMajor
        )
        dir.close()
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testFlushLargestWriter() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig())
        val numDocs = indexDocsForMultipleDWPTs(w)
        val largestNonPendingWriter = w.docWriter.flushControl.findLargestNonPendingWriter()!!
        assertFalse(largestNonPendingWriter.isFlushPending())

        val numRamDocs = w.numRamDocs()
        val numDocsInDWPT = largestNonPendingWriter.numDocsInRAM
        assertTrue(w.flushNextBuffer())
        assertTrue(largestNonPendingWriter.hasFlushed())
        assertEquals(numRamDocs - numDocsInDWPT, w.numRamDocs())

        // make sure it's not locked
        largestNonPendingWriter.lock()
        largestNonPendingWriter.unlock()
        if (random().nextBoolean()) {
            w.commit()
        }
        val reader = DirectoryReader.open(w, true, true)
        assertEquals(numDocs, reader.numDocs())
        reader.close()
        w.close()
        dir.close()
    }

    @Throws(InterruptedException::class)
    private fun indexDocsForMultipleDWPTs(w: IndexWriter): Int {
        val threads = arrayOfNulls<Thread>(3)
        val latch = CountDownLatch(threads.size)
        val numDocsPerThread = 10 + random().nextInt(30)
        // ensure we have more than on thread state
        for (i in threads.indices) {
            threads[i] =
                Thread {
                    latch.countDown()
                    try {
                        latch.await()
                        for (j in 0 until numDocsPerThread) {
                            val doc = Document()
                            doc.add(StringField("id", "foo", Field.Store.YES))
                            w.addDocument(doc)
                        }
                    } catch (e: Exception) {
                        throw AssertionError(e)
                    }
                }
            threads[i]!!.start()
        }
        for (t in threads) {
            t!!.join()
        }
        return numDocsPerThread * threads.size
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testNeverCheckOutOnFullFlush() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig())
        indexDocsForMultipleDWPTs(w)
        val largestNonPendingWriter = w.docWriter.flushControl.findLargestNonPendingWriter()!!
        assertFalse(largestNonPendingWriter.isFlushPending())
        assertFalse(largestNonPendingWriter.hasFlushed())
        val threadPoolSize = w.docWriter.perThreadPool.size()
        runBlocking { w.docWriter.flushControl.markForFullFlush() }
        val documentsWriterPerThread = w.docWriter.flushControl.checkoutLargestNonPendingWriter()
        assertNull(documentsWriterPerThread)
        assertEquals(threadPoolSize, w.docWriter.flushControl.numQueuedFlushes())
        w.docWriter.flushControl.abortFullFlushes()
        assertNull(w.docWriter.flushControl.checkoutLargestNonPendingWriter(), "was aborted")
        assertEquals(0, w.docWriter.flushControl.numQueuedFlushes())
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    fun testApplyDeletesWithoutFlushes() {
        newDirectory().use { dir ->
            val indexWriterConfig = IndexWriterConfig()
            val flushDeletes = AtomicBoolean(false)
            indexWriterConfig.setFlushPolicy(
                object : FlushPolicy() {
                    override fun onChange(
                        control: DocumentsWriterFlushControl,
                        perThread: DocumentsWriterPerThread?
                    ) {
                        if (flushDeletes.load()) {
                            control.setApplyAllDeletes()
                        }
                    }
                }
            )
            IndexWriter(dir, indexWriterConfig).use { w ->
                assertEquals(0, w.docWriter.flushControl.deleteBytesUsed)
                w.deleteDocuments(Term("foo", "bar"))
                var bytesUsed = w.docWriter.flushControl.deleteBytesUsed
                assertTrue(bytesUsed > 0, "$bytesUsed > 0")
                w.deleteDocuments(Term("foo", "baz"))
                bytesUsed = w.docWriter.flushControl.deleteBytesUsed
                assertTrue(bytesUsed > 0, "$bytesUsed > 0")
                assertEquals(2, w.getBufferedDeleteTermsSize())
                assertEquals(0, w.getFlushDeletesCount())
                flushDeletes.store(true)
                w.deleteDocuments(Term("foo", "bar"))
                assertEquals(0, w.docWriter.flushControl.deleteBytesUsed)
                assertEquals(1, w.getFlushDeletesCount())
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDeletesAppliedOnFlush() {
        newDirectory().use { dir ->
            IndexWriter(dir, IndexWriterConfig()).use { w ->
                var doc = Document()
                doc.add(newField("id", "1", storedTextType))
                w.addDocument(doc)
                w.updateDocument(Term("id", "1"), doc)
                var deleteBytesUsed = w.docWriter.flushControl.deleteBytesUsed
                assertTrue(deleteBytesUsed > 0, "deletedBytesUsed: $deleteBytesUsed")
                assertEquals(0, w.getFlushDeletesCount())
                assertTrue(w.flushNextBuffer())
                assertEquals(1, w.getFlushDeletesCount())
                assertEquals(0, w.docWriter.flushControl.deleteBytesUsed)
                w.deleteAll()
                w.commit()
                assertEquals(2, w.getFlushDeletesCount())
                if (random().nextBoolean()) {
                    w.deleteDocuments(Term("id", "1"))
                } else {
                    w.updateDocValues(Term("id", "1"), NumericDocValuesField("foo", 1L))
                }
                deleteBytesUsed = w.docWriter.flushControl.deleteBytesUsed
                assertTrue(deleteBytesUsed > 0, "deletedBytesUsed: $deleteBytesUsed")
                doc = Document()
                doc.add(newField("id", "5", storedTextType))
                w.addDocument(doc)
                assertTrue(w.flushNextBuffer())
                assertEquals(0, w.docWriter.flushControl.deleteBytesUsed)
                assertEquals(3, w.getFlushDeletesCount())
            }
            RandomIndexWriter(random(), dir, IndexWriterConfig()).use { w ->
                val numDocs = random().nextInt(1, 100)
                for (i in 0 until numDocs) {
                    val doc = Document()
                    doc.add(newField("id", "$i", storedTextType))
                    w.addDocument(doc)
                }
                for (i in 0 until numDocs) {
                    if (random().nextBoolean()) {
                        val doc = Document()
                        doc.add(newField("id", "$i", storedTextType))
                        w.updateDocument(Term("id", "$i"), doc)
                    }
                }

                val deleteBytesUsed = w.w.docWriter.flushControl.deleteBytesUsed
                if (deleteBytesUsed > 0) {
                    assertTrue(w.w.flushNextBuffer())
                    assertEquals(0, w.w.docWriter.flushControl.deleteBytesUsed)
                }
            }
        }
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testHoldLockOnLargestWriter() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig())
        val numDocs = indexDocsForMultipleDWPTs(w)
        val largestNonPendingWriter = w.docWriter.flushControl.findLargestNonPendingWriter()!!
        assertFalse(largestNonPendingWriter.isFlushPending())
        assertFalse(largestNonPendingWriter.hasFlushed())

        val wait = CountDownLatch(1)
        val locked = CountDownLatch(1)
        val lockThread =
            Thread {
                try {
                    largestNonPendingWriter.lock()
                    locked.countDown()
                    wait.await()
                } catch (e: InterruptedException) {
                    throw AssertionError(e)
                } finally {
                    largestNonPendingWriter.unlock()
                }
            }
        lockThread.start()
        val flushThread =
            Thread {
                try {
                    locked.await()
                    assertTrue(w.flushNextBuffer())
                } catch (e: Exception) {
                    throw AssertionError(e)
                }
            }
        flushThread.start()

        locked.await()
        // access a synced method to ensure we never lock while we hold the flush control monitor
        w.docWriter.flushControl.activeBytes()
        wait.countDown()
        lockThread.join()
        flushThread.join()

        assertTrue(largestNonPendingWriter.hasFlushed(), "largest DWPT should be flushed")
        // make sure it's not locked
        largestNonPendingWriter.lock()
        largestNonPendingWriter.unlock()
        if (random().nextBoolean()) {
            w.commit()
        }
        val reader = DirectoryReader.open(w, true, true)
        assertEquals(numDocs, reader.numDocs())
        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    fun testCheckPendingFlushPostUpdate() {
        val dir = newMockDirectory()
        val flushingThreads = Collections.synchronizedSet(HashSet<String>())
        dir.failOn(
            object : MockDirectoryWrapper.Failure() {
                @Throws(IOException::class)
                override fun eval(dir: MockDirectoryWrapper) {
                    if (callStackContains(DocumentsWriterPerThread::class, "flush")) {
                        flushingThreads.add(Thread.currentThread().getName())
                    }
                }
            }
        )
        val w =
            IndexWriter(
                dir,
                IndexWriterConfig()
                    .setCheckPendingFlushUpdate(false)
                    .setMaxBufferedDocs(Int.MAX_VALUE)
                    .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
            )
        val done = AtomicBoolean(false)
        val numThreads = 2 + random().nextInt(3)
        val latch = CountDownLatch(numThreads)
        val indexingThreads = HashSet<String>()
        val threads = arrayOfNulls<Thread>(numThreads)
        for (i in 0 until numThreads) {
            threads[i] =
                Thread {
                    latch.countDown()
                    var numDocs = 0
                    while (done.load() == false) {
                        val doc = Document()
                        doc.add(StringField("id", "foo", Field.Store.YES))
                        try {
                            w.addDocument(doc)
                        } catch (e: Exception) {
                            throw AssertionError(e)
                        }
                        if (numDocs++ % 10 == 0) {
                            Thread.yield()
                        }
                    }
                }
            indexingThreads.add(threads[i]!!.getName())
            threads[i]!!.start()
        }
        latch.await()
        try {
            var numIters = if (rarely()) 1 + random().nextInt(5) else 1
            for (i in 0 until numIters) {
                waitForDocsInBuffers(w, kotlin.math.min(2, threads.size))
                w.commit()
                assertTrue(flushingThreads.contains(Thread.currentThread().getName()), flushingThreads.toString())
                flushingThreads.retainAll(indexingThreads)
                assertTrue(flushingThreads.isEmpty(), flushingThreads.toString())
            }
            w.config.setCheckPendingFlushUpdate(true)
            numIters = 0
            do {
                assertFalse(numIters++ >= 100, "should finish in less than 100 iterations")
                waitForDocsInBuffers(w, kotlin.math.min(2, threads.size))
                w.flush()
                flushingThreads.retainAll(indexingThreads)
            } while (flushingThreads.isEmpty())
        } finally {
            done.store(true)
            for (i in 0 until numThreads) {
                threads[i]!!.join()
            }
            IOUtils.close(w, dir)
        }
    }

    private fun waitForDocsInBuffers(w: IndexWriter, buffersWithDocs: Int) {
        // wait until at least N DWPTs have a doc in order to observe who flushes the segments.
        while (true) {
            var numStatesWithDocs = 0
            val perThreadPool = w.docWriter.perThreadPool
            for (dwpt in perThreadPool) {
                dwpt.lock()
                try {
                    if (dwpt.numDocsInRAM > 1) {
                        numStatesWithDocs++
                    }
                } finally {
                    dwpt.unlock()
                }
            }
            if (numStatesWithDocs >= buffersWithDocs) {
                return
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSoftUpdateDocuments() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig()
                    .setMergePolicy(NoMergePolicy.INSTANCE)
                    .setSoftDeletesField("soft_delete")
            )
        expectThrows(IllegalArgumentException::class) {
            writer.softUpdateDocument(
                null,
                Document(),
                NumericDocValuesField("soft_delete", 1)
            )
        }

        expectThrows(IllegalArgumentException::class) {
            writer.softUpdateDocument(Term("id", "1"), Document())
        }

        expectThrows(IllegalArgumentException::class) {
            writer.softUpdateDocuments(
                null,
                listOf(Document()),
                NumericDocValuesField("soft_delete", 1)
            )
        }

        expectThrows(IllegalArgumentException::class) {
            writer.softUpdateDocuments(Term("id", "1"), listOf(Document()))
        }

        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "2", Field.Store.YES))
        var field: Field = NumericDocValuesField("soft_delete", 1)
        writer.softUpdateDocument(Term("id", "1"), doc, field)
        var reader = DirectoryReader.open(writer)
        assertEquals(2, reader.docFreq(Term("id", "1")))
        var searcher = IndexSearcher(reader)
        var topDocs = searcher.search(TermQuery(Term("id", "1")), 10)
        assertEquals(1, topDocs.totalHits.value)
        var document = reader.storedFields().document(topDocs.scoreDocs[0].doc)
        assertEquals("2", document.get("version"))

        // update the on-disk version
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "3", Field.Store.YES))
        field = NumericDocValuesField("soft_delete", 1)
        writer.softUpdateDocument(Term("id", "1"), doc, field)
        var oldReader = reader
        reader = DirectoryReader.openIfChanged(reader, writer)!!
        assertNotSame(reader, oldReader)
        oldReader.close()
        searcher = IndexSearcher(reader)
        topDocs = searcher.search(TermQuery(Term("id", "1")), 10)
        assertEquals(1, topDocs.totalHits.value)
        document = reader.storedFields().document(topDocs.scoreDocs[0].doc)
        assertEquals("3", document.get("version"))

        // now delete it
        writer.updateDocValues(Term("id", "1"), field)
        oldReader = reader
        reader = DirectoryReader.openIfChanged(reader, writer)!!
        assertNotSame(reader, oldReader)
        assertNotNull(reader)
        oldReader.close()
        searcher = IndexSearcher(reader)
        topDocs = searcher.search(TermQuery(Term("id", "1")), 10)
        assertEquals(0, topDocs.totalHits.value)
        var numSoftDeleted = 0
        for (info in writer.cloneSegmentInfos()) {
            numSoftDeleted += info.getSoftDelCount()
        }
        val docStats = writer.getDocStats()
        assertEquals(docStats.maxDoc - docStats.numDocs, numSoftDeleted)
        for (context in reader.leaves()) {
            val leaf = context.reader()
            assertNull((leaf as SegmentReader).hardLiveDocs)
        }
        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testSoftUpdatesConcurrently() {
        softUpdatesConcurrently(false)
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testSoftUpdatesConcurrentlyMixedDeletes() {
        softUpdatesConcurrently(true)
    }

    @Throws(IOException::class, InterruptedException::class)
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    fun softUpdatesConcurrently(mixDeletes: Boolean) {
        val dir = newDirectory()
        val indexWriterConfig = newIndexWriterConfig()
        indexWriterConfig.setSoftDeletesField("soft_delete")
        val mergeAwaySoftDeletes = AtomicBoolean(random().nextBoolean())
        if (mixDeletes == false) {
            indexWriterConfig.setMergePolicy(
                object : OneMergeWrappingMergePolicy(
                    indexWriterConfig.mergePolicy,
                    { towrap ->
                        object : MergePolicy.OneMerge(towrap.segments) {
                            @Throws(IOException::class)
                            override fun wrapForMerge(reader: CodecReader): CodecReader {
                                return if (mergeAwaySoftDeletes.load()) {
                                    towrap.wrapForMerge(reader)
                                } else {
                                    val wrapped = towrap.wrapForMerge(reader)
                                    object : FilterCodecReader(wrapped) {
                                        override val coreCacheHelper: IndexReader.CacheHelper?
                                            get() = `in`.coreCacheHelper

                                        override val readerCacheHelper: IndexReader.CacheHelper?
                                            get() = `in`.readerCacheHelper

                                        override val liveDocs: org.gnit.lucenekmp.util.Bits?
                                            get() = null // everything is live

                                        override fun numDocs(): Int {
                                            return maxDoc()
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    @Throws(IOException::class)
                    override fun numDeletesToMerge(
                        info: SegmentCommitInfo,
                        delCount: Int,
                        readerSupplier: IOSupplier<CodecReader>
                    ): Int {
                        return if (mergeAwaySoftDeletes.load()) {
                            super.numDeletesToMerge(info, delCount, readerSupplier)
                        } else {
                            0
                        }
                    }
                }
            )
        }
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
                            if (updateSeveralDocs) {
                                val doc = Document()
                                doc.add(StringField("id", id, Field.Store.YES))
                                if (mixDeletes && random().nextBoolean()) {
                                    if (random().nextBoolean()) {
                                        writer.updateDocuments(Term("id", id), listOf(doc, doc))
                                    } else {
                                        writer.updateDocuments(TermQuery(Term("id", id)), listOf(doc, doc))
                                    }
                                } else {
                                    writer.softUpdateDocuments(
                                        Term("id", id),
                                        listOf(doc, doc),
                                        NumericDocValuesField("soft_delete", 1)
                                    )
                                }
                            } else {
                                val doc = Document()
                                doc.add(StringField("id", id, Field.Store.YES))
                                if (mixDeletes && random().nextBoolean()) {
                                    writer.updateDocument(Term("id", id), doc)
                                } else {
                                    writer.softUpdateDocument(
                                        Term("id", id),
                                        doc,
                                        NumericDocValuesField("soft_delete", 1)
                                    )
                                }
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

        for (thread in threads) {
            thread!!.join()
        }
        var reader = DirectoryReader.open(writer)
        val searcher = IndexSearcher(reader)
        for (id in ids) {
            val topDocs = searcher.search(TermQuery(Term("id", id)), 10)
            if (updateSeveralDocs) {
                assertEquals(2, topDocs.totalHits.value)
                assertEquals(kotlin.math.abs(topDocs.scoreDocs[0].doc - topDocs.scoreDocs[1].doc), 1)
            } else {
                assertEquals(1, topDocs.totalHits.value)
            }
        }
        if (mixDeletes == false) {
            for (context in reader.leaves()) {
                val leaf = context.reader()
                assertNull((leaf as SegmentReader).hardLiveDocs)
            }
        }
        mergeAwaySoftDeletes.store(true)
        writer.addDocument(Document()) // add a dummy doc to trigger a segment here
        writer.flush()
        writer.forceMerge(1)
        val oldReader = reader
        reader = DirectoryReader.openIfChanged(reader, writer) ?: oldReader
        if (reader !== oldReader) {
            oldReader.close()
            assertNotSame(oldReader, reader)
        }
        for (id in ids) {
            if (updateSeveralDocs) {
                assertEquals(2, reader.docFreq(Term("id", id)))
            } else {
                assertEquals(1, reader.docFreq(Term("id", id)))
            }
        }
        var numSoftDeleted = 0
        for (info in writer.cloneSegmentInfos()) {
            numSoftDeleted += info.getSoftDelCount() + info.getDelCount(false)
        }
        val docStats = writer.getDocStats()
        assertEquals(docStats.maxDoc - docStats.numDocs, numSoftDeleted)
        writer.commit()
        DirectoryReader.open(dir).use { dirReader ->
            var delCount = 0
            for (ctx in dirReader.leaves()) {
                val segmentInfo = (ctx.reader() as SegmentReader).segmentInfo
                delCount += segmentInfo.getSoftDelCount() + segmentInfo.getDelCount(false)
            }
            assertEquals(numSoftDeleted, delCount)
        }
        IOUtils.close(reader, writer, dir)
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testDeleteHappensBeforeWhileFlush() {
        val latch = CountDownLatch(1)
        val inFlush = CountDownLatch(1)
        object : FilterDirectory(newDirectory()) {
            @Throws(IOException::class)
            override fun createOutput(name: String, context: IOContext): org.gnit.lucenekmp.store.IndexOutput {
                if (callStackContains(IndexingChain::class, "flush")) {
                    try {
                        inFlush.countDown()
                        latch.await()
                    } catch (e: InterruptedException) {
                        throw AssertionError(e)
                    }
                }
                return super.createOutput(name, context)
            }
        }.use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { writer ->
                val document = Document()
                document.add(StringField("id", "1", Field.Store.YES))
                writer.addDocument(document)
                val t =
                    Thread {
                        try {
                            inFlush.await()
                            writer.docWriter.flushControl.setApplyAllDeletes()
                            if (random().nextBoolean()) {
                                writer.updateDocument(Term("id", "1"), document)
                            } else {
                                writer.deleteDocuments(Term("id", "1"))
                            }
                        } catch (e: Exception) {
                            throw AssertionError(e)
                        } finally {
                            latch.countDown()
                        }
                    }
                t.start()
                DirectoryReader.open(writer).use { reader ->
                    assertEquals(1, reader.numDocs())
                }
                t.join()
            }
        }
    }

    @Throws(IOException::class)
    private fun assertFiles(writer: IndexWriter) {
        val segFiles =
            writer.cloneSegmentInfos().files(true)
                .filter { it.startsWith("segments") == false && it != "write.lock" }
                .toSet()
        val dirFiles =
            writer.getDirectory().listAll()
                .filter { !ExtrasFS.isExtra(it) } // ExtraFS might add files, ignore them
                .filter { it.startsWith("segments") == false && it != "write.lock" }
                .toSet()
        assertEquals(segFiles.size, dirFiles.size)
    }

    @Test
    @Throws(IOException::class)
    fun testFullyDeletedSegmentsReleaseFiles() {
        val dir = newDirectory()
        val config = newIndexWriterConfig()
        config.setRAMBufferSizeMB(Int.MAX_VALUE.toDouble())
        config.setMaxBufferedDocs(2) // no auto flush
        val writer = IndexWriter(dir, config)
        var d = Document()
        d.add(StringField("id", "doc-0", Field.Store.YES))
        writer.addDocument(d)
        writer.flush()
        d = Document()
        d.add(StringField("id", "doc-1", Field.Store.YES))
        writer.addDocument(d)
        writer.deleteDocuments(Term("id", "doc-1"))
        assertEquals(1, writer.cloneSegmentInfos().size())
        writer.flush()
        assertEquals(1, writer.cloneSegmentInfos().size())
        writer.commit()
        assertFiles(writer)
        assertEquals(1, writer.cloneSegmentInfos().size())
        IOUtils.close(writer, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testSegmentInfoIsSnapshot() {
        val dir = newDirectory()
        val config = newIndexWriterConfig()
        config.setRAMBufferSizeMB(Int.MAX_VALUE.toDouble())
        config.setMaxBufferedDocs(2) // no auto flush
        val writer = IndexWriter(dir, config)
        var d = Document()
        d.add(StringField("id", "doc-0", Field.Store.YES))
        writer.addDocument(d)
        d = Document()
        d.add(StringField("id", "doc-1", Field.Store.YES))
        writer.addDocument(d)
        val reader = DirectoryReader.open(writer)
        val segmentInfo = (reader.leaves()[0].reader() as SegmentReader).segmentInfo
        val originalInfo = (reader.leaves()[0].reader() as SegmentReader).originalSegmentInfo
        assertEquals(0, originalInfo.getDelCount(false))
        assertEquals(0, segmentInfo.getDelCount(false))
        writer.deleteDocuments(Term("id", "doc-0"))
        writer.commit()
        assertEquals(0, segmentInfo.getDelCount(false))
        assertEquals(1, originalInfo.getDelCount(false))
        IOUtils.close(reader, writer, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testPreventChangingSoftDeletesField() {
        val dir = newDirectory()
        var writer =
            IndexWriter(dir, newIndexWriterConfig().setSoftDeletesField("my_deletes"))
        val v1 = Document()
        v1.add(StringField("id", "1", Field.Store.YES))
        v1.add(StringField("version", "1", Field.Store.YES))
        writer.addDocument(v1)
        val v2 = Document()
        v2.add(StringField("id", "1", Field.Store.YES))
        v2.add(StringField("version", "2", Field.Store.YES))
        writer.softUpdateDocument(Term("id", "1"), v2, NumericDocValuesField("my_deletes", 1))
        writer.commit()
        writer.close()
        for (si in SegmentInfos.readLatestCommit(dir)) {
            val fieldInfos = IndexWriter.readFieldInfos(si)
            assertEquals("my_deletes", fieldInfos.softDeletesField)
            assertTrue(fieldInfos.fieldInfo("my_deletes")!!.isSoftDeletesField)
        }

        val illegalError =
            expectThrows(IllegalArgumentException::class) {
                IndexWriter(dir, newIndexWriterConfig().setSoftDeletesField("your_deletes"))
            }
        assertEquals(
            "cannot configure [your_deletes] as soft-deletes; " +
                "this index uses [my_deletes] as soft-deletes already",
            illegalError.message
        )

        val softDeleteConfig =
            newIndexWriterConfig()
                .setSoftDeletesField("my_deletes")
                .setMergePolicy(
                    SoftDeletesRetentionMergePolicy(
                        "my_deletes",
                        { MatchAllDocsQuery() },
                        newMergePolicy()
                    )
                )
        writer = IndexWriter(dir, softDeleteConfig)
        val tombstone = Document()
        tombstone.add(StringField("id", "tombstone", Field.Store.YES))
        tombstone.add(NumericDocValuesField("my_deletes", 1))
        writer.addDocument(tombstone)
        writer.flush()
        for (si in writer.cloneSegmentInfos()) {
            val fieldInfos = IndexWriter.readFieldInfos(si)
            assertEquals("my_deletes", fieldInfos.softDeletesField)
            assertTrue(fieldInfos.fieldInfo("my_deletes")!!.isSoftDeletesField)
        }
        writer.close()
        // reopen writer without soft-deletes field should be prevented
        val reopenError =
            expectThrows(IllegalArgumentException::class) {
                IndexWriter(dir, newIndexWriterConfig())
            }
        assertEquals(
            "this index has [my_deletes] as soft-deletes already" +
                " but soft-deletes field is not configured in IWC",
            reopenError.message
        )
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testPreventAddingIndexesWithDifferentSoftDeletesField() {
        val dir1 = newDirectory()
        var w1 =
            IndexWriter(dir1, newIndexWriterConfig().setSoftDeletesField("soft_deletes_1"))
        for (i in 0 until 2) {
            val d = Document()
            d.add(StringField("id", "1", Field.Store.YES))
            d.add(StringField("version", i.toString(), Field.Store.YES))
            w1.softUpdateDocument(Term("id", "1"), d, NumericDocValuesField("soft_deletes_1", 1))
        }
        w1.commit()
        w1.close()

        val dir2 = newDirectory()
        val w2 =
            IndexWriter(dir2, newIndexWriterConfig().setSoftDeletesField("soft_deletes_2"))
        val error =
            expectThrows(IllegalArgumentException::class) {
                w2.addIndexes(dir1)
            }
        assertEquals(
            "cannot configure [soft_deletes_2] as soft-deletes; this index uses [soft_deletes_1] as soft-deletes already",
            error.message
        )
        w2.close()

        val dir3 = newDirectory()
        val config = newIndexWriterConfig().setSoftDeletesField("soft_deletes_1")
        val w3 = IndexWriter(dir3, config)
        w3.addIndexes(dir1)
        for (si in w3.cloneSegmentInfos()) {
            val softDeleteField = IndexWriter.readFieldInfos(si).fieldInfo("soft_deletes_1")!!
            assertTrue(softDeleteField.isSoftDeletesField)
        }
        w3.close()
        IOUtils.close(dir1, dir2, dir3)
    }

    @Test
    @Throws(Exception::class)
    fun testNotAllowUsingExistingFieldAsSoftDeletes() {
        val dir = newDirectory()
        var w = IndexWriter(dir, newIndexWriterConfig())
        for (i in 0 until 2) {
            val d = Document()
            d.add(StringField("id", "1", Field.Store.YES))
            if (random().nextBoolean()) {
                d.add(NumericDocValuesField("dv_field", 1))
                w.updateDocument(Term("id", "1"), d)
            } else {
                w.softUpdateDocument(Term("id", "1"), d, NumericDocValuesField("dv_field", 1))
            }
        }
        w.commit()
        w.close()
        val softDeletesField = if (random().nextBoolean()) "id" else "dv_field"
        val error =
            expectThrows(IllegalArgumentException::class) {
                val config = newIndexWriterConfig().setSoftDeletesField(softDeletesField)
                IndexWriter(dir, config)
            }
        assertEquals(
            "cannot configure [$softDeletesField] as soft-deletes;" +
                " this index uses [$softDeletesField] as non-soft-deletes already",
            error.message
        )
        val config = newIndexWriterConfig().setSoftDeletesField("non-existing-field")
        w = IndexWriter(dir, config)
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBrokenPayload() {
        val d = newDirectory()
        val w = IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val token = Token("bar", 0, 3)
        val evil = newBytesRef(ByteArray(1024))
        evil.offset = 1000 // offset + length is now out of bounds.
        token.payload = evil
        doc.add(TextField("foo", CannedTokenStream(token)))
        expectThrows(IndexOutOfBoundsException::class) { w.addDocument(doc) }
        w.close()
        d.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSoftAndHardLiveDocs() {
        val dir = newDirectory()
        val indexWriterConfig = newIndexWriterConfig()
        val softDeletesField = "soft_delete"
        indexWriterConfig.setSoftDeletesField(softDeletesField)
        val writer = IndexWriter(dir, indexWriterConfig)
        val uniqueDocs = HashSet<Int>()
        for (i in 0 until 100) {
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
            if (random().nextBoolean()) {
                assertHardLiveDocs(writer, uniqueDocs)
            }
        }

        if (random().nextBoolean()) {
            writer.commit()
        }
        assertHardLiveDocs(writer, uniqueDocs)

        IOUtils.close(writer, dir)
    }

    @Test
    @Throws(Exception::class)
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    fun testAbortFullyDeletedSegment() {
        val abortMergeBeforeCommit = AtomicBoolean(false)
        val mergePolicy =
            object : OneMergeWrappingMergePolicy(
                newMergePolicy(),
                { toWrap ->
                    object : MergePolicy.OneMerge(toWrap.segments) {
                        @Throws(IOException::class)
                        override fun onMergeComplete() {
                            super.onMergeComplete()
                            if (abortMergeBeforeCommit.load()) {
                                runBlocking {
                                    setAborted()
                                }
                            }
                        }
                    }
                }
            ) {
                override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                    return true
                }
            }

        val dir = newDirectory()
        val indexWriterConfig =
            newIndexWriterConfig().setMergePolicy(mergePolicy).setCommitOnClose(false)
        val writer = IndexWriter(dir, indexWriterConfig)
        writer.addDocument(listOf(StringField("id", "1", Field.Store.YES)))
        writer.flush()

        writer.deleteDocuments(Term("id", "1"))
        abortMergeBeforeCommit.store(true)
        writer.flush()
        writer.forceMerge(1)
        IOUtils.close(writer, dir)
    }

    @Throws(IOException::class)
    private fun assertHardLiveDocs(writer: IndexWriter, uniqueDocs: Set<Int>) {
        DirectoryReader.open(writer).use { reader ->
            assertEquals(uniqueDocs.size, reader.numDocs())
            val leaves = reader.leaves()
            for (ctx in leaves) {
                val leaf = ctx.reader()
                assertTrue(leaf is SegmentReader)
                val sr = leaf as SegmentReader
                if (sr.hardLiveDocs != null) {
                    val id = sr.terms("id")!!
                    val iterator = id.iterator()
                    val hardLiveDocs = sr.hardLiveDocs!!
                    val liveDocs = sr.liveDocs!!
                    for (dId in uniqueDocs) {
                        val mustBeHardDeleted = dId % 2 == 0
                        if (iterator.seekExact(newBytesRef(dId.toString()))) {
                            val postings = iterator.postings(null)
                            while (postings!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                if (liveDocs.get(postings.docID())) {
                                    assertTrue(hardLiveDocs.get(postings.docID()))
                                } else if (mustBeHardDeleted) {
                                    assertFalse(hardLiveDocs.get(postings.docID()))
                                } else {
                                    assertTrue(hardLiveDocs.get(postings.docID()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSetIndexCreatedVersion() {
        var e =
            expectThrows(IllegalArgumentException::class) {
                IndexWriterConfig().setIndexCreatedVersionMajor(org.gnit.lucenekmp.util.Version.LATEST.major + 1)
            }
        assertEquals(
            "indexCreatedVersionMajor may not be in the future: current major version is " +
                org.gnit.lucenekmp.util.Version.LATEST.major +
                ", but got: " +
                (org.gnit.lucenekmp.util.Version.LATEST.major + 1),
            e.message
        )
        e =
            expectThrows(IllegalArgumentException::class) {
                IndexWriterConfig().setIndexCreatedVersionMajor(org.gnit.lucenekmp.util.Version.LATEST.major - 2)
            }
        assertEquals(
            "indexCreatedVersionMajor may not be less than the minimum supported version: " +
                (org.gnit.lucenekmp.util.Version.LATEST.major - 1) +
                ", but got: " +
                (org.gnit.lucenekmp.util.Version.LATEST.major - 2),
            e.message
        )

        for (previousMajor in org.gnit.lucenekmp.util.Version.LATEST.major - 1..org.gnit.lucenekmp.util.Version.LATEST.major) {
            for (newMajor in org.gnit.lucenekmp.util.Version.LATEST.major - 1..org.gnit.lucenekmp.util.Version.LATEST.major) {
                for (openMode in OpenMode.entries) {
                    newDirectory().use { dir ->
                        IndexWriter(
                            dir,
                            newIndexWriterConfig().setIndexCreatedVersionMajor(previousMajor)
                        ).use { w ->
                            assert(w != null)
                        }
                        var infos = SegmentInfos.readLatestCommit(dir)
                        assertEquals(previousMajor, infos.indexCreatedVersionMajor)
                        IndexWriter(
                            dir,
                            newIndexWriterConfig()
                                .setOpenMode(openMode)
                                .setIndexCreatedVersionMajor(newMajor)
                        ).use { w ->
                            assert(w != null)
                        }
                        infos = SegmentInfos.readLatestCommit(dir)
                        if (openMode == OpenMode.CREATE) {
                            assertEquals(newMajor, infos.indexCreatedVersionMajor)
                        } else {
                            assertEquals(previousMajor, infos.indexCreatedVersionMajor)
                        }
                    }
                }
            }
        }
    }

    // see LUCENE-8639
    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testFlushWhileStartingNewThreads() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig())
        w.addDocument(Document())
        assertEquals(1, w.docWriter.perThreadPool.size())
        val latch = CountDownLatch(1)
        val thread =
            Thread {
                latch.countDown()
                val states = ArrayList<AutoCloseable>()
                try {
                    for (i in 0 until 100) {
                        val state = w.docWriter.perThreadPool.getAndLock()
                        states.add(AutoCloseable { state.unlock() })
                        state.deleteQueue.nextSequenceNumber
                    }
                } finally {
                    IOUtils.closeWhileHandlingException(states)
                }
            }
        thread.start()
        latch.await()
        runBlocking {
            w.docWriter.flushControl.markForFullFlush()
        }
        thread.join()
        w.docWriter.flushControl.abortFullFlushes()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    @OptIn(ExperimentalAtomicApi::class)
    fun testRefreshAndRollbackConcurrently() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val stopped = AtomicBoolean(false)
        val indexedDocs = Semaphore(0)
        val indexer =
            Thread {
                while (stopped.load() == false) {
                    try {
                        val id = random().nextInt(100).toString()
                        val doc = Document()
                        doc.add(StringField("id", id, Field.Store.YES))
                        w.updateDocument(Term("id", id), doc)
                        indexedDocs.release(1)
                    } catch (e: IOException) {
                        throw AssertionError(e)
                    } catch (_: AlreadyClosedException) {
                        return@Thread
                    }
                }
            }

        val sm = SearcherManager(w, SearcherFactory())
        val refresher =
            Thread {
                while (stopped.load() == false) {
                    try {
                        sm.maybeRefreshBlocking()
                    } catch (e: IOException) {
                        throw AssertionError(e)
                    } catch (_: AlreadyClosedException) {
                        return@Thread
                    }
                }
            }

        try {
            indexer.start()
            refresher.start()
            indexedDocs.acquire(1 + random().nextInt(100))
            w.rollback()
        } finally {
            stopped.store(true)
            indexer.join()
            refresher.join()
            val e = w.getTragicException()
            val supplier =
                IOSupplier<String> {
                    if (e != null) {
                        e.stackTraceToString()
                    } else {
                        ""
                    }
                }
            assertNull(
                w.getTragicException(),
                "should not consider ACE a tragedy on a closed IW: ${supplier.get()}"
            )
            IOUtils.close(sm, dir)
        }
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    @OptIn(ExperimentalAtomicApi::class)
    fun testCloseableQueue() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { writer ->
                val queue = IndexWriter.EventQueue(writer)
                val executed = AtomicInt(0)

                queue.add {
                    assertNotNull(it)
                    executed.incrementAndFetch()
                }
                queue.add {
                    assertNotNull(it)
                    executed.incrementAndFetch()
                }
                queue.processEvents()
                assertEquals(2, executed.load())
                queue.processEvents()
                assertEquals(2, executed.load())

                queue.add {
                    assertNotNull(it)
                    executed.incrementAndFetch()
                }
                queue.add {
                    assertNotNull(it)
                    executed.incrementAndFetch()
                }

                val t =
                    Thread {
                        try {
                            queue.processEvents()
                        } catch (e: IOException) {
                            throw AssertionError(e)
                        } catch (_: AlreadyClosedException) {
                            // possible
                        }
                    }
                t.start()
                queue.close()
                t.join()
                assertEquals(4, executed.load())
                expectThrows(AlreadyClosedException::class) { queue.processEvents() }
                expectThrows(AlreadyClosedException::class) { queue.add { } }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRandomOperations() {
        val iwc = newIndexWriterConfig()
        iwc.setMergePolicy(
            object : FilterMergePolicy(newMergePolicy()) {
                private val keepFullyDeletedSegment = random().nextBoolean()

                override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                    return keepFullyDeletedSegment
                }
            }
        )
        newDirectory().use { dir ->
            IndexWriter(dir, iwc).use { writer ->
                SearcherManager(writer, SearcherFactory()).use { sm ->
                    val numOperations = Semaphore(10 + random().nextInt(1000))
                    val singleDoc = random().nextBoolean()
                    val threads = arrayOfNulls<Thread>(1 + random().nextInt(4))
                    val latch = CountDownLatch(threads.size)
                    for (i in threads.indices) {
                        threads[i] =
                            Thread {
                                latch.countDown()
                                try {
                                    latch.await()
                                    while (numOperations.tryAcquire()) {
                                        val id = if (singleDoc) "1" else random().nextInt(10).toString()
                                        val doc = Document()
                                        doc.add(StringField("id", id, Field.Store.YES))
                                        if (random().nextInt(10) <= 2) {
                                            writer.updateDocument(Term("id", id), doc)
                                        } else if (random().nextInt(10) <= 2) {
                                            writer.deleteDocuments(Term("id", id))
                                        } else {
                                            writer.addDocument(doc)
                                        }
                                        if (random().nextInt(100) < 10) {
                                            sm.maybeRefreshBlocking()
                                        }
                                        if (random().nextInt(100) < 5) {
                                            writer.commit()
                                        }
                                        if (random().nextInt(100) < 1) {
                                            writer.forceMerge(1 + random().nextInt(10), random().nextBoolean())
                                        }
                                    }
                                } catch (e: Exception) {
                                    throw AssertionError(e)
                                }
                            }
                        threads[i]!!.start()
                    }
                    for (thread in threads) {
                        thread!!.join()
                    }
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    @OptIn(ExperimentalAtomicApi::class)
    fun testRandomOperationsWithSoftDeletes() {
        val iwc = newIndexWriterConfig()
        val seqNo = AtomicInt(-1)
        val retainingSeqNo = AtomicInt(0)
        iwc.setSoftDeletesField("soft_deletes")
        iwc.setMergePolicy(
            SoftDeletesRetentionMergePolicy(
                "soft_deletes",
                {
                    LongPoint.newRangeQuery(
                        "seq_no",
                        retainingSeqNo.load().toLong(),
                        Long.MAX_VALUE
                    )
                },
                newMergePolicy()
            )
        )
        newDirectory().use { dir ->
            IndexWriter(dir, iwc).use { writer ->
                SearcherManager(writer, SearcherFactory()).use { sm ->
                    val numOperations = Semaphore(10 + random().nextInt(1000))
                    val singleDoc = random().nextBoolean()
                    val threads = arrayOfNulls<Thread>(1 + random().nextInt(4))
                    val latch = CountDownLatch(threads.size)
                    for (i in threads.indices) {
                        threads[i] =
                            Thread {
                                latch.countDown()
                                try {
                                    latch.await()
                                    while (numOperations.tryAcquire()) {
                                        val id = if (singleDoc) "1" else random().nextInt(10).toString()
                                        val doc = Document()
                                        doc.add(StringField("id", id, Field.Store.YES))
                                        doc.add(LongPoint("seq_no", seqNo.fetchAndIncrement().toLong()))
                                        if (random().nextInt(10) <= 2) {
                                            if (random().nextBoolean()) {
                                                doc.add(NumericDocValuesField(iwc.softDeletesField!!, 1))
                                            }
                                            writer.softUpdateDocument(
                                                Term("id", id),
                                                doc,
                                                NumericDocValuesField(iwc.softDeletesField!!, 1)
                                            )
                                        } else {
                                            writer.addDocument(doc)
                                        }
                                        if (random().nextInt(100) < 10) {
                                            val min = retainingSeqNo.load()
                                            val max = seqNo.load()
                                            if (min < max && random().nextBoolean()) {
                                                retainingSeqNo.compareAndSet(
                                                    min,
                                                    min - random().nextInt(max - min)
                                                )
                                            }
                                        }
                                        if (random().nextInt(100) < 10) {
                                            sm.maybeRefreshBlocking()
                                        }
                                        if (random().nextInt(100) < 5) {
                                            writer.commit()
                                        }
                                        if (random().nextInt(100) < 1) {
                                            writer.forceMerge(1 + random().nextInt(10), random().nextBoolean())
                                        }
                                    }
                                } catch (e: Exception) {
                                    throw AssertionError(e)
                                }
                            }
                        threads[i]!!.start()
                    }
                    for (thread in threads) {
                        thread!!.join()
                    }
                }
            }
        }
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    @OptIn(ExperimentalAtomicApi::class)
    fun testMaxCompletedSequenceNumber() {
        newDirectory().use { dir ->
            IndexWriter(dir, IndexWriterConfig()).use { writer ->
                assertEquals(1L, writer.addDocument(Document()))
                assertEquals(2L, writer.updateDocument(Term("foo", "bar"), Document()))
                writer.flushNextBuffer()
                assertEquals(3L, writer.commit())
                assertEquals(4L, writer.addDocument(Document()))
                assertEquals(4L, writer.getMaxCompletedSequenceNumber())
                // commit moves seqNo by 2 since there is one DWPT that could still be in-flight
                assertEquals(6L, writer.commit())
                assertEquals(6L, writer.getMaxCompletedSequenceNumber())
                assertEquals(7L, writer.addDocument(Document()))
                DirectoryReader.open(writer).close()
                // getReader moves seqNo by 2 since there is one DWPT that could still be in-flight
                assertEquals(9L, writer.getMaxCompletedSequenceNumber())
            }
        }
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { writer ->
                SearcherManager(writer, SearcherFactory()).use { manager ->
                    val start = CountDownLatch(1)
                    val numDocs =
                        if (TEST_NIGHTLY) TestUtil.nextInt(random(), 100, 600) else TestUtil.nextInt(random(), 10, 60)
                    val maxCompletedSeqID = AtomicLong(-1)
                    val threads = arrayOfNulls<Thread>(2 + random().nextInt(2))
                    for (i in threads.indices) {
                        val idx = i
                        threads[i] =
                            Thread {
                                try {
                                    start.await()
                                    for (j in 0 until numDocs) {
                                        val doc = Document()
                                        val id = "$idx-$j"
                                        doc.add(StringField("id", id, Field.Store.NO))
                                        val seqNo = writer.addDocument(doc)
                                        if (maxCompletedSeqID.load() < seqNo) {
                                            val maxCompletedSequenceNumber = writer.getMaxCompletedSequenceNumber()
                                            manager.maybeRefreshBlocking()
                                            maxCompletedSeqID.updateAndGet { oldVal ->
                                                kotlin.math.max(oldVal, maxCompletedSequenceNumber)
                                            }
                                        }
                                        val acquire = manager.acquire()
                                        try {
                                            assertEquals(
                                                1L,
                                                acquire.search(TermQuery(Term("id", id)), 10).totalHits.value
                                            )
                                        } finally {
                                            manager.release(acquire)
                                        }
                                    }
                                } catch (e: Exception) {
                                    throw AssertionError(e)
                                }
                            }
                        threads[i]!!.start()
                    }
                    start.countDown()
                    for (thread in threads) {
                        thread!!.join()
                    }
                }
            }
        }
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    @OptIn(ExperimentalAtomicApi::class)
    fun testEnsureMaxSeqNoIsAccurateDuringFlush() {
        val waitRef = AtomicReference(CountDownLatch(0))
        val arrivedRef = AtomicReference(CountDownLatch(0))
        val stream =
            object : InfoStream() {
                override fun message(component: String, message: String) {
                    if ("TP" == component && "DocumentsWriterPerThread addDocuments start" == message) {
                        try {
                            arrivedRef.load().countDown()
                            waitRef.load().await()
                        } catch (e: InterruptedException) {
                            throw AssertionError(e)
                        }
                    }
                }

                override fun isEnabled(component: String): Boolean {
                    return "TP" == component
                }

                override fun close() {}
            }
        val indexWriterConfig = newIndexWriterConfig()
        indexWriterConfig.setInfoStream(stream)
        newDirectory().use { dir ->
            val writer =
                object : IndexWriter(dir, indexWriterConfig) {
                    override fun isEnableTestPoints(): Boolean {
                        return true
                    }
                }
            writer.use {
                // we produce once DWPT with 1 doc
                writer.addDocument(Document())
                assertEquals(1, writer.docWriter.perThreadPool.size())
                val maxCompletedSequenceNumber = writer.getMaxCompletedSequenceNumber()
                // safe the seqNo and use the latches to block this DWPT such that a refresh must wait for it
                waitRef.store(CountDownLatch(1))
                arrivedRef.store(CountDownLatch(1))
                val waiterThread =
                    Thread {
                        try {
                            writer.addDocument(Document())
                        } catch (e: IOException) {
                            throw AssertionError(e)
                        }
                    }
                waiterThread.start()
                arrivedRef.load().await()
                val refreshThread =
                    Thread {
                        try {
                            DirectoryReader.open(writer).close()
                        } catch (e: IOException) {
                            throw AssertionError(e)
                        }
                    }
                val deleteQueue = writer.docWriter.deleteQueue
                refreshThread.start()
                // now we wait until the refresh has swapped the deleted queue and assert that
                // we see an accurate seqId
                while (writer.docWriter.deleteQueue === deleteQueue) {
                    Thread.yield() // busy wait for refresh to swap the queue
                }
                try {
                    assertEquals(maxCompletedSequenceNumber, writer.getMaxCompletedSequenceNumber())
                } finally {
                    waitRef.load().countDown()
                    waiterThread.join()
                    refreshThread.join()
                }
                assertEquals(maxCompletedSequenceNumber + 2, writer.getMaxCompletedSequenceNumber())
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSegmentCommitInfoId() {
        newDirectory().use { dir ->
            var segmentCommitInfos: SegmentInfos
            IndexWriter(
                dir,
                IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
            ).use { writer ->
                var doc = Document()
                doc.add(NumericDocValuesField("num", 1))
                doc.add(StringField("id", "1", Field.Store.NO))
                writer.addDocument(doc)
                doc = Document()
                doc.add(NumericDocValuesField("num", 1))
                doc.add(StringField("id", "2", Field.Store.NO))
                writer.addDocument(doc)
                writer.commit()
                segmentCommitInfos = SegmentInfos.readLatestCommit(dir)
                var id = segmentCommitInfos.info(0).getId()
                val segInfoId = segmentCommitInfos.info(0).info.getId()

                writer.updateNumericDocValue(Term("id", "1"), "num", 2)
                writer.commit()
                segmentCommitInfos = SegmentInfos.readLatestCommit(dir)
                assertEquals(1, segmentCommitInfos.size())
                assertNotEquals(
                    StringHelper.idToString(id),
                    StringHelper.idToString(segmentCommitInfos.info(0).getId())
                )
                assertEquals(
                    StringHelper.idToString(segInfoId),
                    StringHelper.idToString(segmentCommitInfos.info(0).info.getId())
                )
                id = segmentCommitInfos.info(0).getId()
                writer.addDocument(Document()) // second segment
                writer.commit()
                segmentCommitInfos = SegmentInfos.readLatestCommit(dir)
                assertEquals(2, segmentCommitInfos.size())
                assertEquals(
                    StringHelper.idToString(id),
                    StringHelper.idToString(segmentCommitInfos.info(0).getId())
                )
                assertEquals(
                    StringHelper.idToString(segInfoId),
                    StringHelper.idToString(segmentCommitInfos.info(0).info.getId())
                )

                doc = Document()
                doc.add(NumericDocValuesField("num", 5))
                doc.add(StringField("id", "1", Field.Store.NO))
                writer.updateDocument(Term("id", "1"), doc)
                writer.commit()
                segmentCommitInfos = SegmentInfos.readLatestCommit(dir)
                assertEquals(3, segmentCommitInfos.size())
                assertNotEquals(
                    StringHelper.idToString(id),
                    StringHelper.idToString(segmentCommitInfos.info(0).getId())
                )
                assertEquals(
                    StringHelper.idToString(segInfoId),
                    StringHelper.idToString(segmentCommitInfos.info(0).info.getId())
                )
            }

            newDirectory().use { dir2 ->
                IndexWriter(
                    dir2,
                    IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
                ).use { writer2 ->
                    writer2.addIndexes(dir)
                    writer2.commit()
                    val infos2 = SegmentInfos.readLatestCommit(dir2)
                    assertEquals(infos2.size(), segmentCommitInfos.size())
                    for (i in 0 until infos2.size()) {
                        assertEquals(
                            StringHelper.idToString(infos2.info(i).getId()),
                            StringHelper.idToString(segmentCommitInfos.info(i).getId())
                        )
                        assertEquals(
                            StringHelper.idToString(infos2.info(i).info.getId()),
                            StringHelper.idToString(segmentCommitInfos.info(i).info.getId())
                        )
                    }
                }
            }
        }

        val ids = HashSet<String>()
        for (i in 0 until 2) {
            newDirectory().use { dir ->
                IndexWriter(
                    dir,
                    IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
                ).use { writer ->
                    val doc = Document()
                    doc.add(NumericDocValuesField("num", 1))
                    doc.add(StringField("id", "1", Field.Store.NO))
                    writer.addDocument(doc)
                    writer.commit()
                    var segmentCommitInfos = SegmentInfos.readLatestCommit(dir)
                    var id = StringHelper.idToString(segmentCommitInfos.info(0).getId())
                    assertTrue(ids.add(id))
                    writer.updateNumericDocValue(Term("id", "1"), "num", 2)
                    writer.commit()
                    segmentCommitInfos = SegmentInfos.readLatestCommit(dir)
                    id = StringHelper.idToString(segmentCommitInfos.info(0).getId())
                    assertTrue(ids.add(id))
                }
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testMergeZeroDocsMergeIsClosedOnce() {
        val keepAllSegments =
            object : FilterMergePolicy(LogDocMergePolicy()) {
                override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                    return true
                }
            }
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                IndexWriterConfig()
                    .setMergePolicy(
                        OneMergeWrappingMergePolicy(
                            keepAllSegments
                        ) { merge ->
                            val onlyFinishOnce = SetOnce<Boolean>()
                            object : MergePolicy.OneMerge(merge.segments) {
                                @Throws(IOException::class)
                                override fun mergeFinished(success: Boolean, segmentDropped: Boolean) {
                                    super.mergeFinished(success, segmentDropped)
                                    onlyFinishOnce.set(true)
                                }
                            }
                        }
                    )
            ).use { writer ->
                val doc = Document()
                doc.add(StringField("id", "1", Field.Store.NO))
                writer.addDocument(doc)
                writer.flush()
                writer.addDocument(doc)
                writer.flush()
                writer.deleteDocuments(Term("id", "1"))
                writer.flush()
                assertEquals(2, writer.getSegmentCount())
                assertEquals(0, writer.getDocStats().numDocs)
                assertEquals(2, writer.getDocStats().maxDoc)
                writer.forceMerge(1)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMergeOnCommitKeepFullyDeletedSegments() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setMaxFullFlushMergeWaitMillis(30 * 1000)
        iwc.mergePolicy =
            object : FilterMergePolicy(newMergePolicy()) {
                override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                    return true
                }

                override fun findFullFlushMerges(
                    mergeTrigger: MergeTrigger,
                    segmentInfos: SegmentInfos,
                    mergeContext: MergeContext
                ): MergeSpecification? {
                    val fullyDeletedSegments =
                        segmentInfos.asList().filter { s -> s.info.maxDoc() - s.getDelCount(false) == 0 }
                    if (fullyDeletedSegments.isEmpty()) {
                        return null
                    }
                    val spec = MergeSpecification()
                    spec.add(OneMerge(fullyDeletedSegments.toMutableList()))
                    return spec
                }
            }
        val w = IndexWriter(dir, iwc)
        val d = Document()
        d.add(StringField("id", "1", Field.Store.YES))
        w.addDocument(d)
        w.commit()
        w.updateDocument(Term("id", "1"), d)
        w.commit()
        DirectoryReader.open(w).use { reader ->
            assertEquals(1, reader.numDocs())
        }
        IOUtils.close(w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testPendingNumDocs() {
        newDirectory().use { dir ->
            val numDocs = random().nextInt(100)
            IndexWriter(dir, newIndexWriterConfig()).use { writer ->
                for (i in 0 until numDocs) {
                    val d = Document()
                    d.add(StringField("id", i.toString(), Field.Store.YES))
                    writer.addDocument(d)
                    assertEquals((i + 1).toLong(), writer.getPendingNumDocs())
                }
                assertEquals(numDocs.toLong(), writer.getPendingNumDocs())
                writer.flush()
                assertEquals(numDocs.toLong(), writer.getPendingNumDocs())
            }
            IndexWriter(dir, newIndexWriterConfig()).use { writer ->
                assertEquals(numDocs.toLong(), writer.getPendingNumDocs())
            }
        }
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    @OptIn(ExperimentalAtomicApi::class)
    fun testIndexWriterBlocksOnStall() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { writer ->
                val stallControl = writer.getDocsWriter().flushControl.stallControl
                stallControl.updateStalled(true)
                val threads = arrayOfNulls<Thread>(random().nextInt(3) + 1)
                val numThreadsCompleted = AtomicLong(0)
                for (i in threads.indices) {
                    threads[i] =
                        Thread {
                            val d = Document()
                            d.add(StringField("id", 0.toString(), Field.Store.YES))
                            try {
                                writer.addDocument(d)
                            } catch (e: IOException) {
                                throw AssertionError(e)
                            }
                            numThreadsCompleted.incrementAndFetch()
                        }
                    threads[i]!!.start()
                }
                try {
                    for (i in 0 until 10) {
                        stallControl.updateStalled(true)
                        while (stallControl.numWaiting != threads.size) {
                            // wait for all threads to be stalled again
                            assertEquals(0L, writer.getPendingNumDocs())
                            assertEquals(0L, numThreadsCompleted.load())
                        }
                    }
                } finally {
                    stallControl.updateStalled(false)
                    for (t in threads) {
                        t!!.join()
                    }
                }
                writer.commit()
                assertEquals(threads.size, writer.getDocStats().maxDoc)
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testGetFieldNames() {
        val dir = newDirectory()

        var writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        assertEquals(setOf(), writer.getFieldNames())

        addDocWithField(writer, "f1")
        assertEquals(setOf("f1"), writer.getFieldNames())

        // should be unmodifiable:
        val fieldSet = writer.getFieldNames()
        expectThrows(UnsupportedOperationException::class) { fieldSet.add("cannot modify") }
        expectThrows(UnsupportedOperationException::class) { fieldSet.remove("f1") }

        addDocWithField(writer, "f2")
        assertEquals(setOf("f1", "f2"), writer.getFieldNames())

        // set from a previous call is an independent immutable copy, cannot be modified.
        assertEquals(setOf("f1"), fieldSet)

        // flush should not have an effect on field names
        writer.flush()
        assertEquals(setOf("f1", "f2"), writer.getFieldNames())

        // commit should not have an effect on field names
        writer.commit()
        assertEquals(setOf("f1", "f2"), writer.getFieldNames())

        writer.close()

        // new writer should identify committed fields
        writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        assertEquals(setOf("f1", "f2"), writer.getFieldNames())

        writer.deleteAll()
        assertEquals(setOf(), writer.getFieldNames())

        writer.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun addDocWithField(writer: IndexWriter, field: String) {
        val doc = Document()
        doc.add(newField(field, "value", storedTextType))
        writer.addDocument(doc)
    }

    internal class StringSplitAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            return TokenStreamComponents(StringSplitTokenizer())
        }
    }

    private class StringSplitTokenizer : Tokenizer() {
        private var tokens: Array<String> = emptyArray()
        private var upto = 0
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

        override fun incrementToken(): Boolean {
            clearAttributes()
            return if (upto < tokens.size) {
                termAtt.setEmpty()
                termAtt.append(tokens[upto])
                upto++
                true
            } else {
                false
            }
        }

        override fun reset() {
            super.reset()
            upto = 0
            val builder = StringBuilder()
            val buffer = CharArray(1024)
            while (true) {
                val n = input.read(CharBuffer.wrap(buffer))
                if (n == -1) {
                    break
                }
                for (i in 0 until n) {
                    builder.append(buffer[i])
                }
            }
            tokens = builder.toString().split(" ").toTypedArray()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testParentAndSoftDeletesAreTheSame() {
        newDirectory().use { dir ->
            val indexWriterConfig = newIndexWriterConfig(MockAnalyzer(random()))
            indexWriterConfig.setSoftDeletesField("foo")
            indexWriterConfig.setParentField("foo")
            val iae =
                expectThrows(IllegalArgumentException::class) {
                    IndexWriter(dir, indexWriterConfig)
                }
            assertEquals(
                "parent document and soft-deletes field can't be the same field \"foo\"",
                iae.message
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testParentFieldExistingIndex() {
        newDirectory().use { dir ->
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            IndexWriter(dir, iwc).use { writer ->
                val d = Document()
                d.add(TextField("f", "a", Field.Store.NO))
                writer.addDocument(d)
            }
            var iae =
                expectThrows(IllegalArgumentException::class) {
                    IndexWriter(
                        dir,
                        IndexWriterConfig(MockAnalyzer(random()))
                            .setOpenMode(OpenMode.APPEND)
                            .setParentField("foo")
                    )
                }
            assertEquals(
                "can't add a parent field to an already existing index without a parent field",
                iae.message
            )
            iae =
                expectThrows(IllegalArgumentException::class) {
                    IndexWriter(
                        dir,
                        IndexWriterConfig(MockAnalyzer(random()))
                            .setOpenMode(OpenMode.CREATE_OR_APPEND)
                            .setParentField("foo")
                    )
                }
            assertEquals(
                "can't add a parent field to an already existing index without a parent field",
                iae.message
            )

            IndexWriter(
                dir,
                IndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setParentField("foo")
            ).use { writer ->
                writer.addDocument(Document())
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testIndexWithParentFieldIsCongruent() {
        newDirectory().use { dir ->
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            iwc.setParentField("parent")
            IndexWriter(dir, iwc).use { writer ->
                if (random().nextBoolean()) {
                    val child1 = Document()
                    child1.add(StringField("id", 1.toString(), Field.Store.YES))
                    val child2 = Document()
                    child2.add(StringField("id", 1.toString(), Field.Store.YES))
                    val parent = Document()
                    parent.add(StringField("id", 1.toString(), Field.Store.YES))
                    writer.addDocuments(listOf(child1, child2, parent))
                    writer.flush()
                    if (random().nextBoolean()) {
                        writer.addDocuments(listOf(child1, child2, parent))
                    }
                } else {
                    writer.addDocument(Document())
                }
                writer.commit()
            }
            var ex =
                expectThrows(IllegalArgumentException::class) {
                    val config = IndexWriterConfig(MockAnalyzer(random()))
                    config.setParentField("someOtherField")
                    IndexWriter(dir, config)
                }
            assertEquals(
                "can't add field [parent] as parent document field; this IndexWriter is configured with [someOtherField] as parent document field",
                ex.message
            )
            ex =
                expectThrows(IllegalArgumentException::class) {
                    val config = IndexWriterConfig(MockAnalyzer(random()))
                    IndexWriter(dir, config)
                }
            assertEquals(
                "can't add field [parent] as parent document field; this IndexWriter has no parent document field configured",
                ex.message
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testParentFieldIsAlreadyUsed() {
        newDirectory().use { dir ->
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            IndexWriter(dir, iwc).use { writer ->
                val doc = Document()
                doc.add(StringField("parent", 1.toString(), Field.Store.YES))
                writer.addDocument(doc)
                writer.commit()
            }
            val iae =
                expectThrows(IllegalArgumentException::class) {
                    val config = IndexWriterConfig(MockAnalyzer(random()))
                    config.setParentField("parent")

                    IndexWriter(dir, config)
                }
            assertEquals(
                "can't add [parent] as non parent document field; this IndexWriter is configured with [parent] as parent document field",
                iae.message
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testParentFieldEmptyIndex() {
        newMockDirectory().use { dir ->
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            iwc.setParentField("parent")
            IndexWriter(dir, iwc).use { writer ->
                writer.commit()
            }
            val iwc2 = IndexWriterConfig(MockAnalyzer(random()))
            iwc2.setParentField("parent")
            IndexWriter(dir, iwc2).use { writer ->
                writer.commit()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDocValuesMixedSkippingIndex() {
        newDirectory().use { dir ->
            IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { writer ->
                val doc1 = Document()
                doc1.add(SortedNumericDocValuesField.indexedField("test", random().nextLong()))
                writer.addDocument(doc1)

                val doc2 = Document()
                doc2.add(SortedNumericDocValuesField("test", random().nextLong()))
                val ex =
                    expectThrows(IllegalArgumentException::class) {
                        writer.addDocument(doc2)
                    }
                ex.printStackTrace()
                assertEquals(
                    "Inconsistency of field data structures across documents for field [test] of doc [1]. doc values skip index type: expected 'RANGE', but it has 'NONE'.",
                    ex.message
                )
            }
        }
        newDirectory().use { dir ->
            IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { writer ->
                val doc1 = Document()
                doc1.add(SortedSetDocValuesField("test", TestUtil.randomBinaryTerm(random())))
                writer.addDocument(doc1)

                val doc2 = Document()
                doc2.add(SortedSetDocValuesField.indexedField("test", TestUtil.randomBinaryTerm(random())))
                val ex =
                    expectThrows(IllegalArgumentException::class) {
                        writer.addDocument(doc2)
                    }
                assertEquals(
                    "Inconsistency of field data structures across documents for field [test] of doc [1]. doc values skip index type: expected 'NONE', but it has 'RANGE'.",
                    ex.message
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDocValuesSkippingIndexWithoutDocValues() {
        for (docValuesType in arrayOf(DocValuesType.NONE, DocValuesType.BINARY)) {
            val fieldType = FieldType()
            fieldType.setStored(true)
            fieldType.setDocValuesType(docValuesType)
            fieldType.setDocValuesSkipIndexType(DocValuesSkipIndexType.RANGE)
            fieldType.freeze()
            newMockDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { writer ->
                    val doc1 = Document()
                    doc1.add(Field("test", ByteArray(10), fieldType))
                    val ex =
                        expectThrows(IllegalArgumentException::class) {
                            writer.addDocument(doc1)
                        }
                    assertTrue(ex.message!!.startsWith("field 'test' cannot have docValuesSkipIndexType=RANGE"))
                }
            }
        }
    }

    companion object {
        private class MockIndexWriter(dir: Directory, conf: IndexWriterConfig) : IndexWriter(dir, conf) {
            var afterWasCalled = false
            var beforeWasCalled = false

            override fun doAfterFlush() {
                afterWasCalled = true
            }

            override fun doBeforeFlush() {
                beforeWasCalled = true
            }
        }

        private val storedTextType = FieldType(TextField.TYPE_NOT_STORED)

        @Throws(IOException::class)
        fun addDoc(writer: IndexWriter) {
            val doc = Document()
            doc.add(newTextField("content", "aaa", Field.Store.NO))
            writer.addDocument(doc)
        }

        @Throws(IOException::class)
        fun addDocWithIndex(writer: IndexWriter, index: Int) {
            val doc = Document()
            doc.add(newField("content", "aaa $index", storedTextType))
            doc.add(newField("id", "$index", storedTextType))
            writer.addDocument(doc)
        }

        // TODO: we have the logic in MDW to do this check, and it's better, because it knows about files
        //   it tried to delete but couldn't: we should replace this!!!!
        @Throws(IOException::class)
        fun assertNoUnreferencedFiles(dir: Directory, message: String) {
            val startFiles = dir.listAll()
            IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).rollback()
            val endFiles = dir.listAll()

            Arrays.sort(startFiles)
            Arrays.sort(endFiles)

            if (!startFiles.contentEquals(endFiles)) {
                fail(
                    "$message: before delete:\n    " +
                        startFiles.joinToString("\n    ") +
                        "\n  after delete:\n    " +
                        endFiles.joinToString("\n    ")
                )
            }
        }

        /** Returns how many unique segment names are in the directory. */
        @Throws(IOException::class)
        private fun getSegmentCount(dir: Directory): Int {
            val segments = HashSet<String>()
            for (file in dir.listAll()) {
                segments.add(IndexFileNames.parseSegmentName(file))
            }

            return segments.size
        }

    }
}
