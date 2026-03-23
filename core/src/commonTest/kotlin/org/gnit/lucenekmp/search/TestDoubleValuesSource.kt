package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoubleDocValuesField
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FloatDocValuesField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.English
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Tests DoubleValuesSource. */
class TestDoubleValuesSource : LuceneTestCase() {
    companion object {
        private const val LEAST_DOUBLE_VALUE = 45.72

        val testQueries = arrayOf(
            MatchAllDocsQuery(),
            TermQuery(Term("oddeven", "odd")),
            BooleanQuery.Builder()
                .add(TermQuery(Term("english", "one")), BooleanClause.Occur.MUST)
                .add(TermQuery(Term("english", "two")), BooleanClause.Occur.MUST)
                .build(),
        )
    }

    private lateinit var dir: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher

    @BeforeTest
    @Throws(Exception::class)
    fun beforeClass() {
        dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val numDocs = if (TEST_NIGHTLY) {
            TestUtil.nextInt(random(), 2049, 4000)
        } else {
            atLeast(546)
        }
        for (i in 0..<numDocs) {
            val document = Document()
            document.add(newTextField("english", English.intToEnglish(i), Field.Store.NO))
            document.add(newTextField("oddeven", if (i % 2 == 0) "even" else "odd", Field.Store.NO))
            document.add(NumericDocValuesField("int", random().nextInt().toLong()))
            document.add(NumericDocValuesField("long", random().nextLong()))
            document.add(FloatDocValuesField("float", random().nextFloat()))
            document.add(DoubleDocValuesField("double", random().nextDouble()))
            if (i == 545) {
                document.add(DoubleDocValuesField("onefield", LEAST_DOUBLE_VALUE))
            }
            iw.addDocument(document)
        }
        reader = iw.reader
        iw.close()
        searcher = newSearcher(reader)
    }

    @AfterTest
    @Throws(Exception::class)
    fun afterClass() {
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSortMissingZeroDefault() {
        // docs w/no value get default missing value = 0

        val onefield = DoubleValuesSource.fromDoubleField("onefield")
        // sort decreasing
        var results = searcher.search(MatchAllDocsQuery(), 1, Sort(onefield.getSortField(true)))
        var first = results.scoreDocs[0] as FieldDoc
        assertEquals(LEAST_DOUBLE_VALUE, first.fields!![0])

        // sort increasing
        results = searcher.search(MatchAllDocsQuery(), 1, Sort(onefield.getSortField(false)))
        first = results.scoreDocs[0] as FieldDoc
        assertEquals(0.0, first.fields!![0])
    }

    @Test
    @Throws(Exception::class)
    fun testSortMissingExplicit() {
        // docs w/no value get provided missing value

        val onefield = DoubleValuesSource.fromDoubleField("onefield")

        // sort decreasing, missing last
        var oneFieldSort = onefield.getSortField(true)
        oneFieldSort.missingValue = Double.MIN_VALUE

        var results = searcher.search(MatchAllDocsQuery(), 1, Sort(oneFieldSort))
        var first = results.scoreDocs[0] as FieldDoc
        assertEquals(LEAST_DOUBLE_VALUE, first.fields!![0])

        // sort increasing, missing last
        oneFieldSort = onefield.getSortField(false)
        oneFieldSort.missingValue = Double.MAX_VALUE

        results = searcher.search(MatchAllDocsQuery(), 1, Sort(oneFieldSort))
        first = results.scoreDocs[0] as FieldDoc
        assertEquals(LEAST_DOUBLE_VALUE, first.fields!![0])
    }

    @Test
    @Throws(Exception::class)
    fun testSimpleFieldEquivalences() {
        checkSorts(
            MatchAllDocsQuery(),
            Sort(SortField("int", SortField.Type.INT, random().nextBoolean()))
        )
        checkSorts(
            MatchAllDocsQuery(),
            Sort(SortField("long", SortField.Type.LONG, random().nextBoolean()))
        )
        checkSorts(
            MatchAllDocsQuery(),
            Sort(SortField("float", SortField.Type.FLOAT, random().nextBoolean()))
        )
        checkSorts(
            MatchAllDocsQuery(),
            Sort(SortField("double", SortField.Type.DOUBLE, random().nextBoolean()))
        )
    }

    @Test
    fun testHashCodeAndEquals() {
        val vs1 = DoubleValuesSource.fromDoubleField("double")
        val vs2 = DoubleValuesSource.fromDoubleField("double")
        assertEquals(vs1, vs2)
        assertEquals(vs1.hashCode(), vs2.hashCode())
        val v3 = DoubleValuesSource.fromLongField("long")
        assertFalse(vs1 == v3)

        assertEquals(DoubleValuesSource.constant(5.0), DoubleValuesSource.constant(5.0))
        assertEquals(DoubleValuesSource.constant(5.0).hashCode(), DoubleValuesSource.constant(5.0).hashCode())
        assertFalse(DoubleValuesSource.constant(5.0) == DoubleValuesSource.constant(6.0))

        assertEquals(DoubleValuesSource.SCORES, DoubleValuesSource.SCORES)
        assertFalse(DoubleValuesSource.constant(5.0) == DoubleValuesSource.SCORES)
    }

    @Test
    @Throws(Exception::class)
    fun testSimpleFieldSortables() {
        val n = atLeast(4)
        repeat(n) {
            val sort = randomSort()
            checkSorts(MatchAllDocsQuery(), sort)
            checkSorts(TermQuery(Term("english", "one")), sort)
        }
    }

    @Throws(Exception::class)
    fun randomSort(): Sort {
        val reversed = random().nextBoolean()
        val fields = arrayOf(
            SortField("int", SortField.Type.INT, reversed),
            SortField("long", SortField.Type.LONG, reversed),
            SortField("float", SortField.Type.FLOAT, reversed),
            SortField("double", SortField.Type.DOUBLE, reversed),
            SortField("score", SortField.Type.SCORE),
        )
        fields.shuffle(random())
        val numSorts = TestUtil.nextInt(random(), 1, fields.size)
        return Sort(*ArrayUtil.copyOfSubArray(fields, 0, numSorts))
    }

    // Take a Sort, and replace any field sorts with Sortables
    fun convertSortToSortable(sort: Sort): Sort {
        val original = sort.sort
        val mutated = arrayOfNulls<SortField>(original.size)
        for (i in mutated.indices) {
            if (random().nextInt(3) > 0) {
                val s = original[i]
                val reverse = s.type == SortField.Type.SCORE || s.reverse
                when (s.type) {
                    SortField.Type.INT -> mutated[i] = DoubleValuesSource.fromIntField(s.field!!).getSortField(reverse)
                    SortField.Type.LONG -> mutated[i] = DoubleValuesSource.fromLongField(s.field!!).getSortField(reverse)
                    SortField.Type.FLOAT -> mutated[i] = DoubleValuesSource.fromFloatField(s.field!!).getSortField(reverse)
                    SortField.Type.DOUBLE -> mutated[i] = DoubleValuesSource.fromDoubleField(s.field!!).getSortField(reverse)
                    SortField.Type.SCORE -> mutated[i] = DoubleValuesSource.SCORES.getSortField(reverse)
                    SortField.Type.CUSTOM,
                    SortField.Type.DOC,
                    SortField.Type.REWRITEABLE,
                    SortField.Type.STRING,
                    SortField.Type.STRING_VAL -> mutated[i] = original[i]
                }
            } else {
                mutated[i] = original[i]
            }
        }

        return Sort(*mutated.requireNoNulls())
    }

    @Throws(Exception::class)
    fun checkSorts(query: Query, sort: Sort) {
        val size = TestUtil.nextInt(random(), 1, searcher.indexReader.maxDoc() / 5)
        var expected: TopDocs = searcher.search(query, size, sort, random().nextBoolean())
        val mutatedSort = convertSortToSortable(sort)
        var actual: TopDocs = searcher.search(query, size, mutatedSort, random().nextBoolean())

        CheckHits.checkEqual(query, expected.scoreDocs, actual.scoreDocs)

        if (size.toLong() < actual.totalHits.value) {
            expected = searcher.searchAfter(expected.scoreDocs[size - 1], query, size, sort)
            actual = searcher.searchAfter(actual.scoreDocs[size - 1], query, size, mutatedSort)
            CheckHits.checkEqual(query, expected.scoreDocs, actual.scoreDocs)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testExplanations() {
        for (q in testQueries) {
            testExplanations(q, DoubleValuesSource.fromQuery(TermQuery(Term("english", "one"))))
            testExplanations(q, DoubleValuesSource.fromIntField("int"))
            testExplanations(q, DoubleValuesSource.fromLongField("long"))
            testExplanations(q, DoubleValuesSource.fromFloatField("float"))
            testExplanations(q, DoubleValuesSource.fromDoubleField("double"))
            testExplanations(q, DoubleValuesSource.fromDoubleField("onefield"))
            testExplanations(q, DoubleValuesSource.constant(5.45))
        }
    }

    @Throws(IOException::class)
    private fun testExplanations(q: Query, vs: DoubleValuesSource) {
        val rewritten = vs.rewrite(searcher)
        searcher.search(
            q,
            object : CollectorManager<SimpleCollector, Unit> {
                override fun newCollector(): SimpleCollector {
                    return object : SimpleCollector() {
                        lateinit var v: DoubleValues
                        lateinit var ctx: LeafReaderContext
                        private var currentScorer: Scorable? = null
                        override var weight: Weight? = null

                        override fun doSetNextReader(context: LeafReaderContext) {
                            this.ctx = context
                        }

                        override var scorer: Scorable?
                            get() = currentScorer
                            set(value) {
                                currentScorer = value
                                this.v = rewritten.getValues(this.ctx, DoubleValuesSource.fromScorer(value!!))
                            }

                        @Throws(IOException::class)
                        override fun collect(doc: Int) {
                            val scoreExpl = searcher.explain(q, ctx.docBase + doc)
                            if (this.v.advanceExact(doc)) {
                                CheckHits.verifyExplanation(
                                    "",
                                    doc,
                                    v.doubleValue().toFloat(),
                                    true,
                                    rewritten.explain(ctx, doc, scoreExpl)
                                )
                            } else {
                                assertFalse(rewritten.explain(ctx, doc, scoreExpl).isMatch)
                            }
                        }

                        override fun scoreMode(): ScoreMode {
                            return if (vs.needsScores()) ScoreMode.COMPLETE else ScoreMode.COMPLETE_NO_SCORES
                        }
                    }
                }

                override fun reduce(collectors: MutableCollection<SimpleCollector>) {
                }
            }
        )
    }

    @Test
    @Throws(Exception::class)
    fun testQueryDoubleValuesSource() {
        val iteratingQuery = TermQuery(Term("english", "two"))
        val approximatingQuery = PhraseQuery.Builder()
            .add(Term("english", "hundred"), 0)
            .add(Term("english", "one"), 1)
            .build()

        doTestQueryDoubleValuesSources(iteratingQuery)
        doTestQueryDoubleValuesSources(approximatingQuery)
    }

    @Test
    @Throws(IOException::class)
    fun testRewriteSame() {
        val doubleField = DoubleValuesSource.constant(1.0).getSortField(false)
        assertSame(doubleField, doubleField.rewrite(searcher))
    }

    @Throws(Exception::class)
    private fun doTestQueryDoubleValuesSources(q: Query) {
        val vs = DoubleValuesSource.fromQuery(q).rewrite(searcher)
        searcher.search(
            q,
            object : CollectorManager<SimpleCollector, Unit> {
                override fun newCollector(): SimpleCollector {
                    return object : SimpleCollector() {
                        lateinit var v: DoubleValues
                        lateinit var ctx: LeafReaderContext
                        private var currentScorer: Scorable? = null
                        override var weight: Weight? = null

                        override fun doSetNextReader(context: LeafReaderContext) {
                            this.ctx = context
                        }

                        override var scorer: Scorable?
                            get() = currentScorer
                            set(value) {
                                currentScorer = value
                                this.v = vs.getValues(this.ctx, DoubleValuesSource.fromScorer(value!!))
                            }

                        @Throws(IOException::class)
                        override fun collect(doc: Int) {
                            assertTrue(v.advanceExact(doc))
                            assertEquals(scorer!!.score().toDouble(), v.doubleValue(), 0.00001)
                        }

                        override fun scoreMode(): ScoreMode {
                            return ScoreMode.COMPLETE
                        }
                    }
                }

                override fun reduce(collectors: MutableCollection<SimpleCollector>) {
                }
            }
        )
    }

}
