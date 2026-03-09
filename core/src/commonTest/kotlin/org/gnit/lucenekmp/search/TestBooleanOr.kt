package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IntArrayDocIdSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestBooleanOr : LuceneTestCase() {
    private val t1 = TermQuery(Term(FIELD_T, "files"))
    private val t2 = TermQuery(Term(FIELD_T, "deleting"))
    private val c1 = TermQuery(Term(FIELD_C, "production"))
    private val c2 = TermQuery(Term(FIELD_C, "optimize"))

    private var searcher: IndexSearcher? = null
    private var dir: Directory? = null
    private var reader: IndexReader? = null

    @Throws(Exception::class)
    @BeforeTest
    fun setUp() {
        dir = newDirectory()

        val writer = RandomIndexWriter(random(), dir!!)

        val d = Document()
        d.add(newField(FIELD_T, "Optimize not deleting all files", TextField.TYPE_STORED))
        d.add(
            newField(
                FIELD_C,
                "Deleted When I run an optimize in our production environment.",
                TextField.TYPE_STORED
            )
        )

        writer.addDocument(d)

        reader = writer.getReader(true, false)
        searcher = newSearcher(reader!!)
        writer.close()
    }

    @Throws(Exception::class)
    @AfterTest
    fun tearDown() {
        reader?.close()
        dir?.close()
    }

    @Throws(IOException::class)
    private fun search(q: Query): Long {
        QueryUtils.check(random(), q, searcher!!)
        return searcher!!.search(q, 1000).totalHits.value
    }

    @Test
    @Throws(Exception::class)
    fun testElements() {
        assertEquals(1, search(t1))
        assertEquals(1, search(t2))
        assertEquals(1, search(c1))
        assertEquals(1, search(c2))
    }

    /** `T:files T:deleting C:production C:optimize ` it works. */
    @Test
    @Throws(Exception::class)
    fun testFlat() {
        val q = BooleanQuery.Builder()
        q.add(BooleanClause(t1, BooleanClause.Occur.SHOULD))
        q.add(BooleanClause(t2, BooleanClause.Occur.SHOULD))
        q.add(BooleanClause(c1, BooleanClause.Occur.SHOULD))
        q.add(BooleanClause(c2, BooleanClause.Occur.SHOULD))
        assertEquals(1, search(q.build()))
    }

    /** `(T:files T:deleting) (+C:production +C:optimize)` it works. */
    @Test
    @Throws(Exception::class)
    fun testParenthesisMust() {
        val q3 = BooleanQuery.Builder()
        q3.add(BooleanClause(t1, BooleanClause.Occur.SHOULD))
        q3.add(BooleanClause(t2, BooleanClause.Occur.SHOULD))
        val q4 = BooleanQuery.Builder()
        q4.add(BooleanClause(c1, BooleanClause.Occur.MUST))
        q4.add(BooleanClause(c2, BooleanClause.Occur.MUST))
        val q2 = BooleanQuery.Builder()
        q2.add(q3.build(), BooleanClause.Occur.SHOULD)
        q2.add(q4.build(), BooleanClause.Occur.SHOULD)
        assertEquals(1, search(q2.build()))
    }

    /** `(T:files T:deleting) +(C:production C:optimize)` not working. results NO HIT. */
    @Test
    @Throws(Exception::class)
    fun testParenthesisMust2() {
        val q3 = BooleanQuery.Builder()
        q3.add(BooleanClause(t1, BooleanClause.Occur.SHOULD))
        q3.add(BooleanClause(t2, BooleanClause.Occur.SHOULD))
        val q4 = BooleanQuery.Builder()
        q4.add(BooleanClause(c1, BooleanClause.Occur.SHOULD))
        q4.add(BooleanClause(c2, BooleanClause.Occur.SHOULD))
        val q2 = BooleanQuery.Builder()
        q2.add(q3.build(), BooleanClause.Occur.SHOULD)
        q2.add(q4.build(), BooleanClause.Occur.MUST)
        assertEquals(1, search(q2.build()))
    }

    /** `(T:files T:deleting) (C:production C:optimize)` not working. results NO HIT. */
    @Test
    @Throws(Exception::class)
    fun testParenthesisShould() {
        val q3 = BooleanQuery.Builder()
        q3.add(BooleanClause(t1, BooleanClause.Occur.SHOULD))
        q3.add(BooleanClause(t2, BooleanClause.Occur.SHOULD))
        val q4 = BooleanQuery.Builder()
        q4.add(BooleanClause(c1, BooleanClause.Occur.SHOULD))
        q4.add(BooleanClause(c2, BooleanClause.Occur.SHOULD))
        val q2 = BooleanQuery.Builder()
        q2.add(q3.build(), BooleanClause.Occur.SHOULD)
        q2.add(q4.build(), BooleanClause.Occur.SHOULD)
        assertEquals(1, search(q2.build()))
    }

    @Test
    @Throws(Exception::class)
    fun testBooleanScorerMax() {
        val dir = newDirectory()
        val riw =
            RandomIndexWriter(random(), dir, newIndexWriterConfig(MockAnalyzer(random())))

        val docCount = atLeast(10000)

        for (i in 0 until docCount) {
            val doc = Document()
            doc.add(newField("field", "a", TextField.TYPE_NOT_STORED))
            riw.addDocument(doc)
        }

        riw.forceMerge(1)
        val r = riw.getReader(true, false)
        riw.close()

        val s = newSearcher(r)
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term("field", "a")), BooleanClause.Occur.SHOULD)
        bq.add(TermQuery(Term("field", "a")), BooleanClause.Occur.SHOULD)

        val w = s.createWeight(s.rewrite(bq.build()), ScoreMode.COMPLETE, 1f)

        assertEquals(1, s.indexReader.leaves().size)
        val scorer = w.bulkScorer(s.indexReader.leaves()[0])

        val hits = FixedBitSet(docCount)
        var end = 0
        val c =
            object : SimpleCollector() {
                override var weight: Weight? = null

                override fun collect(doc: Int) {
                    assertTrue(doc < end, "collected doc=$doc beyond max=$end")
                    hits.set(doc)
                }

                override fun scoreMode(): ScoreMode {
                    return ScoreMode.COMPLETE_NO_SCORES
                }
            }

        while (end < docCount) {
            val min = end
            val inc = TestUtil.nextInt(random(), 1, 1000)
            end += inc
            scorer!!.score(c, null, min, end)
        }

        assertEquals(docCount, hits.cardinality())
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSubScorerNextIsNotMatch() {
        val optionalScorers =
            mutableListOf(
                scorer(100000, 1000001, 9999999),
                scorer(4000, 1000051),
                scorer(5000, 100000, 9999998, 9999999)
            )
        optionalScorers.shuffle(random())
        val scorer = BooleanScorer(optionalScorers, 1, random().nextBoolean())
        val matches = mutableListOf<Int>()
        scorer.score(
            object : LeafCollector {
                override var scorer: Scorable? = null

                override fun collect(doc: Int) {
                    matches.add(doc)
                }
            },
            null,
            0,
            DocIdSetIterator.NO_MORE_DOCS
        )
        assertEquals(listOf(4000, 5000, 100000, 1000001, 1000051, 9999998, 9999999), matches)
    }

    companion object {
        private const val FIELD_T = "T"
        private const val FIELD_C = "C"

        @Throws(IOException::class)
        private fun scorer(vararg matches: Int): Scorer {
            val array = ArrayUtil.growExact(matches, matches.size + 1)
            array[array.size - 1] = DocIdSetIterator.NO_MORE_DOCS
            val it = IntArrayDocIdSet(array, array.size - 1).iterator()
            return object : Scorer() {
                override fun iterator(): DocIdSetIterator {
                    return it
                }

                override fun docID(): Int {
                    return it.docID()
                }

                override fun getMaxScore(upTo: Int): Float {
                    return Float.MAX_VALUE
                }

                override fun score(): Float {
                    return 0f
                }
            }
        }
    }
}
