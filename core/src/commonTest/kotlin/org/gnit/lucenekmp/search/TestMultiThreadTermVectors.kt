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
package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.TermVectors
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.English
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestMultiThreadTermVectors : LuceneTestCase() {
    private lateinit var directory: Directory
    private var numDocs: Int = 0
    private var numThreads: Int = 0
    private var numIterations: Int = 0

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        numDocs = if (TEST_NIGHTLY) 1000 else 50
        numThreads = if (TEST_NIGHTLY) 3 else 2
        numIterations = if (TEST_NIGHTLY) 100 else 50
        directory = newDirectory()
        val writer =
            IndexWriter(
                directory,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy())
            )
        val customType = FieldType(TextField.TYPE_STORED)
        customType.setTokenized(false)
        customType.setStoreTermVectors(true)
        for (i in 0 until numDocs) {
            val doc = Document()
            val fld = newField("field", English.intToEnglish(i), customType)
            doc.add(fld)
            writer.addDocument(doc)
        }
        writer.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        directory.close()
    }

    @Test
    @Throws(Exception::class)
    fun test() {
        DirectoryReader.open(directory).use { reader ->
            testTermPositionVectors(reader, numThreads)
        }
    }

    @Throws(Exception::class)
    fun testTermPositionVectors(reader: IndexReader, threadCount: Int) {
        val mtr = Array<MultiThreadTermVectorsReader?>(threadCount) { null }
        for (i in 0 until threadCount) {
            mtr[i] = MultiThreadTermVectorsReader()
            mtr[i]!!.init(reader)
        }

        for (vectorReader in mtr) {
            vectorReader!!.start()
        }

        for (vectorReader in mtr) {
            vectorReader!!.join()
        }
    }

    inner class MultiThreadTermVectorsReader : Thread() {
        private var reader: IndexReader? = null

        fun init(reader: IndexReader) {
            this.reader = reader
        }

        override fun run() {
            try {
                for (i in 0 until numIterations) {
                    testTermVectors()
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        @Throws(Exception::class)
        private fun testTermVectors() {
            // check:
            val reader = checkNotNull(reader)
            val numDocs = reader.numDocs()
            val termVectors: TermVectors = reader.termVectors()
            for (docId in 0 until numDocs) {
                val vectors = termVectors.get(docId)
                assertNotNull(vectors)
                // verify vectors result
                verifyVectors(vectors, docId)
                val vector = termVectors.get(docId)?.terms("field")
                assertNotNull(vector)
                verifyVector(vector.iterator(), docId)
            }
        }

    }

    companion object {
        @Throws(Exception::class)
        fun verifyVectors(vectors: Fields, num: Int) {
            for (field in vectors) {
                val terms = vectors.terms(field)
                assertNotNull(terms)
                verifyVector(terms.iterator(), num)
            }
        }

        @Throws(Exception::class)
        fun verifyVector(vector: TermsEnum, num: Int) {
            val temp = StringBuilder()
            while (vector.next() != null) {
                temp.append(vector.term()!!.utf8ToString())
            }
            assertEquals(English.intToEnglish(num).trim(), temp.toString().trim())
        }
    }
}
