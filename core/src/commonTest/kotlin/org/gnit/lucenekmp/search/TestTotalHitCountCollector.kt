package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomizedTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTotalHitCountCollector : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val indexStore: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), indexStore)
        for (i in 0..4) {
            val doc = Document()
            doc.add(StringField("string", "a$i", Field.Store.NO))
            doc.add(StringField("string", "b$i", Field.Store.NO))
            writer.addDocument(doc)
        }
        val reader: IndexReader = writer.reader
        writer.close()

        val concurrency: Companion.Concurrency = RandomizedTest.randomFrom(Companion.Concurrency.entries.toTypedArray())
        val searcher: IndexSearcher = newSearcher(reader, true, true, concurrency)
        val collectorManager: TotalHitCountCollectorManager =
            TotalHitCountCollectorManager(searcher.slices)
        var totalHits: Int = searcher.search<TotalHitCountCollector, Int>(MatchAllDocsQuery(), collectorManager)
        assertEquals(5, totalHits)

        val query: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "a1")), Occur.SHOULD)
                .add(TermQuery(Term("string", "b3")), Occur.SHOULD)
                .build()
        totalHits = searcher.search<TotalHitCountCollector, Int>(query, collectorManager)
        assertEquals(2, totalHits)

        reader.close()
        indexStore.close()
    }
}
