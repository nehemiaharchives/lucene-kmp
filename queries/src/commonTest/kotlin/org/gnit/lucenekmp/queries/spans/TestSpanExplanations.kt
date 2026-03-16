package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.ConstantScoreQuery
import org.gnit.lucenekmp.search.DisjunctionMaxQuery
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.TermQuery
import kotlin.test.Test
import kotlin.test.assertEquals

/** TestExplanations subclass focusing on span queries */
open class TestSpanExplanations : BaseSpanExplanationTestCase() {
    companion object {
        private const val FIELD_CONTENT = "content"
    }

    /* simple SpanTermQueries */

    @Test
    @Throws(Exception::class)
    open fun testST1() {
        val q = st("w1")
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testST2() {
        val q = st("w1")
        qtest(BoostQuery(q, 1000f), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testST4() {
        val q = st("xx")
        qtest(q, intArrayOf(2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testST5() {
        val q = st("xx")
        qtest(BoostQuery(q, 1000f), intArrayOf(2, 3))
    }

    /* some SpanFirstQueries */

    @Test
    @Throws(Exception::class)
    open fun testSF1() {
        val q = sf("w1", 1)
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSF2() {
        val q = sf("w1", 1)
        qtest(BoostQuery(q, 1000f), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSF4() {
        val q = sf("xx", 2)
        qtest(q, intArrayOf(2))
    }

    @Test
    @Throws(Exception::class)
    open fun testSF5() {
        val q = sf("yy", 2)
        qtest(q, intArrayOf())
    }

    @Test
    @Throws(Exception::class)
    open fun testSF6() {
        val q = sf("yy", 4)
        qtest(BoostQuery(q, 1000f), intArrayOf(2))
    }

    /* some SpanOrQueries */

    @Test
    @Throws(Exception::class)
    open fun testSO1() {
        val q = sor("w1", "QQ")
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSO2() {
        val q = sor("w1", "w3", "zz")
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSO3() {
        val q = sor("w5", "QQ", "yy")
        qtest(q, intArrayOf(0, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSO4() {
        val q = sor("w5", "QQ", "yy")
        qtest(q, intArrayOf(0, 2, 3))
    }

    /* some SpanNearQueries */

    @Test
    @Throws(Exception::class)
    open fun testSNear1() {
        val q = snear("w1", "QQ", 100, true)
        qtest(q, intArrayOf())
    }

    @Test
    @Throws(Exception::class)
    open fun testSNear2() {
        val q = snear("w1", "xx", 100, true)
        qtest(q, intArrayOf(2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNear3() {
        val q = snear("w1", "xx", 0, true)
        qtest(q, intArrayOf(2))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNear4() {
        val q = snear("w1", "xx", 1, true)
        qtest(q, intArrayOf(2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNear5() {
        val q = snear("xx", "w1", 0, false)
        qtest(q, intArrayOf(2))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNear6() {
        val q = snear("w1", "w2", "QQ", 100, true)
        qtest(q, intArrayOf())
    }

    @Test
    @Throws(Exception::class)
    open fun testSNear7() {
        val q = snear("w1", "xx", "w2", 100, true)
        qtest(q, intArrayOf(2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNear8() {
        val q = snear("w1", "xx", "w2", 0, true)
        qtest(q, intArrayOf(2))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNear9() {
        val q = snear("w1", "xx", "w2", 1, true)
        qtest(q, intArrayOf(2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNear10() {
        val q = snear("xx", "w1", "w2", 0, false)
        qtest(q, intArrayOf(2))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNear11() {
        val q = snear("w1", "w2", "w3", 1, true)
        qtest(q, intArrayOf(0, 1))
    }

    /* some SpanNotQueries */

    @Test
    @Throws(Exception::class)
    open fun testSNot1() {
        val q = snot(sf("w1", 10), st("QQ"))
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNot2() {
        val q = snot(sf("w1", 10), st("QQ"))
        qtest(BoostQuery(q, 1000f), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNot4() {
        val q = snot(sf("w1", 10), st("xx"))
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNot5() {
        val q = snot(sf("w1", 10), st("xx"))
        qtest(BoostQuery(q, 1000f), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNot7() {
        val f = snear("w1", "w3", 10, true)
        val q = snot(f, st("xx"))
        qtest(q, intArrayOf(0, 1, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSNot10() {
        val t = st("xx")
        val q = snot(snear("w1", "w3", 10, true), t)
        qtest(q, intArrayOf(0, 1, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testExplainWithoutScoring() {
        val query =
            SpanNearQuery(
                arrayOf(
                    SpanTermQuery(Term(FIELD_CONTENT, "dolor")),
                    SpanTermQuery(Term(FIELD_CONTENT, "lorem")),
                ),
                0,
                true,
            )

        newDirectory().use { rd ->
            IndexWriter(rd, newIndexWriterConfig(analyzer!!)).use { writer ->
                val doc = Document()
                doc.add(newTextField(FIELD_CONTENT, "dolor lorem ipsum", Field.Store.YES))
                writer.addDocument(doc)
            }

            DirectoryReader.open(rd).use { reader ->
                val indexSearcher = newSearcher(reader)
                val spanWeight = query.createWeight(indexSearcher, ScoreMode.COMPLETE_NO_SCORES, 1f)

                val ctx: LeafReaderContext = indexSearcher.indexReader.leaves()[0]
                val explanation: Explanation = spanWeight.explain(ctx, 0)

                assertEquals(0f, explanation.value.toFloat())
                assertEquals(
                    "match spanNear([content:dolor, content:lorem], 0, true) in 0 without score",
                    explanation.description,
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    open fun test1() {
        val q = BooleanQuery.Builder()

        val phraseQuery = PhraseQuery(1, FIELD, "w1", "w2")
        q.add(phraseQuery, Occur.MUST)
        q.add(snear(st("w2"), sor("w5", "zz"), 4, true), Occur.SHOULD)
        q.add(snear(sf("w3", 2), st("w2"), st("w3"), 5, true), Occur.SHOULD)

        var t: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term(FIELD, "xx")), Occur.MUST)
                .add(matchTheseItems(intArrayOf(1, 3)), Occur.FILTER)
                .build()
        q.add(BoostQuery(t, 1000f), Occur.SHOULD)

        t = ConstantScoreQuery(matchTheseItems(intArrayOf(0, 2)))
        q.add(BoostQuery(t, 30f), Occur.SHOULD)

        val disjuncts = mutableListOf<Query>()
        disjuncts.add(snear(st("w2"), sor("w5", "zz"), 4, true))
        disjuncts.add(TermQuery(Term(FIELD, "QQ")))

        val xxYYZZ = BooleanQuery.Builder()

        xxYYZZ.add(TermQuery(Term(FIELD, "xx")), Occur.SHOULD)
        xxYYZZ.add(TermQuery(Term(FIELD, "yy")), Occur.SHOULD)
        xxYYZZ.add(TermQuery(Term(FIELD, "zz")), Occur.MUST_NOT)

        disjuncts.add(xxYYZZ.build())

        val xxW1 = BooleanQuery.Builder()

        xxW1.add(TermQuery(Term(FIELD, "xx")), Occur.MUST_NOT)
        xxW1.add(TermQuery(Term(FIELD, "w1")), Occur.MUST_NOT)

        disjuncts.add(xxW1.build())

        val disjuncts2 = mutableListOf<Query>()
        disjuncts2.add(TermQuery(Term(FIELD, "w1")))
        disjuncts2.add(TermQuery(Term(FIELD, "w2")))
        disjuncts2.add(TermQuery(Term(FIELD, "w3")))
        disjuncts.add(DisjunctionMaxQuery(disjuncts2, 0.5f))

        q.add(DisjunctionMaxQuery(disjuncts, 0.2f), Occur.SHOULD)

        val b = BooleanQuery.Builder()

        b.setMinimumNumberShouldMatch(2)
        b.add(snear("w1", "w2", 1, true), Occur.SHOULD)
        b.add(snear("w2", "w3", 1, true), Occur.SHOULD)
        b.add(snear("w1", "w3", 3, true), Occur.SHOULD)

        q.add(b.build(), Occur.SHOULD)

        qtest(q.build(), intArrayOf(0, 1, 2))
    }

    @Test
    @Throws(Exception::class)
    open fun test2() {
        val q = BooleanQuery.Builder()

        val phraseQuery = PhraseQuery(1, FIELD, "w1", "w2")
        q.add(phraseQuery, Occur.MUST)
        q.add(snear(st("w2"), sor("w5", "zz"), 4, true), Occur.SHOULD)
        q.add(snear(sf("w3", 2), st("w2"), st("w3"), 5, true), Occur.SHOULD)

        var t: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term(FIELD, "xx")), Occur.MUST)
                .add(matchTheseItems(intArrayOf(1, 3)), Occur.FILTER)
                .build()
        q.add(BoostQuery(t, 1000f), Occur.SHOULD)

        t = ConstantScoreQuery(matchTheseItems(intArrayOf(0, 2)))
        q.add(BoostQuery(t, 20f), Occur.SHOULD)

        val disjuncts = mutableListOf<Query>()
        disjuncts.add(snear(st("w2"), sor("w5", "zz"), 4, true))
        disjuncts.add(TermQuery(Term(FIELD, "QQ")))

        val xxYYZZ = BooleanQuery.Builder()

        xxYYZZ.add(TermQuery(Term(FIELD, "xx")), Occur.SHOULD)
        xxYYZZ.add(TermQuery(Term(FIELD, "yy")), Occur.SHOULD)
        xxYYZZ.add(TermQuery(Term(FIELD, "zz")), Occur.MUST_NOT)

        disjuncts.add(xxYYZZ.build())

        val xxW1 = BooleanQuery.Builder()

        xxW1.add(TermQuery(Term(FIELD, "xx")), Occur.MUST_NOT)
        xxW1.add(TermQuery(Term(FIELD, "w1")), Occur.MUST_NOT)

        disjuncts.add(xxW1.build())

        val dm2 =
            DisjunctionMaxQuery(
                mutableListOf<Query>(
                    TermQuery(Term(FIELD, "w1")),
                    TermQuery(Term(FIELD, "w2")),
                    TermQuery(Term(FIELD, "w3")),
                ),
                0.5f,
            )
        disjuncts.add(dm2)

        q.add(DisjunctionMaxQuery(disjuncts, 0.2f), Occur.SHOULD)

        val builder = BooleanQuery.Builder()

        builder.setMinimumNumberShouldMatch(2)
        builder.add(snear("w1", "w2", 1, true), Occur.SHOULD)
        builder.add(snear("w2", "w3", 1, true), Occur.SHOULD)
        builder.add(snear("w1", "w3", 3, true), Occur.SHOULD)
        val b = builder.build()

        q.add(BoostQuery(b, 0f), Occur.SHOULD)

        qtest(q.build(), intArrayOf(0, 1, 2))
    }
}
