package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.jdkport.Math
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Test that BooleanQuery.setMinimumNumberShouldMatch works. */
class TestBooleanMinShouldMatch : LuceneTestCase() {
    @Throws(Exception::class)
    fun beforeClass() {
        if (s != null) {
            return
        }

        val data =
            arrayOf(
                "A 1 2 3 4 5 6",
                "Z       4 5 6",
                null,
                "B   2   4 5 6",
                "Y     3   5 6",
                null,
                "C     3     6",
                "X       4 5 6"
            )

        index = newDirectory()
        val w = RandomIndexWriter(random(), index!!)

        for (i in data.indices) {
            val doc = Document()
            doc.add(newStringField("id", i.toString(), Field.Store.YES)) // Field.Keyword("id",String.valueOf(i)));
            doc.add(newStringField("all", "all", Field.Store.YES)) // Field.Keyword("all","all"));
            if (null != data[i]) {
                doc.add(newTextField("data", data[i]!!, Field.Store.YES)) // Field.Text("data",data[i]));
            }
            w.addDocument(doc)
        }

        r = w.getReader(true, false)
        s = newSearcher(r!!)
        w.close()
        // System.out.println("Set up " + getName());
    }

    @Throws(Exception::class)
    fun afterClass() {
        s = null
        r?.close()
        r = null
        index?.close()
        index = null
    }

    @Throws(Exception::class)
    fun verifyNrHits(q: Query, expected: Int) {
        beforeClass()

        val s = s!!
        val test = "TestBooleanMinShouldMatch"

        // bs1
        val h = s.search(q, 1000).scoreDocs
        if (expected != h.size) {
            printHits(test, h, s)
        }
        assertEquals(expected, h.size, "result count")
        // System.out.println("TEST: now check");
        // bs2
        val collectorManager = TopScoreDocCollectorManager(1000, Int.MAX_VALUE)
        val h2 = s.search(q, collectorManager).scoreDocs
        if (expected != h2.size) {
            printHits(test, h2, s)
        }
        assertEquals(expected, h2.size, "result count (bs2)")

        QueryUtils.check(random(), q, s)
    }

    @Test
    @Throws(Exception::class)
    fun testAllOptional() {
        val q = BooleanQuery.Builder()
        for (i in 1..4) {
            q.add(TermQuery(Term("data", i.toString())), BooleanClause.Occur.SHOULD) // false, false);
        }
        q.setMinimumNumberShouldMatch(2) // match at least two of 4
        verifyNrHits(q.build(), 2)
    }

    @Test
    @Throws(Exception::class)
    fun testOneReqAndSomeOptional() {
        /* one required, some optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("all", "all")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "5")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "4")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.SHOULD) // false, false);

        q.setMinimumNumberShouldMatch(2) // 2 of 3 optional

        verifyNrHits(q.build(), 5)
    }

    @Test
    @Throws(Exception::class)
    fun testSomeReqAndSomeOptional() {
        /* two required, some optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("all", "all")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "6")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "5")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "4")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.SHOULD) // false, false);

        q.setMinimumNumberShouldMatch(2) // 2 of 3 optional

        verifyNrHits(q.build(), 5)
    }

    @Test
    @Throws(Exception::class)
    fun testOneProhibAndSomeOptional() {
        /* one prohibited, some optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "2")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.MUST_NOT) // false, true );
        q.add(TermQuery(Term("data", "4")), BooleanClause.Occur.SHOULD) // false, false);

        q.setMinimumNumberShouldMatch(2) // 2 of 3 optional

        verifyNrHits(q.build(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testSomeProhibAndSomeOptional() {
        /* two prohibited, some optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "2")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.MUST_NOT) // false, true );
        q.add(TermQuery(Term("data", "4")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "C")), BooleanClause.Occur.MUST_NOT) // false, true );

        q.setMinimumNumberShouldMatch(2) // 2 of 3 optional

        verifyNrHits(q.build(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testOneReqOneProhibAndSomeOptional() {
        /* one required, one prohibited, some optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("data", "6")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "5")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "4")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.MUST_NOT) // false, true );
        q.add(TermQuery(Term("data", "2")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD) // false, false);

        q.setMinimumNumberShouldMatch(3) // 3 of 4 optional

        verifyNrHits(q.build(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testSomeReqOneProhibAndSomeOptional() {
        /* two required, one prohibited, some optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("all", "all")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "6")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "5")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "4")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.MUST_NOT) // false, true );
        q.add(TermQuery(Term("data", "2")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD) // false, false);

        q.setMinimumNumberShouldMatch(3) // 3 of 4 optional

        verifyNrHits(q.build(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testOneReqSomeProhibAndSomeOptional() {
        /* one required, two prohibited, some optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("data", "6")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "5")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "4")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.MUST_NOT) // false, true );
        q.add(TermQuery(Term("data", "2")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "C")), BooleanClause.Occur.MUST_NOT) // false, true );

        q.setMinimumNumberShouldMatch(3) // 3 of 4 optional

        verifyNrHits(q.build(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testSomeReqSomeProhibAndSomeOptional() {
        /* two required, two prohibited, some optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("all", "all")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "6")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "5")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "4")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.MUST_NOT) // false, true );
        q.add(TermQuery(Term("data", "2")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "C")), BooleanClause.Occur.MUST_NOT) // false, true );

        q.setMinimumNumberShouldMatch(3) // 3 of 4 optional

        verifyNrHits(q.build(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testMinHigherThenNumOptional() {
        /* two required, two prohibited, some optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("all", "all")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "6")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "5")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "4")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.MUST_NOT) // false, true );
        q.add(TermQuery(Term("data", "2")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "C")), BooleanClause.Occur.MUST_NOT) // false, true );

        q.setMinimumNumberShouldMatch(90) // 90 of 4 optional ?!?!?!

        verifyNrHits(q.build(), 0)
    }

    @Test
    @Throws(Exception::class)
    fun testMinEqualToNumOptional() {
        /* two required, two optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("all", "all")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "6")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "2")), BooleanClause.Occur.SHOULD) // false, false);

        q.setMinimumNumberShouldMatch(2) // 2 of 2 optional

        verifyNrHits(q.build(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testOneOptionalEqualToMin() {
        /* two required, one optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("all", "all")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "3")), BooleanClause.Occur.SHOULD) // false, false);
        q.add(TermQuery(Term("data", "2")), BooleanClause.Occur.MUST) // true,  false);

        q.setMinimumNumberShouldMatch(1) // 1 of 1 optional

        verifyNrHits(q.build(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testNoOptionalButMin() {
        /* two required, no optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("all", "all")), BooleanClause.Occur.MUST) // true,  false);
        q.add(TermQuery(Term("data", "2")), BooleanClause.Occur.MUST) // true,  false);

        q.setMinimumNumberShouldMatch(1) // 1 of 0 optional

        verifyNrHits(q.build(), 0)
    }

    @Test
    @Throws(Exception::class)
    fun testNoOptionalButMin2() {
        /* one required, no optional */
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term("all", "all")), BooleanClause.Occur.MUST) // true,  false);

        q.setMinimumNumberShouldMatch(1) // 1 of 0 optional

        verifyNrHits(q.build(), 0)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomQueries() {
        beforeClass()

        val s = s!!
        val field = "data"
        val vals = arrayOf("1", "2", "3", "4", "5", "6", "A", "Z", "B", "Y", "Z", "X", "foo")
        val maxLev = 4

        // callback object to set a random setMinimumNumberShouldMatch
        val minNrCB =
            object : TestBoolean2.Callback {
                override fun postCreate(q: BooleanQuery.Builder) {
                    var opt = 0
                    for (clause in q.build().clauses()) {
                        if (clause.occur == BooleanClause.Occur.SHOULD) opt++
                    }
                    q.setMinimumNumberShouldMatch(random().nextInt(opt + 2))
                    if (random().nextBoolean()) {
                        // also add a random negation
                        val randomTerm = Term(field, vals[random().nextInt(vals.size)])
                        q.add(TermQuery(randomTerm), BooleanClause.Occur.MUST_NOT)
                    }
                }
            }

        // increase number of iterations for more complete testing
        val num = atLeast(20)
        for (i in 0..<num) {
            val lev = random().nextInt(maxLev)
            val seed = random().nextLong()
            val q1 = TestBoolean2.randBoolQuery(Random(seed), true, lev, field, vals, null)
            // BooleanQuery q2 = TestBoolean2.randBoolQuery(new Random(seed), lev, field, vals, minNrCB);
            val q2 = TestBoolean2.randBoolQuery(Random(seed), true, lev, field, vals, null)
            // only set minimumNumberShouldMatch on the top level query since setting
            // at a lower level can change the score.
            minNrCB.postCreate(q2)

            // Can't use Hits because normalized scores will mess things
            // up.  The non-sorting version of search() that returns TopDocs
            // will not normalize scores.
            val top1 = s.search(q1.build(), 100)
            val top2 = s.search(q2.build(), 100)
            if (i < 100) {
                QueryUtils.check(random(), q1.build(), s)
                QueryUtils.check(random(), q2.build(), s)
            }
            assertSubsetOfSameScores(q2.build(), top1, top2)
        }
        // System.out.println("Total hits:"+tot);
    }

    private fun assertSubsetOfSameScores(q: Query, top1: TopDocs, top2: TopDocs) {
        // The constrained query
        // should be a subset to the unconstrained query.
        if (top2.totalHits.value > top1.totalHits.value) {
            fail(
                "Constrained results not a subset:\n" +
                    CheckHits.topdocsString(top1, 0, 0) +
                    CheckHits.topdocsString(top2, 0, 0) +
                    "for query:" +
                    q.toString()
            )
        }

        for (hit in 0..<top2.totalHits.value.toInt()) {
            val id = top2.scoreDocs[hit].doc
            val score = top2.scoreDocs[hit].score
            var found = false
            // find this doc in other hits
            for (other in 0..<top1.totalHits.value.toInt()) {
                if (top1.scoreDocs[other].doc == id) {
                    found = true
                    val otherScore = top1.scoreDocs[other].score
                    // check if scores match
                    if (abs(score - otherScore) > Math.ulp(score)) {
                        fail(
                            "Doc " +
                                id +
                                " scores don't match\n" +
                                CheckHits.topdocsString(top1, 0, 0) +
                                CheckHits.topdocsString(top2, 0, 0) +
                                "for query:" +
                                q.toString()
                        )
                    }
                }
            }

            // check if subset
            if (!found) {
                fail(
                    "Doc " +
                        id +
                        " not found\n" +
                        CheckHits.topdocsString(top1, 0, 0) +
                        CheckHits.topdocsString(top2, 0, 0) +
                        "for query:" +
                        q.toString()
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRewriteMSM1() {
        beforeClass()

        val s = s!!
        val q1 = BooleanQuery.Builder()
        q1.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD)
        val q2 = BooleanQuery.Builder()
        q2.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD)
        q2.setMinimumNumberShouldMatch(1)
        val top1 = s.search(q1.build(), 100)
        val top2 = s.search(q2.build(), 100)
        assertSubsetOfSameScores(q2.build(), top1, top2)
    }

    @Test
    @Throws(Exception::class)
    fun testRewriteNegate() {
        beforeClass()

        val s = s!!
        val q1 = BooleanQuery.Builder()
        q1.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD)
        val q2 = BooleanQuery.Builder()
        q2.add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD)
        q2.add(TermQuery(Term("data", "Z")), BooleanClause.Occur.MUST_NOT)
        val top1 = s.search(q1.build(), 100)
        val top2 = s.search(q2.build(), 100)
        assertSubsetOfSameScores(q2.build(), top1, top2)
    }

    @Test
    @Throws(Exception::class)
    fun testFlattenInnerDisjunctions() {
        var q: Query =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(2)
                .add(TermQuery(Term("all", "all")), BooleanClause.Occur.SHOULD)
                .add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD)
                .add(TermQuery(Term("data", "2")), BooleanClause.Occur.MUST)
                .build()
        verifyNrHits(q, 1)

        val inner =
            BooleanQuery.Builder()
                .add(TermQuery(Term("all", "all")), BooleanClause.Occur.SHOULD)
                .add(TermQuery(Term("data", "1")), BooleanClause.Occur.SHOULD)
                .build()
        q =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(2)
                .add(inner, BooleanClause.Occur.SHOULD)
                .add(TermQuery(Term("data", "2")), BooleanClause.Occur.MUST)
                .build()

        verifyNrHits(q, 0)
    }

    @Throws(Exception::class)
    protected fun printHits(test: String, h: Array<ScoreDoc>, searcher: IndexSearcher) {
        println("------- $test -------")

        val storedFields: StoredFields = searcher.storedFields()
        for (i in h.indices) {
            val d: Document = storedFields.document(h[i].doc)
            val score = h[i].score
            println("#$i: $score - ${d.get("id")} - ${d.get("data")}")
        }
    }

    companion object {
        private var index: Directory? = null
        private var r: IndexReader? = null
        private var s: IndexSearcher? = null
    }
}
