package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TestMaxClauseLimit : LuceneTestCase() {
    @Test
    fun testIllegalArgumentExceptionOnZero() {
        val current = IndexSearcher.maxClauseCount
        expectThrows(IllegalArgumentException::class) {
            IndexSearcher.maxClauseCount = 0
        }
        assertEquals(
            current,
            IndexSearcher.maxClauseCount,
            "attempt to change to 0 should have failed w/o modifying",
        )
    }

    @Test
    @Throws(IOException::class)
    fun testFlattenInnerDisjunctionsWithMoreThan1024Terms() {
        val searcher = newSearcher(MultiReader())

        val builder1024 = BooleanQuery.Builder()
        for (i in 0 until 1024) {
            builder1024.add(TermQuery(Term("foo", "bar-$i")), BooleanClause.Occur.SHOULD)
        }
        val inner = builder1024.build()
        val query =
            BooleanQuery.Builder()
                .add(inner, BooleanClause.Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), BooleanClause.Occur.SHOULD)
                .build()

        val e =
            expectThrows(IndexSearcher.TooManyClauses::class) {
                searcher.rewrite(query)
            }
        assertFalse(
            e is IndexSearcher.TooManyNestedClauses,
            "Should have been caught during flattening and not required full nested walk",
        )
    }

    @Test
    @Throws(IOException::class)
    fun testLargeTermsNestedFirst() {
        val searcher = newSearcher(MultiReader())
        val nestedBuilder = BooleanQuery.Builder()

        nestedBuilder.setMinimumNumberShouldMatch(5)

        for (i in 0 until 600) {
            nestedBuilder.add(TermQuery(Term("foo", "bar-$i")), BooleanClause.Occur.SHOULD)
        }
        val inner = nestedBuilder.build()
        val builderMixed =
            BooleanQuery.Builder()
                .add(inner, BooleanClause.Occur.SHOULD)

        builderMixed.setMinimumNumberShouldMatch(5)

        for (i in 0 until 600) {
            builderMixed.add(TermQuery(Term("foo", "bar")), BooleanClause.Occur.SHOULD)
        }

        val query = builderMixed.build()

        // Can't be flattened, but high clause count should still be cause during nested walk...
        expectThrows(IndexSearcher.TooManyNestedClauses::class) {
            searcher.rewrite(query)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testLargeTermsNestedLast() {
        val searcher = newSearcher(MultiReader())
        val nestedBuilder = BooleanQuery.Builder()

        nestedBuilder.setMinimumNumberShouldMatch(5)

        for (i in 0 until 600) {
            nestedBuilder.add(TermQuery(Term("foo", "bar-$i")), BooleanClause.Occur.SHOULD)
        }
        val inner = nestedBuilder.build()
        val builderMixed = BooleanQuery.Builder()

        builderMixed.setMinimumNumberShouldMatch(5)

        for (i in 0 until 600) {
            builderMixed.add(TermQuery(Term("foo", "bar")), BooleanClause.Occur.SHOULD)
        }

        builderMixed.add(inner, BooleanClause.Occur.SHOULD)

        val query = builderMixed.build()

        // Can't be flattened, but high clause count should still be cause during nested walk...
        expectThrows(IndexSearcher.TooManyNestedClauses::class) {
            searcher.rewrite(query)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testLargeDisjunctionMaxQuery() {
        val searcher = newSearcher(MultiReader())
        val clausesQueryArray = arrayOfNulls<Query>(1050)

        for (i in 0 until 1049) {
            clausesQueryArray[i] = TermQuery(Term("field", "a"))
        }

        val pq = PhraseQuery("field", *emptyArray<String>())

        clausesQueryArray[1049] = pq

        val dmq = DisjunctionMaxQuery(clausesQueryArray.filterNotNull().toMutableList(), 0.5f)

        // Can't be flattened, but high clause count should still be cause during nested walk...
        expectThrows(IndexSearcher.TooManyNestedClauses::class) {
            searcher.rewrite(dmq)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testMultiExactWithRepeats() {
        val searcher = newSearcher(MultiReader())
        val qb = MultiPhraseQuery.Builder()

        for (i in 0 until 1050) {
            qb.add(arrayOf(Term("foo", "bar-$i"), Term("foo", "bar+$i")), 0)
        }

        // Can't be flattened, but high clause count should still be cause during nested walk...
        expectThrows(IndexSearcher.TooManyNestedClauses::class) {
            searcher.rewrite(qb.build())
        }
    }
}
