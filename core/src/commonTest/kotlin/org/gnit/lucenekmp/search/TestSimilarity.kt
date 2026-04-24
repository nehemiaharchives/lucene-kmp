package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSimilarity : LuceneTestCase() {
    private class SimpleSimilarity : ClassicSimilarity() {
        override fun lengthNorm(length: Int): Float {
            return 1.0f
        }

        override fun tf(freq: Float): Float {
            return freq
        }

        override fun idf(docFreq: Long, docCount: Long): Float {
            return 1.0f
        }

        override fun idfExplain(
            collectionStats: CollectionStatistics,
            stats: Array<TermStatistics>
        ): Explanation {
            return Explanation.match(1.0f, "Inexplicable")
        }
    }

    @Test
    fun testSimilarity() {
        val store = newDirectory()
        val writer = RandomIndexWriter(
            random(),
            store,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setSimilarity(SimpleSimilarity())
                .setMergePolicy(newMergePolicy(random(), false))
        )
        val d1 = Document()
        d1.add(newTextField("field", "a c", Field.Store.YES))
        
        val d2 = Document()
        d2.add(newTextField("field", "a c b", Field.Store.YES))

        writer.addDocument(d1)
        writer.addDocument(d2)
        val reader = writer.reader
        writer.close()

        val searcher = newSearcher(reader)
        searcher.similarity = SimpleSimilarity()

        val a = Term("field", "a")
        val b = Term("field", "b")
        val c = Term("field", "c")

        assertScore(searcher, TermQuery(b), 1.0f)

        val query = BooleanQuery.Builder().add(TermQuery(a), BooleanClause.Occur.SHOULD)
            .add(TermQuery(b), BooleanClause.Occur.SHOULD)
            .build()

        val queryCollectorManager: CollectorManager<SimpleCollector, Unit> =
            object : CollectorManager<SimpleCollector, Unit> {
                override fun newCollector(): SimpleCollector {
                    return object : ScoreAssertingCollector() {
                        private var base = 0

                        override fun collect(doc: Int) {
                            assertEquals(doc.toFloat() + base + 1, scorer!!.score(), 0.0f)
                        }

                        override fun doSetNextReader(context: org.gnit.lucenekmp.index.LeafReaderContext) {
                            base = context.docBase
                        }
                    }
                }

                override fun reduce(collectors: MutableCollection<SimpleCollector>): Unit {
                    return Unit
                }
            }
        searcher.search(query, queryCollectorManager)

        var pq = PhraseQuery(a.field(), a.bytes(), c.bytes())
        assertScore(searcher, pq, 1.0f)

        pq = PhraseQuery(2, a.field(), a.bytes(), b.bytes())
        assertScore(searcher, pq, 0.5f)

        reader.close()
        store.close()
    }

    private fun assertScore(searcher: IndexSearcher, q: Query, score: Float) {
        val collectorManager: CollectorManager<SimpleCollector, Unit> =
            object : CollectorManager<SimpleCollector, Unit> {
                override fun newCollector(): SimpleCollector {
                    return object : ScoreAssertingCollector() {
                        override fun collect(doc: Int) {
                            assertEquals(score, scorer!!.score(), 0.0f)
                        }
                    }
                }

                override fun reduce(collectors: MutableCollection<SimpleCollector>): Unit {
                    return Unit
                }
            }
        searcher.search(q, collectorManager)
    }

    private abstract class ScoreAssertingCollector : SimpleCollector() {
        override var scorer: Scorable? = null
        override var weight: Weight? = null

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE
        }
    }
}
