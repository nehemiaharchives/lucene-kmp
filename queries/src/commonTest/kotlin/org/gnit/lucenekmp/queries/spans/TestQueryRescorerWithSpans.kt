package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.QueryRescorer
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestQueryRescorerWithSpans : LuceneTestCase() {
    private fun getSearcher(r: IndexReader): IndexSearcher {
        val searcher = newSearcher(r)

        // We rely on more tokens = lower score:
        searcher.similarity = ClassicSimilarity()

        return searcher
    }

    @Test
    fun testBasic() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())

        var doc = Document()
        doc.add(newStringField("id", "0", Field.Store.YES))
        doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        // 1 extra token, but wizard and oz are close;
        doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO))
        w.addDocument(doc)
        val r: IndexReader = w.reader
        w.close()

        // Do ordinary BooleanQuery:
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term("field", "wizard")), BooleanClause.Occur.SHOULD)
        bq.add(TermQuery(Term("field", "oz")), BooleanClause.Occur.SHOULD)
        val searcher = getSearcher(r)

        val hits: TopDocs = searcher.search(bq.build(), 10)
        assertEquals(2L, hits.totalHits.value)
        assertEquals("0", searcher.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(hits.scoreDocs[1].doc).get("id"))

        // Resort using SpanNearQuery:
        val t1 = SpanTermQuery(Term("field", "wizard"))
        val t2 = SpanTermQuery(Term("field", "oz"))
        val snq = SpanNearQuery(arrayOf(t1, t2), 0, true)

        val hits3 = QueryRescorer.rescore(searcher, hits, snq, 2.0, 10)

        // Resorting changed the order:
        assertEquals(2L, hits3.totalHits.value)
        assertEquals("1", searcher.storedFields().document(hits3.scoreDocs[0].doc).get("id"))
        assertEquals("0", searcher.storedFields().document(hits3.scoreDocs[1].doc).get("id"))

        r.close()
        dir.close()
    }

    @Test
    fun testMissingSecondPassScore() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())

        var doc = Document()
        doc.add(newStringField("id", "0", Field.Store.YES))
        doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        // 1 extra token, but wizard and oz are close;
        doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO))
        w.addDocument(doc)
        val r: IndexReader = w.reader
        w.close()

        // Do ordinary BooleanQuery:
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term("field", "wizard")), BooleanClause.Occur.SHOULD)
        bq.add(TermQuery(Term("field", "oz")), BooleanClause.Occur.SHOULD)
        val searcher = getSearcher(r)

        val hits: TopDocs = searcher.search(bq.build(), 10)
        assertEquals(2L, hits.totalHits.value)
        assertEquals("0", searcher.storedFields().document(hits.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(hits.scoreDocs[1].doc).get("id"))

        // Resort using SpanNearQuery:
        val t1 = SpanTermQuery(Term("field", "wizard"))
        val t2 = SpanTermQuery(Term("field", "oz"))
        val snq = SpanNearQuery(arrayOf(t1, t2), 0, true)

        val hits3 = QueryRescorer.rescore(searcher, hits, snq, 2.0, 10)

        // Resorting changed the order:
        assertEquals(2L, hits3.totalHits.value)
        assertEquals("1", searcher.storedFields().document(hits3.scoreDocs[0].doc).get("id"))
        assertEquals("0", searcher.storedFields().document(hits3.scoreDocs[1].doc).get("id"))

        r.close()
        dir.close()
    }
}
