package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TestBoostQuery : LuceneTestCase() {
    @Test
    fun testValidation() {
        var e =
            expectThrows(IllegalArgumentException::class) {
                BoostQuery(MatchAllDocsQuery(), -3f)
            }
        assertEquals("boost must be a positive float, got -3.0", e.message)

        e =
            expectThrows(IllegalArgumentException::class) {
                BoostQuery(MatchAllDocsQuery(), -0f)
            }
        assertEquals("boost must be a positive float, got -0.0", e.message)

        e =
            expectThrows(IllegalArgumentException::class) {
                BoostQuery(MatchAllDocsQuery(), Float.NaN)
            }
        assertEquals("boost must be a positive float, got NaN", e.message)
    }

    @Test
    fun testEquals() {
        val boost = random().nextFloat() * 3
        val q1 = BoostQuery(MatchAllDocsQuery(), boost)
        val q2 = BoostQuery(MatchAllDocsQuery(), boost)
        assertEquals(q1, q2)
        assertEquals(q1.boost, q2.boost, 0f)

        var boost2 = boost
        while (boost == boost2) {
            boost2 = random().nextFloat() * 3
        }
        val q3 = BoostQuery(MatchAllDocsQuery(), boost2)
        assertFalse(q1 == q3)
        assertFalse(q1.hashCode() == q3.hashCode())
    }

    @Test
    fun testToString() {
        assertEquals("(foo:bar)^2.0", BoostQuery(TermQuery(Term("foo", "bar")), 2f).toString())
        val bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .build()
        assertEquals("(foo:bar foo:baz)^2.0", BoostQuery(bq, 2f).toString())
    }

    @Test
    @Throws(IOException::class)
    fun testRewrite() {
        val searcher = IndexSearcher(MultiReader())

        var q: Query = BoostQuery(PhraseQuery("foo", "bar"), 2f)
        assertEquals(BoostQuery(TermQuery(Term("foo", "bar")), 2f), searcher.rewrite(q))

        q = BoostQuery(BoostQuery(MatchAllDocsQuery(), 3f), 2f)
        assertEquals(BoostQuery(MatchAllDocsQuery(), 6f), searcher.rewrite(q))

        q = BoostQuery(MatchAllDocsQuery(), 0f)
        assertEquals(BoostQuery(ConstantScoreQuery(MatchAllDocsQuery()), 0f), searcher.rewrite(q))
    }

    @Test
    @Throws(IOException::class)
    fun testRewriteBubblesUpMatchNoDocsQuery() {
        val searcher = newSearcher(MultiReader())

        var query: Query = BoostQuery(MatchNoDocsQuery(), 2f)
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))

        query = BoostQuery(MatchNoDocsQuery(), 0f)
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))
    }
}
