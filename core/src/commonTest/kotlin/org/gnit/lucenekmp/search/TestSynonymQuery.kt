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
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.MIN_NORMAL
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestSynonymQuery : LuceneTestCase() {
    @Test
    fun testEquals() {
        QueryUtils.checkEqual(
            SynonymQuery.Builder("foo").build(),
            SynonymQuery.Builder("foo").build()
        )
        QueryUtils.checkEqual(
            SynonymQuery.Builder("foo").addTerm(Term("foo", "bar")).build(),
            SynonymQuery.Builder("foo").addTerm(Term("foo", "bar")).build()
        )

        QueryUtils.checkEqual(
            SynonymQuery.Builder("a")
                .addTerm(Term("a", "a"))
                .addTerm(Term("a", "b"))
                .build(),
            SynonymQuery.Builder("a")
                .addTerm(Term("a", "b"))
                .addTerm(Term("a", "a"))
                .build()
        )

        QueryUtils.checkEqual(
            SynonymQuery.Builder("field")
                .addTerm(Term("field", "b"), 0.4f)
                .addTerm(Term("field", "c"), 0.2f)
                .addTerm(Term("field", "d"))
                .build(),
            SynonymQuery.Builder("field")
                .addTerm(Term("field", "b"), 0.4f)
                .addTerm(Term("field", "c"), 0.2f)
                .addTerm(Term("field", "d"))
                .build()
        )

        QueryUtils.checkUnequal(
            SynonymQuery.Builder("field").addTerm(Term("field", "a"), 0.4f).build(),
            SynonymQuery.Builder("field").addTerm(Term("field", "b"), 0.4f).build()
        )

        QueryUtils.checkUnequal(
            SynonymQuery.Builder("field").addTerm(Term("field", "a"), 0.2f).build(),
            SynonymQuery.Builder("field").addTerm(Term("field", "a"), 0.4f).build()
        )

        QueryUtils.checkUnequal(
            SynonymQuery.Builder("field1").addTerm(Term("field1", "b"), 0.4f).build(),
            SynonymQuery.Builder("field2").addTerm(Term("field2", "b"), 0.4f).build()
        )
    }

    @Test
    fun testHashCode() {
        val q0: Query = SynonymQuery.Builder("field1").addTerm(Term("field1", "a"), 0.4f).build()
        val q1: Query = SynonymQuery.Builder("field1").addTerm(Term("field1", "a"), 0.4f).build()
        val q2: Query = SynonymQuery.Builder("field2").addTerm(Term("field2", "a"), 0.4f).build()

        assertEquals(q0.hashCode(), q1.hashCode())
        assertNotEquals(q0.hashCode(), q2.hashCode())
    }

    @Test
    fun testGetField() {
        val query = SynonymQuery.Builder("field1").addTerm(Term("field1", "a")).build()
        assertEquals("field1", query.field)
    }

    @Test
    fun testBogusParams() {
        expectThrows(IllegalArgumentException::class) {
            SynonymQuery.Builder("field1")
                .addTerm(Term("field1", "a"))
                .addTerm(Term("field2", "b"))
        }

        expectThrows(IllegalArgumentException::class) {
            SynonymQuery.Builder("field1").addTerm(Term("field1", "a"), 1.3f)
        }

        expectThrows(IllegalArgumentException::class) {
            SynonymQuery.Builder("field1").addTerm(Term("field1", "a"), Float.NaN)
        }

        expectThrows(IllegalArgumentException::class) {
            SynonymQuery.Builder("field1")
                .addTerm(Term("field1", "a"), Float.POSITIVE_INFINITY)
        }

        expectThrows(IllegalArgumentException::class) {
            SynonymQuery.Builder("field1")
                .addTerm(Term("field1", "a"), Float.NEGATIVE_INFINITY)
        }

        expectThrows(IllegalArgumentException::class) {
            SynonymQuery.Builder("field1").addTerm(Term("field1", "a"), -0.3f)
        }

        expectThrows(IllegalArgumentException::class) {
            SynonymQuery.Builder("field1").addTerm(Term("field1", "a"), 0f)
        }

        expectThrows(IllegalArgumentException::class) {
            SynonymQuery.Builder("field1").addTerm(Term("field1", "a"), -0f)
        }

        expectThrows(NullPointerException::class) {
            SynonymQuery.Builder(null as String).addTerm(Term("field1", "a"), -0f)
        }

        expectThrows(NullPointerException::class) {
            SynonymQuery.Builder(null as String).build()
        }
    }

    @Test
    fun testToString() {
        assertEquals("Synonym()", SynonymQuery.Builder("foo").build().toString())
        val t1 = Term("foo", "bar")
        assertEquals(
            "Synonym(foo:bar)",
            SynonymQuery.Builder("foo").addTerm(t1).build().toString()
        )
        val t2 = Term("foo", "baz")
        assertEquals(
            "Synonym(foo:bar foo:baz)",
            SynonymQuery.Builder("foo").addTerm(t1).addTerm(t2).build().toString()
        )
    }

    @Test
    @Throws(IOException::class)
    fun testScores() {
        doTestScores(1)
        doTestScores(Int.MAX_VALUE)
    }

    @Throws(IOException::class)
    private fun doTestScores(totalHitsThreshold: Int) {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        var doc = Document()
        doc.add(StringField("f", "a", Store.NO))
        w.addDocument(doc)

        doc = Document()
        doc.add(StringField("f", "b", Store.NO))
        for (i in 0..<10) {
            w.addDocument(doc)
        }
        val boost = if (random().nextBoolean()) random().nextFloat() else 1f
        val reader: IndexReader = w.reader
        val searcher = newSearcher(reader)
        val query =
            SynonymQuery.Builder("f")
                .addTerm(Term("f", "a"), if (boost == 0f) 1f else boost)
                .addTerm(Term("f", "b"), if (boost == 0f) 1f else boost)
                .build()

        val collectorManager =
            TopScoreDocCollectorManager(
                minOf(reader.numDocs(), totalHitsThreshold),
                totalHitsThreshold
            )
        val topDocs = searcher.search(query, collectorManager)
        if (topDocs.totalHits.value < totalHitsThreshold) {
            assertEquals(TotalHits(11, TotalHits.Relation.EQUAL_TO), topDocs.totalHits)
        } else {
            assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)
        }
        // All docs must have the same score
        for (i in topDocs.scoreDocs.indices) {
            assertEquals(topDocs.scoreDocs[0].score, topDocs.scoreDocs[i].score, 0.0f)
        }

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testBoosts() {
        doTestBoosts(1)
        doTestBoosts(Int.MAX_VALUE)
    }

    @Throws(IOException::class)
    fun doTestBoosts(totalHitsThreshold: Int) {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setOmitNorms(true)
        doc.add(Field("f", "c", ft))
        w.addDocument(doc)
        for (i in 0..<10) {
            doc.clear()
            doc.add(Field("f", "a a a a", ft))
            w.addDocument(doc)
            if (i % 2 == 0) {
                doc.clear()
                doc.add(Field("f", "b b", ft))
                w.addDocument(doc)
            } else {
                doc.clear()
                doc.add(Field("f", "a a b", ft))
                w.addDocument(doc)
            }
        }
        doc.clear()
        doc.add(Field("f", "c", ft))
        w.addDocument(doc)
        val reader: IndexReader = w.reader
        val searcher = newSearcher(reader)
        val query =
            SynonymQuery.Builder("f")
                .addTerm(Term("f", "a"), 0.25f)
                .addTerm(Term("f", "b"), 0.5f)
                .addTerm(Term("f", "c"))
                .build()

        val collectorManager =
            TopScoreDocCollectorManager(
                minOf(reader.numDocs(), totalHitsThreshold),
                totalHitsThreshold
            )
        val topDocs = searcher.search(query, collectorManager)
        if (topDocs.totalHits.value < totalHitsThreshold) {
            assertEquals(TotalHits.Relation.EQUAL_TO, topDocs.totalHits.relation)
            assertEquals(22, topDocs.totalHits.value)
        } else {
            assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)
        }
        // All docs must have the same score
        for (i in topDocs.scoreDocs.indices) {
            assertEquals(topDocs.scoreDocs[0].score, topDocs.scoreDocs[i].score, 0.0f)
        }

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMergeImpacts() {
        val impacts1 = DummyImpactsEnum()
        impacts1.reset(
            42,
            arrayOf(
                arrayOf(Impact(3, 10), Impact(5, 12), Impact(8, 13)),
                arrayOf(Impact(5, 11), Impact(8, 13), Impact(12, 14))
            ),
            intArrayOf(110, 945)
        )
        val impacts2 = DummyImpactsEnum()
        impacts2.reset(
            45,
            arrayOf(
                arrayOf(Impact(2, 10), Impact(6, 13)),
                arrayOf(Impact(3, 9), Impact(5, 11), Impact(7, 13))
            ),
            intArrayOf(90, 1000)
        )

        val mergedImpacts =
            SynonymQuery.mergeImpacts(arrayOf(impacts1, impacts2), floatArrayOf(1f, 1f))
        assertEquals(
            arrayOf(
                arrayOf(Impact(5, 10), Impact(7, 12), Impact(14, 13)),
                arrayOf(Impact(Int.MAX_VALUE, 1))
            ),
            intArrayOf(90, 1000),
            mergedImpacts.impacts
        )

        val mergedBoostedImpacts =
            SynonymQuery.mergeImpacts(arrayOf(impacts1, impacts2), floatArrayOf(0.3f, 0.9f))
        assertEquals(
            arrayOf(
                arrayOf(Impact(3, 10), Impact(4, 12), Impact(9, 13)),
                arrayOf(Impact(Int.MAX_VALUE, 1))
            ),
            intArrayOf(90, 1000),
            mergedBoostedImpacts.impacts
        )

        // docID is > the first doIdUpTo of impacts1
        impacts2.reset(
            112,
            arrayOf(
                arrayOf(Impact(2, 10), Impact(6, 13)),
                arrayOf(Impact(3, 9), Impact(5, 11), Impact(7, 13))
            ),
            intArrayOf(150, 1000)
        )
        assertEquals(
            arrayOf(
                arrayOf(
                    Impact(3, 10), Impact(5, 12), Impact(8, 13)
                ), // same as impacts1
                arrayOf(Impact(3, 9), Impact(10, 11), Impact(15, 13), Impact(19, 14))
            ),
            intArrayOf(110, 945),
            mergedImpacts.impacts
        )

        assertEquals(
            arrayOf(
                arrayOf(
                    Impact(1, 10), Impact(2, 12), Impact(3, 13)
                ), // same as impacts1*boost
                arrayOf(Impact(3, 9), Impact(7, 11), Impact(10, 13), Impact(11, 14))
            ),
            intArrayOf(110, 945),
            mergedBoostedImpacts.impacts
        )
    }

    private fun assertEquals(impacts: Array<Array<Impact>>, docIdUpTo: IntArray, actual: Impacts) {
        assertEquals(impacts.size, actual.numLevels())
        for (i in impacts.indices) {
            assertEquals(docIdUpTo[i], actual.getDocIdUpTo(i))
            assertEquals(impacts[i].toList(), actual.getImpacts(i))
        }
    }

    private class DummyImpactsEnum : ImpactsEnum() {
        private var docIDValue = 0
        private lateinit var impactsValue: Array<Array<Impact>>
        private lateinit var docIdUpToValue: IntArray

        fun reset(docID: Int, impacts: Array<Array<Impact>>, docIdUpTo: IntArray) {
            docIDValue = docID
            impactsValue = impacts
            docIdUpToValue = docIdUpTo
        }

        override fun advanceShallow(target: Int) {
            throw UnsupportedOperationException()
        }

        override val impacts: Impacts
            get() =
                object : Impacts() {
                    override fun numLevels(): Int {
                        return impactsValue.size
                    }

                    override fun getDocIdUpTo(level: Int): Int {
                        return docIdUpToValue[level]
                    }

                    override fun getImpacts(level: Int): MutableList<Impact> {
                        return impactsValue[level].toMutableList()
                    }
                }

        override fun freq(): Int {
            throw UnsupportedOperationException()
        }

        override fun nextPosition(): Int {
            throw UnsupportedOperationException()
        }

        override fun startOffset(): Int {
            throw UnsupportedOperationException()
        }

        override fun endOffset(): Int {
            throw UnsupportedOperationException()
        }

        override val payload: BytesRef?
            get() {
                throw UnsupportedOperationException()
            }

        override fun docID(): Int {
            return docIDValue
        }

        override fun nextDoc(): Int {
            throw UnsupportedOperationException()
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }

        override fun cost(): Long {
            throw UnsupportedOperationException()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testRandomTopDocs() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val numDocs =
            if (TEST_NIGHTLY) {
                atLeast(128 * 8 * 8 * 3)
            } else {
                atLeast(100)
            } // at night, make sure some terms have skip data
        for (i in 0..<numDocs) {
            val doc = Document()
            val numValues = random().nextInt(1 shl random().nextInt(5))
            val start = random().nextInt(10)
            for (j in 0..<numValues) {
                val freq = TestUtil.nextInt(random(), 1, 1 shl random().nextInt(3))
                for (k in 0..<freq) {
                    doc.add(TextField("foo", (start + j).toString(), Store.NO))
                }
            }
            w.addDocument(doc)
        }
        val reader: IndexReader = DirectoryReader.open(w)
        w.close()
        val searcher = newSearcher(reader)

        for (term1 in 0..<15) {
            var term2: Int
            do {
                term2 = random().nextInt(15)
            } while (term1 == term2)
            val boost1 =
                if (random().nextBoolean()) max(random().nextFloat(), Float.MIN_NORMAL) else 1f
            val boost2 =
                if (random().nextBoolean()) max(random().nextFloat(), Float.MIN_NORMAL) else 1f
            val query =
                SynonymQuery.Builder("foo")
                    .addTerm(Term("foo", term1.toString()), boost1)
                    .addTerm(Term("foo", term2.toString()), boost2)
                    .build()

            var completeManager = TopScoreDocCollectorManager(10, Int.MAX_VALUE) // COMPLETE
            var topScoresManager = TopScoreDocCollectorManager(10, 1) // TOP_SCORES

            var complete = searcher.search(query, completeManager)
            var topScores = searcher.search(query, topScoresManager)
            CheckHits.checkEqual(query, complete.scoreDocs, topScores.scoreDocs)

            val filterTerm = random().nextInt(15)
            val filteredQuery =
                BooleanQuery.Builder()
                    .add(query, Occur.MUST)
                    .add(TermQuery(Term("foo", filterTerm.toString())), Occur.FILTER)
                    .build()

            completeManager = TopScoreDocCollectorManager(10, Int.MAX_VALUE) // COMPLETE
            topScoresManager = TopScoreDocCollectorManager(10, 1) // TOP_SCORES

            complete = searcher.search(filteredQuery, completeManager)
            topScores = searcher.search(filteredQuery, topScoresManager)
            CheckHits.checkEqual(query, complete.scoreDocs, topScores.scoreDocs)
        }
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRewrite() {
        val searcher = IndexSearcher(MultiReader())

        // zero length SynonymQuery is rewritten
        var q = SynonymQuery.Builder("f").build()
        assertTrue(q.getTerms().isEmpty())
        assertEquals(searcher.rewrite(q), MatchNoDocsQuery())

        // non-boosted single term SynonymQuery is rewritten
        q = SynonymQuery.Builder("f").addTerm(Term("f"), 1f).build()
        assertEquals(q.getTerms().size, 1)
        assertEquals(searcher.rewrite(q), TermQuery(Term("f")))

        // boosted single term SynonymQuery is not rewritten
        q = SynonymQuery.Builder("f").addTerm(Term("f"), 0.8f).build()
        assertEquals(q.getTerms().size, 1)
        assertEquals(searcher.rewrite(q), q)

        // multiple term SynonymQuery is not rewritten
        q = SynonymQuery.Builder("f").addTerm(Term("f"), 1f).addTerm(Term("f"), 1f).build()
        assertEquals(q.getTerms().size, 2)
        assertEquals(searcher.rewrite(q), q)
    }
}
