package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Executors
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.DummyTotalHitCountCollector
import org.gnit.lucenekmp.tests.search.FixedBitSetCollector
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.RandomizedTest
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.NamedThreadFactory
import org.gnit.lucenekmp.util.automaton.Operations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestBooleanQuery : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testEquality() {
        val bq1 = BooleanQuery.Builder()
        bq1.add(TermQuery(Term("field", "value1")), BooleanClause.Occur.SHOULD)
        bq1.add(TermQuery(Term("field", "value2")), BooleanClause.Occur.SHOULD)
        val nested1 = BooleanQuery.Builder()
        nested1.add(TermQuery(Term("field", "nestedvalue1")), BooleanClause.Occur.SHOULD)
        nested1.add(TermQuery(Term("field", "nestedvalue2")), BooleanClause.Occur.SHOULD)
        bq1.add(nested1.build(), BooleanClause.Occur.SHOULD)

        val bq2 = BooleanQuery.Builder()
        bq2.add(TermQuery(Term("field", "value1")), BooleanClause.Occur.SHOULD)
        bq2.add(TermQuery(Term("field", "value2")), BooleanClause.Occur.SHOULD)
        val nested2 = BooleanQuery.Builder()
        nested2.add(TermQuery(Term("field", "nestedvalue1")), BooleanClause.Occur.SHOULD)
        nested2.add(TermQuery(Term("field", "nestedvalue2")), BooleanClause.Occur.SHOULD)
        bq2.add(nested2.build(), BooleanClause.Occur.SHOULD)

        assertEquals(bq1.build(), bq2.build())
    }

    @Test
    fun testEqualityDoesNotDependOnOrder() {
        val queries =
            arrayOf(
                TermQuery(Term("foo", "bar")),
                TermQuery(Term("foo", "baz"))
            )
        for (iter in 0 until 10) {
            val clauses = mutableListOf<BooleanClause>()
            val numClauses = random().nextInt(20)
            for (i in 0 until numClauses) {
                var query: Query = RandomPicks.randomFrom(random(), queries)
                if (random().nextBoolean()) {
                    query = BoostQuery(query, random().nextFloat())
                }
                val occur = RandomPicks.randomFrom(random(), Occur.entries.toTypedArray())
                clauses.add(BooleanClause(query, occur))
            }

            val minShouldMatch = random().nextInt(5)
            val bq1Builder = BooleanQuery.Builder()
            bq1Builder.setMinimumNumberShouldMatch(minShouldMatch)
            for (clause in clauses) {
                bq1Builder.add(clause)
            }
            val bq1 = bq1Builder.build()

            clauses.shuffle(random())
            val bq2Builder = BooleanQuery.Builder()
            bq2Builder.setMinimumNumberShouldMatch(minShouldMatch)
            for (clause in clauses) {
                bq2Builder.add(clause)
            }
            val bq2 = bq2Builder.build()

            QueryUtils.checkEqual(bq1, bq2)
        }
    }

    @Test
    fun testEqualityOnDuplicateShouldClauses() {
        val bq1 =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(random().nextInt(2))
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .build()
        val bq2 =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(bq1.minimumNumberShouldMatch)
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .build()
        QueryUtils.checkUnequal(bq1, bq2)
    }

    @Test
    fun testEqualityOnDuplicateMustClauses() {
        val bq1 =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(random().nextInt(2))
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .build()
        val bq2 =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(bq1.minimumNumberShouldMatch)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .build()
        QueryUtils.checkUnequal(bq1, bq2)
    }

    @Test
    fun testEqualityOnDuplicateFilterClauses() {
        val bq1 =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(random().nextInt(2))
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .build()
        val bq2 =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(bq1.minimumNumberShouldMatch)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .build()
        QueryUtils.checkEqual(bq1, bq2)
    }

    @Test
    fun testEqualityOnDuplicateMustNotClauses() {
        val bq1 =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(random().nextInt(2))
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .build()
        val bq2 =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(bq1.minimumNumberShouldMatch)
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .build()
        QueryUtils.checkEqual(bq1, bq2)
    }

    @Test
    fun testHashCodeIsStable() {
        val bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", TestUtil.randomSimpleString(random()))), Occur.SHOULD)
                .add(TermQuery(Term("foo", TestUtil.randomSimpleString(random()))), Occur.SHOULD)
                .build()
        val hashCode = bq.hashCode()
        assertEquals(hashCode, bq.hashCode())
    }

    @Test
    fun testTooManyClauses() {
        val bq = BooleanQuery.Builder()
        for (i in 0 until IndexSearcher.maxClauseCount) {
            bq.add(TermQuery(Term("foo", "bar-$i")), Occur.SHOULD)
        }
        expectThrows(IndexSearcher.TooManyClauses::class) {
            bq.add(TermQuery(Term("foo", "bar-MAX")), Occur.SHOULD)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testNullOrSubScorer() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(newTextField("field", "a b c d", Field.Store.NO))
        w.addDocument(doc)

        val r = w.getReader(true, false)
        val s = newSearcher(r)
        s.similarity = ClassicSimilarity()

        var q = BooleanQuery.Builder()
        q.add(TermQuery(Term("field", "a")), BooleanClause.Occur.SHOULD)

        var pq = PhraseQuery("field", *emptyArray<String>())
        q.add(pq, BooleanClause.Occur.SHOULD)
        assertEquals(1, s.search(q.build(), 10).totalHits.value)

        q = BooleanQuery.Builder()
        pq = PhraseQuery("field", *emptyArray<String>())
        q.add(TermQuery(Term("field", "a")), BooleanClause.Occur.SHOULD)
        q.add(pq, BooleanClause.Occur.MUST)
        assertEquals(0, s.search(q.build(), 10).totalHits.value)

        val dmq =
            DisjunctionMaxQuery(
                mutableListOf(TermQuery(Term("field", "a")), pq),
                1.0f
            )
        assertEquals(1, s.search(dmq, 10).totalHits.value)

        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeMorgan() {
        val dir1 = newDirectory()
        val iw1 = RandomIndexWriter(random(), dir1)
        val doc1 = Document()
        doc1.add(newTextField("field", "foo bar", Field.Store.NO))
        iw1.addDocument(doc1)
        val reader1 = iw1.getReader(true, false)
        iw1.close()

        val dir2 = newDirectory()
        val iw2 = RandomIndexWriter(random(), dir2)
        val doc2 = Document()
        doc2.add(newTextField("field", "foo baz", Field.Store.NO))
        iw2.addDocument(doc2)
        val reader2 = iw2.getReader(true, false)
        iw2.close()

        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term("field", "foo")), BooleanClause.Occur.MUST)
        val wildcardQuery =
            WildcardQuery(
                Term("field", "ba*"),
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                MultiTermQuery.SCORING_BOOLEAN_REWRITE
            )
        query.add(wildcardQuery, BooleanClause.Occur.MUST_NOT)

        val multireader = MultiReader(reader1, reader2)
        var searcher = newSearcher(multireader)
        assertEquals(0, searcher.search(query.build(), 10).totalHits.value)

        val es = Executors.newFixedThreadPool(2, NamedThreadFactory("NRT search threads"))
        searcher = IndexSearcher(multireader, es)
        if (VERBOSE) println("rewritten form: ${searcher.rewrite(query.build())}")
        assertEquals(0, searcher.search(query.build(), 10).totalHits.value)
        TestUtil.shutdownExecutorService(es)

        multireader.close()
        reader1.close()
        reader2.close()
        dir1.close()
        dir2.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBS2DisjunctionNextVsAdvance() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val numDocs = atLeast(300)
        for (docUpto in 0 until numDocs) {
            var contents = "a"
            if (random().nextInt(20) <= 16) {
                contents += " b"
            }
            if (random().nextInt(20) <= 8) {
                contents += " c"
            }
            if (random().nextInt(20) <= 4) {
                contents += " d"
            }
            if (random().nextInt(20) <= 2) {
                contents += " e"
            }
            if (random().nextInt(20) <= 1) {
                contents += " f"
            }
            val doc = Document()
            doc.add(TextField("field", contents, Field.Store.NO))
            w.addDocument(doc)
        }
        w.forceMerge(1)
        val r = w.getReader(true, false)
        val s = newSearcher(r)
        w.close()

        for (iter in 0 until 10 * RANDOM_MULTIPLIER) {
            if (VERBOSE) {
                println("iter=$iter")
            }
            val terms = mutableListOf("a", "b", "c", "d", "e", "f")
            val numTerms = TestUtil.nextInt(random(), 1, terms.size)
            while (terms.size > numTerms) {
                terms.removeAt(random().nextInt(terms.size))
            }

            if (VERBOSE) {
                println("  terms=$terms")
            }

            val q = BooleanQuery.Builder()
            for (term in terms) {
                q.add(BooleanClause(TermQuery(Term("field", term)), BooleanClause.Occur.SHOULD))
            }

            var weight = s.createWeight(s.rewrite(q.build()), ScoreMode.COMPLETE, 1f)
            var scorer = weight.scorer(s.leafContexts[0])!!

            val hits = mutableListOf<ScoreDoc>()
            while (scorer.iterator().nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                hits.add(ScoreDoc(scorer.docID(), scorer.score()))
            }

            if (VERBOSE) {
                println("  ${hits.size} hits")
            }

            for (iter2 in 0 until 10) {
                weight = s.createWeight(s.rewrite(q.build()), ScoreMode.COMPLETE, 1f)
                scorer = weight.scorer(s.leafContexts[0])!!

                if (VERBOSE) {
                    println("  iter2=$iter2")
                }

                var upto = -1
                while (upto < hits.size) {
                    val nextUpto: Int
                    val nextDoc: Int
                    val left = hits.size - upto
                    if (left == 1 || random().nextBoolean()) {
                        nextUpto = 1 + upto
                        nextDoc = scorer.iterator().nextDoc()
                    } else {
                        val inc = TestUtil.nextInt(random(), 1, left - 1)
                        nextUpto = inc + upto
                        nextDoc = scorer.iterator().advance(hits[nextUpto].doc)
                    }

                    if (nextUpto == hits.size) {
                        assertEquals(DocIdSetIterator.NO_MORE_DOCS, nextDoc)
                    } else {
                        val hit = hits[nextUpto]
                        assertEquals(hit.doc, nextDoc)
                        assertTrue(
                            hit.score == scorer.score(),
                            "doc ${hit.doc} has wrong score: expected=${hit.score} actual=${scorer.score()}"
                        )
                    }
                    upto = nextUpto
                }
            }
        }

        r.close()
        d.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMinShouldMatchLeniency() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(newTextField("field", "a b c d", Field.Store.NO))
        w.addDocument(doc)
        val r = DirectoryReader.open(w)
        val s = newSearcher(r)
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term("field", "a")), BooleanClause.Occur.SHOULD)
        bq.add(TermQuery(Term("field", "b")), BooleanClause.Occur.SHOULD)

        bq.setMinimumNumberShouldMatch(4)
        assertEquals(0, s.search(bq.build(), 1).totalHits.value)
        r.close()
        w.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun getMatches(searcher: IndexSearcher, query: Query): FixedBitSet {
        return searcher.search(query, FixedBitSetCollector.createManager(searcher.reader.maxDoc()))
    }

    @Test
    @Throws(IOException::class)
    fun testFILTERClauseBehavesLikeMUST() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = newTextField("field", "a b c d", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        f.setStringValue("b d")
        w.addDocument(doc)
        f.setStringValue("d")
        w.addDocument(doc)
        w.commit()

        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)

        val requiredTerms =
            listOf(
                listOf("a", "d"),
                listOf("a", "b", "d"),
                listOf("d"),
                listOf("e"),
                emptyList()
            )
        for (terms in requiredTerms) {
            val bq1 = BooleanQuery.Builder()
            val bq2 = BooleanQuery.Builder()
            for (term in terms) {
                val q = TermQuery(Term("field", term))
                bq1.add(q, Occur.MUST)
                bq2.add(q, Occur.FILTER)
            }

            val matches1 = getMatches(searcher, bq1.build())
            val matches2 = getMatches(searcher, bq2.build())
            assertEquals(matches1, matches2)
        }

        reader.close()
        w.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun assertSameScoresWithoutFilters(searcher: IndexSearcher, bq: BooleanQuery) {
        val bq2Builder = BooleanQuery.Builder()
        for (c in bq) {
            if (c.occur != Occur.FILTER) {
                bq2Builder.add(c)
            }
        }
        bq2Builder.setMinimumNumberShouldMatch(bq.minimumNumberShouldMatch)
        val bq2 = bq2Builder.build()

        var matched = false
        searcher.search(
            bq,
            object : CollectorManager<SimpleCollector, Unit> {
                override fun newCollector(): SimpleCollector {
                    return object : SimpleCollector() {
                        var docBase = 0
                        override var scorer: Scorable? = null
                        override var weight: Weight? = null

                        @Throws(IOException::class)
                        override fun doSetNextReader(context: LeafReaderContext) {
                            super.doSetNextReader(context)
                            docBase = context.docBase
                        }

                        override fun scoreMode(): ScoreMode {
                            return ScoreMode.COMPLETE
                        }

                        @Throws(IOException::class)
                        override fun collect(doc: Int) {
                            val actualScore = scorer!!.score()
                            val expectedScore = searcher.explain(bq2, docBase + doc).value.toFloat()
                            assertEquals(expectedScore, actualScore, 10e-5f)
                            matched = true
                        }
                    }
                }

                override fun reduce(collectors: MutableCollection<SimpleCollector>) = Unit
            }
        )

        assertTrue(matched)
    }

    @Test
    @Throws(IOException::class)
    fun testFilterClauseDoesNotImpactScore() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = newTextField("field", "a b c d", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        f.setStringValue("b d")
        w.addDocument(doc)
        f.setStringValue("a d")
        w.addDocument(doc)
        w.commit()

        val reader = w.getReader(true, false)
        val searcher = newSearcher(reader)

        var qBuilder = BooleanQuery.Builder()
        qBuilder.add(TermQuery(Term("field", "a")), Occur.FILTER)
        assertSameScoresWithoutFilters(searcher, qBuilder.build())

        qBuilder.add(TermQuery(Term("field", "b")), Occur.FILTER)
        var q = qBuilder.build()
        assertSameScoresWithoutFilters(searcher, q)

        qBuilder.add(TermQuery(Term("field", "c")), Occur.SHOULD)
        q = qBuilder.build()
        assertSameScoresWithoutFilters(searcher, q)

        qBuilder = BooleanQuery.Builder()
        qBuilder.add(TermQuery(Term("field", "a")), Occur.FILTER)
        qBuilder.add(TermQuery(Term("field", "e")), Occur.SHOULD)
        q = qBuilder.build()
        assertSameScoresWithoutFilters(searcher, q)

        qBuilder = BooleanQuery.Builder()
        qBuilder.add(TermQuery(Term("field", "a")), Occur.FILTER)
        qBuilder.add(TermQuery(Term("field", "d")), Occur.MUST)
        q = qBuilder.build()
        assertSameScoresWithoutFilters(searcher, q)

        qBuilder = BooleanQuery.Builder()
        qBuilder.add(TermQuery(Term("field", "b")), Occur.FILTER)
        qBuilder.add(TermQuery(Term("field", "a")), Occur.SHOULD)
        qBuilder.add(TermQuery(Term("field", "d")), Occur.SHOULD)
        qBuilder.setMinimumNumberShouldMatch(1)
        q = qBuilder.build()
        assertSameScoresWithoutFilters(searcher, q)

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testConjunctionPropagatesApproximations() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = newTextField("field", "a b c", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        w.commit()

        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)
        searcher.queryCache = null

        val pq = PhraseQuery("field", "a", "b")
        val q = BooleanQuery.Builder()
        q.add(pq, Occur.MUST)
        q.add(TermQuery(Term("field", "c")), Occur.FILTER)

        val weight = searcher.createWeight(searcher.rewrite(q.build()), ScoreMode.COMPLETE, 1f)
        val scorer = weight.scorer(searcher.indexReader.leaves()[0])!!
        assertTrue(scorer is ConjunctionScorer)
        assertNotNull(scorer.twoPhaseIterator())

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDisjunctionPropagatesApproximations() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = newTextField("field", "a b c", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        w.commit()

        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)
        searcher.queryCache = null

        val pq = PhraseQuery("field", "a", "b")
        val q = BooleanQuery.Builder()
        q.add(pq, Occur.SHOULD)
        q.add(TermQuery(Term("field", "c")), Occur.SHOULD)

        val weight = searcher.createWeight(searcher.rewrite(q.build()), ScoreMode.COMPLETE, 1f)
        val scorer = weight.scorer(reader.leaves()[0])!!
        assertTrue(scorer is DisjunctionScorer)
        assertNotNull(scorer.twoPhaseIterator())

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testBoostedScorerPropagatesApproximations() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = newTextField("field", "a b c", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        w.commit()

        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)
        searcher.queryCache = null

        val pq = PhraseQuery("field", "a", "b")
        val q = BooleanQuery.Builder()
        q.add(pq, Occur.SHOULD)
        q.add(TermQuery(Term("field", "d")), Occur.SHOULD)

        val weight = searcher.createWeight(searcher.rewrite(q.build()), ScoreMode.COMPLETE, 1f)
        val scorer = weight.scorer(searcher.indexReader.leaves()[0])!!
        assertTrue(scorer is PhraseScorer)
        assertNotNull(scorer.twoPhaseIterator())

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testExclusionPropagatesApproximations() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = newTextField("field", "a b c", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        w.commit()

        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)
        searcher.queryCache = null

        val pq = PhraseQuery("field", "a", "b")
        val q = BooleanQuery.Builder()
        q.add(pq, Occur.SHOULD)
        q.add(TermQuery(Term("field", "c")), Occur.MUST_NOT)

        val weight = searcher.createWeight(searcher.rewrite(q.build()), ScoreMode.COMPLETE, 1f)
        val scorer = weight.scorer(reader.leaves()[0])!!
        assertTrue(scorer is ReqExclScorer)
        assertNotNull(scorer.twoPhaseIterator())

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testReqOptPropagatesApproximations() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = newTextField("field", "a b c", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        w.commit()

        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)
        searcher.queryCache = null

        val pq = PhraseQuery("field", "a", "b")
        val q = BooleanQuery.Builder()
        q.add(pq, Occur.MUST)
        q.add(TermQuery(Term("field", "c")), Occur.SHOULD)

        val weight = searcher.createWeight(searcher.rewrite(q.build()), ScoreMode.COMPLETE, 1f)
        val scorer = weight.scorer(reader.leaves()[0])!!
        assertTrue(scorer is ReqOptSumScorer)
        assertNotNull(scorer.twoPhaseIterator())

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testQueryMatchesCount() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val randomNumDocs = TestUtil.nextInt(random(), 10, 100)
        var numMatchingDocs = 0

        for (i in 0 until randomNumDocs) {
            val doc = Document()
            val f =
                if (random().nextBoolean()) {
                    numMatchingDocs++
                    newTextField("field", "a b c ${random().nextInt()}", Field.Store.NO)
                } else {
                    newTextField("field", random().nextInt().toString(), Field.Store.NO)
                }
            doc.add(f)
            w.addDocument(doc)
        }
        w.commit()

        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)

        val q = BooleanQuery.Builder()
        q.add(PhraseQuery("field", "a", "b"), Occur.SHOULD)
        q.add(TermQuery(Term("field", "c")), Occur.SHOULD)
        val builtQuery = q.build()

        assertEquals(searcher.count(builtQuery), numMatchingDocs)

        IOUtils.close(reader, w, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testConjunctionMatchesCount() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        val longPoint = LongPoint("long", 3L)
        doc.add(longPoint)
        val stringField = StringField("string", "abc", Field.Store.NO)
        doc.add(stringField)
        writer.addDocument(doc)
        longPoint.setLongValue(10)
        stringField.setStringValue("xyz")
        writer.addDocument(doc)
        val reader = DirectoryReader.open(writer)
        writer.close()
        val searcher = IndexSearcher(reader)

        var query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "abc")), Occur.MUST)
                .add(LongPoint.newExactQuery("long", 3L), Occur.FILTER)
                .build()
        var weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(-1, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "missing")), Occur.MUST)
                .add(LongPoint.newExactQuery("long", 3L), Occur.FILTER)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(0, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "abc")), Occur.MUST)
                .add(LongPoint.newExactQuery("long", 5L), Occur.FILTER)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(0, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "abc")), Occur.MUST)
                .add(LongPoint.newRangeQuery("long", 0L, 10L), Occur.FILTER)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(1, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(LongPoint.newRangeQuery("long", 1L, 5L), Occur.FILTER)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(1, weight.count(reader.leaves()[0]))

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDisjunctionMatchesCount() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        val longPoint = LongPoint("long", 3L)
        val longPoint3dim = LongPoint("long3dim", 3L, 4L, 5L)
        doc.add(longPoint)
        doc.add(longPoint3dim)
        val stringField = StringField("string", "abc", Field.Store.NO)
        doc.add(stringField)
        writer.addDocument(doc)
        longPoint.setLongValue(10)
        longPoint3dim.setLongValues(10L, 11L, 12L)
        stringField.setStringValue("xyz")
        writer.addDocument(doc)
        val reader = DirectoryReader.open(writer)
        writer.close()
        val searcher = IndexSearcher(reader)

        var query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "abc")), Occur.SHOULD)
                .add(LongPoint.newExactQuery("long", 3L), Occur.SHOULD)
                .build()
        var weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(-1, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "missing")), Occur.SHOULD)
                .add(LongPoint.newExactQuery("long", 3L), Occur.SHOULD)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(1, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "abc")), Occur.SHOULD)
                .add(LongPoint.newExactQuery("long", 5L), Occur.SHOULD)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(1, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "abc")), Occur.SHOULD)
                .add(LongPoint.newRangeQuery("long", 0L, 10L), Occur.SHOULD)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(2, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.SHOULD)
                .add(LongPoint.newRangeQuery("long", 1L, 5L), Occur.SHOULD)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(2, weight.count(reader.leaves()[0]))

        val lower = longArrayOf(4L, 5L, 6L)
        val upper = longArrayOf(9L, 10L, 11L)
        val unknownCountQuery = LongPoint.newRangeQuery("long3dim", lower, upper)
        assertEquals(1, reader.leaves().size)
        assertEquals(-1, searcher.createWeight(unknownCountQuery, ScoreMode.COMPLETE, 1f).count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "xyz")), Occur.MUST)
                .add(unknownCountQuery, Occur.MUST_NOT)
                .add(MatchAllDocsQuery(), Occur.MUST_NOT)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(0, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "xyz")), Occur.MUST)
                .add(unknownCountQuery, Occur.MUST_NOT)
                .add(TermQuery(Term("string", "abc")), Occur.MUST_NOT)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(-1, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(unknownCountQuery, Occur.SHOULD)
                .add(MatchAllDocsQuery(), Occur.SHOULD)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(2, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(unknownCountQuery, Occur.SHOULD)
                .add(TermQuery(Term("string", "abc")), Occur.SHOULD)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(-1, weight.count(reader.leaves()[0]))

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testTwoClauseTermDisjunctionCountOptimization() {
        val largerTermCount = RandomNumbers.randomIntBetween(random(), 11, 100)
        val smallerTermCount = RandomNumbers.randomIntBetween(random(), 1, (largerTermCount - 1) / 10)

        val docContent = mutableListOf<Array<String>>()
        for (i in 0 until largerTermCount) {
            docContent.add(arrayOf("large"))
        }
        for (i in 0 until smallerTermCount) {
            docContent.add(arrayOf("small", "also small"))
        }

        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
        for (values in docContent) {
            val doc = Document()
            for (value in values) {
                doc.add(StringField("foo", value, Field.Store.NO))
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)
        w.close()

        val reader = DirectoryReader.open(dir)
        val countInvocations = intArrayOf(0)
        val countingIndexSearcher =
            object : IndexSearcher(reader) {
                override fun count(query: Query): Int {
                    countInvocations[0]++
                    return super.count(query)
                }
            }

        run {
            countInvocations[0] = 0
            val query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "no match")), BooleanClause.Occur.SHOULD)
                    .add(TermQuery(Term("foo", "also no match")), BooleanClause.Occur.SHOULD)
                    .build()

            assertEquals(0, countingIndexSearcher.count(query))
            assertEquals(3, countInvocations[0])
        }
        run {
            countInvocations[0] = 0
            val query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "no match")), BooleanClause.Occur.SHOULD)
                    .add(TermQuery(Term("foo", "small")), BooleanClause.Occur.SHOULD)
                    .build()

            assertEquals(smallerTermCount, countingIndexSearcher.count(query))
            assertEquals(3, countInvocations[0])
        }
        run {
            countInvocations[0] = 0
            val query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "small")), BooleanClause.Occur.SHOULD)
                    .add(TermQuery(Term("foo", "no match")), BooleanClause.Occur.SHOULD)
                    .build()

            assertEquals(smallerTermCount, countingIndexSearcher.count(query))
            assertEquals(3, countInvocations[0])
        }
        run {
            countInvocations[0] = 0

            val query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "small")), BooleanClause.Occur.SHOULD)
                    .add(TermQuery(Term("foo", "large")), BooleanClause.Occur.SHOULD)
                    .build()

            val count = countingIndexSearcher.count(query)

            assertEquals(largerTermCount + smallerTermCount, count)
            assertEquals(4, countInvocations[0])

            assertTrue(query.isTwoClausePureDisjunctionWithTerms)
            val queries = query.rewriteTwoClauseDisjunctionWithTermsForCount(countingIndexSearcher)
            assertEquals(3, queries.size)
            assertEquals(smallerTermCount, countingIndexSearcher.count(queries[0]))
            assertEquals(largerTermCount, countingIndexSearcher.count(queries[1]))
        }
        run {
            countInvocations[0] = 0

            val query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "large")), BooleanClause.Occur.SHOULD)
                    .add(TermQuery(Term("foo", "small")), BooleanClause.Occur.SHOULD)
                    .build()

            val count = countingIndexSearcher.count(query)

            assertEquals(largerTermCount + smallerTermCount, count)
            assertEquals(4, countInvocations[0])

            assertTrue(query.isTwoClausePureDisjunctionWithTerms)
            val queries = query.rewriteTwoClauseDisjunctionWithTermsForCount(countingIndexSearcher)
            assertEquals(3, queries.size)
            assertEquals(largerTermCount, countingIndexSearcher.count(queries[0]))
            assertEquals(smallerTermCount, countingIndexSearcher.count(queries[1]))
        }
        run {
            countInvocations[0] = 0
            val query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "small")), BooleanClause.Occur.SHOULD)
                    .add(TermQuery(Term("foo", "also small")), BooleanClause.Occur.SHOULD)
                    .build()

            val count = countingIndexSearcher.count(query)

            assertEquals(smallerTermCount, count)
            assertEquals(3, countInvocations[0])
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDisjunctionTwoClausesMatchesCountAndScore() {
        val docContent =
            listOf(
                arrayOf("A", "B"),
                arrayOf("A"),
                emptyArray(),
                arrayOf("A", "B", "C"),
                arrayOf("B"),
                arrayOf("B", "C")
            )

        val matchDocScore =
            arrayOf(
                intArrayOf(0, 2 + 1),
                intArrayOf(3, 2 + 1),
                intArrayOf(1, 2),
                intArrayOf(4, 1),
                intArrayOf(5, 1)
            )

        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
        for (values in docContent) {
            val doc = Document()
            for (value in values) {
                doc.add(StringField("foo", value, Field.Store.NO))
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)
        w.close()

        val reader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)

        val query =
            BooleanQuery.Builder()
                .add(
                    BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "A"))), 2f),
                    BooleanClause.Occur.SHOULD
                )
                .add(
                    ConstantScoreQuery(TermQuery(Term("foo", "B"))),
                    BooleanClause.Occur.SHOULD
                )
                .build()

        val topDocs = searcher.search(query, 10)

        for (i in topDocs.scoreDocs.indices) {
            val scoreDoc = topDocs.scoreDocs[i]
            assertEquals(matchDocScore[i][0], scoreDoc.doc)
            assertEquals(matchDocScore[i][1].toFloat(), scoreDoc.score, 0f)
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDisjunctionRandomClausesMatchesCount() {
        val numFieldValue = RandomNumbers.randomIntBetween(random(), 1, 10)
        val numDocsPerFieldValue = IntArray(numFieldValue)
        var allDocsCount = 0

        for (i in numDocsPerFieldValue.indices) {
            val numDocs = RandomNumbers.randomIntBetween(random(), 10, 50)
            numDocsPerFieldValue[i] = numDocs
            allDocsCount += numDocs
        }

        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
        for (i in 0 until numFieldValue) {
            for (j in 0 until numDocsPerFieldValue[i]) {
                val doc = Document()
                doc.add(StringField("field", i.toString(), Field.Store.NO))
                w.addDocument(doc)
            }
        }
        w.forceMerge(1)
        w.close()

        var matchedDocsCount = 0
        val reader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)

        val builder = BooleanQuery.Builder()
        for (i in 0 until numFieldValue) {
            if (RandomizedTest.randomBoolean()) {
                matchedDocsCount += numDocsPerFieldValue[i]
                builder.add(TermQuery(Term("field", i.toString())), BooleanClause.Occur.SHOULD)
            }
        }

        val query = builder.build()
        val topDocs = searcher.search(query, allDocsCount)
        assertEquals(matchedDocsCount, topDocs.scoreDocs.size)

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testProhibitedMatchesCount() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        val longPoint = LongPoint("long", 3L)
        doc.add(longPoint)
        val stringField = StringField("string", "abc", Field.Store.NO)
        doc.add(stringField)
        writer.addDocument(doc)
        longPoint.setLongValue(10)
        stringField.setStringValue("xyz")
        writer.addDocument(doc)
        val reader = DirectoryReader.open(writer)
        writer.close()
        val searcher = IndexSearcher(reader)

        var query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "abc")), Occur.MUST)
                .add(LongPoint.newExactQuery("long", 3L), Occur.MUST_NOT)
                .build()
        var weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(-1, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "missing")), Occur.MUST)
                .add(LongPoint.newExactQuery("long", 3L), Occur.MUST_NOT)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(0, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "abc")), Occur.MUST)
                .add(LongPoint.newExactQuery("long", 5L), Occur.MUST_NOT)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(1, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("string", "abc")), Occur.MUST)
                .add(LongPoint.newRangeQuery("long", 0L, 10L), Occur.MUST_NOT)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(0, weight.count(reader.leaves()[0]))

        query =
            BooleanQuery.Builder()
                .add(LongPoint.newRangeQuery("long", 0L, 10L), Occur.MUST)
                .add(TermQuery(Term("string", "abc")), Occur.MUST_NOT)
                .build()
        weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        assertEquals(1, weight.count(reader.leaves()[0]))

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRandomBooleanQueryMatchesCount() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        val longPoint = LongPoint("long", 3L)
        doc.add(longPoint)
        val stringField = StringField("string", "abc", Field.Store.NO)
        doc.add(stringField)
        writer.addDocument(doc)
        longPoint.setLongValue(10)
        stringField.setStringValue("xyz")
        writer.addDocument(doc)
        val reader = DirectoryReader.open(writer)
        writer.close()
        val searcher = IndexSearcher(reader)
        for (iter in 0 until 1000) {
            val numClauses = TestUtil.nextInt(random(), 2, 5)
            val builder = BooleanQuery.Builder()
            var numShouldClauses = 0
            for (i in 0 until numClauses) {
                val query =
                    when (random().nextInt(6)) {
                        0 -> TermQuery(Term("string", "abc"))
                        1 -> LongPoint.newExactQuery("long", 3L)
                        2 -> TermQuery(Term("string", "missing"))
                        3 -> LongPoint.newExactQuery("long", 5L)
                        4 -> MatchAllDocsQuery()
                        else -> LongPoint.newRangeQuery("long", 0L, 10L)
                    }
                val occur = RandomPicks.randomFrom(random(), Occur.entries.toTypedArray())
                if (occur == Occur.SHOULD) {
                    numShouldClauses++
                }
                builder.add(query, occur)
            }
            builder.setMinimumNumberShouldMatch(TestUtil.nextInt(random(), 0, numShouldClauses))
            val booleanQuery = builder.build()
            assertEquals(
                searcher.search(booleanQuery, DummyTotalHitCountCollector.createManager()),
                searcher.count(booleanQuery)
            )
        }
        reader.close()
        dir.close()
    }

    @Test
    fun testToString() {
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term("field", "a")), Occur.SHOULD)
        bq.add(TermQuery(Term("field", "b")), Occur.MUST)
        bq.add(TermQuery(Term("field", "c")), Occur.MUST_NOT)
        bq.add(TermQuery(Term("field", "d")), Occur.FILTER)
        assertEquals("a +b -c #d", bq.build().toString("field"))
    }

    @Test
    @Throws(IOException::class)
    fun testQueryVisitor() {
        val a = Term("f", "a")
        val b = Term("f", "b")
        val c = Term("f", "c")
        val d = Term("f", "d")
        val bqBuilder = BooleanQuery.Builder()
        bqBuilder.add(TermQuery(a), Occur.SHOULD)
        bqBuilder.add(TermQuery(b), Occur.MUST)
        bqBuilder.add(TermQuery(c), Occur.FILTER)
        bqBuilder.add(TermQuery(d), Occur.MUST_NOT)
        val bq = bqBuilder.build()

        bq.visit(
            object : QueryVisitor() {
                private var expected: Term? = null

                override fun getSubVisitor(occur: Occur, parent: Query): QueryVisitor {
                    expected =
                        when (occur) {
                            Occur.SHOULD -> a
                            Occur.MUST -> b
                            Occur.FILTER -> c
                            Occur.MUST_NOT -> d
                        }
                    return this
                }

                override fun consumeTerms(query: Query, vararg terms: Term) {
                    assertEquals(expected, terms[0])
                }
            }
        )
    }

    @Test
    @Throws(Exception::class)
    fun testClauseSetsImmutability() {
        val a = Term("f", "a")
        val b = Term("f", "b")
        val c = Term("f", "c")
        val d = Term("f", "d")
        val bqBuilder = BooleanQuery.Builder()
        bqBuilder.add(TermQuery(a), Occur.SHOULD)
        bqBuilder.add(TermQuery(a), Occur.SHOULD)
        bqBuilder.add(TermQuery(b), Occur.MUST)
        bqBuilder.add(TermQuery(b), Occur.MUST)
        bqBuilder.add(TermQuery(c), Occur.FILTER)
        bqBuilder.add(TermQuery(c), Occur.FILTER)
        bqBuilder.add(TermQuery(d), Occur.MUST_NOT)
        bqBuilder.add(TermQuery(d), Occur.MUST_NOT)
        val bq = bqBuilder.build()
        assertEquals(2, bq.getClauses(Occur.SHOULD).size)
        assertEquals(2, bq.getClauses(Occur.MUST).size)
        assertEquals(1, bq.getClauses(Occur.FILTER).size)
        assertEquals(1, bq.getClauses(Occur.MUST_NOT).size)
        for (occur in Occur.entries) {
            expectThrows(UnsupportedOperationException::class) {
                bq.getClauses(occur).add(MatchNoDocsQuery())
            }
        }
        expectThrows(UnsupportedOperationException::class) {
            bq.clauses().add(BooleanClause(MatchNoDocsQuery(), Occur.SHOULD))
        }
    }
}
