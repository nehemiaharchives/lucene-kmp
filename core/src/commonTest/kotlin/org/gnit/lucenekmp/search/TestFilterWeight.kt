package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestFilterWeight : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testDelegatesAbstractMethodsAndInheritedBulkScorer() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        iw.addDocument(Document())
        val reader = iw.getReader(true, false)
        val context = reader.leaves()[0]

        val query = DummyQuery()
        val inner = RecordingWeight(query)
        val filterWeight = object : FilterWeight(inner) {}

        assertSame(inner.explainResult, filterWeight.explain(context, 0))
        assertSame(inner.matchesResult, filterWeight.matches(context, 0))
        assertSame(inner.scorerSupplierInstance, filterWeight.scorerSupplier(context))
        assertFalse(filterWeight.isCacheable(context))

        val bulkScorer = filterWeight.bulkScorer(context)
        assertNotNull(bulkScorer)
        assertTrue(inner.scorerSupplierTopLevelSet)

        reader.close()
        iw.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testCountIsNotDelegated() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        iw.addDocument(Document())
        val reader = iw.getReader(true, false)
        val context = reader.leaves()[0]

        val query = DummyQuery()
        val inner = RecordingWeight(query)
        val filterWeight = object : FilterWeight(inner) {}

        assertEquals(-1, filterWeight.count(context))
        assertFalse(inner.countCalled)

        reader.close()
        iw.close()
        dir.close()
    }

    private class DummyQuery : Query() {
        override fun toString(field: String?): String {
            return "DummyQuery"
        }

        override fun visit(visitor: QueryVisitor) {
            visitor.visitLeaf(this)
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other)
        }

        override fun hashCode(): Int {
            return classHash()
        }

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            throw UnsupportedOperationException()
        }
    }

    private class RecordingWeight(query: Query) : Weight(query) {
        val explainResult: Explanation = Explanation.match(2f, "explain")
        val matchesResult: Matches = MatchesUtils.MATCH_WITH_NO_TERMS
        var countCalled: Boolean = false
        var scorerSupplierTopLevelSet: Boolean = false

        val scorerSupplierInstance: ScorerSupplier =
            object : ScorerSupplier() {
                override fun get(leadCost: Long): Scorer {
                    return ConstantScoreScorer(1f, ScoreMode.COMPLETE, DocIdSetIterator.all(1))
                }

                override fun cost(): Long {
                    return 1
                }

                override fun setTopLevelScoringClause() {
                    scorerSupplierTopLevelSet = true
                }
            }

        override fun isCacheable(ctx: org.gnit.lucenekmp.index.LeafReaderContext): Boolean {
            return false
        }

        override fun explain(context: org.gnit.lucenekmp.index.LeafReaderContext, doc: Int): Explanation {
            return explainResult
        }

        override fun matches(context: org.gnit.lucenekmp.index.LeafReaderContext, doc: Int): Matches {
            return matchesResult
        }

        override fun scorerSupplier(context: org.gnit.lucenekmp.index.LeafReaderContext): ScorerSupplier {
            return scorerSupplierInstance
        }

        override fun count(context: org.gnit.lucenekmp.index.LeafReaderContext): Int {
            countCalled = true
            return 1
        }
    }
}
