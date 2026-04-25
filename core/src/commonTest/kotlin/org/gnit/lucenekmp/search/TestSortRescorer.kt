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

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSortRescorer : LuceneTestCase() {
    private lateinit var searcher: IndexSearcher
    private lateinit var reader: DirectoryReader
    private lateinit var dir: Directory

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        dir = newDirectory()
        val iw =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setSimilarity(ClassicSimilarity()),
            )

        var doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        doc.add(newTextField("body", "some contents and more contents", Field.Store.NO))
        doc.add(NumericDocValuesField("popularity", 5))
        iw.addDocument(doc)

        doc = Document()
        doc.add(newStringField("id", "2", Field.Store.YES))
        doc.add(newTextField("body", "another document with different contents", Field.Store.NO))
        doc.add(NumericDocValuesField("popularity", 20))
        iw.addDocument(doc)

        doc = Document()
        doc.add(newStringField("id", "3", Field.Store.YES))
        doc.add(newTextField("body", "crappy contents", Field.Store.NO))
        doc.add(NumericDocValuesField("popularity", 2))
        iw.addDocument(doc)

        reader = iw.reader
        searcher = IndexSearcher(reader)
        // TODO: fix this test to not be so flaky and use newSearcher
        searcher.similarity = ClassicSimilarity()
        iw.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasic() {

        // create a sort field and sort by it (reverse order)
        val query = TermQuery(Term("body", "contents"))
        val r: IndexReader = searcher.indexReader

        // Just first pass query
        var hits = searcher.search(query, 10)
        assertEquals(3, hits.totalHits.value)
        assertEquals("3", r.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", r.storedFields().document(hits.scoreDocs[1].doc).get("id"))
        assertEquals("2", r.storedFields().document(hits.scoreDocs[2].doc).get("id"))

        // Now, rescore:
        val sort = Sort(SortField("popularity", SortField.Type.INT, true))
        val rescorer: Rescorer = SortRescorer(sort)
        hits = rescorer.rescore(searcher, hits, 10)
        assertEquals(3, hits.totalHits.value)
        assertEquals("2", r.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", r.storedFields().document(hits.scoreDocs[1].doc).get("id"))
        assertEquals("3", r.storedFields().document(hits.scoreDocs[2].doc).get("id"))

        val expl =
            rescorer
                .explain(
                    searcher,
                    searcher.explain(query, hits.scoreDocs[0].doc),
                    hits.scoreDocs[0].doc,
                )
                .toString()

        // Confirm the explanation breaks out the individual
        // sort fields:
        assertTrue(expl.contains("= sort field <int: \"popularity\">! value=20"), expl)

        // Confirm the explanation includes first pass details:
        assertTrue(expl.contains("= first pass score"))
        assertTrue(expl.contains("body:contents in"))
    }

    @Test
    @Throws(Exception::class)
    fun testDoubleValuesSourceSort() {
        // create a sort field and sort by it (reverse order)
        val query = TermQuery(Term("body", "contents"))
        val r: IndexReader = searcher.indexReader

        // Just first pass query
        var hits = searcher.search(query, 10)
        assertEquals(3, hits.totalHits.value)
        assertEquals("3", r.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", r.storedFields().document(hits.scoreDocs[1].doc).get("id"))
        assertEquals("2", r.storedFields().document(hits.scoreDocs[2].doc).get("id"))

        val source = DoubleValuesSource.fromLongField("popularity")

        // Now, rescore:
        val sort = Sort(source.getSortField(true))
        val rescorer: Rescorer = SortRescorer(sort)
        hits = rescorer.rescore(searcher, hits, 10)
        assertEquals(3, hits.totalHits.value)
        assertEquals("2", r.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", r.storedFields().document(hits.scoreDocs[1].doc).get("id"))
        assertEquals("3", r.storedFields().document(hits.scoreDocs[2].doc).get("id"))

        val expl =
            rescorer
                .explain(
                    searcher,
                    searcher.explain(query, hits.scoreDocs[0].doc),
                    hits.scoreDocs[0].doc,
                )
                .toString()

        // Confirm the explanation breaks out the individual
        // sort fields:
        assertTrue(expl.contains("= sort field <double(popularity)>! value=20.0"), expl)

        // Confirm the explanation includes first pass details:
        assertTrue(expl.contains("= first pass score"))
        assertTrue(expl.contains("body:contents in"))
    }

    @Test
    @Throws(Exception::class)
    fun testRandom() {
        val dir = newDirectory()
        val numDocs = atLeast(1000)
        val w = RandomIndexWriter(random(), dir)

        val idToNum = IntArray(numDocs)
        val maxValue = TestUtil.nextInt(random(), 10, 1000000)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.YES))
            val numTokens = TestUtil.nextInt(random(), 1, 10)
            val b = StringBuilder()
            for (j in 0 until numTokens) {
                b.append("a ")
            }
            doc.add(newTextField("field", b.toString(), Field.Store.NO))
            idToNum[i] = random().nextInt(maxValue)
            doc.add(NumericDocValuesField("num", idToNum[i].toLong()))
            w.addDocument(doc)
        }
        val r = w.reader
        w.close()

        val s = newSearcher(r)
        val numHits = TestUtil.nextInt(random(), 1, numDocs)
        val reverse = random().nextBoolean()

        val hits = s.search(TermQuery(Term("field", "a")), numHits)

        val rescorer: Rescorer =
            SortRescorer(Sort(SortField("num", SortField.Type.INT, reverse)))
        val hits2 = rescorer.rescore(s, hits, numHits)

        val expected = Array(numHits) { i -> hits.scoreDocs[i].doc }

        val reverseInt = if (reverse) -1 else 1

        expected.sortWith(
            Comparator<Int> { a, b ->
                try {
                    val av = idToNum[r.storedFields().document(a).get("id")!!.toInt()]
                    val bv = idToNum[r.storedFields().document(b).get("id")!!.toInt()]
                    if (av < bv) {
                        -reverseInt
                    } else if (bv < av) {
                        reverseInt
                    } else {
                        // Tie break by docID
                        a - b
                    }
                } catch (ioe: IOException) {
                    throw RuntimeException(ioe)
                }
            }
        )

        var fail = false
        for (i in 0 until numHits) {
            fail = fail or (expected[i] != hits2.scoreDocs[i].doc)
        }
        assertFalse(fail)

        r.close()
        dir.close()
    }
}
