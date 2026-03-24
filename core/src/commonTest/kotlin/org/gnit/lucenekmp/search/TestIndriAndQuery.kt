package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.similarities.IndriDirichletSimilarity
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestIndriAndQuery : LuceneTestCase() {
    /** threshold for comparing floats */
    val SCORE_COMP_THRESH = 0.0000f

    var sim: Similarity = IndriDirichletSimilarity()
    var index: Directory? = null
    var r: IndexReader? = null
    var s: IndexSearcher? = null

    @BeforeTest
    fun setUpTestIndriAndQuery() {
        index = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                index!!,
                newIndexWriterConfig(
                    MockAnalyzer(
                        random(),
                        MockTokenizer.WHITESPACE,
                        true,
                        MockTokenFilter.ENGLISH_STOPSET
                    )
                )
                    .setSimilarity(sim)
                    .setMergePolicy(newLogMergePolicy())
            )
        // Query is "President Washington"
        run {
            val d1 = Document()
            d1.add(newField("id", "d1", TextField.TYPE_STORED))
            d1.add(
                newTextField(
                    "body",
                    "President Washington was the first leader of the US",
                    Field.Store.YES
                )
            )
            writer.addDocument(d1)
        }

        run {
            val d2 = Document()
            d2.add(newField("id", "d2", TextField.TYPE_STORED))
            d2.add(
                newTextField(
                    "body",
                    "The president is head of the executive branch of government",
                    Field.Store.YES
                )
            )
            writer.addDocument(d2)
        }

        run {
            val d3 = Document()
            d3.add(newField("id", "d3", TextField.TYPE_STORED))
            d3.add(
                newTextField(
                    "body",
                    "George Washington was a general in the Revolutionary War",
                    Field.Store.YES
                )
            )
            writer.addDocument(d3)
        }

        run {
            val d4 = Document()
            d4.add(newField("id", "d4", TextField.TYPE_STORED))
            d4.add(newTextField("body", "A company or college can have a president", Field.Store.YES))
            writer.addDocument(d4)
        }

        writer.forceMerge(1)
        r = getOnlyLeafReader(writer.reader)
        writer.close()
        s = IndexSearcher(r!!)
        s!!.similarity = sim
    }

    @AfterTest
    fun tearDownTestIndriAndQuery() {
        r!!.close()
        index!!.close()
    }

    @Test
    fun testSimpleQuery1() {
        val clause1 = BooleanClause(tq("body", "george"), Occur.SHOULD)
        val clause2 = BooleanClause(tq("body", "washington"), Occur.SHOULD)

        val q = IndriAndQuery(mutableListOf(clause1, clause2))

        val h = s!!.search(q, 1000).scoreDocs

        try {
            assertEquals(2, h.size, "2 docs should match ${q.toString()}")
        } catch (e: Error) {
            printHits("testSimpleEqualScores1", h, s!!)
            throw e
        }
    }

    @Test
    fun testSimpleQuery2() {
        val clause1 = BooleanClause(tq("body", "president"), Occur.SHOULD)
        val clause2 = BooleanClause(tq("body", "washington"), Occur.SHOULD)

        val q = IndriAndQuery(mutableListOf(clause1, clause2))

        val h = s!!.search(q, 1000).scoreDocs

        try {
            assertEquals(4, h.size, "all docs should match ${q.toString()}")
        } catch (e: Error) {
            printHits("testSimpleEqualScores1", h, s!!)
            throw e
        }
    }

    /** macro */
    protected fun tq(f: String, t: String): Query {
        return TermQuery(Term(f, t))
    }

    /** macro */
    protected fun tq(f: String, t: String, b: Float): Query {
        val q = tq(f, t)
        return BoostQuery(q, b)
    }

    protected fun printHits(test: String, h: Array<ScoreDoc>, searcher: IndexSearcher) {
        println("------- $test -------")

        val storedFields: StoredFields = searcher.storedFields()
        for (i in h.indices) {
            val d = storedFields.document(h[i].doc)
            val score = h[i].score
            println("#$i: $score - ${d.get("body")}")
        }
    }
}
