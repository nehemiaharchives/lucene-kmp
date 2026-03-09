package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test BooleanQuery2 against BooleanQuery by overriding the standard query parser. This also tests
 * the scoring order of BooleanQuery.
 */
class TestBoolean2 : LuceneTestCase() {
    @Throws(IOException::class)
    private fun copyOf(dir: Directory): Directory {
        val copy = newFSDirectory(createTempDir())
        for (name in dir.listAll()) {
            if (name.startsWith("extra")) {
                continue
            }
            copy.copyFrom(dir, name, name, IOContext.DEFAULT)
            copy.sync(mutableSetOf(name))
        }
        return copy
    }

    @Throws(Exception::class)
    fun beforeClass() {
        if (searcher != null) {
            return
        }

        try {
            // in some runs, test immediate adjacency of matches - in others, force a full bucket gap
            // between docs
            NUM_FILLER_DOCS = if (random().nextBoolean()) 0 else BooleanScorer.SIZE
            PRE_FILLER_DOCS = TestUtil.nextInt(random(), 0, NUM_FILLER_DOCS / 2)
            if (VERBOSE) {
                println("TEST: NUM_FILLER_DOCS=$NUM_FILLER_DOCS PRE_FILLER_DOCS=$PRE_FILLER_DOCS")
            }

            directory = if (NUM_FILLER_DOCS * PRE_FILLER_DOCS > 100000) {
                newFSDirectory(createTempDir())
            } else {
                newDirectory()
            }

            var iwc: IndexWriterConfig = newIndexWriterConfig(random(), MockAnalyzer(random()))
            // randomized codecs are sometimes too costly for this test:
            iwc.setCodec(TestUtil.getDefaultCodec())
            iwc.setMergePolicy(newLogMergePolicy())
            val writer = RandomIndexWriter(random(), directory!!, iwc)
            // we'll make a ton of docs, disable store/norms/vectors
            val ft = FieldType(TextField.TYPE_NOT_STORED)
            ft.setOmitNorms(true)

            var doc = Document()
            repeat(PRE_FILLER_DOCS) {
                writer.addDocument(doc)
            }
            for (i in docFields.indices) {
                doc.add(Field(field, docFields[i], ft))
                writer.addDocument(doc)

                doc = Document()
                repeat(NUM_FILLER_DOCS) {
                    writer.addDocument(doc)
                }
            }
            writer.close()
            littleReader = DirectoryReader.open(directory!!)
            searcher = newSearcher(littleReader!!)
            // this is intentionally using the baseline sim, because it compares against bigSearcher
            // (which uses a random one)
            searcher!!.similarity = ClassicSimilarity()

            // make a copy of our index using a single segment
            singleSegmentDirectory = if (NUM_FILLER_DOCS * PRE_FILLER_DOCS > 100000) {
                newFSDirectory(createTempDir())
            } else {
                newDirectory()
            }

            // TODO: this test does not need to be doing this crazy stuff. please improve it!
            for (fileName in directory!!.listAll()) {
                if (fileName.startsWith("extra")) {
                    continue
                }
                singleSegmentDirectory!!.copyFrom(directory!!, fileName, fileName, IOContext.DEFAULT)
                singleSegmentDirectory!!.sync(mutableSetOf(fileName))
            }

            iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
            // we need docID order to be preserved:
            // randomized codecs are sometimes too costly for this test:
            iwc.setCodec(TestUtil.getDefaultCodec())
            iwc.setMergePolicy(newLogMergePolicy())
            IndexWriter(singleSegmentDirectory!!, iwc).use { w ->
                w.forceMerge(1, true)
            }
            singleSegmentReader = DirectoryReader.open(singleSegmentDirectory!!)
            singleSegmentSearcher = newSearcher(singleSegmentReader!!)
            singleSegmentSearcher!!.similarity = searcher!!.similarity

            // Make big index
            dir2 = copyOf(directory!!)

            // First multiply small test index:
            mulFactor = 1
            var docCount: Int
            if (VERBOSE) {
                println("\nTEST: now copy index...")
            }
            do {
                if (VERBOSE) {
                    println("\nTEST: cycle...")
                }
                val copy = copyOf(dir2!!)

                iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
                // randomized codecs are sometimes too costly for this test:
                iwc.setCodec(TestUtil.getDefaultCodec())
                val w = RandomIndexWriter(random(), dir2!!, iwc)
                w.addIndexes(copy)
                copy.close()
                docCount = w.docStats.maxDoc
                w.close()
                mulFactor *= 2
            } while (docCount < 3000 * NUM_FILLER_DOCS)

            iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
            iwc.setMaxBufferedDocs(TestUtil.nextInt(random(), 50, 1000))
            // randomized codecs are sometimes too costly for this test:
            iwc.setCodec(TestUtil.getDefaultCodec())
            val w = RandomIndexWriter(random(), dir2!!, iwc)

            doc = Document()
            doc.add(Field("field2", "xxx", ft))
            repeat(NUM_EXTRA_DOCS / 2) {
                w.addDocument(doc)
            }
            doc = Document()
            doc.add(Field("field2", "big bad bug", ft))
            repeat(NUM_EXTRA_DOCS / 2) {
                w.addDocument(doc)
            }
            reader = w.getReader(true, false)
            bigSearcher = newSearcher(reader!!)
            w.close()
        } catch (t: Throwable) {
            afterClass()
            throw t
        }
    }

    @Throws(Exception::class)
    fun afterClass() {
        reader?.close()
        littleReader?.close()
        singleSegmentReader?.close()
        dir2?.close()
        directory?.close()
        singleSegmentDirectory?.close()
        singleSegmentSearcher = null
        singleSegmentReader = null
        singleSegmentDirectory = null
        searcher = null
        reader = null
        littleReader = null
        dir2 = null
        directory = null
        bigSearcher = null
    }

    @Throws(Exception::class)
    fun queriesTest(query: Query, expDocNrs: IntArray) {
        beforeClass()

        val searcher = searcher!!
        val singleSegmentSearcher = singleSegmentSearcher!!
        val bigSearcher = bigSearcher!!
        var expDocNrs = expDocNrs

        // adjust the expected doc numbers according to our filler docs
        if (0 < NUM_FILLER_DOCS) {
            expDocNrs = ArrayUtil.copyArray(expDocNrs)
            for (i in expDocNrs.indices) {
                expDocNrs[i] = PRE_FILLER_DOCS + ((NUM_FILLER_DOCS + 1) * expDocNrs[i])
            }
        }

        val topDocsToCheck = atLeast(1000)
        // The asserting searcher will sometimes return the bulk scorer and
        // sometimes return a default impl around the scorer so that we can
        // compare BS1 and BS2
        var collectorManager = TopScoreDocCollectorManager(topDocsToCheck, Int.MAX_VALUE)
        var hits1 = searcher.search(query, collectorManager).scoreDocs
        collectorManager = TopScoreDocCollectorManager(topDocsToCheck, Int.MAX_VALUE)
        var hits2 = searcher.search(query, collectorManager).scoreDocs

        CheckHits.checkHitsQuery(query, hits1, hits2, expDocNrs)

        // Since we have no deleted docs, we should also be able to verify identical matches &
        // scores against an single segment copy of our index
        collectorManager = TopScoreDocCollectorManager(topDocsToCheck, Int.MAX_VALUE)
        val topDocs = singleSegmentSearcher.search(query, collectorManager)
        hits2 = topDocs.scoreDocs
        CheckHits.checkHitsQuery(query, hits1, hits2, expDocNrs)

        // sanity check expected num matches in bigSearcher
        assertEquals(mulFactor.toLong() * topDocs.totalHits.value, bigSearcher.count(query).toLong())

        // now check 2 diff scorers from the bigSearcher as well
        collectorManager = TopScoreDocCollectorManager(topDocsToCheck, Int.MAX_VALUE)
        hits1 = bigSearcher.search(query, collectorManager).scoreDocs
        collectorManager = TopScoreDocCollectorManager(topDocsToCheck, Int.MAX_VALUE)
        hits2 = bigSearcher.search(query, collectorManager).scoreDocs

        // NOTE: just comparing results, not vetting against expDocNrs
        // since we have dups in bigSearcher
        CheckHits.checkEqual(query, hits1, hits2)
    }

    @Test
    @Throws(Exception::class)
    fun testQueries01() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(field, "w3")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(field, "xx")), BooleanClause.Occur.MUST)
        val expDocNrs = intArrayOf(2, 3)
        queriesTest(query.build(), expDocNrs)
    }

    @Test
    @Throws(Exception::class)
    fun testQueries02() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(field, "w3")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(field, "xx")), BooleanClause.Occur.SHOULD)
        val expDocNrs = intArrayOf(2, 3, 1, 0)
        queriesTest(query.build(), expDocNrs)
    }

    @Test
    @Throws(Exception::class)
    fun testQueries03() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(field, "w3")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term(field, "xx")), BooleanClause.Occur.SHOULD)
        val expDocNrs = intArrayOf(2, 3, 1, 0)
        queriesTest(query.build(), expDocNrs)
    }

    @Test
    @Throws(Exception::class)
    fun testQueries04() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(field, "w3")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term(field, "xx")), BooleanClause.Occur.MUST_NOT)
        val expDocNrs = intArrayOf(1, 0)
        queriesTest(query.build(), expDocNrs)
    }

    @Test
    @Throws(Exception::class)
    fun testQueries05() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(field, "w3")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(field, "xx")), BooleanClause.Occur.MUST_NOT)
        val expDocNrs = intArrayOf(1, 0)
        queriesTest(query.build(), expDocNrs)
    }

    @Test
    @Throws(Exception::class)
    fun testQueries06() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(field, "w3")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(field, "xx")), BooleanClause.Occur.MUST_NOT)
        query.add(TermQuery(Term(field, "w5")), BooleanClause.Occur.MUST_NOT)
        val expDocNrs = intArrayOf(1)
        queriesTest(query.build(), expDocNrs)
    }

    @Test
    @Throws(Exception::class)
    fun testQueries07() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(field, "w3")), BooleanClause.Occur.MUST_NOT)
        query.add(TermQuery(Term(field, "xx")), BooleanClause.Occur.MUST_NOT)
        query.add(TermQuery(Term(field, "w5")), BooleanClause.Occur.MUST_NOT)
        val expDocNrs = intArrayOf()
        queriesTest(query.build(), expDocNrs)
    }

    @Test
    @Throws(Exception::class)
    fun testQueries08() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(field, "w3")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(field, "xx")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term(field, "w5")), BooleanClause.Occur.MUST_NOT)
        val expDocNrs = intArrayOf(2, 3, 1)
        queriesTest(query.build(), expDocNrs)
    }

    @Test
    @Throws(Exception::class)
    fun testQueries09() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(field, "w3")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(field, "xx")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(field, "w2")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(field, "zz")), BooleanClause.Occur.SHOULD)
        val expDocNrs = intArrayOf(2, 3)
        queriesTest(query.build(), expDocNrs)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomQueries() {
        beforeClass()

        val searcher = searcher!!
        val bigSearcher = bigSearcher!!
        val vals = arrayOf("w1", "w2", "w3", "w4", "w5", "xx", "yy", "zzz")

        var q1: BooleanQuery? = null
        try {
            // increase number of iterations for more complete testing
            val num = atLeast(3)
            repeat(num) {
                val level = random().nextInt(3)
                q1 = randBoolQuery(Random(random().nextLong()), random().nextBoolean(), level, field, vals, null).build()

                // Can't sort by relevance since floating point numbers may not quite
                // match up.
                val sort = Sort.INDEXORDER

                QueryUtils.check(random(), q1, searcher) // baseline sim
                try {
                    // a little hackish, QueryUtils.check is too costly to do on bigSearcher in this loop.
                    searcher.similarity = bigSearcher.similarity // random sim
                    QueryUtils.check(random(), q1, searcher)
                } finally {
                    searcher.similarity = ClassicSimilarity() // restore
                }

                // check diff (randomized) scorers (from AssertingSearcher) produce the same results
                var hits1 = searcher.search(q1, TopFieldCollectorManager(sort, 1000, 1)).scoreDocs
                val topDocs = searcher.search(q1, TopFieldCollectorManager(sort, 1000, 1))
                var hits2 = topDocs.scoreDocs
                CheckHits.checkEqual(q1, hits1, hits2)

                val q3 = BooleanQuery.Builder()
                q3.add(q1, BooleanClause.Occur.SHOULD)
                q3.add(PrefixQuery(Term("field2", "b")), BooleanClause.Occur.SHOULD)
                assertEquals(
                    mulFactor.toLong() * topDocs.totalHits.value + (NUM_EXTRA_DOCS / 2).toLong(),
                    bigSearcher.count(q3.build()).toLong()
                )

                // test diff (randomized) scorers produce the same results on bigSearcher as well
                hits1 = bigSearcher.search(q1, TopFieldCollectorManager(sort, mulFactor, 1)).scoreDocs
                hits2 = bigSearcher.search(q1, TopFieldCollectorManager(sort, mulFactor, 1)).scoreDocs
                CheckHits.checkEqual(q1, hits1, hits2)
            }
        } catch (e: Exception) {
            // For easier debugging
            println("failed query: $q1")
            throw e
        }

        // System.out.println("Total hits:"+tot);
    }

    // used to set properties or change every BooleanQuery
    // generated from randBoolQuery.
    interface Callback {
        fun postCreate(q: BooleanQuery.Builder)
    }

    companion object {
        private var searcher: IndexSearcher? = null
        private var singleSegmentSearcher: IndexSearcher? = null
        private var bigSearcher: IndexSearcher? = null
        private var reader: IndexReader? = null
        private var littleReader: IndexReader? = null
        private var singleSegmentReader: IndexReader? = null

        /** num of empty docs injected between every doc in the (main) index */
        private var NUM_FILLER_DOCS: Int = 0

        /** num of empty docs injected prior to the first doc in the (main) index */
        private var PRE_FILLER_DOCS: Int = 0

        /** num "extra" docs containing value in "field2" added to the "big" clone of the index */
        private const val NUM_EXTRA_DOCS: Int = 6000

        const val field: String = "field"
        private var directory: Directory? = null
        private var singleSegmentDirectory: Directory? = null
        private var dir2: Directory? = null
        private var mulFactor: Int = 0

        private val docFields = arrayOf(
            "w1 w2 w3 w4 w5",
            "w1 w3 w2 w3",
            "w1 xx w2 yy w3",
            "w1 w3 xx w2 yy mm"
        )

        // Random rnd is passed in so that the exact same random query may be created
        // more than once.
        fun randBoolQuery(
            rnd: Random,
            allowMust: Boolean,
            level: Int,
            field: String,
            vals: Array<String>,
            cb: Callback?
        ): BooleanQuery.Builder {
            val current = BooleanQuery.Builder()
            repeat(rnd.nextInt(vals.size) + 1) {
                var qType = 0 // term query
                if (level > 0) {
                    qType = rnd.nextInt(10)
                }
                val q: Query = if (qType < 3) {
                    TermQuery(Term(field, vals[rnd.nextInt(vals.size)]))
                } else if (qType < 4) {
                    val t1 = vals[rnd.nextInt(vals.size)]
                    val t2 = vals[rnd.nextInt(vals.size)]
                    PhraseQuery(10, field, t1, t2) // slop increases possibility of matching
                } else if (qType < 7) {
                    WildcardQuery(Term(field, "w*"))
                } else {
                    randBoolQuery(rnd, allowMust, level - 1, field, vals, cb).build()
                }

                val r = rnd.nextInt(10)
                val occur: BooleanClause.Occur = if (r < 2) {
                    BooleanClause.Occur.MUST_NOT
                } else if (r < 5) {
                    if (allowMust) {
                        BooleanClause.Occur.MUST
                    } else {
                        BooleanClause.Occur.SHOULD
                    }
                } else {
                    BooleanClause.Occur.SHOULD
                }

                current.add(q, occur)
            }
            cb?.postCreate(current)
            return current
        }
    }
}
