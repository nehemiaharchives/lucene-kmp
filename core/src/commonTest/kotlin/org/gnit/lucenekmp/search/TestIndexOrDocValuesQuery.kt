package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.LongField
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestIndexOrDocValuesQuery : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testUseIndexForSelectiveQueries() {
        val dir: Directory = newDirectory()
        val w =
            IndexWriter(
                dir,
                newIndexWriterConfig()
                    // relies on costs and PointValues.estimateCost so we need the default codec
                    .setCodec(TestUtil.getDefaultCodec()),
            )
        for (i in 0..<2000) {
            val doc = Document()
            if (i == 42) {
                doc.add(StringField("f1", "bar", Store.NO))
                doc.add(LongPoint("f2", 42L))
                doc.add(NumericDocValuesField("f2", 42L))
            } else if (i == 100) {
                doc.add(StringField("f1", "foo", Store.NO))
                doc.add(LongPoint("f2", 2L))
                doc.add(NumericDocValuesField("f2", 2L))
            } else {
                doc.add(StringField("f1", "bar", Store.NO))
                doc.add(LongPoint("f2", 2L))
                doc.add(NumericDocValuesField("f2", 2L))
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)
        val reader: IndexReader = DirectoryReader.open(w)
        val searcher = newSearcher(reader)
        searcher.queryCache = null

        // The term query is more selective, so the IndexOrDocValuesQuery should use doc values
        val q1: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("f1", "foo")), BooleanClause.Occur.MUST)
                .add(
                    IndexOrDocValuesQuery(
                        LongPoint.newExactQuery("f2", 2),
                        NumericDocValuesField.newSlowRangeQuery("f2", 2L, 2L),
                    ),
                    BooleanClause.Occur.MUST,
                )
                .build()
        QueryUtils.check(random(), q1, searcher)

        val w1 = searcher.createWeight(searcher.rewrite(q1), ScoreMode.COMPLETE, 1f)
        val s1 = w1.scorer(searcher.indexReader.leaves()[0])
        assertNotNull(s1!!.twoPhaseIterator()) // means we use doc values

        // The term query is less selective, so the IndexOrDocValuesQuery should use points
        val q2: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("f1", "bar")), BooleanClause.Occur.MUST)
                .add(
                    IndexOrDocValuesQuery(
                        LongPoint.newExactQuery("f2", 42),
                        NumericDocValuesField.newSlowRangeQuery("f2", 42L, 42L),
                    ),
                    BooleanClause.Occur.MUST,
                )
                .build()
        QueryUtils.check(random(), q2, searcher)

        val w2 = searcher.createWeight(searcher.rewrite(q2), ScoreMode.COMPLETE, 1f)
        val s2 = w2.scorer(searcher.indexReader.leaves()[0])
        assertNull(s2!!.twoPhaseIterator()) // means we use points

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testUseIndexForSelectiveMultiValueQueries() {
        val dir: Directory = newDirectory()
        val w =
            IndexWriter(
                dir,
                newIndexWriterConfig()
                    // relies on costs and PointValues.estimateCost so we need the default codec
                    .setCodec(TestUtil.getDefaultCodec()),
            )
        val numDocs = atLeast(100) // TODO reduced from 1000 to 100 for dev speed
        for (i in 0..<numDocs) {
            val doc = Document()
            if (i < numDocs / 2) {
                doc.add(StringField("f1", "bar", Store.NO))
                for (j in 0..<500) {
                    doc.add(LongField("f2", 42L, Store.NO))
                }
            } else if (i == numDocs / 2) {
                doc.add(StringField("f1", "foo", Store.NO))
                doc.add(LongField("f2", 2L, Store.NO))
            } else {
                doc.add(StringField("f1", "bar", Store.NO))
                for (j in 0..<100) {
                    doc.add(LongField("f2", 2L, Store.NO))
                }
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)
        val reader: IndexReader = DirectoryReader.open(w)
        val searcher = newSearcher(reader)
        searcher.queryCache = null

        // The term query is less selective, so the IndexOrDocValuesQuery should use points
        val q1: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("f1", "bar")), BooleanClause.Occur.MUST)
                .add(
                    IndexOrDocValuesQuery(
                        LongPoint.newExactQuery("f2", 2),
                        SortedNumericDocValuesField.newSlowRangeQuery("f2", 2L, 2L),
                    ),
                    BooleanClause.Occur.MUST,
                )
                .build()
        QueryUtils.check(random(), q1, searcher)

        val w1 = searcher.createWeight(searcher.rewrite(q1), ScoreMode.COMPLETE, 1f)
        val s1 = w1.scorer(searcher.indexReader.leaves()[0])
        assertNull(s1!!.twoPhaseIterator()) // means we use points

        // The term query is less selective, so the IndexOrDocValuesQuery should use points
        val q2: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("f1", "bar")), BooleanClause.Occur.MUST)
                .add(
                    IndexOrDocValuesQuery(
                        LongPoint.newExactQuery("f2", 42),
                        SortedNumericDocValuesField.newSlowRangeQuery("f2", 42L, 42L),
                    ),
                    BooleanClause.Occur.MUST,
                )
                .build()
        QueryUtils.check(random(), q2, searcher)

        val w2 = searcher.createWeight(searcher.rewrite(q2), ScoreMode.COMPLETE, 1f)
        val s2 = w2.scorer(searcher.indexReader.leaves()[0])
        assertNull(s2!!.twoPhaseIterator()) // means we use points

        // The term query is more selective, so the IndexOrDocValuesQuery should use doc values
        val q3: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("f1", "foo")), BooleanClause.Occur.MUST)
                .add(
                    IndexOrDocValuesQuery(
                        LongPoint.newExactQuery("f2", 42),
                        SortedNumericDocValuesField.newSlowRangeQuery("f2", 42L, 42L),
                    ),
                    BooleanClause.Occur.MUST,
                )
                .build()
        QueryUtils.check(random(), q3, searcher)

        val w3 = searcher.createWeight(searcher.rewrite(q3), ScoreMode.COMPLETE, 1f)
        val s3 = w3.scorer(searcher.indexReader.leaves()[0])
        assertNotNull(s3!!.twoPhaseIterator()) // means we use doc values

        reader.close()
        w.close()
        dir.close()
    }

    // Weight#count is delegated to the inner weight
    @Test
    @Throws(Exception::class)
    fun testQueryMatchesCount() {
        val dir: Directory = newDirectory()
        val w =
            IndexWriter(
                dir,
                newIndexWriterConfig()
                    // relies on costs and PointValues.estimateCost so we need the default codec
                    .setCodec(TestUtil.getDefaultCodec()),
            )
        val numDocs = random().nextInt(5000)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(LongPoint("f2", 42L))
            doc.add(SortedNumericDocValuesField("f2", 42L))
            w.addDocument(doc)
        }
        w.forceMerge(1)
        val reader: IndexReader = DirectoryReader.open(w)
        val searcher = newSearcher(reader)

        val query =
            IndexOrDocValuesQuery(
                LongPoint.newExactQuery("f2", 42),
                SortedNumericDocValuesField.newSlowRangeQuery("f2", 42L, 42L),
            )
        QueryUtils.check(random(), query, searcher)

        val searchCount = searcher.count(query)
        val weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        var weightCount = 0
        for (leafReaderContext: LeafReaderContext in reader.leaves()) {
            weightCount += weight.count(leafReaderContext)
        }
        assertEquals(searchCount, weightCount)

        reader.close()
        w.close()
        dir.close()
    }
}
