package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.search.similarities.TFIDFSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Test of the DisjunctionMaxQuery. */
class TestDisjunctionMaxQuery : LuceneTestCase() {
    /** threshold for comparing floats */
    val SCORE_COMP_THRESH = 0.0000f

    /**
     * Similarity to eliminate tf, idf and lengthNorm effects to isolate test case.
     *
     * same as TestRankingSimilarity in TestRanking.zip from
     * http://issues.apache.org/jira/browse/LUCENE-323
     */
    private class TestSimilarity : TFIDFSimilarity() {
        override fun tf(freq: Float): Float {
            return if (freq > 0.0f) 1.0f else 0.0f
        }

        override fun lengthNorm(numTerms: Int): Float {
            // Disable length norm
            return 1f
        }

        override fun idf(docFreq: Long, docCount: Long): Float {
            return 1.0f
        }

        override fun idfExplain(
            collectionStats: CollectionStatistics,
            termStats: TermStatistics
        ): Explanation {
            val df = termStats.docFreq
            val docCount = collectionStats.docCount
            return Explanation.match(
                1.0f,
                "idf, computed as constant from:",
                Explanation.match(df, "docFreq, number of documents containing term"),
                Explanation.match(docCount, "docCount, total number of documents with field")
            )
        }
    }

    var sim: Similarity = TestSimilarity()
    var index: Directory? = null
    var r: IndexReader? = null
    var s: IndexSearcher? = null

    @BeforeTest
    @Throws(Exception::class)
    fun setUpTestDisjunctionMaxQuery() {
        index = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                index!!,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setSimilarity(sim)
                    .setMergePolicy(newLogMergePolicy())
            )

        // hed is the most important field, dek is secondary

        // d1 is an "ok" match for: albino elephant
        run {
            val d1 = Document()
            d1.add(newField("id", "d1", nonAnalyzedType))
            d1.add(newTextField("hed", "elephant", Field.Store.YES))
            d1.add(newTextField("dek", "elephant", Field.Store.YES))
            writer.addDocument(d1)
        }

        // d2 is a "good" match for: albino elephant
        run {
            val d2 = Document()
            d2.add(newField("id", "d2", nonAnalyzedType))
            d2.add(newTextField("hed", "elephant", Field.Store.YES))
            d2.add(newTextField("dek", "albino", Field.Store.YES))
            d2.add(newTextField("dek", "elephant", Field.Store.YES))
            writer.addDocument(d2)
        }

        // d3 is a "better" match for: albino elephant
        run {
            val d3 = Document()
            d3.add(newField("id", "d3", nonAnalyzedType))
            d3.add(newTextField("hed", "albino", Field.Store.YES))
            d3.add(newTextField("hed", "elephant", Field.Store.YES))
            writer.addDocument(d3)
        }

        // d4 is the "best" match for: albino elephant
        run {
            val d4 = Document()
            d4.add(newField("id", "d4", nonAnalyzedType))
            d4.add(newTextField("hed", "albino", Field.Store.YES))
            d4.add(newField("hed", "elephant", nonAnalyzedType))
            d4.add(newTextField("dek", "albino", Field.Store.YES))
            writer.addDocument(d4)
        }

        writer.forceMerge(1)
        r = getOnlyLeafReader(writer.reader)
        writer.close()
        s = IndexSearcher(r!!)
        s!!.similarity = sim
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDownTestDisjunctionMaxQuery() {
        r!!.close()
        index!!.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSkipToFirsttimeMiss() {
        val dq =
            DisjunctionMaxQuery(
                arrayListOf(tq("id", "d1"), tq("dek", "DOES_NOT_EXIST")),
                0.0f
            )

        QueryUtils.check(random(), dq, s!!)
        assertTrue(s!!.topReaderContext is LeafReaderContext)
        val dw = s!!.createWeight(s!!.rewrite(dq), ScoreMode.COMPLETE, 1f)
        val context = s!!.topReaderContext as LeafReaderContext
        val ds = dw.scorer(context)!!
        val skipOk = ds.iterator().advance(3) != DocIdSetIterator.NO_MORE_DOCS
        if (skipOk) {
            fail("firsttime skipTo found a match? ... ${r!!.storedFields().document(ds.docID()).get("id")}")
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSkipToFirsttimeHit() {
        val dq =
            DisjunctionMaxQuery(
                arrayListOf(tq("dek", "albino"), tq("dek", "DOES_NOT_EXIST")),
                0.0f
            )

        assertTrue(s!!.topReaderContext is LeafReaderContext)
        QueryUtils.check(random(), dq, s!!)
        val dw = s!!.createWeight(s!!.rewrite(dq), ScoreMode.COMPLETE, 1f)
        val context = s!!.topReaderContext as LeafReaderContext
        val ds = dw.scorer(context)!!
        assertTrue(ds.iterator().advance(3) != DocIdSetIterator.NO_MORE_DOCS, "firsttime skipTo found no match")
        assertEquals("d4", r!!.storedFields().document(ds.docID()).get("id"), "found wrong docid")
    }

    @Test
    @Throws(Exception::class)
    fun testSimpleEqualScores1() {
        val q =
            DisjunctionMaxQuery(
                arrayListOf(tq("hed", "albino"), tq("hed", "elephant")),
                0.0f
            )
        QueryUtils.check(random(), q, s!!)

        val h = s!!.search(q, 1000).scoreDocs

        try {
            assertEquals(4, h.size, "all docs should match ${q.toString()}")

            val score = h[0].score
            for (i in 1..<h.size) {
                assertEquals(score, h[i].score, SCORE_COMP_THRESH, "score #$i is not the same")
            }
        } catch (e: Throwable) {
            printHits("testSimpleEqualScores1", h, s!!)
            throw e
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSimpleEqualScores2() {
        val q =
            DisjunctionMaxQuery(
                arrayListOf(tq("dek", "albino"), tq("dek", "elephant")),
                0.0f
            )
        QueryUtils.check(random(), q, s!!)

        val h = s!!.search(q, 1000).scoreDocs

        try {
            assertEquals(3, h.size, "3 docs should match ${q.toString()}")
            val score = h[0].score
            for (i in 1..<h.size) {
                assertEquals(score, h[i].score, SCORE_COMP_THRESH, "score #$i is not the same")
            }
        } catch (e: Throwable) {
            printHits("testSimpleEqualScores2", h, s!!)
            throw e
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSimpleEqualScores3() {
        val q =
            DisjunctionMaxQuery(
                arrayListOf(
                    tq("hed", "albino"),
                    tq("hed", "elephant"),
                    tq("dek", "albino"),
                    tq("dek", "elephant")
                ),
                0.0f
            )
        QueryUtils.check(random(), q, s!!)

        val h = s!!.search(q, 1000).scoreDocs

        try {
            assertEquals(4, h.size, "all docs should match ${q.toString()}")
            val score = h[0].score
            for (i in 1..<h.size) {
                assertEquals(score, h[i].score, SCORE_COMP_THRESH, "score #$i is not the same")
            }
        } catch (e: Throwable) {
            printHits("testSimpleEqualScores3", h, s!!)
            throw e
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSimpleTiebreaker() {
        val q =
            DisjunctionMaxQuery(
                arrayListOf(tq("dek", "albino"), tq("dek", "elephant")),
                0.01f
            )
        QueryUtils.check(random(), q, s!!)

        val h = s!!.search(q, 1000).scoreDocs

        try {
            assertEquals(3, h.size, "3 docs should match ${q.toString()}")
            assertEquals("d2", s!!.storedFields().document(h[0].doc).get("id"), "wrong first")
            val score0 = h[0].score
            val score1 = h[1].score
            val score2 = h[2].score
            assertTrue(score0 > score1, "d2 does not have better score then others: $score0 >? $score1")
            assertEquals(score1, score2, SCORE_COMP_THRESH, "d4 and d1 don't have equal scores")
        } catch (e: Throwable) {
            printHits("testSimpleTiebreaker", h, s!!)
            throw e
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBooleanRequiredEqualScores() {
        val q = BooleanQuery.Builder()
        run {
            val q1 =
                DisjunctionMaxQuery(
                    arrayListOf(tq("hed", "albino"), tq("dek", "albino")),
                    0.0f
                )
            q.add(q1, BooleanClause.Occur.MUST)
            QueryUtils.check(random(), q1, s!!)
        }
        run {
            val q2 =
                DisjunctionMaxQuery(
                    arrayListOf(tq("hed", "elephant"), tq("dek", "elephant")),
                    0.0f
                )
            q.add(q2, BooleanClause.Occur.MUST)
            QueryUtils.check(random(), q2, s!!)
        }

        QueryUtils.check(random(), q.build(), s!!)

        val h = s!!.search(q.build(), 1000).scoreDocs

        try {
            assertEquals(3, h.size, "3 docs should match ${q.toString()}")
            val score = h[0].score
            for (i in 1..<h.size) {
                assertEquals(score, h[i].score, SCORE_COMP_THRESH, "score #$i is not the same")
            }
        } catch (e: Throwable) {
            printHits("testBooleanRequiredEqualScores1", h, s!!)
            throw e
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBooleanOptionalNoTiebreaker() {
        val q = BooleanQuery.Builder()
        run {
            val q1 =
                DisjunctionMaxQuery(
                    arrayListOf(tq("hed", "albino"), tq("dek", "albino")),
                    0.0f
                )
            q.add(q1, BooleanClause.Occur.SHOULD)
        }
        run {
            val q2 =
                DisjunctionMaxQuery(
                    arrayListOf(tq("hed", "elephant"), tq("dek", "elephant")),
                    0.0f
                )
            q.add(q2, BooleanClause.Occur.SHOULD)
        }
        QueryUtils.check(random(), q.build(), s!!)

        val h = s!!.search(q.build(), 1000).scoreDocs

        try {
            assertEquals(4, h.size, "4 docs should match ${q.toString()}")
            val score = h[0].score
            for (i in 1..<(h.size - 1)) {
                /* note: -1 */
                assertEquals(score, h[i].score, SCORE_COMP_THRESH, "score #$i is not the same")
            }
            assertEquals("d1", s!!.storedFields().document(h[h.size - 1].doc).get("id"), "wrong last")
            val score1 = h[h.size - 1].score
            assertTrue(score > score1, "d1 does not have worse score then others: $score >? $score1")
        } catch (e: Throwable) {
            printHits("testBooleanOptionalNoTiebreaker", h, s!!)
            throw e
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBooleanOptionalWithTiebreaker() {
        val q = BooleanQuery.Builder()
        run {
            val q1 =
                DisjunctionMaxQuery(
                    arrayListOf(tq("hed", "albino"), tq("dek", "albino")),
                    0.01f
                )
            q.add(q1, BooleanClause.Occur.SHOULD)
        }
        run {
            val q2 =
                DisjunctionMaxQuery(
                    arrayListOf(tq("hed", "elephant"), tq("dek", "elephant")),
                    0.01f
                )
            q.add(q2, BooleanClause.Occur.SHOULD)
        }
        QueryUtils.check(random(), q.build(), s!!)

        val h = s!!.search(q.build(), 1000).scoreDocs

        try {
            assertEquals(4, h.size, "4 docs should match ${q.toString()}")

            val score0 = h[0].score
            val score1 = h[1].score
            val score2 = h[2].score
            val score3 = h[3].score

            val doc0 = s!!.storedFields().document(h[0].doc).get("id")
            val doc1 = s!!.storedFields().document(h[1].doc).get("id")
            val doc2 = s!!.storedFields().document(h[2].doc).get("id")
            val doc3 = s!!.storedFields().document(h[3].doc).get("id")

            assertTrue(doc0 == "d2" || doc0 == "d4", "doc0 should be d2 or d4: $doc0")
            assertTrue(doc1 == "d2" || doc1 == "d4", "doc1 should be d2 or d4: $doc0")
            assertEquals(score0, score1, SCORE_COMP_THRESH, "score0 and score1 should match")
            assertEquals("d3", doc2, "wrong third")
            assertTrue(score1 > score2, "d3 does not have worse score then d2 and d4: $score1 >? $score2")

            assertEquals("d1", doc3, "wrong fourth")
            assertTrue(score2 > score3, "d1 does not have worse score then d3: $score2 >? $score3")
        } catch (e: Throwable) {
            printHits("testBooleanOptionalWithTiebreaker", h, s!!)
            throw e
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBooleanOptionalWithTiebreakerAndBoost() {
        val q = BooleanQuery.Builder()
        run {
            val q1 =
                DisjunctionMaxQuery(
                    arrayListOf(tq("hed", "albino", 1.5f), tq("dek", "albino")),
                    0.01f
                )
            q.add(q1, BooleanClause.Occur.SHOULD)
        }
        run {
            val q2 =
                DisjunctionMaxQuery(
                    arrayListOf(tq("hed", "elephant", 1.5f), tq("dek", "elephant")),
                    0.01f
                )
            q.add(q2, BooleanClause.Occur.SHOULD)
        }
        QueryUtils.check(random(), q.build(), s!!)

        val h = s!!.search(q.build(), 1000).scoreDocs

        try {
            assertEquals(4, h.size, "4 docs should match ${q.toString()}")

            val score0 = h[0].score
            val score1 = h[1].score
            val score2 = h[2].score
            val score3 = h[3].score

            val doc0 = s!!.storedFields().document(h[0].doc).get("id")
            val doc1 = s!!.storedFields().document(h[1].doc).get("id")
            val doc2 = s!!.storedFields().document(h[2].doc).get("id")
            val doc3 = s!!.storedFields().document(h[3].doc).get("id")

            assertEquals("d4", doc0, "doc0 should be d4: ")
            assertEquals("d3", doc1, "doc1 should be d3: ")
            assertEquals("d2", doc2, "doc2 should be d2: ")
            assertEquals("d1", doc3, "doc3 should be d1: ")

            assertTrue(score0 > score1, "d4 does not have a better score then d3: $score0 >? $score1")
            assertTrue(score1 > score2, "d3 does not have a better score then d2: $score1 >? $score2")
            assertTrue(score2 > score3, "d3 does not have a better score then d1: $score2 >? $score3")
        } catch (e: Throwable) {
            printHits("testBooleanOptionalWithTiebreakerAndBoost", h, s!!)
            throw e
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRewriteBoolean() {
        val sub1 = tq("hed", "albino")
        val sub2 = tq("hed", "elephant")
        val q = DisjunctionMaxQuery(arrayListOf(sub1, sub2), 1.0f)
        val rewritten = s!!.rewrite(q)
        val expected =
            BooleanQuery.Builder()
                .add(sub1, BooleanClause.Occur.SHOULD)
                .add(sub2, BooleanClause.Occur.SHOULD)
                .build()
        assertEquals(expected, rewritten)
    }

    @Test
    @Throws(Exception::class)
    fun testRewriteEmpty() {
        val q = DisjunctionMaxQuery(arrayListOf(), 0.0f)
        val rewritten = s!!.rewrite(q)
        val expected: Query = MatchNoDocsQuery()
        assertEquals(expected, rewritten)
    }

    @Test
    @Throws(Exception::class)
    fun testDisjunctOrderAndEquals() {
        // the order that disjuncts are provided in should not matter for equals() comparisons
        val sub1 = tq("hed", "albino")
        val sub2 = tq("hed", "elephant")
        val q1: Query = DisjunctionMaxQuery(arrayListOf(sub1, sub2), 1.0f)
        val q2: Query = DisjunctionMaxQuery(arrayListOf(sub2, sub1), 1.0f)
        assertEquals(q1, q2)
    }

    /* Inspired from TestIntervals.testIntervalDisjunctionToStringStability */
    @Test
    fun testToStringOrderMatters() {
        val clauseNbr = random().nextInt(22) + 4 // ensure a reasonably large minimum number of clauses
        val terms = Array(clauseNbr) { i -> ('a' + i).toString() }

        val expected = terms.joinToString(" | ", "(", ")~1.0") { term -> "test:$term" }

        val source =
            DisjunctionMaxQuery(
                ArrayList(terms.map { term -> tq("test", term) }),
                1.0f
            )

        assertEquals(expected, source.toString(""))
    }

    @Test
    @Throws(Exception::class)
    fun testRandomTopDocs() {
        doTestRandomTopDocs(2, 0.05f, 0.05f)
        doTestRandomTopDocs(2, 1.0f, 0.05f)
        doTestRandomTopDocs(3, 1.0f, 0.5f, 0.05f)
        doTestRandomTopDocs(4, 1.0f, 0.5f, 0.05f, 0f)
        doTestRandomTopDocs(4, 1.0f, 0.5f, 0.05f, 0f)
    }

    @Test
    @Throws(Exception::class)
    fun testExplainMatch() {
        // Both match
        val sub1 = tq("hed", "elephant")
        val sub2 = tq("dek", "elephant")

        val dq = DisjunctionMaxQuery(arrayListOf(sub1, sub2), 0.0f)

        val dw = s!!.createWeight(s!!.rewrite(dq), ScoreMode.COMPLETE, 1f)
        val context = s!!.topReaderContext as LeafReaderContext
        val explanation = dw.explain(context, 1)

        assertEquals("max of:", explanation.description)
        // Two matching sub queries should be included in the explanation details
        assertEquals(2, explanation.getDetails().size)
    }

    @Test
    @Throws(Exception::class)
    fun testExplainNoMatch() {
        // No match
        val sub1 = tq("abc", "elephant")
        val sub2 = tq("def", "elephant")

        val dq = DisjunctionMaxQuery(arrayListOf(sub1, sub2), 0.0f)

        val dw = s!!.createWeight(s!!.rewrite(dq), ScoreMode.COMPLETE, 1f)
        val context = s!!.topReaderContext as LeafReaderContext
        val explanation = dw.explain(context, 1)

        assertEquals("No matching clause", explanation.description)
        // Two non-matching sub queries should be included in the explanation details
        assertEquals(2, explanation.getDetails().size)
    }

    @Test
    @Throws(Exception::class)
    fun testExplainMatch_OneNonMatchingSubQuery_NotIncludedInExplanation() {
        // Matches
        val sub1 = tq("hed", "elephant")

        // Doesn't match
        val sub2 = tq("def", "elephant")

        val dq = DisjunctionMaxQuery(arrayListOf(sub1, sub2), 0.0f)

        val dw = s!!.createWeight(s!!.rewrite(dq), ScoreMode.COMPLETE, 1f)
        val context = s!!.topReaderContext as LeafReaderContext
        val explanation = dw.explain(context, 1)

        assertEquals("max of:", explanation.description)
        // Only the matching sub query (sub1) should be included in the explanation details
        assertEquals(1, explanation.getDetails().size)
    }

    @Test
    fun testGenerics() {
        var query =
            DisjunctionMaxQuery(
                ArrayList(arrayOf("term").map { term -> tq("test", term) }),
                1.0f
            )
        assertEquals(1, query.getDisjuncts().size)

        val disjuncts =
            listOf<Query>(
                RegexpQuery(Term("field", "foobar")),
                WildcardQuery(Term("field", "foobar"))
            )
        query = DisjunctionMaxQuery(ArrayList(disjuncts), 1.0f)
        assertEquals(2, query.getDisjuncts().size)
    }

    @Throws(Exception::class)
    private fun doTestRandomTopDocs(numFields: Int, vararg freqs: Float) {
        assertEquals(numFields, freqs.size)
        val dir = newDirectory()
        val config = IndexWriterConfig(StandardAnalyzer())
        val w = IndexWriter(dir, config)

        val numDocs =
            if (TEST_NIGHTLY) {
                atLeast(1000)
            } else {
                atLeast(100) // at night, make sure some terms have skip data
            }
        for (i in 0..<numDocs) {
            val doc = Document()
            for (j in 0..<numFields) {
                val builder = StringBuilder()
                val numAs = if (random().nextDouble() < freqs[j]) 0 else 1 + random().nextInt(5)
                for (k in 0..<numAs) {
                    if (builder.isNotEmpty()) {
                        builder.append(' ')
                    }
                    builder.append('a')
                }
                if (random().nextBoolean()) {
                    doc.add(StringField("field", "c", Field.Store.NO))
                }
                val numOthers = if (random().nextBoolean()) 0 else 1 + random().nextInt(5)
                for (k in 0..<numOthers) {
                    if (builder.isNotEmpty()) {
                        builder.append(' ')
                    }
                    builder.append(random().nextInt().toString())
                }
                doc.add(TextField(j.toString(), StringReader(builder.toString())))
            }
            w.addDocument(doc)
        }
        val reader = DirectoryReader.open(w)
        w.close()
        val searcher = newSearcher(reader)
        for (i in 0..<4) {
            val clauses = ArrayList<Query>()
            for (j in 0..<numFields) {
                if (i % 2 == 1) {
                    clauses.add(tq(j.toString(), "a"))
                } else {
                    val boost = if (random().nextBoolean()) 0f else random().nextFloat()
                    if (boost > 0) {
                        clauses.add(tq(j.toString(), "a", boost))
                    } else {
                        clauses.add(tq(j.toString(), "a"))
                    }
                }
            }
            val tieBreaker = random().nextFloat()
            var query: Query = DisjunctionMaxQuery(clauses, tieBreaker)
            CheckHits.checkTopScores(random(), query, searcher)

            query =
                BooleanQuery.Builder()
                    .add(DisjunctionMaxQuery(clauses, tieBreaker), BooleanClause.Occur.MUST)
                    .add(tq("field", "c"), BooleanClause.Occur.FILTER)
                    .build()
            CheckHits.checkTopScores(random(), query, searcher)
        }
        reader.close()
        dir.close()
    }

    /** macro */
    protected fun tq(f: String, t: String): TermQuery {
        return TermQuery(Term(f, t))
    }

    /** macro */
    protected fun tq(f: String, t: String, b: Float): BoostQuery {
        val q: Query = tq(f, t)
        return BoostQuery(q, b)
    }

    protected fun printHits(test: String, h: Array<ScoreDoc>, searcher: IndexSearcher) {
        println("------- $test -------")

        val storedFields: StoredFields = searcher.storedFields()
        for (i in h.indices) {
            val d = storedFields.document(h[i].doc)
            val score = h[i].score
            println("#$i: $score - ${d.get("id")}")
        }
    }

    companion object {
        private val nonAnalyzedType = FieldType(TextField.TYPE_STORED)

        init {
            nonAnalyzedType.setTokenized(false)
        }
    }
}
