package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Bits
import kotlin.test.Test
import kotlin.test.assertTrue

class TestTimeLimitingBulkScorer : LuceneTestCase() {

    @Test
    fun testTimeLimitingBulkScorer() {
        val directory = ByteBuffersDirectory()
        val writer = IndexWriter(directory, IndexWriterConfig(MockAnalyzer(random())))
        val n = 10000
        for (i in 0 until n) {
            val d = Document()
            d.add(TextField("default", "ones ", Field.Store.YES))
            writer.addDocument(d)
        }
        writer.forceMerge(1)
        writer.commit()
        writer.close()

        val query = TermQuery(Term("default", "ones"))
        val directoryReader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(directoryReader)
        searcher.timeout = countingQueryTimeout(10)
        val top = searcher.search(query, n)
        val hits = top.scoreDocs
        assertTrue(
            hits.isNotEmpty() && hits.size < n && searcher.timedOut(),
            "Partial result and is aborted is true"
        )
        directoryReader.close()
        directory.close()
    }

    @Test
    fun testExponentialRate() {
        val MAX_DOCS = DocIdSetIterator.NO_MORE_DOCS - 1
        val bulkScorer = object : BulkScorer() {
            var expectedInterval = TimeLimitingBulkScorer.INTERVAL
            var lastMax = 0
            var lastInterval = 0

            override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
                val difference = max - min
                assertTrue(difference >= lastInterval, "Rate should only go up")
                assertTrue(lastMax == min, "Documents skipped")
                assertTrue(
                    if (max == MAX_DOCS) expectedInterval >= difference else expectedInterval == difference,
                    "Incorrect rate encountered"
                )

                lastMax = max
                lastInterval = difference

                expectedInterval = expectedInterval + expectedInterval / 2
                if (expectedInterval < 0) {
                    expectedInterval = lastInterval
                }
                return max
            }

            override fun cost(): Long {
                return 1
            }
        }

        val scorer = TimeLimitingBulkScorer(bulkScorer, object : QueryTimeout {
            override fun shouldExit(): Boolean = false
        })
        scorer.score(dummyCollector(), Bits.MatchAllBits(Int.MAX_VALUE), 0, MAX_DOCS)
    }

    private fun countingQueryTimeout(timeallowed: Int): QueryTimeout {
        return object : QueryTimeout {
            var counter = 0
            override fun shouldExit(): Boolean {
                counter++
                return counter == timeallowed
            }
        }
    }

    private fun dummyCollector(): LeafCollector {
        return object : LeafCollector {
            override var scorer: Scorable? = null
            override fun collect(doc: Int) {}
        }
    }
}

