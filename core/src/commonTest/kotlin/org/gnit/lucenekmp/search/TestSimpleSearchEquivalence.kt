package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.search.AssertingQuery
import org.gnit.lucenekmp.tests.search.SearchEquivalenceTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

/** Basic equivalence tests for core queries */
class TestSimpleSearchEquivalence : SearchEquivalenceTestBase() {
    // TODO: we could go a little crazy for a lot of these,
    // but these are just simple minimal cases in case something
    // goes horribly wrong. Put more intense tests elsewhere.

    /** A ⊆ (A B) */
    @Test
    @Throws(Exception::class)
    fun testTermVersusBooleanOr() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = BooleanQuery.Builder()
        q2.add(TermQuery(t1), BooleanClause.Occur.SHOULD)
        q2.add(TermQuery(t2), BooleanClause.Occur.SHOULD)
        assertSubsetOf(q1, q2.build())
    }

    /** A ⊆ (+A B) */
    @Test
    @Throws(Exception::class)
    fun testTermVersusBooleanReqOpt() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = BooleanQuery.Builder()
        q2.add(TermQuery(t1), BooleanClause.Occur.MUST)
        q2.add(TermQuery(t2), BooleanClause.Occur.SHOULD)
        assertSubsetOf(q1, q2.build())
    }

    /** (A -B) ⊆ A */
    @Test
    @Throws(Exception::class)
    fun testBooleanReqExclVersusTerm() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = BooleanQuery.Builder()
        q1.add(TermQuery(t1), BooleanClause.Occur.MUST)
        q1.add(TermQuery(t2), BooleanClause.Occur.MUST_NOT)
        val q2 = TermQuery(t1)
        assertSubsetOf(q1.build(), q2)
    }

    /** (+A +B) ⊆ (A B) */
    @Test
    @Throws(Exception::class)
    fun testBooleanAndVersusBooleanOr() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = BooleanQuery.Builder()
        q1.add(TermQuery(t1), BooleanClause.Occur.SHOULD)
        q1.add(TermQuery(t2), BooleanClause.Occur.SHOULD)
        val q2 = BooleanQuery.Builder()
        q2.add(TermQuery(t1), BooleanClause.Occur.SHOULD)
        q2.add(TermQuery(t2), BooleanClause.Occur.SHOULD)
        assertSubsetOf(q1.build(), q2.build())
    }

    /** (A B) = (A | B) */
    @Test
    @Throws(Exception::class)
    fun testDisjunctionSumVersusDisjunctionMax() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = BooleanQuery.Builder()
        q1.add(TermQuery(t1), BooleanClause.Occur.SHOULD)
        q1.add(TermQuery(t2), BooleanClause.Occur.SHOULD)
        val q2 = DisjunctionMaxQuery(mutableListOf<Query>(TermQuery(t1), TermQuery(t2)), 0.5f)
        assertSameSet(q1.build(), q2)
    }

    /** "A B" ⊆ (+A +B) */
    @Test
    @Throws(Exception::class)
    fun testExactPhraseVersusBooleanAnd() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = PhraseQuery(t1.field(), t1.bytes(), t2.bytes())
        val q2 = BooleanQuery.Builder()
        q2.add(TermQuery(t1), BooleanClause.Occur.MUST)
        q2.add(TermQuery(t2), BooleanClause.Occur.MUST)
        assertSubsetOf(q1, q2.build())
    }

    /** same as above, with posincs */
    @Test
    @Throws(Exception::class)
    fun testExactPhraseVersusBooleanAndWithHoles() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val builder = PhraseQuery.Builder()
        builder.add(t1, 0)
        builder.add(t2, 2)
        val q1 = builder.build()
        val q2 = BooleanQuery.Builder()
        q2.add(TermQuery(t1), BooleanClause.Occur.MUST)
        q2.add(TermQuery(t2), BooleanClause.Occur.MUST)
        assertSubsetOf(q1, q2.build())
    }

    /** "A B" ⊆ "A B"~1 */
    @Test
    @Throws(Exception::class)
    fun testPhraseVersusSloppyPhrase() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = PhraseQuery(t1.field(), t1.bytes(), t2.bytes())
        val q2 = PhraseQuery(1, t1.field(), t1.bytes(), t2.bytes())
        assertSubsetOf(q1, q2)
    }

    /** same as above, with posincs */
    @Test
    @Throws(Exception::class)
    fun testPhraseVersusSloppyPhraseWithHoles() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val builder = PhraseQuery.Builder()
        builder.add(t1, 0)
        builder.add(t2, 2)
        val q1 = builder.build()
        builder.setSlop(2)
        val q2 = builder.build()
        assertSubsetOf(q1, q2)
    }

    /** "A B" ⊆ "A (B C)" */
    @Test
    @Throws(Exception::class)
    fun testExactPhraseVersusMultiPhrase() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = PhraseQuery(t1.field(), t1.bytes(), t2.bytes())
        val t3 = randomTerm()
        val q2b = MultiPhraseQuery.Builder()
        q2b.add(t1)
        q2b.add(arrayOf(t2, t3))
        assertSubsetOf(q1, q2b.build())
    }

    /** same as above, with posincs */
    @Test
    @Throws(Exception::class)
    fun testExactPhraseVersusMultiPhraseWithHoles() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val builder = PhraseQuery.Builder()
        builder.add(t1, 0)
        builder.add(t2, 2)
        val q1 = builder.build()
        val t3 = randomTerm()
        val q2b = MultiPhraseQuery.Builder()
        q2b.add(t1)
        q2b.add(arrayOf(t2, t3), 2)
        assertSubsetOf(q1, q2b.build())
    }

    /** "A B"~∞ = +A +B if A != B */
    @Test
    @Throws(Exception::class)
    fun testSloppyPhraseVersusBooleanAnd() {
        val t1 = randomTerm()
        var t2: Term
        // semantics differ from SpanNear: SloppyPhrase handles repeats,
        // so we must ensure t1 != t2
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val q1 = PhraseQuery(Int.MAX_VALUE, t1.field(), t1.bytes(), t2.bytes())
        val q2 = BooleanQuery.Builder()
        q2.add(TermQuery(t1), BooleanClause.Occur.MUST)
        q2.add(TermQuery(t2), BooleanClause.Occur.MUST)
        assertSameSet(q1, q2.build())
    }

    /** Phrase positions are relative. */
    @Test
    @Throws(Exception::class)
    fun testPhraseRelativePositions() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = PhraseQuery(t1.field(), t1.bytes(), t2.bytes())
        val builder = PhraseQuery.Builder()
        builder.add(t1, 10000)
        builder.add(t2, 10001)
        val q2 = builder.build()
        assertSameScores(q1, q2)
    }

    /** Sloppy-phrase positions are relative. */
    @Test
    @Throws(Exception::class)
    fun testSloppyPhraseRelativePositions() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = PhraseQuery(2, t1.field(), t1.bytes(), t2.bytes())
        val builder = PhraseQuery.Builder()
        builder.add(t1, 10000)
        builder.add(t2, 10001)
        builder.setSlop(2)
        val q2 = builder.build()
        assertSameScores(q1, q2)
    }

    @Test
    @Throws(Exception::class)
    fun testBoostQuerySimplification() {
        val b1 = random().nextFloat() * 10
        val b2 = random().nextFloat() * 10
        val term = randomTerm()

        val q1: Query = BoostQuery(BoostQuery(TermQuery(term), b2), b1)
        // Use AssertingQuery to prevent BoostQuery from merging inner and outer boosts
        val q2: Query =
            BoostQuery(AssertingQuery(random(), BoostQuery(TermQuery(term), b2)), b1)

        assertSameScores(q1, q2)
    }

    @Test
    @Throws(Exception::class)
    fun testBooleanBoostPropagation() {
        val boost1 = random().nextFloat()
        val tq: Query = BoostQuery(TermQuery(randomTerm()), boost1)

        val boost2 = random().nextFloat()
        // Applying boost2 over the term or boolean query should have the same effect
        val q1: Query = BoostQuery(tq, boost2)
        var q2: Query =
            BooleanQuery.Builder().add(tq, BooleanClause.Occur.MUST).add(tq, BooleanClause.Occur.FILTER).build()
        q2 = BoostQuery(q2, boost2)

        assertSameScores(q1, q2)
    }

    @Test
    @Throws(Exception::class)
    fun testBooleanOrVsSynonym() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        assertEquals(t1.field(), t2.field())
        val q1 = SynonymQuery.Builder(t1.field()).addTerm(t1).addTerm(t2).build()
        val q2 = BooleanQuery.Builder()
        q2.add(TermQuery(t1), BooleanClause.Occur.SHOULD)
        q2.add(TermQuery(t2), BooleanClause.Occur.SHOULD)
        assertSameSet(q1, q2.build())
    }
}
