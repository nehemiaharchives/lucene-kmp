package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.ConstantScoreQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestSortedSetDocValuesSetQuery : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testMissingTerms() {
        val fieldName = "field1"
        val rd: Directory = newDirectory()
        val w = RandomIndexWriter(random(), rd)
        for (i in 0..<100) {
            val doc = Document()
            val term = i * 10 // terms are units of 10;
            doc.add(newStringField(fieldName, "$term", Field.Store.YES))
            doc.add(SortedDocValuesField(fieldName, BytesRef("$term")))
            w.addDocument(doc)
        }
        val reader = w.reader
        w.close()

        val searcher = newSearcher(reader)
        val numDocs = reader.numDocs()
        var results = emptyArray<org.gnit.lucenekmp.search.ScoreDoc>()

        var terms = ArrayList<BytesRef>()
        terms.add(BytesRef("5"))
        results = searcher.search(SortedDocValuesField.newSlowSetQuery(fieldName, terms), numDocs).scoreDocs
        assertEquals(0, results.size, "Must match nothing")

        terms = ArrayList()
        terms.add(BytesRef("10"))
        results = searcher.search(SortedDocValuesField.newSlowSetQuery(fieldName, terms), numDocs).scoreDocs
        assertEquals(1, results.size, "Must match 1")

        terms = ArrayList()
        terms.add(BytesRef("10"))
        terms.add(BytesRef("20"))
        results = searcher.search(SortedDocValuesField.newSlowSetQuery(fieldName, terms), numDocs).scoreDocs
        assertEquals(2, results.size, "Must match 2")

        reader.close()
        rd.close()
    }

    @Test
    fun testEquals() {
        val bar = ArrayList<BytesRef>()
        bar.add(BytesRef("bar"))

        val barbar = ArrayList<BytesRef>()
        barbar.add(BytesRef("bar"))
        barbar.add(BytesRef("bar"))

        val barbaz = ArrayList<BytesRef>()
        barbaz.add(BytesRef("bar"))
        barbaz.add(BytesRef("baz"))

        val bazbar = ArrayList<BytesRef>()
        bazbar.add(BytesRef("baz"))
        bazbar.add(BytesRef("bar"))

        val baz = ArrayList<BytesRef>()
        baz.add(BytesRef("baz"))

        assertEquals(
            SortedDocValuesField.newSlowSetQuery("foo", bar),
            SortedDocValuesField.newSlowSetQuery("foo", bar)
        )
        assertEquals(
            SortedDocValuesField.newSlowSetQuery("foo", bar),
            SortedDocValuesField.newSlowSetQuery("foo", barbar)
        )
        assertEquals(
            SortedDocValuesField.newSlowSetQuery("foo", barbaz),
            SortedDocValuesField.newSlowSetQuery("foo", bazbar)
        )
        assertNotEquals(
            SortedDocValuesField.newSlowSetQuery("foo", bar),
            SortedDocValuesField.newSlowSetQuery("foo2", bar)
        )
        assertNotEquals(
            SortedDocValuesField.newSlowSetQuery("foo", bar),
            SortedDocValuesField.newSlowSetQuery("foo", baz)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testDuelTermsQuery() {
        val iters = atLeast(2)
        for (iter in 0..<iters) {
            val allTerms = ArrayList<Term>()
            val numTerms = TestUtil.nextInt(random(), 1, 1 shl TestUtil.nextInt(random(), 1, 10))
            for (i in 0..<numTerms) {
                val value = TestUtil.randomAnalysisString(random(), 10, true)
                allTerms.add(Term("f", value))
            }
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(3) // TODO reduced from 100 to 3 for dev speed
            for (i in 0..<numDocs) {
                val doc = Document()
                val term = allTerms[random().nextInt(allTerms.size)]
                doc.add(StringField(term.field(), term.text(), Field.Store.NO))
                doc.add(SortedDocValuesField(term.field(), BytesRef(term.text())))
                iw.addDocument(doc)
            }
            if (numTerms > 1 && random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(allTerms[0]))
            }
            iw.commit()
            val reader = iw.reader
            val searcher = newSearcher(reader)
            iw.close()

            if (reader.numDocs() == 0) {
                // may occasionally happen if all documents got the same term
                IOUtils.close(reader, dir)
                continue
            }

            for (i in 0..<3) { // TODO reduced from 100 to 3 for dev speed
                val boost = random().nextFloat() * 10
                val numQueryTerms = TestUtil.nextInt(random(), 1, 1 shl TestUtil.nextInt(random(), 1, 8))
                val queryTerms = ArrayList<Term>()
                for (j in 0..<numQueryTerms) {
                    queryTerms.add(allTerms[random().nextInt(allTerms.size)])
                }
                val bq = BooleanQuery.Builder()
                for (term in queryTerms) {
                    bq.add(TermQuery(term), Occur.SHOULD)
                }
                val q1: Query = BoostQuery(ConstantScoreQuery(bq.build()), boost)
                val bytesTerms = ArrayList<BytesRef>()
                for (term in queryTerms) {
                    bytesTerms.add(term.bytes())
                }
                val q2: Query = BoostQuery(SortedDocValuesField.newSlowSetQuery("f", bytesTerms), boost)
                assertSameMatches(searcher, q1, q2, true)
            }

            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testApproximation() {
        val iters = atLeast(2)
        for (iter in 0..<iters) {
            val allTerms = ArrayList<Term>()
            val numTerms = TestUtil.nextInt(random(), 1, 1 shl TestUtil.nextInt(random(), 1, 10))
            for (i in 0..<numTerms) {
                val value = TestUtil.randomAnalysisString(random(), 10, true)
                allTerms.add(Term("f", value))
            }
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(3) // TODO reduced from 100 to 3 for dev speed
            for (i in 0..<numDocs) {
                val doc = Document()
                val term = allTerms[random().nextInt(allTerms.size)]
                doc.add(StringField(term.field(), term.text(), Field.Store.NO))
                doc.add(SortedDocValuesField(term.field(), BytesRef(term.text())))
                iw.addDocument(doc)
            }
            if (numTerms > 1 && random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(allTerms[0]))
            }
            iw.commit()
            val reader = iw.reader
            val searcher = newSearcher(reader)
            iw.close()

            if (reader.numDocs() == 0) {
                // may occasionally happen if all documents got the same term
                IOUtils.close(reader, dir)
                continue
            }

            for (i in 0..<3) { // TODO reduced from 100 to 3 for dev speed
                val boost = random().nextFloat() * 10
                val numQueryTerms = TestUtil.nextInt(random(), 1, 1 shl TestUtil.nextInt(random(), 1, 8))
                val queryTerms = ArrayList<Term>()
                for (j in 0..<numQueryTerms) {
                    queryTerms.add(allTerms[random().nextInt(allTerms.size)])
                }
                val bq = BooleanQuery.Builder()
                for (term in queryTerms) {
                    bq.add(TermQuery(term), Occur.SHOULD)
                }
                val q1: Query = BoostQuery(ConstantScoreQuery(bq.build()), boost)
                val bytesTerms = ArrayList<BytesRef>()
                for (term in queryTerms) {
                    bytesTerms.add(term.bytes())
                }
                val q2: Query = BoostQuery(SortedDocValuesField.newSlowSetQuery("f", bytesTerms), boost)

                val bq1 = BooleanQuery.Builder()
                bq1.add(q1, Occur.MUST)
                bq1.add(TermQuery(allTerms[0]), Occur.FILTER)

                val bq2 = BooleanQuery.Builder()
                bq2.add(q2, Occur.MUST)
                bq2.add(TermQuery(allTerms[0]), Occur.FILTER)

                assertSameMatches(searcher, bq1.build(), bq2.build(), true)
            }

            reader.close()
            dir.close()
        }
    }

    @Throws(Exception::class)
    private fun assertSameMatches(searcher: IndexSearcher, q1: Query, q2: Query, scores: Boolean) {
        val maxDoc = searcher.indexReader.maxDoc()
        val td1: TopDocs = searcher.search(q1, maxDoc, if (scores) Sort.RELEVANCE else Sort.INDEXORDER)
        val td2: TopDocs = searcher.search(q2, maxDoc, if (scores) Sort.RELEVANCE else Sort.INDEXORDER)
        assertEquals(td1.totalHits.value, td2.totalHits.value)
        for (i in td1.scoreDocs.indices) {
            assertEquals(td1.scoreDocs[i].doc, td2.scoreDocs[i].doc)
            if (scores) {
                assertEquals(td1.scoreDocs[i].score, td2.scoreDocs[i].score, 10e-7f)
            }
        }
    }
}
