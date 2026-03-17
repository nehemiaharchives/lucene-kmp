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
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

@OptIn(ExperimentalAtomicApi::class)
class TestIndexWriterMerging : LuceneTestCase() {
    /**
     * Tests that index merging (specifically addIndexes(Directory...)) doesn't change the index
     * order of documents.
     */
    @Test
    @Throws(IOException::class)
    fun testLucene() {
        val num = 100

        val indexA = newDirectory()
        val indexB = newDirectory()

        fillIndex(random(), indexA, 0, num)
        var fail = verifyIndex(indexA, 0)
        if (fail) {
            fail("Index a is invalid")
        }

        fillIndex(random(), indexB, num, num)
        fail = verifyIndex(indexB, num)
        if (fail) {
            fail("Index b is invalid")
        }

        val merged = newDirectory()

        val writer = IndexWriter(
            merged, newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy(2))
        )
        writer.addIndexes(indexA, indexB)
        writer.forceMerge(1)
        writer.close()

        fail = verifyIndex(merged, 0)

        assertFalse(fail, "The merged index is invalid")
        indexA.close()
        indexB.close()
        merged.close()
    }

    @Throws(IOException::class)
    private fun verifyIndex(directory: Directory, startAt: Int): Boolean {
        var fail = false
        val reader = DirectoryReader.open(directory)

        val max = reader.maxDoc()
        val storedFields = reader.storedFields()
        for (i in 0 until max) {
            val temp = storedFields.document(i)
            val countField = requireNotNull(temp.getField("count"))
            // System.out.println("doc "+i+"="+temp.getField("count").stringValue());
            // compare the index doc number to the value that it should be
            if (countField.stringValue() != "${i + startAt}") {
                fail = true
                println("Document ${i + startAt} is returning document ${countField.stringValue()}")
            }
        }
        reader.close()
        return fail
    }

    @Throws(IOException::class)
    private fun fillIndex(random: Random, dir: Directory, start: Int, numDocs: Int) {
        val writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random))
                .setOpenMode(OpenMode.CREATE)
                .setMaxBufferedDocs(2)
                .setMergePolicy(newLogMergePolicy(2))
        )

        for (i in start until (start + numDocs)) {
            val temp = Document()
            temp.add(newStringField("count", "$i", Field.Store.YES))

            writer.addDocument(temp)
        }
        writer.close()
    }

    // LUCENE-325: test forceMergeDeletes, when 2 singular merges
    // are required
    @Test
    @Throws(IOException::class)
    fun testForceMergeDeletes() {
        val dir = newDirectory()
        var writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
        )
        val document = Document()

        val customType = FieldType()
        customType.setStored(true)

        val customType1 = FieldType(TextField.TYPE_STORED)
        customType1.setTokenized(false)
        customType1.setStoreTermVectors(true)
        customType1.setStoreTermVectorPositions(true)
        customType1.setStoreTermVectorOffsets(true)

        val idField = newStringField("id", "", Field.Store.NO)
        document.add(idField)
        val storedField = newField("stored", "stored", customType)
        document.add(storedField)
        val termVectorField = newField("termVector", "termVector", customType1)
        document.add(termVectorField)
        for (i in 0 until 10) {
            idField.setStringValue("$i")
            writer.addDocument(document)
        }
        writer.close()

        var ir = DirectoryReader.open(dir)
        assertEquals(10, ir.maxDoc())
        assertEquals(10, ir.numDocs())
        ir.close()

        val dontMergeConfig =
            IndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        writer = IndexWriter(dir, dontMergeConfig)
        writer.deleteDocuments(Term("id", "0"))
        writer.deleteDocuments(Term("id", "7"))
        writer.close()

        ir = DirectoryReader.open(dir)
        assertEquals(8, ir.numDocs())
        ir.close()

        writer = IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy())
        )
        assertEquals(8, writer.getDocStats().numDocs)
        assertEquals(10, writer.getDocStats().maxDoc)
        writer.forceMergeDeletes()
        assertEquals(8, writer.getDocStats().numDocs)
        writer.close()
        ir = DirectoryReader.open(dir)
        assertEquals(8, ir.maxDoc())
        assertEquals(8, ir.numDocs())
        ir.close()
        dir.close()
    }

    // LUCENE-325: test forceMergeDeletes, when many adjacent merges are required
    @Test
    @Throws(IOException::class)
    fun testForceMergeDeletes2() {
        val dir = newDirectory()
        var writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                .setMergePolicy(newLogMergePolicy(50))
        )

        val document = Document()

        val customType = FieldType()
        customType.setStored(true)

        val customType1 = FieldType(TextField.TYPE_NOT_STORED)
        customType1.setTokenized(false)
        customType1.setStoreTermVectors(true)
        customType1.setStoreTermVectorPositions(true)
        customType1.setStoreTermVectorOffsets(true)

        val storedField = newField("stored", "stored", customType)
        document.add(storedField)
        val termVectorField = newField("termVector", "termVector", customType1)
        document.add(termVectorField)
        val idField = newStringField("id", "", Field.Store.NO)
        document.add(idField)
        for (i in 0 until 98) {
            idField.setStringValue("$i")
            writer.addDocument(document)
        }
        writer.close()

        var ir = DirectoryReader.open(dir)
        assertEquals(98, ir.maxDoc())
        assertEquals(98, ir.numDocs())
        ir.close()

        val dontMergeConfig =
            IndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        writer = IndexWriter(dir, dontMergeConfig)
        for (i in 0 until 98 step 2) {
            writer.deleteDocuments(Term("id", "$i"))
        }
        writer.close()

        ir = DirectoryReader.open(dir)
        assertEquals(49, ir.numDocs())
        ir.close()

        writer = IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy(3))
        )
        assertEquals(49, writer.getDocStats().numDocs)
        writer.forceMergeDeletes()
        writer.close()
        ir = DirectoryReader.open(dir)
        assertEquals(49, ir.maxDoc())
        assertEquals(49, ir.numDocs())
        ir.close()
        dir.close()
    }

    // LUCENE-325: test forceMergeDeletes without waiting, when
    // many adjacent merges are required
    @Test
    @Throws(IOException::class)
    fun testForceMergeDeletes3() {
        val dir = newDirectory()
        var writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                .setMergePolicy(newLogMergePolicy(50))
        )

        val customType = FieldType()
        customType.setStored(true)

        val customType1 = FieldType(TextField.TYPE_NOT_STORED)
        customType1.setTokenized(false)
        customType1.setStoreTermVectors(true)
        customType1.setStoreTermVectorPositions(true)
        customType1.setStoreTermVectorOffsets(true)

        val document = Document()
        val storedField = newField("stored", "stored", customType)
        document.add(storedField)
        val termVectorField = newField("termVector", "termVector", customType1)
        document.add(termVectorField)
        val idField = newStringField("id", "", Field.Store.NO)
        document.add(idField)
        for (i in 0 until 98) {
            idField.setStringValue("$i")
            writer.addDocument(document)
        }
        writer.close()

        var ir = DirectoryReader.open(dir)
        assertEquals(98, ir.maxDoc())
        assertEquals(98, ir.numDocs())
        ir.close()

        val dontMergeConfig =
            IndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        writer = IndexWriter(dir, dontMergeConfig)
        for (i in 0 until 98 step 2) {
            writer.deleteDocuments(Term("id", "$i"))
        }
        writer.close()
        ir = DirectoryReader.open(dir)
        assertEquals(49, ir.numDocs())
        ir.close()

        writer = IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy(3))
        )
        writer.forceMergeDeletes(false)
        writer.close()
        ir = DirectoryReader.open(dir)
        assertEquals(49, ir.maxDoc())
        assertEquals(49, ir.numDocs())
        ir.close()
        dir.close()
    }

    // Just intercepts all merges & verifies that we are never
    // merging a segment with >= 20 (maxMergeDocs) docs
    private class MyMergeScheduler : MergeScheduler() {
        override suspend fun merge(mergeSource: MergeSource, trigger: MergeTrigger) {
            while (true) {
                val merge = mergeSource.nextMerge ?: break
                var numDocs = 0
                for (i in 0 until merge.segments.size) {
                    val maxDoc = merge.segments[i].info.maxDoc()
                    numDocs += maxDoc
                    assertTrue(maxDoc < 20)
                }
                mergeSource.merge(merge)
                assertEquals(numDocs, requireNotNull(merge.mergeInfo).info.maxDoc())
            }
        }

        override fun close() {}
    }

    // LUCENE-1013
    @Test
    @Throws(IOException::class)
    fun testSetMaxMergeDocs() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
            .setMergeScheduler(MyMergeScheduler())
            .setMaxBufferedDocs(2)
            .setMergePolicy(newLogMergePolicy())
        val lmp = conf.mergePolicy as LogMergePolicy
        lmp.maxMergeDocs = 20
        lmp.mergeFactor = 2
        val iw = IndexWriter(dir, conf)
        val document = Document()

        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)

        document.add(newField("tvtest", "a b c", customType))
        for (i in 0 until 177) {
            iw.addDocument(document)
        }
        iw.close()
        dir.close()
    }

    @Test
    @Throws(Throwable::class)
    fun testNoWaitClose() {
        val directory = newDirectory()

        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setTokenized(false)

        val idField = newField("id", "", customType)
        doc.add(idField)

        for (pass in 0 until 2) {
            if (VERBOSE) {
                println("TEST: pass=$pass")
            }

            val conf = newIndexWriterConfig(MockAnalyzer(random()))
                .setOpenMode(OpenMode.CREATE)
                .setMaxBufferedDocs(2)
                .setMergePolicy(newLogMergePolicy())
                .setCommitOnClose(false)
            if (pass == 2) {
                conf.setMergeScheduler(SerialMergeScheduler())
            }

            var writer = IndexWriter(directory, conf)
            (writer.config.mergePolicy as LogMergePolicy).mergeFactor = 100

            for (iter in 0 until atLeast(3)) {
                if (VERBOSE) {
                    println("TEST: iter=$iter")
                }
                for (j in 0 until 199) {
                    idField.setStringValue((iter * 201 + j).toString())
                    writer.addDocument(doc)
                }

                var delID = iter * 199
                for (j in 0 until 20) {
                    writer.deleteDocuments(Term("id", delID.toString()))
                    delID += 5
                }

                writer.commit()

                // Force a bunch of merge threads to kick off so we
                // stress out aborting them on close:
                (writer.config.mergePolicy as LogMergePolicy).mergeFactor = 2

                val finalWriter = writer
                val failure = AtomicReference<Throwable?>(null)
                val t1 = object : Thread() {
                    override fun run() {
                        var done = false
                        while (!done) {
                            for (i in 0 until 100) {
                                try {
                                    finalWriter.addDocument(doc)
                                } catch (_: AlreadyClosedException) {
                                    done = true
                                    break
                                } catch (_: NullPointerException) {
                                    done = true
                                    break
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                    failure.store(e)
                                    done = true
                                    break
                                }
                            }
                            runBlocking { yield() }
                        }
                    }
                }

                t1.start()

                writer.close()
                t1.join()

                val threadFailure = failure.load()
                if (threadFailure != null) {
                    throw threadFailure
                }

                // Make sure reader can read
                val reader = DirectoryReader.open(directory)
                reader.close()

                // Reopen
                writer = IndexWriter(
                    directory,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setOpenMode(OpenMode.APPEND)
                        .setMergePolicy(newLogMergePolicy())
                        .setCommitOnClose(false)
                )
            }
            writer.close()
        }

        directory.close()
    }
}
