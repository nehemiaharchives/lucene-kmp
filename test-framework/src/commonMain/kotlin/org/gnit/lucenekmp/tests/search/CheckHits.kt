package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.search.Collector
import org.gnit.lucenekmp.search.CollectorManager
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Matches
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.SimpleCollector
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TopFieldDocs
import org.gnit.lucenekmp.search.TopScoreDocCollectorManager
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.tests.util.TestUtil.Companion.rarely
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail


/** Utility class for asserting expected hits in tests.  */
object CheckHits {
    /**
     * Tests that all documents up to maxDoc which are *not* in the expected result set, have an
     * explanation which indicates that the document does not match
     */
    @Throws(IOException::class)
    fun checkNoMatchExplanations(
        q: Query,
        defaultFieldName: String,
        searcher: IndexSearcher,
        results: IntArray
    ) {
        val d: String = q.toString(defaultFieldName)
        val ignore: MutableSet<Int> = TreeSet()
        for (i in results.indices) {
            ignore.add(results[i])
        }

        val maxDoc: Int = searcher.indexReader.maxDoc()
        for (doc in 0..<maxDoc) {
            if (ignore.contains(doc)) continue

            val exp: Explanation = searcher.explain(q, doc)
            assertNotNull(exp, "Explanation of [[$d]] for #$doc is null")
            assertFalse(
                exp.isMatch,
                ("Explanation of [["
                        + d
                        + "]] for #"
                        + doc
                        + " doesn't indicate non-match: "
                        + exp.toString())
            )
        }
    }

    /**
     * Tests that a query matches the an expected set of documents using a HitCollector.
     *
     *
     * Note that when using the HitCollector API, documents will be collected if they "match"
     * regardless of what their score is.
     *
     * @param query the query to test
     * @param searcher the searcher to test the query against
     * @param defaultFieldName used for displaying the query in assertion messages
     * @param results a list of documentIds that must match the query
     * @see .checkHits
     */
    @Throws(IOException::class)
    fun checkHitCollector(
        random: Random,
        query: Query,
        defaultFieldName: String,
        searcher: IndexSearcher,
        results: IntArray
    ) {
        QueryUtils.check(random, query, searcher)

        val correct: MutableSet<Int> = TreeSet()
        for (i in results.indices) {
            correct.add(results[i])
        }

        var actual: MutableSet<Int> = searcher.search(query, SetCollectorManager())
        assertEquals(correct, actual, message = "Simple: " + query.toString(defaultFieldName))

        for (i in -1..1) {
            actual.clear()
            val s: IndexSearcher = QueryUtils.wrapUnderlyingReader(random, searcher, i)
            actual = s.search(query, SetCollectorManager())
            assertEquals(correct, actual, message = "Wrap Reader " + i + ": " + query.toString(defaultFieldName))
        }
    }

    /**
     * Tests that a query matches the expected set of documents using Hits.
     *
     *
     * Note that when using the Hits API, documents will only be returned if they have a positive
     * normalized score.
     *
     * @param query the query to test
     * @param searcher the searcher to test the query against
     * @param defaultFieldName used for displaing the query in assertion messages
     * @param results a list of documentIds that must match the query
     * @see .checkHitCollector
     */
    @Throws(IOException::class)
    fun checkHits(
        random: Random,
        query: Query,
        defaultFieldName: String,
        searcher: IndexSearcher,
        results: IntArray
    ) {
        val hits: Array<ScoreDoc> =
            searcher.search(query, max(10, results.size * 2)).scoreDocs

        val correct: MutableSet<Int> = TreeSet()
        for (result in results) {
            correct.add(result)
        }

        val actual: MutableSet<Int> = TreeSet()
        for (hit in hits) {
            actual.add(hit.doc)
        }

        assertEquals( correct, actual, message = query.toString(defaultFieldName))

        QueryUtils.check(
            random,
            query,
            searcher,
            rarely(random)
        )
    }

    /** Tests that a Hits has an expected order of documents  */
    fun checkDocIds(
        mes: String,
        results: IntArray,
        hits: Array<ScoreDoc>
    ) {
        assertEquals(
            hits.size.toLong(),
            results.size.toLong(),
            message = "$mes nr of hits"
        )
        for (i in results.indices) {
            assertEquals(
                results[i].toLong(),
                hits[i].doc.toLong(),
                "$mes doc nrs for hit $i"
            )
        }
    }

    /**
     * Tests that two queries have an expected order of documents, and that the two queries have the
     * same score values.
     */
    fun checkHitsQuery(
        query: Query,
        hits1: Array<ScoreDoc>,
        hits2: Array<ScoreDoc>,
        results: IntArray
    ) {
        checkDocIds("hits1", results, hits1)
        checkDocIds("hits2", results, hits2)
        checkEqual(query, hits1, hits2)
    }

    fun checkEqual(
        query: Query,
        hits1: Array<ScoreDoc>,
        hits2: Array<ScoreDoc>
    ) {
        val scoreTolerance = 1.0e-6f
        if (hits1.size != hits2.size) {
            fail("Unequal lengths: hits1=" + hits1.size + ",hits2=" + hits2.size)
        }
        for (i in hits1.indices) {
            if (hits1[i].doc != hits2[i].doc) {
                fail(
                    (("Hit $i")
                            + (" docnumbers don't match\n" + hits2str(hits1, hits2, 0, 0))
                            + ("for query:$query"))
                )
            }

            if ((hits1[i].doc != hits2[i].doc)
                || abs(hits1[i].score - hits2[i].score) > scoreTolerance
            ) {
                fail(
                    (("Hit $i")
                            + (", doc nrs " + hits1[i].doc)
                            + (" and " + hits2[i].doc)
                            + ("\nunequal       : " + hits1[i].score)
                            + ("\n           and: " + hits2[i].score)
                            + ("\nfor query:$query"))
                )
            }
        }
    }

    fun hits2str(
        hits1: Array<ScoreDoc>,
        hits2: Array<ScoreDoc>,
        start: Int,
        end: Int
    ): String {
        var end = end
        val sb = StringBuilder()
        val len1 = if (hits1 == null) 0 else hits1.size
        val len2 = if (hits2 == null) 0 else hits2.size
        if (end <= 0) {
            end = max(len1, len2)
        }

        sb.append("Hits length1=").append(len1).append("\tlength2=").append(len2)

        sb.append('\n')
        for (i in start..<end) {
            sb.append("hit=").append(i).append(':')
            if (i < len1) {
                sb.append(" doc")
                    .append(hits1[i].doc)
                    .append('=')
                    .append(hits1[i].score)
                    .append(" shardIndex=")
                    .append(hits1[i].shardIndex)
            } else {
                sb.append("               ")
            }
            sb.append(",\t")
            if (i < len2) {
                sb.append(" doc")
                    .append(hits2[i].doc)
                    .append('=')
                    .append(hits2[i].score)
                    .append(" shardIndex=")
                    .append(hits2[i].shardIndex)
            }

            sb.append('\n')
        }
        return sb.toString()
    }

    fun topdocsString(docs: TopDocs, start: Int, end: Int): String {
        var end = end
        val sb = StringBuilder()
        sb.append("TopDocs totalHits=")
            .append(docs.totalHits)
            .append(" top=")
            .append(docs.scoreDocs.size)
            .append('\n')
        end = if (end <= 0) docs.scoreDocs.size
        else min(end, docs.scoreDocs.size)
        for (i in start..<end) {
            sb.append('\t')
            sb.append(i)
            sb.append(") doc=")
            sb.append(docs.scoreDocs[i].doc)
            sb.append("\tscore=")
            sb.append(docs.scoreDocs[i].score)
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * Asserts that the explanation value for every document matching a query corresponds with the
     * true score. Optionally does "deep" testing of the explanation details.
     *
     * @param query the query to test
     * @param searcher the searcher to test the query against
     * @param defaultFieldName used for displaing the query in assertion messages
     * @param deep indicates whether a deep comparison of sub-Explanation details should be executed
     * @see ExplanationAsserter
     */
    /**
     * Asserts that the explanation value for every document matching a query corresponds with the
     * true score.
     *
     * @param query the query to test
     * @param searcher the searcher to test the query against
     * @param defaultFieldName used for displaing the query in assertion messages
     * @see ExplanationAsserter
     *
     * @see .checkExplanations
     */
    @Throws(IOException::class)
    fun checkExplanations(
        query: Query,
        defaultFieldName: String?,
        searcher: IndexSearcher,
        deep: Boolean = false
    ) {
        searcher.search(
            query,
            object :
                CollectorManager<ExplanationAsserter, Unit> {
                override fun newCollector(): ExplanationAsserter {
                    return ExplanationAsserter(query, defaultFieldName, searcher, deep)
                }

                override fun reduce(collectors: MutableCollection<ExplanationAsserter>) {
                }
            })
    }

    /**
     * Asserts that the result of calling [Weight.matches] for every
     * document matching a query returns a non-null [Matches]
     *
     * @param query the query to test
     * @param searcher the search to test against
     */
    @Throws(IOException::class)
    fun checkMatches(
        query: Query,
        searcher: IndexSearcher
    ) {
        searcher.search(
            query,
            object :
                CollectorManager<MatchesAsserter, Unit> {
                @Throws(IOException::class)
                override fun newCollector(): MatchesAsserter {
                    return MatchesAsserter(query, searcher)
                }

                override fun reduce(collectors: MutableCollection<MatchesAsserter>) {
                }
            })
    }

    private val COMPUTED_FROM_PATTERN: Regex =
        Regex(".*, computed as .* from:")

    /**
     * Assert that an explanation has the expected score, and optionally that its sub-details
     * max/sum/factor match to that score.
     *
     * @param q String representation of the query for assertion messages
     * @param doc Document ID for assertion messages
     * @param score Real score value of doc with query q
     * @param deep indicates whether a deep comparison of sub-Explanation details should be executed
     * @param expl The Explanation to match against score
     */
    // TODO: speed up this method to not be so slow
    fun verifyExplanation(
        q: String,
        doc: Int,
        score: Float,
        deep: Boolean,
        expl: Explanation
    ) {
        val value = expl.value.toFloat()
        // TODO: clean this up if we use junit 5 (the assert message is costly)
        try {
            assertEquals(score.toDouble(), value.toDouble(), 0.0)
        } catch (e: Exception) {
            fail(
                (q
                        + ": score(doc="
                        + doc
                        + ")="
                        + score
                        + " != explanationScore="
                        + value
                        + " Explanation: "
                        + expl)
            )
        }

        if (!deep) return

        val detail: Array<Explanation> = expl.getDetails()
        // TODO: can we improve this entire method it's really geared to work only with TF/IDF
        if (expl.description.endsWith("computed from:")) {
            return  // something more complicated.
        }
        val descr: String = expl.description.lowercase()
        if (descr.startsWith("score based on ") && descr.contains("child docs in range")) {
            assertTrue(detail.isNotEmpty(), message = "Child doc explanations are missing")
        }
        if (detail.isNotEmpty() && expl.isMatch) {
            if (detail.size == 1 && COMPUTED_FROM_PATTERN.matches(descr) == false) {
                // simple containment, unless it's a freq of: (which lets a query explain how the freq is
                // calculated),
                // just verify contained expl has same score
                if (expl.description
                        .endsWith("with freq of:") == false // with dismax, even if there is a single sub explanation, its
                    // score might be different if the score is negative
                    && (score >= 0 || expl.description.endsWith("times others of:") == false)
                ) {
                    verifyExplanation(q, doc, score, deep, detail[0])
                }
            } else {
                // explanation must either:
                // - end with one of: "product of:", "sum of:", "max of:", or
                // - have "max plus <x> times others" (where <x> is float).
                var x = 0f
                val productOf = descr.endsWith("product of:")
                val sumOf = descr.endsWith("sum of:")
                val maxOf = descr.endsWith("max of:")
                val computedOf =
                    descr.indexOf("computed as") > 0 && COMPUTED_FROM_PATTERN.matches(descr)
                var maxTimesOthers = false
                if (!(productOf || sumOf || maxOf || computedOf)) {
                    // maybe 'max plus x times others'
                    var k1 = descr.indexOf("max plus ")
                    if (k1 >= 0) {
                        k1 += "max plus ".length
                        val k2 = descr.indexOf(' ', k1)
                        try {
                            x = descr.substring(k1, k2).trim { it <= ' ' }.toFloat()
                            if (descr.substring(k2).trim { it <= ' ' } == "times others of:") {
                                maxTimesOthers = true
                            }
                        } catch (e: NumberFormatException) {
                        }
                    }
                }
                // TODO: this is a TERRIBLE assertion!!!!
                if (false == (productOf || sumOf || maxOf || computedOf || maxTimesOthers)) {
                    fail(
                        (q
                                + ": multi valued explanation description=\""
                                + descr
                                + "\" must be 'max of plus x times others', 'computed as x from:' or end with 'product of'"
                                + " or 'sum of:' or 'max of:' - "
                                + expl)
                    )
                }
                var sum = 0.0
                var product = 1f
                var max = Float.NEGATIVE_INFINITY
                var maxError = 0.0
                for (i in detail.indices) {
                    val dval = detail[i].value.toFloat()
                    verifyExplanation(q, doc, dval, deep, detail[i])
                    product *= dval
                    sum += dval.toDouble()
                    max = max(max, dval)

                    if (sumOf) {
                        // "sum of" is used by BooleanQuery. Making it accurate is not
                        // easy since ReqOptSumScorer casts some intermediate
                        // contributions to the score to a float before doing another sum.
                        // So we introduce some (reasonable) leniency.
                        // TODO: remove this leniency
                        maxError += (Math.ulp(dval) * 2).toDouble()
                    }
                }
                val combined: Float
                if (productOf) {
                    combined = product
                } else if (sumOf) {
                    combined = sum.toFloat()
                } else if (maxOf) {
                    combined = max
                } else if (maxTimesOthers) {
                    combined = (max + x * (sum - max)).toFloat()
                } else {
                    assertTrue( computedOf, "should never get here!")
                    combined = value
                }
                // TODO: clean this up if we use junit 5 (the assert message is costly)
                try {
                    assertEquals(combined.toDouble(), value.toDouble(), maxError)
                } catch (e: Exception) {
                    fail(
                        (q
                                + ": actual subDetails combined=="
                                + combined
                                + " != value="
                                + value
                                + " Explanation: "
                                + expl)
                    )
                }
            }
        }
    }

    @Throws(IOException::class)
    fun checkTopScores(
        random: Random,
        query: Query,
        searcher: IndexSearcher
    ) {
        // Check it computed the top hits correctly
        doCheckTopScores(query, searcher, 1)
        doCheckTopScores(query, searcher, 10)

        // Now check that the exposed max scores and block boundaries are valid
        doCheckMaxScores(random, query, searcher)
    }

    @Throws(IOException::class)
    private fun doCheckTopScores(
        query: Query,
        searcher: IndexSearcher,
        numHits: Int
    ) {
        val complete = TopScoreDocCollectorManager(
                numHits,
                null,
                Int.MAX_VALUE
            ) // COMPLETE
        val topScores = TopScoreDocCollectorManager(numHits, null, 1) // TOP_SCORES
        val completeTopDocs: TopDocs = searcher.search(query, complete)
        val topScoresTopDocs: TopDocs = searcher.search(query, topScores)
        checkEqual(query, completeTopDocs.scoreDocs, topScoresTopDocs.scoreDocs)
    }

    @Throws(IOException::class)
    private fun doCheckMaxScores(
        random: Random,
        query: Query,
        searcher: IndexSearcher
    ) {
        var query: Query = query
        query = searcher.rewrite(query)
        val w1: Weight =
            searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        val w2: Weight =
            searcher.createWeight(query, ScoreMode.TOP_SCORES, 1f)

        // Check boundaries and max scores when iterating all matches
        for (ctx in searcher.indexReader.leaves()) {
            val s1: Scorer? = w1.scorer(ctx)
            val ss2: ScorerSupplier? = w2.scorerSupplier(ctx)
            var s2: Scorer?
            if (ss2 == null) {
                s2 = null
            } else {
                // We'll call setMinCompetitiveScore on s2
                ss2.setTopLevelScoringClause()
                s2 = ss2.get(Long.MAX_VALUE)
            }
            if (s1 == null) {
                assertTrue(
                    s2 == null || s2.iterator()
                        .nextDoc() == DocIdSetIterator.NO_MORE_DOCS
                )
                continue
            }
            if (s2 == null) {
                assertEquals(
                    s1.iterator()
                        .nextDoc(), DocIdSetIterator.NO_MORE_DOCS
                )
                continue
            }
            val twoPhase1: TwoPhaseIterator? = s1.twoPhaseIterator()
            val twoPhase2: TwoPhaseIterator? = s2.twoPhaseIterator()
            val approx1: DocIdSetIterator =
                if (twoPhase1 == null) s1.iterator() else twoPhase1.approximation()
            val approx2: DocIdSetIterator =
                if (twoPhase2 == null) s2.iterator() else twoPhase2.approximation()
            var upTo = -1
            var maxScore = 0f
            var minScore = 0f
            var doc2: Int = approx2.nextDoc()
            while (true) {
                var doc1: Int
                doc1 = approx1.nextDoc()
                while (doc1 < doc2) {
                    if (twoPhase1 == null || twoPhase1.matches()) {
                        assertTrue(s1.score() < minScore)
                    }
                    doc1 = approx1.nextDoc()
                }
                assertEquals(doc1.toLong(), doc2.toLong())
                if (doc2 == DocIdSetIterator.NO_MORE_DOCS) {
                    break
                }

                if (doc2 > upTo) {
                    upTo = s2.advanceShallow(doc2)
                    assertTrue(upTo >= doc2)
                    maxScore = s2.getMaxScore(upTo)
                }

                if (twoPhase2 == null || twoPhase2.matches()) {
                    assertTrue(twoPhase1 == null || twoPhase1.matches())
                    val score: Float = s2.score()
                    assertEquals(s1.score(), score, 0f)
                    assertTrue(score <= maxScore, message = "$score > $maxScore up to $upTo")

                    if (score >= minScore && random.nextInt(10) == 0) {
                        // On some scorers, changing the min score changes the way that docs are iterated
                        minScore = score
                        s2.minCompetitiveScore = minScore
                    }
                }
                doc2 = approx2.nextDoc()
            }
        }

        // Now check advancing
        for (ctx in searcher.indexReader.leaves()) {
            val s1: Scorer? = w1.scorer(ctx)
            val ss2: ScorerSupplier? = w2.scorerSupplier(ctx)
            val s2: Scorer?
            if (ss2 == null) {
                s2 = null
            } else {
                // We'll call setMinCompetitiveScore on s2
                ss2.setTopLevelScoringClause()
                s2 = ss2.get(Long.Companion.MAX_VALUE)
            }
            if (s1 == null) {
                assertTrue(
                    s2 == null || s2.iterator()
                        .nextDoc() == DocIdSetIterator.NO_MORE_DOCS
                )
                continue
            }
            if (s2 == null) {
                assertEquals(
                    s1.iterator()
                        .nextDoc(), DocIdSetIterator.NO_MORE_DOCS
                )
                continue
            }
            val twoPhase1: TwoPhaseIterator? = s1.twoPhaseIterator()
            val twoPhase2: TwoPhaseIterator? = s2.twoPhaseIterator()
            val approx1: DocIdSetIterator =
                if (twoPhase1 == null) s1.iterator() else twoPhase1.approximation()
            val approx2: DocIdSetIterator =
                if (twoPhase2 == null) s2.iterator() else twoPhase2.approximation()

            var upTo = -1
            var minScore = 0f
            var maxScore = 0f
            while (true) {
                var doc2: Int = s2.docID()
                val advance: Boolean
                val target: Int
                if (random.nextBoolean()) {
                    advance = false
                    target = doc2 + 1
                } else {
                    advance = true
                    val delta: Int = min(
                        1 + random.nextInt(512),
                        DocIdSetIterator.NO_MORE_DOCS - doc2
                    )
                    target = s2.docID() + delta
                }

                if (target > upTo && random.nextBoolean()) {
                    val delta: Int = min(
                        random.nextInt(512),
                        DocIdSetIterator.NO_MORE_DOCS - target
                    )
                    upTo = target + delta
                    val m: Int = s2.advanceShallow(target)
                    assertTrue(m >= target)
                    maxScore = s2.getMaxScore(upTo)
                }

                if (advance) {
                    doc2 = approx2.advance(target)
                } else {
                    doc2 = approx2.nextDoc()
                }

                var doc1: Int
                doc1 = approx1.advance(target)
                while (doc1 < doc2) {
                    if (twoPhase1 == null || twoPhase1.matches()) {
                        assertTrue(s1.score() < minScore)
                    }
                    doc1 = approx1.nextDoc()
                }
                assertEquals(doc1.toLong(), doc2.toLong())

                if (doc2 == DocIdSetIterator.NO_MORE_DOCS) {
                    break
                }

                if (twoPhase2 == null || twoPhase2.matches()) {
                    assertTrue(twoPhase1 == null || twoPhase1.matches())
                    val score: Float = s2.score()
                    assertEquals(s1.score(), score, 0f)

                    if (doc2 > upTo) {
                        upTo = s2.advanceShallow(doc2)
                        assertTrue(upTo >= doc2)
                        maxScore = s2.getMaxScore(upTo)
                    }

                    assertTrue(score <= maxScore)

                    if (score >= minScore && random.nextInt(10) == 0) {
                        // On some scorers, changing the min score changes the way that docs are iterated
                        minScore = score
                        s2.minCompetitiveScore = minScore
                    }
                }
            }
        }
    }

    private class SetCollectorManager :
        CollectorManager<SetCollector, MutableSet<Int>> {
        @Throws(IOException::class)
        override fun newCollector(): SetCollector {
            return SetCollector(mutableSetOf())
        }

        @Throws(IOException::class)
        override fun reduce(collectors: MutableCollection<SetCollector>): MutableSet<Int> {
            val ids: MutableSet<Int> = TreeSet()
            for (collector in collectors) {
                ids.addAll(collector.bag)
            }
            return ids
        }
    }

    /** Just collects document ids into a set.  */
    class SetCollector(val bag: MutableSet<Int>) : SimpleCollector() {
        private var base = 0

        override var scorer: Scorable? = null

        override var weight: Weight? = null

        override fun collect(doc: Int) {
            bag.add(doc + base)
        }

        @Throws(IOException::class)
        override fun doSetNextReader(context: LeafReaderContext) {
            base = context.docBase
        }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE_NO_SCORES
        }
    }

    /**
     * an IndexSearcher that implicitly checks hte explanation of every match whenever it executes a
     * search.
     *
     * @see ExplanationAsserter
     */
    class ExplanationAssertingSearcher(r: IndexReader) :
        IndexSearcher(r) {
        @Throws(IOException::class)
        protected fun checkExplanations(q: Query) {
            super.search(
                q,
                object :
                    CollectorManager<ExplanationAsserter, Any> {
                    override fun newCollector(): ExplanationAsserter {
                        return ExplanationAsserter(q, null, this@ExplanationAssertingSearcher)
                    }

                    override fun reduce(collectors: MutableCollection<ExplanationAsserter>): Any? {
                        return null
                    }
                })
        }

        @Throws(IOException::class)
        override fun search(
            query: Query,
            n: Int,
            sort: Sort
        ): TopFieldDocs {
            checkExplanations(query)
            return super.search(query, n, sort)
        }

        @Throws(IOException::class)
        override fun search(
            query: Query,
            collector: Collector
        ) {
            checkExplanations(query)
            super.search(query, collector)
        }

        @Throws(IOException::class)
        override fun <C : Collector, T> search(
            query: Query,
            collectorManager: CollectorManager<C, T>
        ): T {
            checkExplanations(query)
            return super.search(query, collectorManager)
        }

        @Throws(IOException::class)
        override fun search(
            query: Query,
            n: Int
        ): TopDocs {
            checkExplanations(query)
            return super.search(query, n)
        }
    }

    /**
     * Asserts that the score explanation for every document matching a query corresponds with the
     * true score.
     *
     *
     * NOTE: this HitCollector should only be used with the Query and Searcher specified at when it
     * is constructed.
     *
     * @see CheckHits.verifyExplanation
     */
    class ExplanationAsserter(
        var q: Query,
        defaultFieldName: String?,
        var s: IndexSearcher,
        deep: Boolean = false
    ) : SimpleCollector() {
        var d: String = q.toString(defaultFieldName)
        var deep: Boolean

        override var scorer: Scorable? = null

        override var weight: Weight? = null

        private var base = 0

        /** Constructs an instance which does shallow tests on the Explanation  */
        init {
            this.deep = deep
        }

        @Throws(IOException::class)
        override fun collect(doc: Int) {
            var doc = doc
            var exp: Explanation? = null
            doc += base
            try {
                exp = s.explain(q, doc)
            } catch (e: IOException) {
                throw RuntimeException(
                    "exception in hitcollector of [[$d]] for #$doc",
                    e
                )
            }

            assertNotNull(exp, message = "Explanation of [[$d]] for #$doc is null")
            verifyExplanation(d, doc, scorer!!.score(), deep, exp)
            assertTrue(
                exp.isMatch,
                message = ("Explanation of [["
                        + d
                        + "]] for #"
                        + doc
                        + " does not indicate match: "
                        + exp.toString())
            )
        }

        @Throws(IOException::class)
        override fun doSetNextReader(context: LeafReaderContext) {
            base = context.docBase
        }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE
        }
    }

    /**
     * Asserts that the [Matches] from a query is non-null whenever the document its created for
     * is a hit.
     *
     *
     * Also checks that the previous non-matching document has a `null` [Matches]
     */
    class MatchesAsserter(
        query: Query,
        searcher: IndexSearcher
    ) : SimpleCollector() {
        override var weight: Weight? = searcher.createWeight(
            searcher.rewrite(query),
            ScoreMode.COMPLETE_NO_SCORES,
            1f
        )
        private var context: LeafReaderContext? = null
        var lastCheckedDoc: Int = -1

        // with intra-segment concurrency, we may start from a doc id that isn't -1. We need to make
        // sure that we don't go outside of the bounds of the current slice, meaning -1 can't be
        // reliably used to signal that we are collecting the first doc for a given segment partition.
        var collectedOnce: Boolean = false

        @Throws(IOException::class)
        override fun doSetNextReader(context: LeafReaderContext) {
            this.context = context
            this.lastCheckedDoc = -1
        }

        @Throws(IOException::class)
        override fun collect(doc: Int) {
            val matches: Matches? = this.weight!!.matches(context!!, doc)
            assertNotNull(matches, message = "Unexpected null Matches object in doc" + doc + " for query " + this.weight!!.query)
            if (collectedOnce && lastCheckedDoc != doc - 1) {
                assertNull(this.weight!!.matches(context!!, doc - 1), message = ("Unexpected non-null Matches object in non-matching doc"
                        + doc
                        + " for query "
                        + this.weight!!.query)
                )
            }
            collectedOnce = true
            lastCheckedDoc = doc
        }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE_NO_SCORES
        }
    }
}
