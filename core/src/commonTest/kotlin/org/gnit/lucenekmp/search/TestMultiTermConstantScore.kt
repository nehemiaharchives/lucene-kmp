package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.util.automaton.Operations
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMultiTermConstantScore : TestBaseRangeFilter() {
    /** threshold for comparing floats */
    companion object {
        const val SCORE_COMP_THRESH = 1e-6f

        val CONSTANT_SCORE_REWRITES = setOf(
            MultiTermQuery.CONSTANT_SCORE_REWRITE,
            MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE,
        )
    }

    private lateinit var small: Directory
    private lateinit var reader: IndexReader

    fun assertEquals(m: String, e: Int, a: Int) {
        assertEquals(e, a, m)
    }

    @BeforeTest
    @Throws(Exception::class)
    fun beforeClass() {
        val data =
            arrayOf(
                "A 1 2 3 4 5 6",
                "Z       4 5 6",
                null,
                "B   2   4 5 6",
                "Y     3   5 6",
                null,
                "C     3     6",
                "X       4 5 6",
            )

        small = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                small,
                newIndexWriterConfig(
                    MockAnalyzer(random(), MockTokenizer.WHITESPACE, false),
                ).setMergePolicy(newLogMergePolicy()),
            )

        val customType = FieldType(TextField.TYPE_STORED)
        customType.setTokenized(false)
        for (i in data.indices) {
            val doc = Document()
            doc.add(newField("id", i.toString(), customType))
            doc.add(newField("all", "all", customType))
            if (data[i] != null) {
                doc.add(newTextField("data", data[i], Field.Store.YES))
            }
            writer.addDocument(doc)
        }

        reader = writer.getReader(true, false)
        writer.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun afterClass() {
        reader.close()
        small.close()
    }

    /** macro for readability */
    fun csrq(
        f: String,
        l: String?,
        h: String?,
        il: Boolean,
        ih: Boolean,
        method: MultiTermQuery.RewriteMethod,
    ): Query {
        val query = TermRangeQuery.newStringRange(f, l, h, il, ih, method)
        if (VERBOSE) {
            println("TEST: query=$query method=$method")
        }
        return query
    }

    /** macro for readability */
    fun cspq(prefix: Term, method: MultiTermQuery.RewriteMethod): Query {
        return PrefixQuery(prefix, method)
    }

    /** macro for readability */
    fun cswcq(wild: Term, method: MultiTermQuery.RewriteMethod): Query {
        return WildcardQuery(wild, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT, method)
    }

    @Test
    @Throws(IOException::class)
    fun testBasics() {
        for (rw in CONSTANT_SCORE_REWRITES) {
            QueryUtils.check(csrq("data", "1", "6", T, T, rw))
            QueryUtils.check(csrq("data", "A", "Z", T, T, rw))
            QueryUtils.checkUnequal(csrq("data", "1", "6", T, T, rw), csrq("data", "A", "Z", T, T, rw))

            QueryUtils.check(cspq(Term("data", "p*u?"), rw))
            QueryUtils.checkUnequal(cspq(Term("data", "pre*"), rw), cspq(Term("data", "pres*"), rw))

            QueryUtils.check(cswcq(Term("data", "p"), rw))
            QueryUtils.checkUnequal(cswcq(Term("data", "pre*n?t"), rw), cswcq(Term("data", "pr*t?j"), rw))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testEqualScores() {
        // NOTE: uses index build in *this* setUp

        val search = newSearcher(reader)

        var result: Array<ScoreDoc>

        // some hits match more terms then others, score should be the same

        result =
            search.search(csrq("data", "1", "6", T, T, MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE), 1000)
                .scoreDocs
        var numHits = result.size
        assertEquals("wrong number of results", 6, numHits)
        val score = result[0].score
        for (i in 1..<numHits) {
            assertEquals(score, result[i].score, SCORE_COMP_THRESH, "score for $i was not the same")
        }

        result =
            search.search(csrq("data", "1", "6", T, T, MultiTermQuery.CONSTANT_SCORE_BOOLEAN_REWRITE), 1000)
                .scoreDocs
        numHits = result.size
        assertEquals("wrong number of results", 6, numHits)
        for (i in 0..<numHits) {
            assertEquals(score, result[i].score, SCORE_COMP_THRESH, "score for $i was not the same")
        }

        result =
            search.search(csrq("data", "1", "6", T, T, MultiTermQuery.CONSTANT_SCORE_REWRITE), 1000).scoreDocs
        numHits = result.size
        assertEquals("wrong number of results", 6, numHits)
        for (i in 0..<numHits) {
            assertEquals(score, result[i].score, SCORE_COMP_THRESH, "score for $i was not the same")
        }
    }

    @Test // Test for LUCENE-5245: Empty MTQ rewrites should have a consistent norm, so always need to
    // return a CSQ!
    @Throws(IOException::class)
    fun testEqualScoresWhenNoHits() {
        // NOTE: uses index build in *this* setUp

        val search = newSearcher(reader)

        var result: Array<ScoreDoc>

        val dummyTerm = TermQuery(Term("data", "1"))

        var bq = BooleanQuery.Builder()
        bq.add(dummyTerm, BooleanClause.Occur.SHOULD) // hits one doc
        bq.add(
            csrq("data", "#", "#", T, T, MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE),
            BooleanClause.Occur.SHOULD,
        ) // hits no docs
        result = search.search(bq.build(), 1000).scoreDocs
        var numHits = result.size
        assertEquals("wrong number of results", 1, numHits)
        val score = result[0].score
        for (i in 1..<numHits) {
            assertEquals(score, result[i].score, SCORE_COMP_THRESH, "score for $i was not the same")
        }

        bq = BooleanQuery.Builder()
        bq.add(dummyTerm, BooleanClause.Occur.SHOULD) // hits one doc
        bq.add(
            csrq("data", "#", "#", T, T, MultiTermQuery.CONSTANT_SCORE_BOOLEAN_REWRITE),
            BooleanClause.Occur.SHOULD,
        ) // hits no docs
        result = search.search(bq.build(), 1000).scoreDocs
        numHits = result.size
        assertEquals("wrong number of results", 1, numHits)
        for (i in 0..<numHits) {
            assertEquals(score, result[i].score, SCORE_COMP_THRESH, "score for $i was not the same")
        }

        bq = BooleanQuery.Builder()
        bq.add(dummyTerm, BooleanClause.Occur.SHOULD) // hits one doc
        bq.add(
            csrq("data", "#", "#", T, T, MultiTermQuery.CONSTANT_SCORE_REWRITE),
            BooleanClause.Occur.SHOULD,
        ) // hits no docs
        result = search.search(bq.build(), 1000).scoreDocs
        numHits = result.size
        assertEquals("wrong number of results", 1, numHits)
        for (i in 0..<numHits) {
            assertEquals(score, result[i].score, SCORE_COMP_THRESH, "score for $i was not the same")
        }
    }

    @Test
    @Throws(IOException::class)
    fun testBooleanOrderUnAffected() {
        // NOTE: uses index build in *this* setUp

        val search = newSearcher(reader)

        for (rw in CONSTANT_SCORE_REWRITES) {
            // first do a regular TermRangeQuery which uses term expansion so
            // docs with more terms in range get higher scores

            val rq = TermRangeQuery.newStringRange("data", "1", "4", T, T, rw)

            val expected = search.search(rq, 1000).scoreDocs
            val numHits = expected.size

            // now do a boolean where which also contains a
            // ConstantScoreRangeQuery and make sure the order is the same

            val q = BooleanQuery.Builder()
            q.add(rq, BooleanClause.Occur.MUST) // T, F);
            q.add(csrq("data", "1", "6", T, T, rw), BooleanClause.Occur.MUST) // T, F);

            val actual = search.search(q.build(), 1000).scoreDocs

            assertEquals("wrong number of hits", numHits, actual.size)
            for (i in 0..<numHits) {
                assertEquals("mismatch in docid for hit#$i", expected[i].doc, actual[i].doc)
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testRangeQueryId() {
        // NOTE: uses index build in *super* setUp

        val reader = signedIndexReader!!
        val search = newSearcher(reader)

        if (VERBOSE) {
            println("TEST: reader=$reader")
        }

        val medId = (maxId - minId) / 2

        val minIP = pad(minId)
        val maxIP = pad(maxId)
        val medIP = pad(medId)

        val numDocs = reader.numDocs()

        assertEquals("num of docs", numDocs, 1 + maxId - minId)

        var result: Array<ScoreDoc>

        for (rw in CONSTANT_SCORE_REWRITES) {
            // test id, bounded on both ends

            result = search.search(csrq("id", minIP, maxIP, T, T, rw), numDocs).scoreDocs
            assertEquals("find all", numDocs, result.size)

            result = search.search(csrq("id", minIP, maxIP, T, F, rw), numDocs).scoreDocs
            assertEquals("all but last", numDocs - 1, result.size)

            result = search.search(csrq("id", minIP, maxIP, F, T, rw), numDocs).scoreDocs
            assertEquals("all but first", numDocs - 1, result.size)

            result = search.search(csrq("id", minIP, maxIP, F, F, rw), numDocs).scoreDocs
            assertEquals("all but ends", numDocs - 2, result.size)

            result = search.search(csrq("id", medIP, maxIP, T, T, rw), numDocs).scoreDocs
            assertEquals("med and up", 1 + maxId - medId, result.size)

            result = search.search(csrq("id", minIP, medIP, T, T, rw), numDocs).scoreDocs
            assertEquals("up to med", 1 + medId - minId, result.size)

            // unbounded id

            result = search.search(csrq("id", minIP, null, T, F, rw), numDocs).scoreDocs
            assertEquals("min and up", numDocs, result.size)

            result = search.search(csrq("id", null, maxIP, F, T, rw), numDocs).scoreDocs
            assertEquals("max and down", numDocs, result.size)

            result = search.search(csrq("id", minIP, null, F, F, rw), numDocs).scoreDocs
            assertEquals("not min, but up", numDocs - 1, result.size)

            result = search.search(csrq("id", null, maxIP, F, F, rw), numDocs).scoreDocs
            assertEquals("not max, but down", numDocs - 1, result.size)

            result = search.search(csrq("id", medIP, maxIP, T, F, rw), numDocs).scoreDocs
            assertEquals("med and up, not max", maxId - medId, result.size)

            result = search.search(csrq("id", minIP, medIP, F, T, rw), numDocs).scoreDocs
            assertEquals("not min, up to med", medId - minId, result.size)

            // very small sets

            result = search.search(csrq("id", minIP, minIP, F, F, rw), numDocs).scoreDocs
            assertEquals("min,min,F,F", 0, result.size)

            result = search.search(csrq("id", medIP, medIP, F, F, rw), numDocs).scoreDocs
            assertEquals("med,med,F,F", 0, result.size)

            result = search.search(csrq("id", maxIP, maxIP, F, F, rw), numDocs).scoreDocs
            assertEquals("max,max,F,F", 0, result.size)

            result = search.search(csrq("id", minIP, minIP, T, T, rw), numDocs).scoreDocs
            assertEquals("min,min,T,T", 1, result.size)

            result = search.search(csrq("id", null, minIP, F, T, rw), numDocs).scoreDocs
            assertEquals("nul,min,F,T", 1, result.size)

            result = search.search(csrq("id", maxIP, maxIP, T, T, rw), numDocs).scoreDocs
            assertEquals("max,max,T,T", 1, result.size)

            result = search.search(csrq("id", maxIP, null, T, F, rw), numDocs).scoreDocs
            assertEquals("max,nul,T,T", 1, result.size)

            result = search.search(csrq("id", medIP, medIP, T, T, rw), numDocs).scoreDocs
            assertEquals("med,med,T,T", 1, result.size)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testRangeQueryRand() {
        // NOTE: uses index build in *super* setUp

        val reader = signedIndexReader!!
        val search = newSearcher(reader)

        val minRP = pad(signedIndexDir!!.minR)
        val maxRP = pad(signedIndexDir!!.maxR)

        val numDocs = reader.numDocs()

        assertEquals("num of docs", numDocs, 1 + maxId - minId)

        var result: Array<ScoreDoc>

        for (rw in CONSTANT_SCORE_REWRITES) {
            // test extremes, bounded on both ends

            result = search.search(csrq("rand", minRP, maxRP, T, T, rw), numDocs).scoreDocs
            assertEquals("find all", numDocs, result.size)

            result = search.search(csrq("rand", minRP, maxRP, T, F, rw), numDocs).scoreDocs
            assertEquals("all but biggest", numDocs - 1, result.size)

            result = search.search(csrq("rand", minRP, maxRP, F, T, rw), numDocs).scoreDocs
            assertEquals("all but smallest", numDocs - 1, result.size)

            result = search.search(csrq("rand", minRP, maxRP, F, F, rw), numDocs).scoreDocs
            assertEquals("all but extremes", numDocs - 2, result.size)

            // unbounded

            result = search.search(csrq("rand", minRP, null, T, F, rw), numDocs).scoreDocs
            assertEquals("smallest and up", numDocs, result.size)

            result = search.search(csrq("rand", null, maxRP, F, T, rw), numDocs).scoreDocs
            assertEquals("biggest and down", numDocs, result.size)

            result = search.search(csrq("rand", minRP, null, F, F, rw), numDocs).scoreDocs
            assertEquals("not smallest, but up", numDocs - 1, result.size)

            result = search.search(csrq("rand", null, maxRP, F, F, rw), numDocs).scoreDocs
            assertEquals("not biggest, but down", numDocs - 1, result.size)

            // very small sets

            result = search.search(csrq("rand", minRP, minRP, F, F, rw), numDocs).scoreDocs
            assertEquals("min,min,F,F", 0, result.size)
            result = search.search(csrq("rand", maxRP, maxRP, F, F, rw), numDocs).scoreDocs
            assertEquals("max,max,F,F", 0, result.size)

            result = search.search(csrq("rand", minRP, minRP, T, T, rw), numDocs).scoreDocs
            assertEquals("min,min,T,T", 1, result.size)
            result = search.search(csrq("rand", null, minRP, F, T, rw), numDocs).scoreDocs
            assertEquals("nul,min,F,T", 1, result.size)

            result = search.search(csrq("rand", maxRP, maxRP, T, T, rw), numDocs).scoreDocs
            assertEquals("max,max,T,T", 1, result.size)
            result = search.search(csrq("rand", maxRP, null, T, F, rw), numDocs).scoreDocs
            assertEquals("max,nul,T,T", 1, result.size)
        }
    }

    // tests inherited from TestBaseRangeFilter
    @Test
    override fun testPad() = super.testPad()
}
