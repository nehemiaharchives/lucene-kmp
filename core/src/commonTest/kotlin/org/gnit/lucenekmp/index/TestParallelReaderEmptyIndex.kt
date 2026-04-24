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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

/** Some tests for [ParallelLeafReader]s with empty indexes */
class TestParallelReaderEmptyIndex : LuceneTestCase() {

    /**
     * Creates two empty indexes and wraps a ParallelReader around. Adding this reader to a new index
     * should not throw any exception.
     */
    @Test
    fun testEmptyIndex() {
        val rd1 = newDirectory()
        val iw = IndexWriter(rd1, newIndexWriterConfig(MockAnalyzer(random())))
        iw.close()
        // create a copy:
        val rd2 = newDirectory(rd1)

        val rdOut = newDirectory()

        val iwOut = IndexWriter(rdOut, newIndexWriterConfig(MockAnalyzer(random())))

        // add a readerless parallel reader
        iwOut.addIndexes(SlowCodecReaderWrapper.wrap(ParallelLeafReader()))
        iwOut.forceMerge(1)

        val cpr =
            ParallelCompositeReader(DirectoryReader.open(rd1), DirectoryReader.open(rd2))

        // When unpatched, Lucene crashes here with a NoSuchElementException (caused by
        // ParallelTermEnum)
        val leaves: MutableList<CodecReader> = ArrayList()
        for (leaf in cpr.leaves()) {
            leaves.add(SlowCodecReaderWrapper.wrap(leaf.reader()))
        }
        iwOut.addIndexes(*leaves.toTypedArray())
        iwOut.forceMerge(1)

        iwOut.close()
        rdOut.close()
        rd1.close()
        rd2.close()
    }

    /**
     * This method creates an empty index (numFields=0, numDocs=0) but is marked to have TermVectors.
     * Adding this index to another index should not throw any exception.
     */
    @Test
    fun testEmptyIndexWithVectors() {
        val rd1 = newDirectory()
        run {
            if (VERBOSE) {
                println("\nTEST: make 1st writer")
            }
            var iw = IndexWriter(rd1, newIndexWriterConfig(MockAnalyzer(random())))
            val doc = Document()
            val idField = newTextField("id", "", Field.Store.NO)
            doc.add(idField)
            val customType = FieldType(TextField.TYPE_NOT_STORED)
            customType.setStoreTermVectors(true)
            doc.add(newField("test", "", customType))
            idField.setStringValue("1")
            iw.addDocument(doc)
            doc.add(newField("test", "", customType))
            idField.setStringValue("2")
            iw.addDocument(doc)
            iw.close()

            val dontMergeConfig =
                IndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
            if (VERBOSE) {
                println("\nTEST: make 2nd writer")
            }
            val writer = IndexWriter(rd1, dontMergeConfig)

            writer.deleteDocuments(Term("id", "1"))
            writer.close()
            val ir = DirectoryReader.open(rd1)
            assertEquals(2, ir.maxDoc())
            assertEquals(1, ir.numDocs())
            ir.close()

            iw =
                IndexWriter(
                    rd1,
                    newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND),
                )
            iw.forceMerge(1)
            iw.close()
        }

        val rd2: Directory = newDirectory()
        run {
            val iw = IndexWriter(rd2, newIndexWriterConfig(MockAnalyzer(random())))
            val doc = Document()
            iw.addDocument(doc)
            iw.close()
        }

        val rdOut = newDirectory()

        val iwOut = IndexWriter(rdOut, newIndexWriterConfig(MockAnalyzer(random())))
        val reader1 = DirectoryReader.open(rd1)
        val reader2 = DirectoryReader.open(rd2)
        val pr =
            ParallelLeafReader(false, getOnlyLeafReader(reader1), getOnlyLeafReader(reader2))

        // When unpatched, Lucene crashes here with an ArrayIndexOutOfBoundsException (caused by
        // TermVectorsWriter)
        iwOut.addIndexes(SlowCodecReaderWrapper.wrap(pr))

        pr.close()
        reader1.close()
        reader2.close()

        // assert subreaders were closed
        assertEquals(0, reader1.getRefCount())
        assertEquals(0, reader2.getRefCount())

        rd1.close()
        rd2.close()

        iwOut.forceMerge(1)
        iwOut.close()

        rdOut.close()
    }
}
