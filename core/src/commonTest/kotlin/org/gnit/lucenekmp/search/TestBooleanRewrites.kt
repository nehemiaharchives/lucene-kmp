package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestBooleanRewrites : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testOneClauseRewriteOptimization() {
        val field = "content"
        val value = "foo"

        val dir = newDirectory()
        RandomIndexWriter(random(), dir).close()
        val r = DirectoryReader.open(dir)

        val expected = TermQuery(Term(field, value))

        val numLayers = atLeast(3)
        var actual: Query = TermQuery(Term(field, value))

        for (i in 0 until numLayers) {
            val bq = BooleanQuery.Builder()
            bq.add(actual, if (random().nextBoolean()) Occur.SHOULD else Occur.MUST)
            actual = bq.build()
        }

        assertEquals(expected, IndexSearcher(r).rewrite(actual), "$numLayers: $actual")

        r.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSingleFilterClause() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = newTextField("field", "a", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        w.commit()

        val reader = w.getReader(true, writeAllDeletes = false)
        val searcher = IndexSearcher(reader)

        val query1 = BooleanQuery.Builder()
        query1.add(TermQuery(Term("field", "a")), Occur.FILTER)

        val rewritten1 = query1.build().rewrite(searcher)
        assertTrue(rewritten1 is BoostQuery)
        assertEquals(0f, rewritten1.boost, 0f)

        val query2 = BooleanQuery.Builder()
        query2.add(TermQuery(Term("field", "a")), Occur.FILTER)
        query2.add(TermQuery(Term("missing_field", "b")), Occur.SHOULD)
        val weight = searcher.createWeight(searcher.rewrite(query2.build()), ScoreMode.COMPLETE, 1f)
        val scorer = weight.scorer(reader.leaves()[0])!!
        assertEquals(0, scorer.iterator().nextDoc())
        assertTrue(scorer is FilterScorer, scorer::class.qualifiedName ?: scorer::class.toString())
        assertEquals(0f, scorer.score(), 0f)

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSingleMustMatchAll() {
        val searcher = newSearcher(MultiReader())

        var bq =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .build()
        assertEquals(ConstantScoreQuery(TermQuery(Term("foo", "bar"))), searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(BoostQuery(MatchAllDocsQuery(), 42f), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .build()
        assertEquals(
            BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "bar"))), 42f),
            searcher.rewrite(bq)
        )

        bq =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(MatchAllDocsQuery(), Occur.FILTER)
                .build()
        assertEquals(MatchAllDocsQuery(), searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(BoostQuery(MatchAllDocsQuery(), 42f), Occur.MUST)
                .add(MatchAllDocsQuery(), Occur.FILTER)
                .build()
        assertEquals(BoostQuery(MatchAllDocsQuery(), 42f), searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST_NOT)
                .build()
        assertEquals(bq, searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(MatchAllDocsQuery(), Occur.FILTER)
                .build()
        assertEquals(MatchAllDocsQuery(), searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .build()
        var expected: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .build()
        assertEquals(ConstantScoreQuery(expected), searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST_NOT)
                .build()
        expected =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST_NOT)
                .build()
        assertEquals(ConstantScoreQuery(expected), searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .build()
        assertEquals(bq, searcher.rewrite(bq))
    }

    @Test
    @Throws(IOException::class)
    fun testSingleMustMatchAllWithShouldClauses() {
        val searcher = newSearcher(MultiReader())

        val bq =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .build()
        val expected =
            BooleanQuery.Builder()
                .add(ConstantScoreQuery(TermQuery(Term("foo", "bar"))), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .build()
        assertEquals(expected, searcher.rewrite(bq))
    }

    @Test
    @Throws(IOException::class)
    fun testDeduplicateMustAndFilter() {
        val searcher = newSearcher(MultiReader())

        var bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .build()
        assertEquals(TermQuery(Term("foo", "bar")), searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .build()
        val expected =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .build()
        assertEquals(expected, searcher.rewrite(bq))
    }

    @Test
    @Throws(IOException::class)
    fun testConvertShouldAndFilterToMust() {
        val searcher = newSearcher(MultiReader())

        var bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .build()
        assertEquals(TermQuery(Term("foo", "bar")), searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quz")), Occur.SHOULD)
                .setMinimumNumberShouldMatch(2)
                .build()

        val expected =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quz")), Occur.SHOULD)
                .setMinimumNumberShouldMatch(1)
                .build()
        assertEquals(expected, searcher.rewrite(bq))
    }

    @Test
    @Throws(IOException::class)
    fun testDuplicateMustOrFilterWithMustNot() {
        val searcher = newSearcher(MultiReader())

        val bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .add(TermQuery(Term("foo", "bad")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST_NOT)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(bq))

        val bq2 =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .add(TermQuery(Term("foo", "bad")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST_NOT)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(bq2))
    }

    @Test
    @Throws(IOException::class)
    fun testMatchAllMustNot() {
        val searcher = newSearcher(MultiReader())

        val bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .add(TermQuery(Term("foo", "bad")), Occur.SHOULD)
                .add(MatchAllDocsQuery(), Occur.MUST_NOT)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(bq))

        val bq2 =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .add(TermQuery(Term("foo", "bad")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "bor")), Occur.MUST_NOT)
                .add(MatchAllDocsQuery(), Occur.MUST_NOT)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(bq2))
    }

    @Test
    @Throws(IOException::class)
    fun testDeeplyNestedBooleanRewriteShouldClauses() {
        val searcher = newSearcher(MultiReader())
        val termQueryFunction = { i: Int -> TermQuery(Term("layer[$i]", "foo")) }
        val depth = TestUtil.nextInt(random(), 10, 30)
        val rewriteQueryExpected = TestRewriteQuery()
        val rewriteQuery = TestRewriteQuery()
        val expectedQueryBuilder = BooleanQuery.Builder().add(rewriteQueryExpected, Occur.FILTER)
        var deepBuilder: Query =
            BooleanQuery.Builder()
                .add(rewriteQuery, Occur.SHOULD)
                .setMinimumNumberShouldMatch(1)
                .build()
        for (i in depth downTo 1) {
            val tq = termQueryFunction(i)
            val bq =
                BooleanQuery.Builder()
                    .setMinimumNumberShouldMatch(2)
                    .add(tq, Occur.SHOULD)
                    .add(deepBuilder, Occur.SHOULD)
            deepBuilder = bq.build()
            expectedQueryBuilder.add(tq, Occur.FILTER)
            if (i == depth) {
                expectedQueryBuilder.add(rewriteQuery, Occur.FILTER)
            }
        }
        val bq = BooleanQuery.Builder().add(deepBuilder, Occur.FILTER).build()
        val expectedQuery = BoostQuery(ConstantScoreQuery(expectedQueryBuilder.build()), 0.0f)
        val rewritten = searcher.rewrite(bq)
        assertEquals(expectedQuery, rewritten)
        assertEquals(depth * 2, rewriteQuery.numRewrites, "Depth=$depth")
    }

    @Test
    @Throws(IOException::class)
    fun testDeeplyNestedBooleanRewrite() {
        val searcher = newSearcher(MultiReader())
        val termQueryFunction = { i: Int -> TermQuery(Term("layer[$i]", "foo")) }
        val depth = TestUtil.nextInt(random(), 10, 30)
        val rewriteQueryExpected = TestRewriteQuery()
        val rewriteQuery = TestRewriteQuery()
        val expectedQueryBuilder = BooleanQuery.Builder().add(rewriteQueryExpected, Occur.FILTER)
        var deepBuilder: Query = BooleanQuery.Builder().add(rewriteQuery, Occur.MUST).build()
        for (i in depth downTo 1) {
            val tq = termQueryFunction(i)
            val bq = BooleanQuery.Builder().add(tq, Occur.MUST).add(deepBuilder, Occur.MUST)
            deepBuilder = bq.build()
            expectedQueryBuilder.add(tq, Occur.FILTER)
            if (i == depth) {
                expectedQueryBuilder.add(rewriteQuery, Occur.FILTER)
            }
        }
        val bq = BooleanQuery.Builder().add(deepBuilder, Occur.FILTER).build()
        val expectedQuery = BoostQuery(ConstantScoreQuery(expectedQueryBuilder.build()), 0.0f)
        val rewritten = searcher.rewrite(bq)
        assertEquals(expectedQuery, rewritten)
        assertEquals(depth, rewriteQuery.numRewrites, "Depth=$depth")
    }

    @Test
    @Throws(IOException::class)
    fun testRemoveMatchAllFilter() {
        val searcher = newSearcher(MultiReader())

        var bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(MatchAllDocsQuery(), Occur.FILTER)
                .build()
        assertEquals(TermQuery(Term("foo", "bar")), searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .add(MatchAllDocsQuery(), Occur.FILTER)
                .build()
        var expected: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        assertEquals(expected, searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(MatchAllDocsQuery(), Occur.FILTER)
                .build()
        expected = BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "bar"))), 0.0f)
        assertEquals(expected, searcher.rewrite(bq))

        bq =
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), Occur.FILTER)
                .add(MatchAllDocsQuery(), Occur.FILTER)
                .build()
        expected = BoostQuery(ConstantScoreQuery(MatchAllDocsQuery()), 0.0f)
        assertEquals(expected, searcher.rewrite(bq))
    }

    @Test
    @Throws(IOException::class)
    fun testRandom() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = TextField("body", "a b c", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        f.setStringValue("")
        w.addDocument(doc)
        f.setStringValue("a b")
        w.addDocument(doc)
        f.setStringValue("b c")
        w.addDocument(doc)
        f.setStringValue("a")
        w.addDocument(doc)
        f.setStringValue("c")
        w.addDocument(doc)
        val numRandomDocs = atLeast(3)
        for (i in 0 until numRandomDocs) {
            val numTerms = random().nextInt(20)
            val text = StringBuilder()
            for (j in 0 until numTerms) {
                text.append(('a'.code + random().nextInt(4)).toChar()).append(' ')
            }
            f.setStringValue(text.toString())
            w.addDocument(doc)
        }
        val reader = w.getReader(true, false)
        w.close()
        val searcher1 = newSearcher(reader)
        val searcher2 =
            object : IndexSearcher(reader) {
                @Throws(IOException::class)
                override fun rewrite(original: Query): Query {
                    return original
                }
            }
        searcher2.similarity = searcher1.similarity

        val iters = atLeast(1000)
        for (i in 0 until iters) {
            var query = randomBooleanQuery(random())
            val td1 = searcher1.search(query, 100)
            val td2 = searcher2.search(query, 100)
            try {
                assertEquals(td1, td2)
            } catch (e: AssertionError) {
                println(query)
                var rewritten = query
                do {
                    query = rewritten
                    rewritten = query.rewrite(searcher1)
                    println(rewritten)
                    val tdx = searcher2.search(rewritten, 100)
                    if (td2.totalHits.value != tdx.totalHits.value) {
                        println("Bad")
                    }
                } while (query !== rewritten)
                throw e
            }
        }

        searcher1.indexReader.close()
        dir.close()
    }

    private fun randomBooleanQuery(random: Random): Query {
        val numClauses = random.nextInt(5)
        val b = BooleanQuery.Builder()
        var numShoulds = 0
        for (i in 0 until numClauses) {
            val occur = Occur.entries.toTypedArray()[random.nextInt(Occur.entries.size)]
            if (occur == Occur.SHOULD) {
                numShoulds++
            }
            val query = randomQuery(random)
            b.add(query, occur)
        }
        b.setMinimumNumberShouldMatch(if (random().nextBoolean()) 0 else TestUtil.nextInt(random(), 0, numShoulds + 1))
        var query: Query = b.build()
        if (random.nextBoolean()) {
            query = randomWrapper(random, query)
        }
        return query
    }

    private fun randomWrapper(random: Random, query: Query): Query {
        return when (random.nextInt(2)) {
            0 -> BoostQuery(query, TestUtil.nextInt(random, 0, 4).toFloat())
            1 -> ConstantScoreQuery(query)
            else -> throw AssertionError()
        }
    }

    private fun randomQuery(random: Random): Query {
        if (random.nextInt(5) == 0) {
            return randomWrapper(random, randomQuery(random))
        }
        return when (random().nextInt(6)) {
            0 -> MatchAllDocsQuery()
            1 -> TermQuery(Term("body", "a"))
            2 -> TermQuery(Term("body", "b"))
            3 -> TermQuery(Term("body", "c"))
            4 -> TermQuery(Term("body", "d"))
            5 -> randomBooleanQuery(random)
            else -> throw AssertionError()
        }
    }

    private fun assertEquals(td1: TopDocs, td2: TopDocs) {
        assertEquals(td1.totalHits.value, td2.totalHits.value)
        assertEquals(td1.scoreDocs.size, td2.scoreDocs.size)
        val expectedScores = td1.scoreDocs.associate { scoreDoc -> scoreDoc.doc to scoreDoc.score }
        val actualResultSet = td2.scoreDocs.map { scoreDoc -> scoreDoc.doc }.toSet()

        assertEquals(expectedScores.keys, actualResultSet, "Set of matching documents differs")

        for (scoreDoc in td2.scoreDocs) {
            val expectedScore = expectedScores[scoreDoc.doc]!!
            val actualScore = scoreDoc.score
            assertEquals(expectedScore, actualScore, expectedScore / 100)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDeduplicateShouldClauses() {
        val searcher = newSearcher(MultiReader())

        var query: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .build()
        var expected: Query = BoostQuery(TermQuery(Term("foo", "bar")), 2f)
        assertEquals(expected, searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(BoostQuery(TermQuery(Term("foo", "bar")), 2f), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .build()
        expected =
            BooleanQuery.Builder()
                .add(BoostQuery(TermQuery(Term("foo", "bar")), 3f), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .build()
        assertEquals(expected, searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(2)
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .build()
        expected = query
        assertEquals(expected, searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testDeduplicateMustClauses() {
        val searcher = newSearcher(MultiReader())

        var query: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .build()
        var expected: Query = BoostQuery(TermQuery(Term("foo", "bar")), 2f)
        assertEquals(expected, searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(BoostQuery(TermQuery(Term("foo", "bar")), 2f), Occur.MUST)
                .add(TermQuery(Term("foo", "quux")), Occur.MUST)
                .build()
        expected =
            BooleanQuery.Builder()
                .add(BoostQuery(TermQuery(Term("foo", "bar")), 3f), Occur.MUST)
                .add(TermQuery(Term("foo", "quux")), Occur.MUST)
                .build()
        assertEquals(expected, searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testFlattenInnerDisjunctions() {
        val searcher = newSearcher(MultiReader())

        var inner: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .build()
        var query: Query =
            BooleanQuery.Builder()
                .add(inner, Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .build()
        var expectedRewritten: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(0)
                .add(inner, Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        expectedRewritten =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(0)
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(1)
                .add(inner, Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        expectedRewritten =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(1)
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(2)
                .add(inner, Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))

        inner =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .setMinimumNumberShouldMatch(2)
                .build()
        query =
            BooleanQuery.Builder()
                .add(inner, Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .build()
        assertSame(query, searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testFlattenInnerConjunctions() {
        val searcher = newSearcher(MultiReader())

        var inner: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "quux")), Occur.MUST)
                .build()
        var query: Query =
            BooleanQuery.Builder()
                .add(inner, Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .build()
        var expectedRewritten: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "quux")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(0)
                .add(inner, Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .build()
        expectedRewritten =
            BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(0)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "quux")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .add(inner, Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST_NOT)
                .build()
        expectedRewritten =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "quux")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST_NOT)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))

        inner =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "quux")), Occur.FILTER)
                .build()
        query =
            BooleanQuery.Builder()
                .add(inner, Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        expectedRewritten =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "quux")), Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))

        inner =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "quux")), Occur.FILTER)
                .build()
        query =
            BooleanQuery.Builder()
                .add(inner, Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        expectedRewritten =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "quux")), Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))

        inner =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "quux")), Occur.MUST_NOT)
                .build()
        query =
            BooleanQuery.Builder()
                .add(inner, Occur.FILTER)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        expectedRewritten =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(TermQuery(Term("foo", "quux")), Occur.MUST_NOT)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testFlattenDisjunctionInMustClause() {
        val searcher = newSearcher(MultiReader())

        var inner: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .build()
        var query: Query =
            BooleanQuery.Builder()
                .add(inner, Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .build()
        var expectedRewritten: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .setMinimumNumberShouldMatch(1)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))

        inner =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "foo")), Occur.SHOULD)
                .setMinimumNumberShouldMatch(2)
                .build()
        query =
            BooleanQuery.Builder()
                .add(inner, Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .build()
        expectedRewritten =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "quux")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "foo")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .setMinimumNumberShouldMatch(2)
                .build()
        assertEquals(expectedRewritten, searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testDiscardShouldClauses() {
        val searcher = newSearcher(MultiReader())

        val query1 =
            ConstantScoreQuery(
                BooleanQuery.Builder()
                    .add(TermQuery(Term("field", "a")), Occur.MUST)
                    .add(TermQuery(Term("field", "b")), Occur.SHOULD)
                    .build()
            )
        val rewritten1 = ConstantScoreQuery(TermQuery(Term("field", "a")))
        assertEquals(rewritten1, searcher.rewrite(query1))

        val query2 =
            ConstantScoreQuery(
                BooleanQuery.Builder()
                    .add(TermQuery(Term("field", "a")), Occur.MUST)
                    .add(TermQuery(Term("field", "b")), Occur.SHOULD)
                    .add(TermQuery(Term("field", "c")), Occur.FILTER)
                    .build()
            )
        val rewritten2 =
            ConstantScoreQuery(
                BooleanQuery.Builder()
                    .add(TermQuery(Term("field", "a")), Occur.FILTER)
                    .add(TermQuery(Term("field", "c")), Occur.FILTER)
                    .build()
            )
        assertEquals(rewritten2, searcher.rewrite(query2))

        val query3 =
            ConstantScoreQuery(
                BooleanQuery.Builder()
                    .add(TermQuery(Term("field", "a")), Occur.SHOULD)
                    .add(TermQuery(Term("field", "b")), Occur.SHOULD)
                    .build()
            )
        assertSame(query3, searcher.rewrite(query3))

        val query4 =
            ConstantScoreQuery(
                BooleanQuery.Builder()
                    .add(TermQuery(Term("field", "a")), Occur.SHOULD)
                    .add(TermQuery(Term("field", "b")), Occur.MUST_NOT)
                    .build()
            )
        assertSame(query4, searcher.rewrite(query4))

        val query5 =
            ConstantScoreQuery(
                BooleanQuery.Builder()
                    .setMinimumNumberShouldMatch(1)
                    .add(TermQuery(Term("field", "a")), Occur.SHOULD)
                    .add(TermQuery(Term("field", "b")), Occur.SHOULD)
                    .add(TermQuery(Term("field", "c")), Occur.FILTER)
                    .build()
            )
        assertSame(query5, searcher.rewrite(query5))
    }

    @Test
    @Throws(IOException::class)
    fun testShouldMatchNoDocsQuery() {
        val searcher = newSearcher(MultiReader())

        val query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(MatchNoDocsQuery(), Occur.SHOULD)
                .build()
        assertEquals(TermQuery(Term("foo", "bar")), searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testMustNotMatchNoDocsQuery() {
        val searcher = newSearcher(MultiReader())

        val query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(MatchNoDocsQuery(), Occur.MUST_NOT)
                .build()
        assertEquals(TermQuery(Term("foo", "bar")), searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testMustMatchNoDocsQuery() {
        val searcher = newSearcher(MultiReader())

        val query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(MatchNoDocsQuery(), Occur.MUST)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testFilterMatchNoDocsQuery() {
        val searcher = newSearcher(MultiReader())

        val query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(MatchNoDocsQuery(), Occur.FILTER)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyBoolean() {
        val searcher = newSearcher(MultiReader())
        val query = BooleanQuery.Builder().build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testSimplifyFilterClauses() {
        val searcher = newSearcher(MultiReader())

        val query1 =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(ConstantScoreQuery(TermQuery(Term("foo", "baz"))), Occur.FILTER)
                .build()
        val expected1 =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .build()
        assertEquals(expected1, searcher.rewrite(query1))

        val query2 =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                .add(ConstantScoreQuery(TermQuery(Term("foo", "bar"))), Occur.FILTER)
                .build()
        val expected2 = BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "bar"))), 0f)
        assertEquals(expected2, searcher.rewrite(query2))
    }

    @Test
    @Throws(IOException::class)
    fun testSimplifyMustNotClauses() {
        val searcher = newSearcher(MultiReader())

        val query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(ConstantScoreQuery(TermQuery(Term("foo", "baz"))), Occur.MUST_NOT)
                .build()
        val expected =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.MUST)
                .add(TermQuery(Term("foo", "baz")), Occur.MUST_NOT)
                .build()
        assertEquals(expected, searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testSimplifyNonScoringShouldClauses() {
        val searcher = newSearcher(MultiReader())

        val query =
            ConstantScoreQuery(
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                    .add(ConstantScoreQuery(TermQuery(Term("foo", "baz"))), Occur.SHOULD)
                    .build()
            )
        val expected =
            ConstantScoreQuery(
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                    .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                    .build()
            )
        assertEquals(expected, searcher.rewrite(query))
    }

    @Test
    @Throws(IOException::class)
    fun testShouldClausesLessThanOrEqualToMinimumNumberShouldMatch() {
        val searcher = newSearcher(MultiReader())

        var query =
            BooleanQuery.Builder()
                .add(PhraseQuery.Builder().build(), Occur.SHOULD)
                .setMinimumNumberShouldMatch(1)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))
        query =
            BooleanQuery.Builder()
                .add(PhraseQuery.Builder().build(), Occur.SHOULD)
                .setMinimumNumberShouldMatch(0)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .add(PhraseQuery.Builder().build(), Occur.SHOULD)
                .add(PhraseQuery.Builder().add(Term("field", "a")).build(), Occur.SHOULD)
                .setMinimumNumberShouldMatch(2)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .add(PhraseQuery.Builder().add(Term("field", "b")).build(), Occur.SHOULD)
                .add(
                    PhraseQuery.Builder()
                        .add(Term("field", "a"))
                        .add(Term("field", "c"))
                        .build(),
                    Occur.SHOULD
                )
                .setMinimumNumberShouldMatch(2)
                .build()
        val expected =
            BooleanQuery.Builder()
                .add(TermQuery(Term("field", "b")), Occur.MUST)
                .add(
                    PhraseQuery.Builder()
                        .add(Term("field", "a"))
                        .add(Term("field", "c"))
                        .build(),
                    Occur.MUST
                )
                .build()
        assertEquals(expected, searcher.rewrite(query))

        val inner: Query =
            BooleanQuery.Builder()
                .add(PhraseQuery.Builder().build(), Occur.SHOULD)
                .add(PhraseQuery.Builder().add(Term("field", "a")).build(), Occur.SHOULD)
                .setMinimumNumberShouldMatch(2)
                .build()

        query =
            BooleanQuery.Builder()
                .add(inner, Occur.SHOULD)
                .add(PhraseQuery.Builder().add(Term("field", "b")).build(), Occur.SHOULD)
                .add(
                    PhraseQuery.Builder()
                        .add(Term("field", "a"))
                        .add(Term("field", "c"))
                        .build(),
                    Occur.SHOULD
                )
                .setMinimumNumberShouldMatch(2)
                .build()
        assertEquals(expected, searcher.rewrite(query))

        query =
            BooleanQuery.Builder()
                .add(inner, Occur.SHOULD)
                .add(PhraseQuery.Builder().add(Term("field", "b")).build(), Occur.SHOULD)
                .setMinimumNumberShouldMatch(2)
                .build()
        assertEquals(MatchNoDocsQuery(), searcher.rewrite(query))
    }

    private class TestRewriteQuery : Query() {
        var numRewrites = 0

        override fun toString(field: String?): String {
            return "TestRewriteQuery{rewrites=$numRewrites}"
        }

        override fun rewrite(indexSearcher: IndexSearcher): Query {
            numRewrites++
            return this
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun equals(other: Any?): Boolean {
            return other is TestRewriteQuery
        }

        override fun hashCode(): Int {
            return TestRewriteQuery::class.hashCode()
        }
    }
}
