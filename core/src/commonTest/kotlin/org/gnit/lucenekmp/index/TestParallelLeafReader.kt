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
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestParallelLeafReader : LuceneTestCase() {

    private var parallel: IndexSearcher? = null
    private var single: IndexSearcher? = null
    private var dir: Directory? = null
    private var dir1: Directory? = null
    private var dir2: Directory? = null

    @Test
    fun testQueries() {
        single = single(random())
        parallel = parallel(random())

        queryTest(TermQuery(Term("f1", "v1")))
        queryTest(TermQuery(Term("f1", "v2")))
        queryTest(TermQuery(Term("f2", "v1")))
        queryTest(TermQuery(Term("f2", "v2")))
        queryTest(TermQuery(Term("f3", "v1")))
        queryTest(TermQuery(Term("f3", "v2")))
        queryTest(TermQuery(Term("f4", "v1")))
        queryTest(TermQuery(Term("f4", "v2")))

        val bq1 = BooleanQuery.Builder()
        bq1.add(TermQuery(Term("f1", "v1")), Occur.MUST)
        bq1.add(TermQuery(Term("f4", "v1")), Occur.MUST)
        queryTest(bq1.build())

        single!!.indexReader.close()
        single = null
        parallel!!.indexReader.close()
        parallel = null
        dir!!.close()
        dir = null
        dir1!!.close()
        dir1 = null
        dir2!!.close()
        dir2 = null
    }

    @Test
    fun testFieldNames() {
        val dir1 = getDir1(random())
        val dir2 = getDir2(random())
        val pr =
            ParallelLeafReader(
                getOnlyLeafReader(DirectoryReader.open(dir1)),
                getOnlyLeafReader(DirectoryReader.open(dir2)),
            )
        val fieldInfos = pr.fieldInfos
        assertEquals(4, fieldInfos.size())
        assertNotNull(fieldInfos.fieldInfo("f1"))
        assertNotNull(fieldInfos.fieldInfo("f2"))
        assertNotNull(fieldInfos.fieldInfo("f3"))
        assertNotNull(fieldInfos.fieldInfo("f4"))
        pr.close()
        dir1.close()
        dir2.close()
    }

    @Test
    fun testRefCounts1() {
        val dir1 = getDir1(random())
        val dir2 = getDir2(random())
        val ir1: LeafReader
        val ir2: LeafReader
        // close subreaders, ParallelReader will not change refCounts, but close on its own close
        val pr =
            ParallelLeafReader(
                getOnlyLeafReader(DirectoryReader.open(dir1)).also { ir1 = it },
                getOnlyLeafReader(DirectoryReader.open(dir2)).also { ir2 = it },
            )

        // check RefCounts
        assertEquals(1, ir1.getRefCount())
        assertEquals(1, ir2.getRefCount())
        pr.close()
        assertEquals(0, ir1.getRefCount())
        assertEquals(0, ir2.getRefCount())
        dir1.close()
        dir2.close()
    }

    @Test
    fun testRefCounts2() {
        val dir1 = getDir1(random())
        val dir2 = getDir2(random())
        val ir1 = getOnlyLeafReader(DirectoryReader.open(dir1))
        val ir2 = getOnlyLeafReader(DirectoryReader.open(dir2))
        // don't close subreaders, so ParallelReader will increment refcounts
        val pr = ParallelLeafReader(false, ir1, ir2)
        // check RefCounts
        assertEquals(2, ir1.getRefCount())
        assertEquals(2, ir2.getRefCount())
        pr.close()
        assertEquals(1, ir1.getRefCount())
        assertEquals(1, ir2.getRefCount())
        ir1.close()
        ir2.close()
        assertEquals(0, ir1.getRefCount())
        assertEquals(0, ir2.getRefCount())
        dir1.close()
        dir2.close()
    }

    @Test
    fun testCloseInnerReader() {
        val dir1 = getDir1(random())
        val ir1 = getOnlyLeafReader(DirectoryReader.open(dir1))

        // with overlapping
        val pr =
            ParallelLeafReader(true, arrayOf(ir1), arrayOf(ir1))

        ir1.close()

        // should already be closed because inner reader is closed!
        expectThrows(AlreadyClosedException::class) {
            pr.storedFields().document(0)
        }

        // noop:
        pr.close()
        dir1.close()
    }

    @Test
    fun testIncompatibleIndexes() {
        // two documents:
        val dir1 = getDir1(random())

        // one document only:
        val dir2 = newDirectory()
        val w2 = IndexWriter(dir2, newIndexWriterConfig(MockAnalyzer(random())))
        val d3 = Document()

        d3.add(newTextField("f3", "v1", Field.Store.YES))
        w2.addDocument(d3)
        w2.close()

        val ir1 = getOnlyLeafReader(DirectoryReader.open(dir1))
        val ir2 = getOnlyLeafReader(DirectoryReader.open(dir2))

        // indexes don't have the same number of documents
        expectThrows(IllegalArgumentException::class) {
            ParallelLeafReader(ir1, ir2)
        }

        expectThrows(IllegalArgumentException::class) {
            ParallelLeafReader(
                random().nextBoolean(),
                arrayOf(ir1, ir2),
                arrayOf(ir1, ir2),
            )
        }

        // check RefCounts
        assertEquals(1, ir1.getRefCount())
        assertEquals(1, ir2.getRefCount())
        ir1.close()
        ir2.close()
        dir1.close()
        dir2.close()
    }

    @Test
    fun testIgnoreStoredFields() {
        val dir1 = getDir1(random())
        val dir2 = getDir2(random())
        val ir1 = getOnlyLeafReader(DirectoryReader.open(dir1))
        val ir2 = getOnlyLeafReader(DirectoryReader.open(dir2))

        // with overlapping
        var pr = ParallelLeafReader(false, arrayOf(ir1, ir2), arrayOf(ir1))
        assertEquals("v1", pr.storedFields().document(0).get("f1"))
        assertEquals("v1", pr.storedFields().document(0).get("f2"))
        assertNull(pr.storedFields().document(0).get("f3"))
        assertNull(pr.storedFields().document(0).get("f4"))
        // check that fields are there
        assertNotNull(pr.terms("f1"))
        assertNotNull(pr.terms("f2"))
        assertNotNull(pr.terms("f3"))
        assertNotNull(pr.terms("f4"))
        pr.close()

        // no stored fields at all
        pr = ParallelLeafReader(false, arrayOf(ir2), emptyArray<LeafReader>())
        assertNull(pr.storedFields().document(0).get("f1"))
        assertNull(pr.storedFields().document(0).get("f2"))
        assertNull(pr.storedFields().document(0).get("f3"))
        assertNull(pr.storedFields().document(0).get("f4"))
        // check that fields are there
        assertNull(pr.terms("f1"))
        assertNull(pr.terms("f2"))
        assertNotNull(pr.terms("f3"))
        assertNotNull(pr.terms("f4"))
        pr.close()

        // without overlapping
        pr = ParallelLeafReader(true, arrayOf(ir2), arrayOf(ir1))
        assertEquals("v1", pr.storedFields().document(0).get("f1"))
        assertEquals("v1", pr.storedFields().document(0).get("f2"))
        assertNull(pr.storedFields().document(0).get("f3"))
        assertNull(pr.storedFields().document(0).get("f4"))
        // check that fields are there
        assertNull(pr.terms("f1"))
        assertNull(pr.terms("f2"))
        assertNotNull(pr.terms("f3"))
        assertNotNull(pr.terms("f4"))
        pr.close()

        // no main readers
        expectThrows(IllegalArgumentException::class) {
            ParallelLeafReader(true, emptyArray<LeafReader>(), arrayOf(ir1))
        }

        dir1.close()
        dir2.close()
    }

    private fun queryTest(query: Query) {
        val parallelHits: Array<ScoreDoc> = parallel!!.search(query, 1000).scoreDocs
        val singleHits: Array<ScoreDoc> = single!!.search(query, 1000).scoreDocs
        assertEquals(parallelHits.size, singleHits.size)
        val parallelFields = parallel!!.storedFields()
        val singleFields = single!!.storedFields()
        for (i in parallelHits.indices) {
            assertEquals(parallelHits[i].score, singleHits[i].score, 0.001f)
            val docParallel = parallelFields.document(parallelHits[i].doc)
            val docSingle = singleFields.document(singleHits[i].doc)
            assertEquals(docParallel.get("f1"), docSingle.get("f1"))
            assertEquals(docParallel.get("f2"), docSingle.get("f2"))
            assertEquals(docParallel.get("f3"), docSingle.get("f3"))
            assertEquals(docParallel.get("f4"), docSingle.get("f4"))
        }
    }

    // Fields 1-4 indexed together:
    private fun single(random: Random): IndexSearcher {
        dir = newDirectory()
        val w = IndexWriter(dir!!, newIndexWriterConfig(MockAnalyzer(random)))
        val d1 = Document()
        d1.add(newTextField("f1", "v1", Field.Store.YES))
        d1.add(newTextField("f2", "v1", Field.Store.YES))
        d1.add(newTextField("f3", "v1", Field.Store.YES))
        d1.add(newTextField("f4", "v1", Field.Store.YES))
        w.addDocument(d1)
        val d2 = Document()
        d2.add(newTextField("f1", "v2", Field.Store.YES))
        d2.add(newTextField("f2", "v2", Field.Store.YES))
        d2.add(newTextField("f3", "v2", Field.Store.YES))
        d2.add(newTextField("f4", "v2", Field.Store.YES))
        w.addDocument(d2)
        w.close()

        val ir = DirectoryReader.open(dir!!)
        return newSearcher(ir)
    }

    // Fields 1 & 2 in one index, 3 & 4 in other, with ParallelReader:
    private fun parallel(random: Random): IndexSearcher {
        dir1 = getDir1(random)
        dir2 = getDir2(random)
        val pr =
            ParallelLeafReader(
                getOnlyLeafReader(DirectoryReader.open(dir1!!)),
                getOnlyLeafReader(DirectoryReader.open(dir2!!)),
            )
        TestUtil.checkReader(pr)
        return newSearcher(pr)
    }

    private fun getDir1(random: Random): Directory {
        val dir1 = newDirectory()
        val conf =
            newIndexWriterConfig(MockAnalyzer(random)).setMergePolicy(LogDocMergePolicy())
        val w1 = IndexWriter(dir1, conf)
        val d1 = Document()
        d1.add(newTextField("f1", "v1", Field.Store.YES))
        d1.add(newTextField("f2", "v1", Field.Store.YES))
        w1.addDocument(d1)
        val d2 = Document()
        d2.add(newTextField("f1", "v2", Field.Store.YES))
        d2.add(newTextField("f2", "v2", Field.Store.YES))
        w1.addDocument(d2)
        w1.forceMerge(1)
        w1.close()
        return dir1
    }

    private fun getDir2(random: Random): Directory {
        val dir2 = newDirectory()
        val conf =
            newIndexWriterConfig(MockAnalyzer(random)).setMergePolicy(LogDocMergePolicy())
        val w2 = IndexWriter(dir2, conf)
        val d3 = Document()
        d3.add(newTextField("f3", "v1", Field.Store.YES))
        d3.add(newTextField("f4", "v1", Field.Store.YES))
        w2.addDocument(d3)
        val d4 = Document()
        d4.add(newTextField("f3", "v2", Field.Store.YES))
        d4.add(newTextField("f4", "v2", Field.Store.YES))
        w2.addDocument(d4)
        w2.forceMerge(1)
        w2.close()
        return dir2
    }

    // not ok to have one leaf w/ index sort and another with a different index sort
    @Test
    fun testWithIndexSort1() {
        val dir1 = newDirectory()
        val iwc1 = newIndexWriterConfig(MockAnalyzer(random()))
        iwc1.setIndexSort(Sort(SortField("foo", SortField.Type.INT)))
        val w1 = IndexWriter(dir1, iwc1)
        w1.addDocument(Document())
        w1.commit()
        w1.addDocument(Document())
        w1.forceMerge(1)
        w1.close()
        val r1 = DirectoryReader.open(dir1)

        val dir2 = newDirectory()
        val iwc2 = newIndexWriterConfig(MockAnalyzer(random()))
        iwc2.setIndexSort(Sort(SortField("bar", SortField.Type.INT)))
        val w2 = IndexWriter(dir2, iwc2)
        w2.addDocument(Document())
        w2.commit()
        w2.addDocument(Document())
        w2.forceMerge(1)
        w2.close()
        val r2 = DirectoryReader.open(dir2)

        val message =
            expectThrows(IllegalArgumentException::class) {
                ParallelLeafReader(getOnlyLeafReader(r1), getOnlyLeafReader(r2))
            }.message
        assertEquals(
            "cannot combine LeafReaders that have different index sorts: saw both sort=<int: \"foo\"> and <int: \"bar\">",
            message,
        )
        IOUtils.close(r1, dir1, r2, dir2)
    }

    // ok to have one leaf w/ index sort and the other with no sort
    @Test
    fun testWithIndexSort2() {
        val dir1 = newDirectory()
        val iwc1 = newIndexWriterConfig(MockAnalyzer(random()))
        iwc1.setIndexSort(Sort(SortField("foo", SortField.Type.INT)))
        val w1 = IndexWriter(dir1, iwc1)
        w1.addDocument(Document())
        w1.commit()
        w1.addDocument(Document())
        w1.forceMerge(1)
        w1.close()
        val r1 = DirectoryReader.open(dir1)

        val dir2 = newDirectory()
        val iwc2 = newIndexWriterConfig(MockAnalyzer(random()))
        val w2 = IndexWriter(dir2, iwc2)
        w2.addDocument(Document())
        w2.addDocument(Document())
        w2.close()

        val r2 = DirectoryReader.open(dir2)
        ParallelLeafReader(false, getOnlyLeafReader(r1), getOnlyLeafReader(r2)).close()
        ParallelLeafReader(false, getOnlyLeafReader(r2), getOnlyLeafReader(r1)).close()
        IOUtils.close(r1, dir1, r2, dir2)
    }

    @Test
    fun testWithDocValuesUpdates() {
        val dir1 = newDirectory()
        val iwc1 = newIndexWriterConfig(MockAnalyzer(random()))
        val w1 = IndexWriter(dir1, iwc1)
        val d = Document()
        d.add(newTextField("name", "billy", Field.Store.NO))
        d.add(NumericDocValuesField("age", 21))
        w1.addDocument(d)
        w1.commit()
        w1.updateNumericDocValue(Term("name", "billy"), "age", 22)
        w1.close()

        val r1 = DirectoryReader.open(dir1)
        val lr: LeafReader = ParallelLeafReader(false, getOnlyLeafReader(r1))

        val dv = lr.getNumericDocValues("age")!!
        assertEquals(0, dv.nextDoc())
        assertEquals(22, dv.longValue())

        assertEquals(1, lr.fieldInfos.fieldInfo("age")!!.docValuesGen)

        IOUtils.close(lr, r1, dir1)
    }
}
