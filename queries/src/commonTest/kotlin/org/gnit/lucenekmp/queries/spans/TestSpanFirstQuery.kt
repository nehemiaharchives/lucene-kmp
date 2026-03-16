package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSpanFirstQuery : LuceneTestCase() {
    @Test
    fun testStartPositions() {
        val dir = newDirectory()

        // mimic StopAnalyzer
        val stopSet = CharacterRunAutomaton(RegExp("the|a|of").toAutomaton())
        val analyzer = MockAnalyzer(random(), MockTokenizer.SIMPLE, true, stopSet)

        val writer = RandomIndexWriter(random(), dir, analyzer)
        val doc = Document()
        doc.add(newTextField("field", "the quick brown fox", Field.Store.NO))
        writer.addDocument(doc)
        val doc2 = Document()
        doc2.add(newTextField("field", "quick brown fox", Field.Store.NO))
        writer.addDocument(doc2)

        val reader: IndexReader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)

        // user queries on "starts-with quick"
        var sfq = SpanTestUtil.spanFirstQuery(SpanTestUtil.spanTermQuery("field", "quick"), 1)
        assertEquals(1L, searcher.search(sfq, 10).totalHits.value)

        // user queries on "starts-with the quick"
        val include = SpanTestUtil.spanFirstQuery(SpanTestUtil.spanTermQuery("field", "quick"), 2)
        sfq = SpanTestUtil.spanNotQuery(include, sfq)
        assertEquals(1L, searcher.search(sfq, 10).totalHits.value)

        writer.close()
        reader.close()
        dir.close()
    }
}
