package org.gnit.lucenekmp.search

import kotlin.test.Test
import kotlin.test.Ignore
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

    @Ignore
    @Test
    fun testEqualityDoesNotDependOnOrder() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testEqualityOnDuplicateShouldClauses() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testEqualityOnDuplicateMustClauses() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testEqualityOnDuplicateFilterClauses() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testEqualityOnDuplicateMustNotClauses() {
        // TODO: port this test
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

    @Ignore
    @Test
    fun testNullOrSubScorer() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testDeMorgan() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testBS2DisjunctionNextVsAdvance() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testMinShouldMatchLeniency() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testFILTERClauseBehavesLikeMUST() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testFilterClauseDoesNotImpactScore() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testConjunctionPropagatesApproximations() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testDisjunctionPropagatesApproximations() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testBoostedScorerPropagatesApproximations() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testExclusionPropagatesApproximations() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testReqOptPropagatesApproximations() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testQueryMatchesCount() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testConjunctionMatchesCount() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testDisjunctionMatchesCount() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testTwoClauseTermDisjunctionCountOptimization() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testDisjunctionTwoClausesMatchesCountAndScore() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testDisjunctionRandomClausesMatchesCount() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testProhibitedMatchesCount() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testRandomBooleanQueryMatchesCount() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testToString() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testQueryVisitor() {
        // TODO: port this test
    }

    @Ignore
    @Test
    fun testClauseSetsImmutability() {
        // TODO: port this test
    }
}

