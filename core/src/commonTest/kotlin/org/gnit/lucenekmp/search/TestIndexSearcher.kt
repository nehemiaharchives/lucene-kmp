package org.gnit.lucenekmp.search

import kotlinx.coroutines.Runnable
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.LinkedBlockingQueue
import org.gnit.lucenekmp.jdkport.ThreadPoolExecutor
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.get
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.IOUtils.close
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestIndexSearcher : LuceneTestCase() {
    lateinit var dir: Directory
    lateinit var reader: IndexReader

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val random: Random = random()
        for (i in 0..99) {
            val doc = Document()
            doc.add(newStringField("field", i.toString(), Store.NO))
            doc.add(newStringField("field2", (i % 2 == 0).toString(), Store.NO))
            doc.add(SortedDocValuesField("field2", newBytesRef((i % 2 == 0).toString())))
            iw.addDocument(doc)

            if (random.nextBoolean()) {
                iw.commit()
            }
        }
        reader = iw.reader
        iw.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        close(reader, dir)
    }

    // should not throw exception
    @Test
    @Throws(Exception::class)
    fun testHugeN() {
        val service: ExecutorService =
            ThreadPoolExecutor(
                4,
                4,
                0L,
                TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(),
                NamedThreadFactory("TestIndexSearcher")
            )

        val searchers: Array<IndexSearcher> =
            arrayOf(IndexSearcher(reader), IndexSearcher(reader, service))
        val queries: Array<Query> = arrayOf(MatchAllDocsQuery(), TermQuery(Term("field", "1")))
        val sorts: Array<Sort?> = arrayOf(null, Sort(SortField("field2", SortField.Type.STRING)))
        val afters: Array<ScoreDoc?> =
            arrayOf(null, FieldDoc(0, 0f, arrayOf(newBytesRef("boo!"))))

        for (searcher in searchers) {
            for (after in afters) {
                for (query in queries) {
                    for (sort in sorts) {
                        searcher.search(query, Int.MAX_VALUE)
                        searcher.searchAfter(after, query, Int.MAX_VALUE)
                        if (sort != null) {
                            searcher.search(query, Int.MAX_VALUE, sort)
                            searcher.search(query, Int.MAX_VALUE, sort, true)
                            searcher.search(query, Int.MAX_VALUE, sort, false)
                            searcher.searchAfter(after, query, Int.MAX_VALUE, sort)
                            searcher.searchAfter(after, query, Int.MAX_VALUE, sort, true)
                            searcher.searchAfter(after, query, Int.MAX_VALUE, sort, false)
                        }
                    }
                }
            }
        }

        TestUtil.shutdownExecutorService(service)
    }

    @Test
    @Throws(Exception::class)
    fun testSearchAfterPassedMaxDoc() {
        // LUCENE-5128: ensure we get a meaningful message if searchAfter exceeds maxDoc
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.addDocument(Document())
        val r: IndexReader = w.reader
        w.close()

        val s = IndexSearcher(r)
        expectThrows(
            IllegalArgumentException::class
        ) {
            s.searchAfter(ScoreDoc(r.maxDoc(), 0.54f), MatchAllDocsQuery(), 10)
        }

        IOUtils.close(r, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testCount() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(100)
        for (i in 0..<numDocs) {
            val doc = Document()
            if (random().nextBoolean()) {
                doc.add(StringField("foo", "bar", Store.NO))
            }
            if (random().nextBoolean()) {
                doc.add(StringField("foo", "baz", Store.NO))
            }
            if (rarely()) {
                doc.add(StringField("delete", "yes", Store.NO))
            }
            w.addDocument(doc)
        }
        for (delete in booleanArrayOf(false, true)) {
            if (delete) {
                w.deleteDocuments(Term("delete", "yes"))
            }
            val reader: IndexReader = w.reader
            val searcher: IndexSearcher = newSearcher(reader)
            // Test multiple queries, some of them are optimized by IndexSearcher.count()
            for (query in listOf(
                MatchAllDocsQuery(),
                MatchNoDocsQuery(),
                TermQuery(Term("foo", "bar")),
                ConstantScoreQuery(TermQuery(Term("foo", "baz"))),
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                    .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                    .build()
            )) {
                assertEquals(searcher.count(query), searcher.search(query, 1).totalHits.value.toInt())
            }
            reader.close()
        }
        IOUtils.close(w, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testGetQueryCache() {
        var searcher = IndexSearcher(MultiReader())
        assertEquals(IndexSearcher.defaultQueryCache, searcher.queryCache)
        val dummyCache: QueryCache =
            object : QueryCache {
                override fun doCache(weight: Weight, policy: QueryCachingPolicy): Weight {
                    return weight
                }
            }
        searcher.queryCache = dummyCache
        assertEquals(dummyCache, searcher.queryCache)

        IndexSearcher.setDefaultQueryCache(dummyCache)
        searcher = IndexSearcher(MultiReader())
        assertEquals(dummyCache, searcher.queryCache)

        searcher.queryCache = null
        assertNull(searcher.queryCache)

        IndexSearcher.setDefaultQueryCache(null)
        searcher = IndexSearcher(MultiReader())
        assertNull(searcher.queryCache)
    }

    @Test
    @Throws(IOException::class)
    fun testGetQueryCachingPolicy() {
        var searcher = IndexSearcher(MultiReader())
        assertEquals(IndexSearcher.defaultQueryCachingPolicy, searcher.queryCachingPolicy)
        val dummyPolicy: QueryCachingPolicy =
            object : QueryCachingPolicy {
                @Throws(IOException::class)
                override fun shouldCache(query: Query): Boolean {
                    return false
                }

                override fun onUse(query: Query) {}
            }
        searcher.queryCachingPolicy = dummyPolicy
        assertEquals(dummyPolicy, searcher.queryCachingPolicy)

        IndexSearcher.setDefaultQueryCachingPolicy(dummyPolicy)
        searcher = IndexSearcher(MultiReader())
        assertEquals(dummyPolicy, searcher.queryCachingPolicy)
    }

    @Test
    @Throws(IOException::class)
    fun testGetSlicesNoLeavesNoExecutor() {
        val slices: Array<IndexSearcher.LeafSlice> = IndexSearcher(MultiReader()).slices
        assertEquals(0, slices.size)
    }

    @Test
    @Throws(IOException::class)
    fun testGetSlicesNoLeavesWithExecutor() {
        val slices: Array<IndexSearcher.LeafSlice> =
            IndexSearcher(MultiReader()) { obj: Runnable -> obj.run() }.slices
        assertEquals(0, slices.size)
    }

    @Test
    @Throws(Exception::class)
    fun testGetSlices() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        for (i in 0..9) {
            w.addDocument(Document())
            // manually flush, so we get to create multiple segments almost all the times, as well as
            // multiple slices
            w.flush()
        }
        val r: IndexReader = w.reader
        w.close()

        run {
            // without executor
            val slices: Array<IndexSearcher.LeafSlice> = IndexSearcher(r).slices
            assertEquals(1, slices.size)
            assertEquals(r.leaves().size, slices[0].partitions.size)
        }
        run {
            // force creation of multiple slices, and provide an executor
            val searcher: IndexSearcher =
                object : IndexSearcher(r, Executor { obj: Runnable -> obj.run() }) {
                    override fun slices(leaves: MutableList<LeafReaderContext>): Array<LeafSlice> {
                        return slices(leaves, 1, 1, false)
                    }
                }
            val slices: Array<IndexSearcher.LeafSlice> = searcher.slices
            for (slice in slices) {
                assertEquals(1, slice.partitions.size)
            }
            assertEquals(r.leaves().size, slices.size)
        }
        IOUtils.close(r, dir)
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun testSlicesOffloadedToTheExecutor() {
        val leaves: MutableList<LeafReaderContext> = reader.leaves()
        val numExecutions: AtomicInteger = AtomicInteger(0)
        val searcher: IndexSearcher =
            object : IndexSearcher(
                reader,
                Executor { task: Runnable ->
                    numExecutions.incrementAndFetch()
                    task.run()
                }) {
                override fun slices(leaves: MutableList<LeafReaderContext>): Array<LeafSlice> {
                    val slices: MutableList<LeafSlice> = mutableListOf()
                    for (ctx in leaves) {
                        slices.add(
                            LeafSlice(
                                mutableListOf(
                                    LeafReaderContextPartition.createForEntireSegment(ctx)
                                )
                            )
                        )
                    }
                    return slices.toTypedArray<LeafSlice>()
                }
            }
        searcher.search(MatchAllDocsQuery(), 10)
        assertEquals(leaves.size - 1, numExecutions.get())
    }

    @Test
    fun testNullExecutorNonNullTaskExecutor() {
        val indexSearcher = IndexSearcher(reader)
        assertNotNull(indexSearcher.getTaskExecutor())
    }

    @Test
    @Throws(Exception::class)
    fun testSegmentPartitionsSameSlice() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.addDocument(Document())
        w.addDocument(Document())
        w.forceMerge(1)
        val reader: IndexReader = w.reader
        w.close()

        try {
            val indexSearcher: IndexSearcher =
                object : IndexSearcher(reader, Executor { obj: Runnable -> obj.run() }) {
                    override fun slices(leaves: MutableList<LeafReaderContext>): Array<LeafSlice> {
                        val slices: MutableList<LeafSlice> = mutableListOf()
                        for (ctx in leaves) {
                            slices.add(
                                LeafSlice(
                                    mutableListOf(
                                        LeafReaderContextPartition.createFromAndTo(ctx, 0, 1),
                                        LeafReaderContextPartition.createFromAndTo(ctx, 1, ctx.reader().maxDoc())
                                    )
                                )
                            )
                        }
                        return slices.toTypedArray<LeafSlice>()
                    }
                }

            assumeTrue(
                "Needs at least 2 docs in the same segment",
                indexSearcher.leafContexts.all { ctx: LeafReaderContext -> ctx.reader().maxDoc() > 1 })
            val e: IllegalStateException = expectThrows(IllegalStateException::class) { indexSearcher.slices }
            assertEquals(
                "The same slice targets multiple leaf partitions of the same leaf reader context. A physical segment should rather get partitioned to be searched concurrently from as many slices as the number of leaf partitions it is split into.",
                e.message
            )
        } finally {
            IOUtils.close(reader, dir)
        }
    }
}
