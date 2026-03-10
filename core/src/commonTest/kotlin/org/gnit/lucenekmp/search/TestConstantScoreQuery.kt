package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * This class only tests some basic functionality in CSQ, the main parts are mostly tested by
 * MultiTermQuery tests, explanations seems to be tested in TestExplanations!
 */
class TestConstantScoreQuery : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testCSQ() {
        val q1: Query = ConstantScoreQuery(TermQuery(Term("a", "b")))
        val q2: Query = ConstantScoreQuery(TermQuery(Term("a", "c")))
        val q3: Query =
            ConstantScoreQuery(
                TermRangeQuery.newStringRange(
                    "a",
                    "b",
                    "c",
                    includeLower = true,
                    includeUpper = true,
                )
            )
        QueryUtils.check(q1)
        QueryUtils.check(q2)
        QueryUtils.checkEqual(q1, q1)
        QueryUtils.checkEqual(q2, q2)
        QueryUtils.checkEqual(q3, q3)
        QueryUtils.checkUnequal(q1, q2)
        QueryUtils.checkUnequal(q2, q3)
        QueryUtils.checkUnequal(q1, q3)
        QueryUtils.checkUnequal(q1, TermQuery(Term("a", "b")))
    }

    // a query for which other queries don't have special rewrite rules
    private class QueryWrapper(private val `in`: Query) : Query() {
        override fun toString(field: String?): String {
            return "MockQuery"
        }

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return `in`.createWeight(searcher, scoreMode, boost)
        }

        override fun visit(visitor: QueryVisitor) {
            `in`.visit(visitor)
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && `in` == (other as QueryWrapper).`in`
        }

        override fun hashCode(): Int {
            return 31 * classHash() + `in`.hashCode()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testConstantScoreQueryAndFilter() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        var doc = Document()
        doc.add(newStringField("field", "a", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("field", "b", Field.Store.NO))
        w.addDocument(doc)
        val r = w.getReader(applyDeletions = true, writeAllDeletes = false)
        w.close()

        val filterB: Query = QueryWrapper(TermQuery(Term("field", "b")))
        var query: Query = ConstantScoreQuery(filterB)

        val s = newSearcher(r)
        var filtered =
            BooleanQuery.Builder().add(query, Occur.MUST).add(filterB, Occur.FILTER).build()
        assertEquals(1, s.count(filtered)) // Query for field:b, Filter field:b

        val filterA: Query = QueryWrapper(TermQuery(Term("field", "a")))
        query = ConstantScoreQuery(filterA)

        filtered = BooleanQuery.Builder().add(query, Occur.MUST).add(filterB, Occur.FILTER).build()
        assertEquals(0, s.count(filtered)) // Query field:b, Filter field:a

        r.close()
        d.close()
    }

    @Test
    @Throws(IOException::class)
    fun testPropagatesApproximations() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = newTextField("field", "a b", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        w.commit()

        val reader = w.getReader(applyDeletions = true, writeAllDeletes = false)
        val searcher = newSearcher(reader)
        searcher.queryCache = null // to still have approximations

        val pq = PhraseQuery("field", "a", "b")

        val q = searcher.rewrite(ConstantScoreQuery(pq))

        val weight = searcher.createWeight(q, ScoreMode.COMPLETE, 1f)
        val scorer = weight.scorer(searcher.indexReader.leaves()[0])!!
        assertNotNull(scorer.twoPhaseIterator())

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRewriteBubblesUpMatchNoDocsQuery() {
        val searcher = newSearcher(MultiReader())

        val query: Query = ConstantScoreQuery(MatchNoDocsQuery())
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))
    }
}
