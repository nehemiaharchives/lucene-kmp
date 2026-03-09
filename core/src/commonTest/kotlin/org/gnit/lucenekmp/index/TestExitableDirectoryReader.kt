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

import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.KnnByteVectorField
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.ExitableDirectoryReader.ExitingReaderException
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.TestVectorUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Test that uses a default/lucene Implementation of [QueryTimeout] to exit out long running
 * queries that take too long to iterate over Terms.
 */
class TestExitableDirectoryReader : LuceneTestCase() {
    private class TestReader(reader: LeafReader) : FilterLeafReader(reader) {
        private class TestTerms(`in`: Terms) : FilterTerms(`in`) {
            override val sumTotalTermFreq: Long
                get() = `in`.sumTotalTermFreq

            override fun iterator(): TermsEnum {
                return TestTermsEnum(super.iterator())
            }
        }

        private class TestTermsEnum(`in`: TermsEnum) : FilterTermsEnum(`in`) {
            /** Sleep between iterations to timeout things.  */
            override fun next(): BytesRef? {
                // Sleep for 100ms before each .next() call.
                val deadline = System.nanoTime() + 100_000_000L
                while (System.nanoTime() < deadline) {
                }
                return `in`.next()
            }
        }

        /** Constructor * */
        init {
            requireNotNull(reader)
        }

        override fun terms(field: String?): Terms? {
            val terms = super.terms(field)
            return if (terms == null) null else TestTerms(terms)
        }

        override val coreCacheHelper: CacheHelper?
            get() = `in`.coreCacheHelper

        override val readerCacheHelper: CacheHelper?
            get() = `in`.readerCacheHelper
    }

    /**
     * Tests timing out of TermsEnum iterations
     *
     * @throws Exception on error
     */
    @Test
    fun testExitableFilterTermsIndexReader() {
        val directory = newDirectory()
        val writer = IndexWriter(directory, newIndexWriterConfig(MockAnalyzer(random())))

        val d1 = Document()
        d1.add(newTextField("default", "one two", Field.Store.YES))
        writer.addDocument(d1)

        val d2 = Document()
        d2.add(newTextField("default", "one three", Field.Store.YES))
        writer.addDocument(d2)

        val d3 = Document()
        d3.add(newTextField("default", "ones two four", Field.Store.YES))
        writer.addDocument(d3)

        writer.forceMerge(1)
        writer.commit()
        writer.close()

        val query: Query = PrefixQuery(Term("default", "o"))

        var directoryReader = DirectoryReader.open(directory)
        var exitableDirectoryReader = ExitableDirectoryReader(directoryReader, infiniteQueryTimeout())
        var reader: IndexReader = TestReader(getOnlyLeafReader(exitableDirectoryReader))
        var searcher = IndexSearcher(reader)
        searcher.search(query, 10)
        reader.close()

        directoryReader = DirectoryReader.open(directory)
        exitableDirectoryReader = ExitableDirectoryReader(directoryReader, immediateQueryTimeout())
        reader = TestReader(getOnlyLeafReader(exitableDirectoryReader))
        val slowSearcher = IndexSearcher(reader)
        expectThrows(ExitingReaderException::class) {
            slowSearcher.search(query, 10)
        }
        reader.close()

        directoryReader = DirectoryReader.open(directory)
        exitableDirectoryReader = ExitableDirectoryReader(directoryReader, infiniteQueryTimeout())
        reader = TestReader(getOnlyLeafReader(exitableDirectoryReader))
        searcher = IndexSearcher(reader)
        searcher.search(query, 10)
        reader.close()
        directory.close()
    }

    /**
     * Tests time out check sampling of TermsEnum iterations
     *
     * @throws Exception on error
     */
    @Test
    fun testExitableTermsEnumSampleTimeoutCheck() {
        newDirectory().use { directory ->
            IndexWriter(directory, newIndexWriterConfig(MockAnalyzer(random()))).use { writer ->
                for (i in 0 until 50) {
                    val d1 = Document()
                    d1.add(newTextField("default", "term$i", Field.Store.YES))
                    writer.addDocument(d1)
                }

                writer.forceMerge(1)
                writer.commit()

                val query: Query = PrefixQuery(Term("default", "term"))
                val queryTimeout = CountingQueryTimeout()
                val directoryReader = DirectoryReader.open(directory)
                val exitableDirectoryReader = ExitableDirectoryReader(directoryReader, queryTimeout)
                val reader: IndexReader = TestReader(getOnlyLeafReader(exitableDirectoryReader))
                val searcher = IndexSearcher(reader)
                searcher.search(query, 300)
                reader.close()
                assertEquals(5, queryTimeout.getShouldExitCallCount())
            }
        }
    }

    /**
     * Tests timing out of PointValues queries
     *
     * @throws Exception on error
     */
    @Test
    fun testExitablePointValuesIndexReader() {
        val directory = newDirectory()
        val writer = IndexWriter(directory, newIndexWriterConfig(MockAnalyzer(random())))

        val d1 = Document()
        d1.add(IntPoint("default", 10))
        writer.addDocument(d1)

        val d2 = Document()
        d2.add(IntPoint("default", 100))
        writer.addDocument(d2)

        val d3 = Document()
        d3.add(IntPoint("default", 1000))
        writer.addDocument(d3)

        writer.forceMerge(1)
        writer.commit()
        writer.close()

        val query: Query = IntPoint.newRangeQuery("default", 10, 20)

        var directoryReader = DirectoryReader.open(directory)
        var exitableDirectoryReader = ExitableDirectoryReader(directoryReader, infiniteQueryTimeout())
        var reader: IndexReader = TestReader(getOnlyLeafReader(exitableDirectoryReader))
        var searcher = IndexSearcher(reader)
        searcher.search(query, 10)
        reader.close()

        directoryReader = DirectoryReader.open(directory)
        exitableDirectoryReader = ExitableDirectoryReader(directoryReader, immediateQueryTimeout())
        reader = TestReader(getOnlyLeafReader(exitableDirectoryReader))
        val slowSearcher = IndexSearcher(reader)
        expectThrows(ExitingReaderException::class) {
            slowSearcher.search(query, 10)
        }
        reader.close()

        directoryReader = DirectoryReader.open(directory)
        exitableDirectoryReader = ExitableDirectoryReader(directoryReader, infiniteQueryTimeout())
        reader = TestReader(getOnlyLeafReader(exitableDirectoryReader))
        searcher = IndexSearcher(reader)
        searcher.search(query, 10)
        reader.close()
        directory.close()
    }

    @Test
    fun testExitableTermsMinAndMax() {
        val directory = newDirectory()
        val w = IndexWriter(directory, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val fooField = StringField("foo", "bar", Field.Store.NO)
        doc.add(fooField)
        w.addDocument(doc)
        w.flush()

        val directoryReader = DirectoryReader.open(w)
        for (lfc in directoryReader.leaves()) {
            val terms = object : ExitableDirectoryReader.ExitableTerms(
                lfc.reader().terms("foo")!!,
                infiniteQueryTimeout()
            ) {
                override fun iterator(): TermsEnum {
                    fail("min and max should be retrieved from block tree, no need to iterate")
                }
            }
            assertEquals("bar", terms.min!!.utf8ToString())
            assertEquals("bar", terms.max!!.utf8ToString())
        }

        w.close()
        directoryReader.close()
        directory.close()
    }

    @Test
    fun testDocValues() {
        val directory = newDirectory()
        val writer = IndexWriter(directory, newIndexWriterConfig(MockAnalyzer(random())))

        val d1 = Document()
        addDVs(d1, 10)
        writer.addDocument(d1)

        val d2 = Document()
        addDVs(d2, 100)
        writer.addDocument(d2)

        val d3 = Document()
        addDVs(d3, 1000)
        writer.addDocument(d3)

        writer.forceMerge(1)
        writer.commit()
        writer.close()

        for (dvFactory in listOf<DvFactory>(
            DvFactory { r -> r.getSortedDocValues("sorted")!! },
            DvFactory { r -> r.getSortedSetDocValues("sortedset")!! },
            DvFactory { r -> r.getSortedNumericDocValues("sortednumeric")!! },
            DvFactory { r -> r.getNumericDocValues("numeric")!! },
            DvFactory { r -> r.getBinaryDocValues("binary")!! }
        )) {
            var directoryReader = DirectoryReader.open(directory)
            var exitableDirectoryReader = ExitableDirectoryReader(directoryReader, immediateQueryTimeout())

            run {
                val reader: IndexReader = TestReader(getOnlyLeafReader(exitableDirectoryReader))
                expectThrows(ExitingReaderException::class) {
                    val leaf = reader.leaves()[0].reader()
                    val iter = dvFactory.create(leaf)
                    scan(leaf, iter)
                }
                reader.close()
            }

            directoryReader = DirectoryReader.open(directory)
            exitableDirectoryReader = ExitableDirectoryReader(directoryReader, infiniteQueryTimeout())
            run {
                val reader: IndexReader = TestReader(getOnlyLeafReader(exitableDirectoryReader))
                val leaf = reader.leaves()[0].reader()
                scan(leaf, dvFactory.create(leaf))
                assertNull(leaf.getNumericDocValues("absent"))
                assertNull(leaf.getBinaryDocValues("absent"))
                assertNull(leaf.getSortedDocValues("absent"))
                assertNull(leaf.getSortedNumericDocValues("absent"))
                assertNull(leaf.getSortedSetDocValues("absent"))
                reader.close()
            }
        }

        directory.close()
    }

    @Test
    fun testFloatVectorValues() {
        val directory = newDirectory()
        val writer = IndexWriter(directory, newIndexWriterConfig(MockAnalyzer(random())))

        val numDoc = atLeast(20)
        val deletedDoc = TestUtil.nextInt(random(), 0, 5)
        val dimension = atLeast(3)

        for (i in 0 until numDoc) {
            val doc = Document()
            val value = TestVectorUtil.randomVector(dimension)
            val fieldType = KnnFloatVectorField.createFieldType(dimension, VectorSimilarityFunction.COSINE)
            doc.add(KnnFloatVectorField("vector", value, fieldType))
            doc.add(StringField("id", i.toString(), Field.Store.YES))
            writer.addDocument(doc)
        }

        writer.forceMerge(1)
        writer.commit()

        for (i in 0 until deletedDoc) {
            writer.deleteDocuments(Term("id", i.toString()))
        }

        writer.close()

        val queryTimeout = if (random().nextBoolean()) immediateQueryTimeout() else infiniteQueryTimeout()

        val directoryReader = DirectoryReader.open(directory)
        val exitableDirectoryReader = ExitableDirectoryReader(directoryReader, queryTimeout)
        val reader: IndexReader = TestReader(getOnlyLeafReader(exitableDirectoryReader))

        val context = reader.leaves()[0]
        val leaf = context.reader()

        if (queryTimeout.shouldExit()) {
            expectThrows(ExitingReaderException::class) {
                val values = leaf.getFloatVectorValues("vector")!!
                scanAndRetrieve(leaf, values)
            }

            expectThrows(ExitingReaderException::class) {
                leaf.searchNearestVectors(
                    "vector",
                    TestVectorUtil.randomVector(dimension),
                    5,
                    leaf.liveDocs,
                    Int.MAX_VALUE
                )
            }
        } else {
            val values = leaf.getFloatVectorValues("vector")!!
            scanAndRetrieve(leaf, values)

            leaf.searchNearestVectors(
                "vector",
                TestVectorUtil.randomVector(dimension),
                5,
                leaf.liveDocs,
                Int.MAX_VALUE
            )
        }

        reader.close()
        directory.close()
    }

    @Test
    fun testByteVectorValues() {
        val directory = newDirectory()
        val writer = IndexWriter(directory, newIndexWriterConfig(MockAnalyzer(random())))

        val numDoc = atLeast(20)
        val deletedDoc = TestUtil.nextInt(random(), 0, 5)
        val dimension = atLeast(3)

        for (i in 0 until numDoc) {
            val doc = Document()
            val value = TestVectorUtil.randomVectorBytes(dimension)
            doc.add(KnnByteVectorField("vector", value, VectorSimilarityFunction.COSINE))
            doc.add(StringField("id", i.toString(), Field.Store.YES))
            writer.addDocument(doc)
        }

        writer.forceMerge(1)
        writer.commit()

        for (i in 0 until deletedDoc) {
            writer.deleteDocuments(Term("id", i.toString()))
        }

        writer.close()

        val queryTimeout = if (random().nextBoolean()) immediateQueryTimeout() else infiniteQueryTimeout()

        val directoryReader = DirectoryReader.open(directory)
        val exitableDirectoryReader = ExitableDirectoryReader(directoryReader, queryTimeout)
        val reader: IndexReader = TestReader(getOnlyLeafReader(exitableDirectoryReader))

        val context = reader.leaves()[0]
        val leaf = context.reader()

        if (queryTimeout.shouldExit()) {
            expectThrows(ExitingReaderException::class) {
                val values = leaf.getByteVectorValues("vector")!!
                scanAndRetrieve(leaf, values)
            }

            expectThrows(ExitingReaderException::class) {
                leaf.searchNearestVectors(
                    "vector",
                    TestVectorUtil.randomVectorBytes(dimension),
                    5,
                    leaf.liveDocs,
                    Int.MAX_VALUE
                )
            }
        } else {
            val values = leaf.getByteVectorValues("vector")!!
            scanAndRetrieve(leaf, values)

            leaf.searchNearestVectors(
                "vector",
                TestVectorUtil.randomVectorBytes(dimension),
                5,
                leaf.liveDocs,
                Int.MAX_VALUE
            )
        }

        reader.close()
        directory.close()
    }

    private fun scanAndRetrieve(leaf: LeafReader, values: KnnVectorValues) {
        val iter = values.iterator()
        iter.nextDoc()
        while (iter.docID() != DocIdSetIterator.NO_MORE_DOCS && iter.docID() < leaf.maxDoc()) {
            val docId = iter.docID()
            if (docId >= leaf.maxDoc()) {
                break
            }
            val nextDocId = docId + 1
            if (random().nextBoolean() && nextDocId < leaf.maxDoc()) {
                iter.advance(nextDocId)
            } else {
                iter.nextDoc()
            }
            if (random().nextBoolean()
                && iter.docID() != DocIdSetIterator.NO_MORE_DOCS
                && values is FloatVectorValues
            ) {
                values.vectorValue(iter.index())
            }
        }
    }

    private fun scan(leaf: LeafReader, iter: DocValuesIterator) {
        iter.nextDoc()
        while (iter.docID() != DocIdSetIterator.NO_MORE_DOCS && iter.docID() < leaf.maxDoc()) {
            val nextDocId = iter.docID() + 1
            if (random().nextBoolean() && nextDocId < leaf.maxDoc()) {
                if (random().nextBoolean()) {
                    iter.advance(nextDocId)
                } else {
                    iter.advanceExact(nextDocId)
                }
            } else {
                iter.nextDoc()
            }
        }
    }

    private fun addDVs(d1: Document, i: Int) {
        d1.add(NumericDocValuesField("numeric", i.toLong()))
        d1.add(BinaryDocValuesField("binary", BytesRef("$i")))
        d1.add(SortedDocValuesField("sorted", BytesRef("$i")))
        d1.add(SortedNumericDocValuesField("sortednumeric", i.toLong()))
        d1.add(SortedSetDocValuesField("sortedset", BytesRef("$i")))
    }

    private fun infiniteQueryTimeout(): QueryTimeout {
        return QueryTimeout { false }
    }

    private class CountingQueryTimeout : QueryTimeout {
        private var counter = 0

        override fun shouldExit(): Boolean {
            counter++
            return false
        }

        fun getShouldExitCallCount(): Int {
            return counter
        }
    }

    private fun immediateQueryTimeout(): QueryTimeout {
        return QueryTimeout { true }
    }

    fun interface DvFactory {
        fun create(leaf: LeafReader): DocValuesIterator
    }
}
