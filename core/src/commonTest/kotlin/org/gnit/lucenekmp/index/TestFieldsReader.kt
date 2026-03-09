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

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DocumentStoredFieldVisitor
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.store.BufferedIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.DocHelper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestFieldsReader : LuceneTestCase() {
    private lateinit var dir: Directory
    private lateinit var testDoc: Document
    private lateinit var fieldInfos: FieldInfos.Builder

    @BeforeTest
    fun setUpFieldsReader() {
        testDoc = Document()
        fieldInfos = FieldInfos.Builder(FieldInfos.FieldNumbers(null, null))
        DocHelper.setupDoc(testDoc)
        for (field in testDoc.getFields()) {
            val ift = field.fieldType()
            fieldInfos.add(
                FieldInfo(
                    field.name(),
                    -1,
                    false,
                    ift.omitNorms(),
                    false,
                    ift.indexOptions(),
                    ift.docValuesType(),
                    ift.docValuesSkipIndexType(),
                    -1,
                    hashMapOf(),
                    0,
                    0,
                    0,
                    0,
                    VectorEncoding.FLOAT32,
                    VectorSimilarityFunction.EUCLIDEAN,
                    false,
                    false
                )
            )
        }
        dir = newDirectory()
        val conf =
            newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy())
        conf.mergePolicy.noCFSRatio = 0.0
        val writer = IndexWriter(dir, conf)
        writer.addDocument(testDoc)
        writer.close()
    }

    @AfterTest
    fun tearDownFieldsReader() {
        if (this::dir.isInitialized) {
            dir.close()
        }
    }

    @Test
    fun test() {
        assertTrue(this::dir.isInitialized)
        assertTrue(this::fieldInfos.isInitialized)
        val reader = DirectoryReader.open(dir)
        val doc = reader.storedFields().document(0)
        assertNotNull(doc)
        assertNotNull(doc.getField(DocHelper.TEXT_FIELD_1_KEY))

        var field = doc.getField(DocHelper.TEXT_FIELD_2_KEY) as Field
        assertNotNull(field)
        assertTrue(field.fieldType().storeTermVectors())

        assertFalse(field.fieldType().omitNorms())
        assertTrue(field.fieldType().indexOptions() == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)

        field = doc.getField(DocHelper.TEXT_FIELD_3_KEY) as Field
        assertNotNull(field)
        assertFalse(field.fieldType().storeTermVectors())
        assertTrue(field.fieldType().omitNorms())
        assertTrue(field.fieldType().indexOptions() == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)

        field = doc.getField(DocHelper.NO_TF_KEY) as Field
        assertNotNull(field)
        assertFalse(field.fieldType().storeTermVectors())
        assertFalse(field.fieldType().omitNorms())
        assertTrue(field.fieldType().indexOptions() == IndexOptions.DOCS)

        val visitor = DocumentStoredFieldVisitor(DocHelper.TEXT_FIELD_3_KEY)
        reader.storedFields().document(0, visitor)
        val fields = visitor.document.getFields()
        assertEquals(1, fields.size)
        assertEquals(DocHelper.TEXT_FIELD_3_KEY, fields[0].name())
        reader.close()
    }

    inner class FaultyFSDirectory(fsDir: Directory) : FilterDirectory(fsDir) {
        val doFail = AtomicBoolean(false)

        override fun openInput(name: String, context: IOContext): IndexInput {
            return FaultyIndexInput(doFail, `in`.openInput(name, context))
        }

        fun startFailing() {
            doFail.store(true)
        }
    }

    private inner class FaultyIndexInput(
        private val doFail: AtomicBoolean,
        private val delegate: IndexInput
    ) : BufferedIndexInput("FaultyIndexInput($delegate)", BufferedIndexInput.BUFFER_SIZE) {
        private var count = 0

        private fun simOutage() {
            if (doFail.load() && count++ % 2 == 1) {
                throw IOException("Simulated network outage")
            }
        }

        override fun readInternal(b: ByteBuffer) {
            simOutage()
            delegate.seek(filePointer)
            delegate.readBytes(b.array(), b.position, b.remaining())
            b.position = b.limit
        }

        override fun seekInternal(pos: Long) {
        }

        override fun length(): Long {
            return delegate.length()
        }

        override fun close() {
            delegate.close()
        }

        override fun clone(): FaultyIndexInput {
            val i = FaultyIndexInput(doFail, delegate.clone())
            i.seek(filePointer)
            return i
        }

        override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
            val slice = delegate.slice(sliceDescription, offset, length)
            return FaultyIndexInput(doFail, slice)
        }
    }

    // LUCENE-1262
    @Test
    fun testExceptions() {
        val indexDir = createTempDir("testfieldswriterexceptions")

        val fsDir = newFSDirectory(indexDir)
        val dir = FaultyFSDirectory(fsDir)
        val iwc =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
        val writer = IndexWriter(dir, iwc)
        for (i in 0 until 2) writer.addDocument(testDoc)
        writer.forceMerge(1)
        writer.close()

        val reader = DirectoryReader.open(dir)
        dir.startFailing()

        var exc = false

        val storedFields = reader.storedFields()
        for (i in 0 until 2) {
            try {
                storedFields.document(i)
            } catch (_: IOException) {
                exc = true
            }
            try {
                storedFields.document(i)
            } catch (_: IOException) {
                exc = true
            }
        }
        assertTrue(exc)
        reader.close()
        dir.close()
    }
}
