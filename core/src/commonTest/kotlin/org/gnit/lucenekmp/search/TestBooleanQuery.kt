package org.gnit.lucenekmp.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil

/**
 * Ported from Lucene's TestBooleanQuery.java (partial).
 * TODO: Port remaining tests.
 */
class TestBooleanQuery : LuceneTestCase() {

    @Test
    fun testEquality() {
        val bq1Builder = BooleanQuery.Builder()
        bq1Builder.add(TermQuery(Term("field", "value1")), BooleanClause.Occur.SHOULD)
        bq1Builder.add(TermQuery(Term("field", "value2")), BooleanClause.Occur.SHOULD)
        val nested1 = BooleanQuery.Builder()
        nested1.add(TermQuery(Term("field", "nestedvalue1")), BooleanClause.Occur.SHOULD)
        nested1.add(TermQuery(Term("field", "nestedvalue2")), BooleanClause.Occur.SHOULD)
        bq1Builder.add(nested1.build(), BooleanClause.Occur.SHOULD)
        val bq1 = bq1Builder.build()

        val bq2Builder = BooleanQuery.Builder()
        bq2Builder.add(TermQuery(Term("field", "value1")), BooleanClause.Occur.SHOULD)
        bq2Builder.add(TermQuery(Term("field", "value2")), BooleanClause.Occur.SHOULD)
        val nested2 = BooleanQuery.Builder()
        nested2.add(TermQuery(Term("field", "nestedvalue1")), BooleanClause.Occur.SHOULD)
        nested2.add(TermQuery(Term("field", "nestedvalue2")), BooleanClause.Occur.SHOULD)
        bq2Builder.add(nested2.build(), BooleanClause.Occur.SHOULD)
        val bq2 = bq2Builder.build()

        assertEquals(bq1, bq2)
    }

    @Test
    fun testHashCodeIsStable() {
        val bq = BooleanQuery.Builder()
            .add(
                TermQuery(Term("foo", TestUtil.randomSimpleString(random()))),
                BooleanClause.Occur.SHOULD
            )
            .add(
                TermQuery(Term("foo", TestUtil.randomSimpleString(random()))),
                BooleanClause.Occur.SHOULD
            )
            .build()
        val hashCode = bq.hashCode()
        assertEquals(hashCode, bq.hashCode())
    }

    @Test
    fun testTooManyClauses() {
        val bq = BooleanQuery.Builder()
        for (i in 0 until IndexSearcher.maxClauseCount) {
            bq.add(TermQuery(Term("foo", "bar-$i")), BooleanClause.Occur.SHOULD)
        }
        assertFailsWith<IndexSearcher.TooManyClauses> {
            bq.add(TermQuery(Term("foo", "bar-MAX")), BooleanClause.Occur.SHOULD)
        }
    }
}

