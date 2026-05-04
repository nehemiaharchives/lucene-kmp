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
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.BrokenBarrierException
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.CyclicBarrier
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.FieldExistsQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestMixedDocValuesUpdates : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testManyReopensAndFields() {
        val dir = newDirectory()
        val random = random()
        val conf = newIndexWriterConfig(MockAnalyzer(random))
        val lmp = newLogMergePolicy()
        lmp.mergeFactor = 3 // merge often
        conf.setMergePolicy(lmp)
        val writer = IndexWriter(dir, conf)

        val isNRT = random.nextBoolean()
        var reader: DirectoryReader =
            if (isNRT) {
                DirectoryReader.open(writer)
            } else {
                writer.commit()
                DirectoryReader.open(dir)
            }

        val numFields = random.nextInt(4) + 3 // 3-7
        val numNDVFields = random.nextInt(numFields / 2) + 1 // 1-3
        val fieldValues = LongArray(numFields)
        for (i in fieldValues.indices) {
            fieldValues[i] = 1
        }

        val numRounds = atLeast(15)
        var docID = 0
        for (i in 0 until numRounds) {
            val numDocs = atLeast(5)
            for (j in 0 until numDocs) {
                val doc = Document()
                doc.add(StringField("id", "doc-$docID", Store.NO))
                doc.add(StringField("key", "all", Store.NO)) // update key
                // add all fields with their current value
                for (f in fieldValues.indices) {
                    if (f < numNDVFields) {
                        doc.add(NumericDocValuesField("f$f", fieldValues[f]))
                    } else {
                        doc.add(
                            BinaryDocValuesField(
                                "f$f",
                                TestBinaryDocValuesUpdates.toBytes(fieldValues[f])
                            )
                        )
                    }
                }
                writer.addDocument(doc)
                ++docID
            }

            val fieldIdx = random.nextInt(fieldValues.size)
            val updateField = "f$fieldIdx"
            if (fieldIdx < numNDVFields) {
                writer.updateNumericDocValue(Term("key", "all"), updateField, ++fieldValues[fieldIdx])
            } else {
                writer.updateBinaryDocValue(
                    Term("key", "all"),
                    updateField,
                    TestBinaryDocValuesUpdates.toBytes(++fieldValues[fieldIdx])
                )
            }

            if (random.nextDouble() < 0.2) {
                val deleteDoc = random.nextInt(docID) // might also delete an already deleted document, ok!
                writer.deleteDocuments(Term("id", "doc-$deleteDoc"))
            }

            // verify reader
            if (!isNRT) {
                writer.commit()
            }

            val newReader = DirectoryReader.openIfChanged(reader)
            assertNotNull(newReader)
            reader.close()
            reader = newReader
            assertTrue(reader.numDocs() > 0) // we delete at most one document per round
            for (context in reader.leaves()) {
                val r = context.reader()
                val liveDocs = r.liveDocs
                for (field in fieldValues.indices) {
                    val f = "f$field"
                    val bdv = r.getBinaryDocValues(f)
                    val ndv = r.getNumericDocValues(f)
                    if (field < numNDVFields) {
                        assertNotNull(ndv)
                        assertNull(bdv)
                    } else {
                        assertNull(ndv)
                        assertNotNull(bdv)
                    }
                    val maxDoc = r.maxDoc()
                    for (doc in 0 until maxDoc) {
                        if (liveDocs == null || liveDocs.get(doc)) {
                            if (field < numNDVFields) {
                                assertEquals(doc, ndv!!.advance(doc))
                                assertEquals(
                                    fieldValues[field],
                                    ndv.longValue(),
                                    "invalid numeric value for doc=$doc, field=$f, reader=$r"
                                )
                            } else {
                                assertEquals(doc, bdv!!.advance(doc))
                                assertEquals(
                                    fieldValues[field],
                                    TestBinaryDocValuesUpdates.getValue(bdv),
                                    "invalid binary value for doc=$doc, field=$f, reader=$r"
                                )
                            }
                        }
                    }
                }
            }
        }

        writer.close()
        IOUtils.close(reader, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testStressMultiThreading() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        // create index
        val numFields = TestUtil.nextInt(random(), 2, 4)
        val numThreads = TestUtil.nextInt(random(), 3, 6)
        val numDocs = atLeast(200) // TODO reduced from 2000 to 200 for dev speed
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", "doc$i", Store.NO))
            val group = random().nextDouble()
            val g =
                if (group < 0.1) {
                    "g0"
                } else if (group < 0.5) {
                    "g1"
                } else if (group < 0.8) {
                    "g2"
                } else {
                    "g3"
                }
            doc.add(StringField("updKey", g, Store.NO))
            for (j in 0 until numFields) {
                val value = random().nextInt().toLong()
                doc.add(BinaryDocValuesField("f$j", TestBinaryDocValuesUpdates.toBytes(value)))
                doc.add(NumericDocValuesField("cf$j", value * 2)) // control, always updated to f * 2
            }
            writer.addDocument(doc)
        }

        val done = CountDownLatch(numThreads)
        val numUpdates = kotlin.concurrent.atomics.AtomicInt(atLeast(100))

        // same thread updates a field as well as reopens
        val threads = Array(numThreads) { i ->
            object : Thread() {
                override fun run() {
                    var reader: DirectoryReader? = null
                    var success = false
                    try {
                        val random = random()
                        while (numUpdates.fetchAndAdd(-1) > 0) {
                            val group = random.nextDouble()
                            val t =
                                if (group < 0.1) {
                                    Term("updKey", "g0")
                                } else if (group < 0.5) {
                                    Term("updKey", "g1")
                                } else if (group < 0.8) {
                                    Term("updKey", "g2")
                                } else {
                                    Term("updKey", "g3")
                                }
                            val field = random.nextInt(numFields)
                            val f = "f$field"
                            val cf = "cf$field"
                            val updValue = random.nextInt().toLong()
                            writer.updateDocValues(
                                t,
                                BinaryDocValuesField(f, TestBinaryDocValuesUpdates.toBytes(updValue)),
                                NumericDocValuesField(cf, updValue * 2)
                            )

                            if (random.nextDouble() < 0.2) {
                                // delete a random document
                                val doc = random.nextInt(numDocs)
                                writer.deleteDocuments(Term("id", "doc$doc"))
                            }

                            if (random.nextDouble() < 0.05) { // commit every 20 updates on average
                                writer.commit()
                            }

                            if (random.nextDouble() < 0.1) { // reopen NRT reader (apply updates)
                                if (reader == null) {
                                    reader = DirectoryReader.open(writer)
                                } else {
                                    val r2 = DirectoryReader.openIfChanged(reader, writer)
                                    if (r2 != null) {
                                        reader.close()
                                        reader = r2
                                    }
                                }
                            }
                        }
                        success = true
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close()
                            } catch (e: IOException) {
                                if (success) { // suppress this exception only if there was another exception
                                    throw RuntimeException(e)
                                }
                            }
                        }
                        done.countDown()
                    }
                }
            }.also { it.setName("UpdateThread-$i") }
        }

        for (t in threads) t.start()
        done.await()
        writer.close()

        val reader = DirectoryReader.open(dir)
        for (context in reader.leaves()) {
            val r = context.reader()
            for (i in 0 until numFields) {
                val bdv = r.getBinaryDocValues("f$i")!!
                val control = r.getNumericDocValues("cf$i")!!
                val liveDocs = r.liveDocs
                for (j in 0 until r.maxDoc()) {
                    if (liveDocs == null || liveDocs.get(j)) {
                        assertEquals(j, control.advance(j))
                        val ctrlValue = control.longValue()
                        assertEquals(j, bdv.advance(j))
                        val bdvValue = TestBinaryDocValuesUpdates.getValue(bdv) * 2
                        assertEquals(ctrlValue, bdvValue)
                    }
                }
            }
        }
        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateDifferentDocsInDifferentGens() {
        // update same document multiple times across generations
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMaxBufferedDocs(4)
        val writer = IndexWriter(dir, conf)
        val numDocs = atLeast(10)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", "doc$i", Store.NO))
            val value = random().nextInt().toLong()
            doc.add(BinaryDocValuesField("f", TestBinaryDocValuesUpdates.toBytes(value)))
            doc.add(NumericDocValuesField("cf", value * 2))
            writer.addDocument(doc)
        }

        val numGens = atLeast(5)
        for (i in 0 until numGens) {
            val doc = random().nextInt(numDocs)
            val t = Term("id", "doc$doc")
            val value = random().nextLong()
            if (random().nextBoolean()) {
                doUpdate(
                    t,
                    writer,
                    BinaryDocValuesField("f", TestBinaryDocValuesUpdates.toBytes(value)),
                    NumericDocValuesField("cf", value * 2)
                )
            } else {
                writer.updateDocValues(
                    t,
                    BinaryDocValuesField("f", TestBinaryDocValuesUpdates.toBytes(value)),
                    NumericDocValuesField("cf", value * 2)
                )
            }

            val reader = DirectoryReader.open(writer)
            for (context in reader.leaves()) {
                val r = context.reader()
                val fbdv = r.getBinaryDocValues("f")!!
                val cfndv = r.getNumericDocValues("cf")!!
                for (j in 0 until r.maxDoc()) {
                    assertEquals(j, cfndv.nextDoc())
                    assertEquals(j, fbdv.nextDoc())
                    assertEquals(cfndv.longValue(), TestBinaryDocValuesUpdates.getValue(fbdv) * 2)
                }
            }
            reader.close()
        }
        writer.close()
        dir.close()
    }

    @Nightly
    @Test
    @Throws(Exception::class)
    fun testTonsOfUpdates() {
        // LUCENE-5248: make sure that when there are many updates, we don't use too much RAM
        val dir = newDirectory()
        val random = random()
        val conf = newIndexWriterConfig(MockAnalyzer(random))
        conf.setRAMBufferSizeMB(IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB)
        conf.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH) // don't flush by doc
        val writer = IndexWriter(dir, conf)

        // test data: lots of documents (few 10Ks) and lots of update terms (few hundreds)
        val numDocs = atLeast(200) // TODO reduced from 20000 to 200 for dev speed
        val numBinaryFields = atLeast(5)
        val numTerms = TestUtil.nextInt(random, 10, 100) // terms should affect many docs
        val updateTerms = mutableSetOf<String>()
        while (updateTerms.size < numTerms) {
            updateTerms.add(TestUtil.randomSimpleString(random))
        }

        // build a large index with many BDV fields and update terms
        for (i in 0 until numDocs) {
            val doc = Document()
            val numUpdateTerms = TestUtil.nextInt(random, 1, numTerms / 10)
            for (j in 0 until numUpdateTerms) {
                doc.add(StringField("upd", RandomPicks.randomFrom(random, updateTerms), Store.NO))
            }
            for (j in 0 until numBinaryFields) {
                val value = random.nextInt().toLong()
                doc.add(BinaryDocValuesField("f$j", TestBinaryDocValuesUpdates.toBytes(value)))
                doc.add(NumericDocValuesField("cf$j", value * 2))
            }
            writer.addDocument(doc)
        }

        writer.commit() // commit so there's something to apply to

        // set to flush every 2048 bytes (approximately every 12 updates), so we get
        // many flushes during binary updates
        writer.config.setRAMBufferSizeMB(2048.0 / 1024 / 1024)
        val numUpdates = atLeast(100)
        for (i in 0 until numUpdates) {
            val field = random.nextInt(numBinaryFields)
            val updateTerm = Term("upd", RandomPicks.randomFrom(random, updateTerms))
            val value = random.nextInt().toLong()
            writer.updateDocValues(
                updateTerm,
                BinaryDocValuesField("f$field", TestBinaryDocValuesUpdates.toBytes(value)),
                NumericDocValuesField("cf$field", value * 2)
            )
        }

        writer.close()

        val reader = DirectoryReader.open(dir)
        for (context in reader.leaves()) {
            for (i in 0 until numBinaryFields) {
                val r = context.reader()
                val f = r.getBinaryDocValues("f$i")!!
                val cf = r.getNumericDocValues("cf$i")!!
                for (j in 0 until r.maxDoc()) {
                    assertEquals(j, cf.nextDoc())
                    assertEquals(j, f.nextDoc())
                    assertEquals(
                        cf.longValue(),
                        TestBinaryDocValuesUpdates.getValue(f) * 2,
                        "reader=$r, field=f$i, doc=$j"
                    )
                }
            }
        }
        reader.close()

        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testTryUpdateDocValues() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig()
        val writer = IndexWriter(dir, conf)
        val numDocs = 1 + random().nextInt(128)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", "$i", Store.YES))
            doc.add(NumericDocValuesField("numericId", i.toLong()))
            doc.add(BinaryDocValuesField("binaryId", BytesRef(byteArrayOf(i.toByte()))))
            writer.addDocument(doc)
            if (random().nextBoolean()) {
                writer.flush()
            }
        }
        val doc = random().nextInt(numDocs)
        doUpdate(
            Term("id", "$doc"),
            writer,
            NumericDocValuesField("numericId", (doc + 1).toLong()),
            BinaryDocValuesField("binaryId", BytesRef(byteArrayOf((doc + 1).toByte())))
        )
        val reader: IndexReader = DirectoryReader.open(writer)
        var numericIdValues: NumericDocValues? = null
        var binaryIdValues: BinaryDocValues? = null
        for (c in reader.leaves()) {
            val topDocs = IndexSearcher(c.reader()).search(TermQuery(Term("id", "$doc")), 10)
            if (topDocs.totalHits.value == 1L) {
                assertNull(numericIdValues)
                assertNull(binaryIdValues)
                numericIdValues = c.reader().getNumericDocValues("numericId")
                assertEquals(topDocs.scoreDocs[0].doc, numericIdValues!!.advance(topDocs.scoreDocs[0].doc))
                binaryIdValues = c.reader().getBinaryDocValues("binaryId")
                assertEquals(topDocs.scoreDocs[0].doc, binaryIdValues!!.advance(topDocs.scoreDocs[0].doc))
            } else {
                assertEquals(0, topDocs.totalHits.value)
            }
        }

        assertNotNull(numericIdValues)
        assertNotNull(binaryIdValues)

        assertEquals((doc + 1).toLong(), numericIdValues.longValue())
        assertEquals(BytesRef(byteArrayOf((doc + 1).toByte())), binaryIdValues.binaryValue())
        IOUtils.close(reader, writer, dir)
    }

    @Test
    @Throws(IOException::class, BrokenBarrierException::class, InterruptedException::class)
    fun testTryUpdateMultiThreaded() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig()
        val writer = IndexWriter(dir, conf)
        val locks = Array(25 + random().nextInt(50)) { ReentrantLock() }
        val values = arrayOfNulls<Long>(locks.size)

        for (i in locks.indices) {
            val doc = Document()
            values[i] = random().nextLong()
            doc.add(StringField("id", i.toString(), Store.NO))
            doc.add(NumericDocValuesField("value", values[i]!!))
            writer.addDocument(doc)
        }

        val numThreads = if (TEST_NIGHTLY) 2 + random().nextInt(3) else 2
        val threads = Array(numThreads) { Thread() }
        val barrier = CyclicBarrier(threads.size + 1)
        for (i in threads.indices) {
            threads[i] =
                Thread {
                    try {
                        barrier.await()
                        for (doc in 0 until 1000) {
                            val docId = random().nextInt(locks.size)
                            locks[docId].lock()
                            try {
                                val value = if (rarely()) null else random().nextLong() // sometimes reset it
                                if (random().nextBoolean()) {
                                    writer.updateDocValues(
                                        Term("id", "$docId"),
                                        NumericDocValuesField("value", value)
                                    )
                                } else {
                                    doUpdate(
                                        Term("id", "$docId"),
                                        writer,
                                        NumericDocValuesField("value", value)
                                    )
                                }
                                values[docId] = value
                            } catch (e: IOException) {
                                throw AssertionError(e)
                            } finally {
                                locks[docId].unlock()
                            }
                            if (rarely()) {
                                writer.flush()
                            }
                        }
                    } catch (e: Exception) {
                        throw AssertionError(e)
                    }
                }
            threads[i].start()
        }

        barrier.await()
        for (t in threads) {
            t.join()
        }
        DirectoryReader.open(writer).use { reader ->
            for (i in locks.indices) {
                locks[i].lock()
                try {
                    val value = values[i]
                    val topDocs = IndexSearcher(reader).search(TermQuery(Term("id", "$i")), 10)
                    assertEquals(1L, topDocs.totalHits.value)
                    var docID = topDocs.scoreDocs[0].doc
                    val leaves = reader.leaves()
                    val subIndex = ReaderUtil.subIndex(docID, leaves)
                    val leafReader = leaves[subIndex].reader()
                    docID -= leaves[subIndex].docBase
                    val numericDocValues = leafReader.getNumericDocValues("value")!!
                    if (value == null) {
                        assertFalse(numericDocValues.advanceExact(docID), "docID: $docID")
                    } else {
                        assertTrue(numericDocValues.advanceExact(docID), "docID: $docID")
                        assertEquals(value, numericDocValues.longValue())
                    }
                } finally {
                    locks[i].unlock()
                }
            }
        }

        IOUtils.close(writer, dir)
    }

    @Throws(IOException::class)
    fun doUpdate(doc: Term, writer: IndexWriter, vararg fields: Field) {
        var seqId = -1L
        do { // retry if we just committing a merge
            DirectoryReader.open(writer).use { reader ->
                val topDocs = IndexSearcher(reader).search(TermQuery(doc), 10)
                assertEquals(1L, topDocs.totalHits.value)
                val theDoc = topDocs.scoreDocs[0].doc
                seqId = writer.tryUpdateDocValue(reader, theDoc, *fields)
            }
        } while (seqId == -1L)
    }

    @Test
    @Throws(Exception::class)
    fun testResetValue() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)
        val doc = Document()
        doc.add(StringField("id", "0", Store.NO))
        doc.add(NumericDocValuesField("val", 5))
        doc.add(BinaryDocValuesField("val-bin", BytesRef(byteArrayOf(5))))
        writer.addDocument(doc)

        if (random().nextBoolean()) {
            writer.commit()
        }
        DirectoryReader.open(writer).use { reader ->
            assertEquals(1, reader.leaves().size)
            val r = reader.leaves()[0].reader()
            val ndv = r.getNumericDocValues("val")!!
            assertEquals(0, ndv.nextDoc())
            assertEquals(5, ndv.longValue())
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, ndv.nextDoc())

            val bdv = r.getBinaryDocValues("val-bin")!!
            assertEquals(0, bdv.nextDoc())
            assertEquals(BytesRef(byteArrayOf(5)), bdv.binaryValue())
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, bdv.nextDoc())
        }

        writer.updateDocValues(Term("id", "0"), BinaryDocValuesField("val-bin", null))
        DirectoryReader.open(writer).use { reader ->
            assertEquals(1, reader.leaves().size)
            val r = reader.leaves()[0].reader()
            val ndv = r.getNumericDocValues("val")!!
            assertEquals(0, ndv.nextDoc())
            assertEquals(5, ndv.longValue())
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, ndv.nextDoc())

            val bdv = r.getBinaryDocValues("val-bin")!!
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, bdv.nextDoc())
        }
        IOUtils.close(writer, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testResetValueMultipleDocs() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)
        val numDocs = 10 + random().nextInt(50)
        var currentSeqId = 0
        val seqId = intArrayOf(-1, -1, -1, -1, -1)
        for (i in 0 until numDocs) {
            val doc = Document()
            val id = random().nextInt(5)
            seqId[id] = currentSeqId
            doc.add(StringField("id", "$id", Store.YES))
            doc.add(NumericDocValuesField("seqID", currentSeqId++.toLong()))
            doc.add(NumericDocValuesField("is_live", 1))
            if (i > 0) {
                writer.updateDocValues(Term("id", "$id"), NumericDocValuesField("is_live", null))
            }
            writer.addDocument(doc)
            if (random().nextBoolean()) {
                writer.flush()
            }
        }

        if (random().nextBoolean()) {
            writer.commit()
        }
        var numHits = 0 // check if every doc has been selected at least once
        for (i in seqId) {
            if (i > -1) {
                numHits++
            }
        }
        DirectoryReader.open(writer).use { reader ->
            val searcher = IndexSearcher(reader)

            val isLive = searcher.search(FieldExistsQuery("is_live"), 5)
            assertEquals(numHits.toLong(), isLive.totalHits.value)
            val storedFields = reader.storedFields()
            for (doc in isLive.scoreDocs) {
                val id = storedFields.document(doc.doc).get("id")!!.toInt()
                val i = ReaderUtil.subIndex(doc.doc, reader.leaves())
                assertTrue(i >= 0)
                val leafReaderContext = reader.leaves()[i]
                val seqID = leafReaderContext.reader().getNumericDocValues("seqID")
                assertNotNull(seqID)
                assertTrue(seqID.advanceExact(doc.doc - leafReaderContext.docBase))
                assertEquals(seqId[id].toLong(), seqID.longValue())
            }
        }
        IOUtils.close(writer, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testUpdateNotExistingFieldDV() {
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        newDirectory().use { dir ->
            IndexWriter(dir, conf).use { writer ->
                val doc = Document()
                doc.add(StringField("id", "1", Store.YES))
                doc.add(NumericDocValuesField("test", 1))
                writer.addDocument(doc)
                if (random().nextBoolean()) {
                    writer.commit()
                }
                writer.updateDocValues(Term("id", "1"), NumericDocValuesField("not_existing", 1))

                val doc1 = Document()
                doc1.add(StringField("id", "2", Store.YES))
                doc1.add(BinaryDocValuesField("not_existing", BytesRef()))
                var iae =
                    expectThrows(IllegalArgumentException::class) {
                        writer.addDocument(doc1)
                    }
                assertEquals(
                    "cannot change field \"not_existing\" from doc values type=NUMERIC to inconsistent doc values type=BINARY",
                    iae.message
                )

                iae =
                    expectThrows(IllegalArgumentException::class) {
                        writer.updateDocValues(
                            Term("id", "1"),
                            BinaryDocValuesField("not_existing", BytesRef())
                        )
                    }
                assertEquals(
                    "Can't update [BINARY] doc values; the field [not_existing] has inconsistent doc values' type of [NUMERIC].",
                    iae.message
                )
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testUpdateFieldWithNoPreviousDocValuesThrowsError() {
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        newDirectory().use { dir ->
            IndexWriter(dir, conf).use { writer ->
                val doc = Document()
                doc.add(StringField("id", "1", Store.YES))
                writer.addDocument(doc)
                if (random().nextBoolean()) {
                    DirectoryReader.open(writer).use { reader ->
                        val id = reader.leaves()[0].reader().getNumericDocValues("id")
                        assertNull(id)
                    }
                } else if (random().nextBoolean()) {
                    writer.commit()
                }
                val exception =
                    expectThrows(IllegalArgumentException::class) {
                        writer.updateDocValues(Term("id", "1"), NumericDocValuesField("id", 1))
                    }
                assertEquals(
                    "Can't update [NUMERIC] doc values; the field [id] has inconsistent doc values' type of [NONE].",
                    exception.message
                )
            }
        }
    }
}
