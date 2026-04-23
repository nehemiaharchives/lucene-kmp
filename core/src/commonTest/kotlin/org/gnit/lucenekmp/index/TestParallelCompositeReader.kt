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
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestParallelCompositeReader : LuceneTestCase() {
    private var parallel: IndexSearcher? = null
    private var single: IndexSearcher? = null
    private var dir: Directory? = null
    private var dir1: Directory? = null
    private var dir2: Directory? = null

    @Test
    fun testQueries() {
        single = single(random(), false)
        parallel = parallel(random(), false)

        queries()

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
    fun testQueriesCompositeComposite() {
        single = single(random(), true)
        parallel = parallel(random(), true)

        queries()

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

    private fun queries() {
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
    }

    @Test
    fun testRefCounts1() {
        val dir1 = getDir1(random())
        val dir2 = getDir2(random())
        val ir1 = DirectoryReader.open(dir1)
        val ir2 = DirectoryReader.open(dir2)
        val pr = ParallelCompositeReader(ir1, ir2)
        val psub1 = pr.sequentialSubReaders[0]
        assertEquals(1, ir1.getRefCount())
        assertEquals(1, ir2.getRefCount())
        assertEquals(1, psub1.getRefCount())
        pr.close()
        assertEquals(0, ir1.getRefCount())
        assertEquals(0, ir2.getRefCount())
        assertEquals(0, psub1.getRefCount())
        dir1.close()
        dir2.close()
    }

    @Test
    fun testRefCounts2() {
        val dir1 = getDir1(random())
        val dir2 = getDir2(random())
        val ir1 = DirectoryReader.open(dir1)
        val ir2 = DirectoryReader.open(dir2)

        val pr = ParallelCompositeReader(false, ir1, ir2)
        val psub1 = pr.sequentialSubReaders[0]
        assertEquals(2, ir1.getRefCount())
        assertEquals(2, ir2.getRefCount())
        assertEquals(
            1,
            psub1.getRefCount(),
            "refCount must be 1, as the synthetic reader was created by ParallelCompositeReader",
        )
        pr.close()
        assertEquals(1, ir1.getRefCount())
        assertEquals(1, ir2.getRefCount())
        assertEquals(0, psub1.getRefCount(), "refcount must be 0 because parent was closed")
        ir1.close()
        ir2.close()
        assertEquals(0, ir1.getRefCount())
        assertEquals(0, ir2.getRefCount())
        assertEquals(0, psub1.getRefCount(), "refcount should not change anymore")
        dir1.close()
        dir2.close()
    }

    private fun testReaderClosedListener1(closeSubReaders: Boolean, wrapMultiReaderType: Int) {
        val dir1 = getDir1(random())
        val ir1 = DirectoryReader.open(dir1)
        val ir2: CompositeReader =
            when (wrapMultiReaderType) {
                0 -> ir1
                1 -> MultiReader(ir1)
                2 -> MultiReader(arrayOf(ir1), false)
                else -> throw AssertionError()
            }

        val pr = ParallelCompositeReader(closeSubReaders, arrayOf(ir2), arrayOf(ir2))

        assertEquals(3, pr.leaves().size)
        assertEquals(ir1.readerCacheHelper, pr.readerCacheHelper)

        var i = 0
        for (cxt in pr.leaves()) {
            val originalLeaf = ir1.leaves()[i++].reader()
            assertEquals(originalLeaf.coreCacheHelper, cxt.reader().coreCacheHelper)
            assertEquals(originalLeaf.readerCacheHelper, cxt.reader().readerCacheHelper)
        }
        pr.close()
        if (!closeSubReaders) {
            ir1.close()
        }

        if (wrapMultiReaderType == 2) {
            ir2.close()
        }
        dir1.close()
    }

    @Test
    fun testReaderClosedListener1() {
        testReaderClosedListener1(false, 0)
        testReaderClosedListener1(true, 0)
        testReaderClosedListener1(false, 1)
        testReaderClosedListener1(true, 1)
        testReaderClosedListener1(false, 2)
    }

    @Test
    fun testCloseInnerReader() {
        val dir1 = getDir1(random())
        val ir1: CompositeReader = DirectoryReader.open(dir1)
        assertEquals(1, ir1.sequentialSubReaders[0].getRefCount())

        val pr = ParallelCompositeReader(true, arrayOf(ir1), arrayOf(ir1))

        val psub = pr.sequentialSubReaders[0]
        assertEquals(1, psub.getRefCount())

        ir1.close()

        assertEquals(1, psub.getRefCount(), "refCount of synthetic subreader should be unchanged")
        expectThrows(AlreadyClosedException::class) {
            psub.storedFields().document(0)
        }

        expectThrows(AlreadyClosedException::class) {
            pr.storedFields().document(0)
        }

        pr.close()
        assertEquals(0, psub.getRefCount())
        dir1.close()
    }

    @Test
    fun testIncompatibleIndexes1() {
        val dir1 = getDir1(random())
        val dir2 = newDirectory()
        val w2 = IndexWriter(dir2, newIndexWriterConfig(MockAnalyzer(random())))
        val d3 = Document()

        d3.add(newTextField("f3", "v1", Field.Store.YES))
        w2.addDocument(d3)
        w2.close()

        val ir1 = DirectoryReader.open(dir1)
        val ir2 = DirectoryReader.open(dir2)

        expectThrows(IllegalArgumentException::class) {
            ParallelCompositeReader(ir1, ir2)
        }

        expectThrows(IllegalArgumentException::class) {
            ParallelCompositeReader(random().nextBoolean(), ir1, ir2)
        }

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
    fun testIncompatibleIndexes2() {
        val dir1 = getDir1(random())
        val dir2 = getInvalidStructuredDir2(random())

        val ir1 = DirectoryReader.open(dir1)
        val ir2 = DirectoryReader.open(dir2)
        val readers = arrayOf<CompositeReader>(ir1, ir2)
        expectThrows(IllegalArgumentException::class) {
            ParallelCompositeReader(*readers)
        }

        expectThrows(IllegalArgumentException::class) {
            ParallelCompositeReader(random().nextBoolean(), readers, readers)
        }

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
    fun testIgnoreStoredFields() {
        val dir1 = getDir1(random())
        val dir2 = getDir2(random())
        val ir1: CompositeReader = DirectoryReader.open(dir1)
        val ir2: CompositeReader = DirectoryReader.open(dir2)

        var pr = ParallelCompositeReader(false, arrayOf(ir1, ir2), arrayOf(ir1))
        assertEquals("v1", pr.storedFields().document(0).get("f1"))
        assertEquals("v1", pr.storedFields().document(0).get("f2"))
        assertNull(pr.storedFields().document(0).get("f3"))
        assertNull(pr.storedFields().document(0).get("f4"))
        assertNotNull(MultiTerms.getTerms(pr, "f1"))
        assertNotNull(MultiTerms.getTerms(pr, "f2"))
        assertNotNull(MultiTerms.getTerms(pr, "f3"))
        assertNotNull(MultiTerms.getTerms(pr, "f4"))
        pr.close()

        pr = ParallelCompositeReader(false, arrayOf(ir2), emptyArray<CompositeReader>())
        assertNull(pr.storedFields().document(0).get("f1"))
        assertNull(pr.storedFields().document(0).get("f2"))
        assertNull(pr.storedFields().document(0).get("f3"))
        assertNull(pr.storedFields().document(0).get("f4"))
        assertNull(MultiTerms.getTerms(pr, "f1"))
        assertNull(MultiTerms.getTerms(pr, "f2"))
        assertNotNull(MultiTerms.getTerms(pr, "f3"))
        assertNotNull(MultiTerms.getTerms(pr, "f4"))
        pr.close()

        pr = ParallelCompositeReader(true, arrayOf(ir2), arrayOf(ir1))
        assertEquals("v1", pr.storedFields().document(0).get("f1"))
        assertEquals("v1", pr.storedFields().document(0).get("f2"))
        assertNull(pr.storedFields().document(0).get("f3"))
        assertNull(pr.storedFields().document(0).get("f4"))
        assertNull(MultiTerms.getTerms(pr, "f1"))
        assertNull(MultiTerms.getTerms(pr, "f2"))
        assertNotNull(MultiTerms.getTerms(pr, "f3"))
        assertNotNull(MultiTerms.getTerms(pr, "f4"))
        pr.close()

        expectThrows(IllegalArgumentException::class) {
            ParallelCompositeReader(true, emptyArray<CompositeReader>(), arrayOf(ir1))
        }

        dir1.close()
        dir2.close()
    }

    @Test
    fun testToString() {
        val dir1 = getDir1(random())
        val ir1: CompositeReader = DirectoryReader.open(dir1)
        val pr = ParallelCompositeReader(ir1)

        val s = pr.toString()
        assertTrue(s.startsWith("ParallelCompositeReader(ParallelLeafReader("), "toString incorrect: $s")

        pr.close()
        dir1.close()
    }

    @Test
    fun testToStringCompositeComposite() {
        val dir1 = getDir1(random())
        val ir1: CompositeReader = DirectoryReader.open(dir1)
        val pr = ParallelCompositeReader(MultiReader(ir1))

        val s = pr.toString()
        assertTrue(
            s.startsWith("ParallelCompositeReader(ParallelLeafReader("),
            "toString incorrect (should be flattened): $s",
        )

        pr.close()
        dir1.close()
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

    private fun single(random: Random, compositeComposite: Boolean): IndexSearcher {
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
        val d3 = Document()
        d3.add(newTextField("f1", "v3", Field.Store.YES))
        d3.add(newTextField("f2", "v3", Field.Store.YES))
        d3.add(newTextField("f3", "v3", Field.Store.YES))
        d3.add(newTextField("f4", "v3", Field.Store.YES))
        w.addDocument(d3)
        val d4 = Document()
        d4.add(newTextField("f1", "v4", Field.Store.YES))
        d4.add(newTextField("f2", "v4", Field.Store.YES))
        d4.add(newTextField("f3", "v4", Field.Store.YES))
        d4.add(newTextField("f4", "v4", Field.Store.YES))
        w.addDocument(d4)
        w.close()

        val ir: CompositeReader =
            if (compositeComposite) {
                MultiReader(DirectoryReader.open(dir!!), DirectoryReader.open(dir!!))
            } else {
                DirectoryReader.open(dir!!)
            }
        return newSearcher(ir)
    }

    private fun parallel(random: Random, compositeComposite: Boolean): IndexSearcher {
        dir1 = getDir1(random)
        dir2 = getDir2(random)
        val rd1: CompositeReader
        val rd2: CompositeReader
        if (compositeComposite) {
            rd1 = MultiReader(DirectoryReader.open(dir1!!), DirectoryReader.open(dir1!!))
            rd2 = MultiReader(DirectoryReader.open(dir2!!), DirectoryReader.open(dir2!!))
            assertEquals(2, rd1.context.children().size)
            assertEquals(2, rd2.context.children().size)
        } else {
            rd1 = DirectoryReader.open(dir1!!)
            rd2 = DirectoryReader.open(dir2!!)
            assertEquals(3, rd1.context.children().size)
            assertEquals(3, rd2.context.children().size)
        }
        val pr = ParallelCompositeReader(rd1, rd2)
        return newSearcher(pr)
    }

    private fun getDir1(random: Random): Directory {
        val dir1 = newDirectory()
        val w1 =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random)).setMergePolicy(NoMergePolicy.INSTANCE),
            )
        val d1 = Document()
        d1.add(newTextField("f1", "v1", Field.Store.YES))
        d1.add(newTextField("f2", "v1", Field.Store.YES))
        w1.addDocument(d1)
        w1.commit()
        val d2 = Document()
        d2.add(newTextField("f1", "v2", Field.Store.YES))
        d2.add(newTextField("f2", "v2", Field.Store.YES))
        w1.addDocument(d2)
        val d3 = Document()
        d3.add(newTextField("f1", "v3", Field.Store.YES))
        d3.add(newTextField("f2", "v3", Field.Store.YES))
        w1.addDocument(d3)
        w1.commit()
        val d4 = Document()
        d4.add(newTextField("f1", "v4", Field.Store.YES))
        d4.add(newTextField("f2", "v4", Field.Store.YES))
        w1.addDocument(d4)
        w1.close()
        return dir1
    }

    private fun getDir2(random: Random): Directory {
        val dir2 = newDirectory()
        val w2 =
            IndexWriter(
                dir2,
                newIndexWriterConfig(MockAnalyzer(random)).setMergePolicy(NoMergePolicy.INSTANCE),
            )
        val d1 = Document()
        d1.add(newTextField("f3", "v1", Field.Store.YES))
        d1.add(newTextField("f4", "v1", Field.Store.YES))
        w2.addDocument(d1)
        w2.commit()
        val d2 = Document()
        d2.add(newTextField("f3", "v2", Field.Store.YES))
        d2.add(newTextField("f4", "v2", Field.Store.YES))
        w2.addDocument(d2)
        val d3 = Document()
        d3.add(newTextField("f3", "v3", Field.Store.YES))
        d3.add(newTextField("f4", "v3", Field.Store.YES))
        w2.addDocument(d3)
        w2.commit()
        val d4 = Document()
        d4.add(newTextField("f3", "v4", Field.Store.YES))
        d4.add(newTextField("f4", "v4", Field.Store.YES))
        w2.addDocument(d4)
        w2.close()
        return dir2
    }

    private fun getInvalidStructuredDir2(random: Random): Directory {
        val dir2 = newDirectory()
        val w2 =
            IndexWriter(
                dir2,
                newIndexWriterConfig(MockAnalyzer(random)).setMergePolicy(NoMergePolicy.INSTANCE),
            )
        val d1 = Document()
        d1.add(newTextField("f3", "v1", Field.Store.YES))
        d1.add(newTextField("f4", "v1", Field.Store.YES))
        w2.addDocument(d1)
        w2.commit()
        val d2 = Document()
        d2.add(newTextField("f3", "v2", Field.Store.YES))
        d2.add(newTextField("f4", "v2", Field.Store.YES))
        w2.addDocument(d2)
        w2.commit()
        val d3 = Document()
        d3.add(newTextField("f3", "v3", Field.Store.YES))
        d3.add(newTextField("f4", "v3", Field.Store.YES))
        w2.addDocument(d3)
        val d4 = Document()
        d4.add(newTextField("f3", "v4", Field.Store.YES))
        d4.add(newTextField("f4", "v4", Field.Store.YES))
        w2.addDocument(d4)
        w2.close()
        return dir2
    }
}
