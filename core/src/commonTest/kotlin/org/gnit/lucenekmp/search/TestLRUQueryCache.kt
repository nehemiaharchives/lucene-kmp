/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.search

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilterDirectoryReader
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.updateAndGet
import org.gnit.lucenekmp.jdkport.ExecutionException
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.all
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.DummyTotalHitCountCollector
import org.gnit.lucenekmp.tests.util.RamUsageTester
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.AwaitsFix
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomFloat
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalAtomicApi::class)
class TestLRUQueryCache : LuceneTestCase() {

    private val ALWAYS_CACHE: QueryCachingPolicy = object : QueryCachingPolicy {
        override fun onUse(query: Query) {}
        override fun shouldCache(query: Query): Boolean = true
    }

    private val NEVER_CACHE: QueryCachingPolicy = object : QueryCachingPolicy {
        override fun onUse(query: Query) {}
        override fun shouldCache(query: Query): Boolean = false
    }

    @Test
    fun testConcurrency() {
        val queryCache = LRUQueryCache(
            1 + random().nextInt(20),
            1 + random().nextInt(10000).toLong(),
            { random().nextBoolean() },
            Float.POSITIVE_INFINITY
        )
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val searcherFactory = object : SearcherFactory() {
            override fun newSearcher(reader: IndexReader, previousReader: IndexReader?): IndexSearcher {
                return IndexSearcher(reader).also {
                    it.queryCachingPolicy = MAYBE_CACHE_POLICY
                    it.queryCache = queryCache
                }
            }
        }
        val applyDeletes = random().nextBoolean()
        val mgr = SearcherManager(w.w, applyDeletes, false, searcherFactory)
        val indexing = AtomicBoolean(true)
        val error = AtomicReference<Throwable?>(null)
        val numDocs = atLeast(1000)
        val threads = arrayOfNulls<Thread>(3)
        threads[0] = Thread {
            val doc = Document()
            val f = StringField("color", "", Field.Store.NO)
            doc.add(f)
            for (i in 0 until numDocs) {
                if (!indexing.load()) {
                    break
                }
                f.setStringValue(RandomPicks.randomFrom(random(), arrayOf("blue", "red", "yellow")))
                try {
                    w.addDocument(doc)
                    if ((i and 63) == 0) {
                        mgr.maybeRefresh()
                        if (rarely()) {
                            runBlocking { queryCache.clear() }
                        }
                        if (rarely()) {
                            val color = RandomPicks.randomFrom(random(), arrayOf("blue", "red", "yellow"))
                            w.deleteDocuments(Term("color", color))
                        }
                    }
                } catch (t: Throwable) {
                    if (error.load() == null) {
                        error.store(t)
                    }
                    break
                }
            }
            indexing.store(false)
        }
        for (i in 1 until threads.size) {
            threads[i] = Thread {
                while (indexing.load()) {
                    try {
                        val searcher = mgr.acquire()
                        try {
                            val value = RandomPicks.randomFrom(random(), arrayOf("blue", "red", "yellow", "green"))
                            val q: Query = TermQuery(Term("color", value))
                            val totalHits1 = searcher.search(q, DummyTotalHitCountCollector.createManager())
                            val totalHits2 = searcher.search(q, 10).totalHits.value
                            assertEquals(totalHits2, totalHits1.toLong())
                        } finally {
                            mgr.release(searcher)
                        }
                    } catch (t: Throwable) {
                        if (error.load() == null) {
                            error.store(t)
                        }
                    }
                }
            }
        }

        for (thread in threads) {
            thread!!.start()
        }
        for (thread in threads) {
            thread!!.join()
        }

        try {
            error.load()?.let { throw it }
            runBlocking { queryCache.assertConsistent() }
        } finally {
            mgr.close()
            w.close()
            dir.close()
            runBlocking { queryCache.assertConsistent() }
        }
    }

    @Test
    fun testLRUEviction() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val doc = Document()
        val f = StringField("color", "blue", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        f.setStringValue("red")
        w.addDocument(doc)
        f.setStringValue("green")
        w.addDocument(doc)
        val reader = w.reader
        val searcher = newSearcher(reader)
        val queryCache = LRUQueryCache(2, 100000, { true }, Float.POSITIVE_INFINITY)

        val blue: Query = TermQuery(Term("color", "blue"))
        val red: Query = TermQuery(Term("color", "red"))
        val green: Query = TermQuery(Term("color", "green"))

        assertEquals(emptyList(), runBlocking { queryCache.cachedQueries() })

        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = NEVER_CACHE
        searcher.search(ConstantScoreQuery(green), 1)
        assertEquals(emptyList(), runBlocking { queryCache.cachedQueries() })

        searcher.queryCachingPolicy = ALWAYS_CACHE
        searcher.search(ConstantScoreQuery(red), 1)
        assertEquals(listOf(red), runBlocking { queryCache.cachedQueries() })

        searcher.search(ConstantScoreQuery(green), 1)
        assertEquals(listOf(red, green), runBlocking { queryCache.cachedQueries() })

        searcher.search(ConstantScoreQuery(red), 1)
        assertEquals(listOf(green, red), runBlocking { queryCache.cachedQueries() })

        searcher.search(ConstantScoreQuery(blue), 1)
        assertEquals(listOf(red, blue), runBlocking { queryCache.cachedQueries() })

        searcher.search(ConstantScoreQuery(blue), 1)
        assertEquals(listOf(red, blue), runBlocking { queryCache.cachedQueries() })

        searcher.search(ConstantScoreQuery(green), 1)
        assertEquals(listOf(blue, green), runBlocking { queryCache.cachedQueries() })

        searcher.queryCachingPolicy = NEVER_CACHE
        searcher.search(ConstantScoreQuery(red), 1)
        assertEquals(listOf(blue, green), runBlocking { queryCache.cachedQueries() })

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testClearFilter() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val doc = Document()
        val f = StringField("color", "", Field.Store.NO)
        doc.add(f)
        val numDocs = atLeast(10)
        for (i in 0 until numDocs) {
            f.setStringValue(if (random().nextBoolean()) "red" else "blue")
            w.addDocument(doc)
        }
        val reader = w.reader
        val searcher = newSearcher(reader)

        val query1: Query = TermQuery(Term("color", "blue"))
        val query2: Query = TermQuery(Term("color", "blue"))

        val queryCache = LRUQueryCache(Int.MAX_VALUE, Long.MAX_VALUE, { true }, 1f)
        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = ALWAYS_CACHE

        searcher.search(BoostQuery(ConstantScoreQuery(query1), randomFloat()), 1)
        assertEquals(1, runBlocking { queryCache.cachedQueries() }.size)

        runBlocking { queryCache.clearQuery(query2) }

        assertTrue(runBlocking { queryCache.cachedQueries() }.isEmpty())
        runBlocking { queryCache.assertConsistent() }

        reader.close()
        w.close()
        dir.close()
    }

    // This test makes sure that by making the same assumptions as LRUQueryCache, RAMUsageTester
    // computes the same memory usage.
    @AwaitsFix(bugUrl = "https://issues.apache.org/jira/browse/LUCENE-7595")
    @Test
    fun testRamBytesUsedAgreesWithRamUsageTester() {
        val queryCache = LRUQueryCache(
            1 + random().nextInt(5),
            1 + random().nextInt(10000).toLong(),
            { random().nextBoolean() },
            Float.POSITIVE_INFINITY
        )
        val acc = object : RamUsageTester.Accumulator() {
            override fun accumulateObject(
                o: Any,
                shallowSize: Long,
                fieldValues: Collection<Any>,
                queue: MutableCollection<Any>
            ): Long {
                if (o is DocIdSet) {
                    return o.ramBytesUsed()
                }
                if (o is Query) {
                    return RamUsageEstimator.QUERY_DEFAULT_RAM_BYTES_USED.toLong()
                }
                if (o is IndexReader || o::class.simpleName == "SegmentCoreReaders") {
                    return 0
                }
                if (o is Map<*, *>) {
                    queue.addAll(o.keys.filterNotNull())
                    queue.addAll(o.values.filterNotNull())
                    val sizePerEntry =
                        if (o is LinkedHashMap<*, *>) {
                            RamUsageEstimator.LINKED_HASHTABLE_RAM_BYTES_PER_ENTRY
                        } else {
                            RamUsageEstimator.HASHTABLE_RAM_BYTES_PER_ENTRY
                        }
                    return sizePerEntry * o.size
                }
                return super.accumulateObject(o, shallowSize, fieldValues, queue)
            }

            override fun accumulateArray(
                array: Any,
                shallowSize: Long,
                values: MutableList<Any>,
                queue: MutableCollection<Any>
            ): Long {
                return super.accumulateArray(array, shallowSize, values, queue)
            }
        }

        val dir = newDirectory()
        val iwc = newIndexWriterConfig().setMergeScheduler(SerialMergeScheduler())
        val w = RandomIndexWriter(random(), dir, iwc)
        val colors = listOf("blue", "red", "green", "yellow")

        val doc = Document()
        val f = StringField("color", "", Field.Store.NO)
        doc.add(f)
        val iters = atLeast(5)
        for (iter in 0 until iters) {
            val numDocs = atLeast(10)
            for (i in 0 until numDocs) {
                f.setStringValue(RandomPicks.randomFrom(random(), colors.toMutableList()))
                w.addDocument(doc)
            }
            DirectoryReader.open(w.w).use { reader ->
                val searcher = newSearcher(reader)
                searcher.queryCache = queryCache
                searcher.queryCachingPolicy = MAYBE_CACHE_POLICY
                for (i in 0 until 3) {
                    val query: Query =
                        TermQuery(Term("color", RandomPicks.randomFrom(random(), colors.toMutableList())))
                    searcher.search(ConstantScoreQuery(query), 1)
                }
            }
            runBlocking { queryCache.assertConsistent() }
            assertEquals(RamUsageTester.ramUsed(queryCache, acc), queryCache.ramBytesUsed())
        }

        w.close()
        dir.close()
    }

    /** A query that doesn't match anything */
    private open class DummyQuery : Query() {
        private val id: Int = COUNTER.fetchAndIncrement()

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : ConstantScoreWeight(this, boost) {
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? = null
                override fun isCacheable(ctx: LeafReaderContext): Boolean = true
            }
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && id == (other as DummyQuery).id
        }

        override fun hashCode(): Int = id

        override fun toString(field: String?): String = "DummyQuery"

        companion object {
            private val COUNTER = AtomicInt(0)
        }
    }

    /** DummyQuery with Accountable, pretending to be a memory-eating query */
    private class AccountableDummyQuery : DummyQuery(), Accountable {
        override fun ramBytesUsed(): Long {
            return 10 * RamUsageEstimator.QUERY_DEFAULT_RAM_BYTES_USED.toLong()
        }
    }

    // Test what happens when the cache contains only filters and doc id sets
    // that require very little memory. In that case most of the memory is taken
    // by the cache itself, not cache entries, and we want to make sure that
    // memory usage is not grossly underestimated.
    @AwaitsFix(bugUrl = "https://issues.apache.org/jira/browse/LUCENE-7595")
    @Test
    fun testRamBytesUsedConstantEntryOverhead() {
        val queryCache = LRUQueryCache(1000000, 10000000, { true }, Float.POSITIVE_INFINITY)

        val acc = object : RamUsageTester.Accumulator() {
            override fun accumulateObject(
                o: Any,
                shallowSize: Long,
                fieldValues: Collection<Any>,
                queue: MutableCollection<Any>
            ): Long {
                if (o is DocIdSet) {
                    return o.ramBytesUsed()
                }
                if (o is Query) {
                    return RamUsageEstimator.QUERY_DEFAULT_RAM_BYTES_USED.toLong()
                }
                if (o::class.simpleName == "SegmentCoreReaders") {
                    return 0
                }
                return super.accumulateObject(o, shallowSize, fieldValues, queue)
            }
        }

        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val numDocs = atLeast(100)
        for (i in 0 until numDocs) {
            w.addDocument(doc)
        }
        val reader = w.reader
        val searcher = IndexSearcher(reader)
        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = ALWAYS_CACHE

        val numQueries = atLeast(1000)
        for (i in 0 until numQueries) {
            val query: Query = DummyQuery()
            searcher.search(ConstantScoreQuery(query), 1)
        }
        assertTrue(queryCache.cacheCount > 0)

        val actualRamBytesUsed = RamUsageTester.ramUsed(queryCache, acc)
        val expectedRamBytesUsed = queryCache.ramBytesUsed()
        assertEquals(actualRamBytesUsed.toDouble(), expectedRamBytesUsed.toDouble(), 30.0 * actualRamBytesUsed / 100.0)

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testCachingAccountableQuery() {
        val queryCache = LRUQueryCache(1000000, 10000000, { true }, Float.POSITIVE_INFINITY)
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val numDocs = atLeast(100)
        for (i in 0 until numDocs) {
            w.addDocument(doc)
        }
        val reader = w.reader
        val searcher = IndexSearcher(reader)
        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = ALWAYS_CACHE

        val numQueries = random().nextInt(100) + 100
        for (i in 0 until numQueries) {
            val query: Query = AccountableDummyQuery()
            searcher.count(query)
        }
        val queryRamBytesUsed = numQueries * (10 * RamUsageEstimator.QUERY_DEFAULT_RAM_BYTES_USED.toLong())
        assertTrue(queryCache.ramBytesUsed() > queryRamBytesUsed)

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testConsistencyWithAccountableQueries() {
        val queryCache = LRUQueryCache(1, 10000000, { true }, Float.POSITIVE_INFINITY)
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        writer.addDocument(Document())
        val reader = writer.reader
        val searcher = IndexSearcher(reader)
        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = ALWAYS_CACHE

        runBlocking { queryCache.assertConsistent() }

        val accountableQuery = AccountableDummyQuery()
        searcher.count(accountableQuery)
        val expectedRamBytesUsed =
            RamUsageEstimator.HASHTABLE_RAM_BYTES_PER_ENTRY +
                RamUsageEstimator.LINKED_HASHTABLE_RAM_BYTES_PER_ENTRY +
                accountableQuery.ramBytesUsed() +
                queryCache.childResources.iterator().next().ramBytesUsed()
        assertEquals(expectedRamBytesUsed, queryCache.ramBytesUsed())
        runBlocking { queryCache.assertConsistent() }

        runBlocking { queryCache.clearQuery(accountableQuery) }
        assertEquals(RamUsageEstimator.HASHTABLE_RAM_BYTES_PER_ENTRY, queryCache.ramBytesUsed())
        runBlocking { queryCache.assertConsistent() }

        runBlocking {
            queryCache.clearCoreCacheKey(reader.context.leaves()[0].reader().coreCacheHelper!!.key)
        }
        assertEquals(0, queryCache.ramBytesUsed())
        runBlocking { queryCache.assertConsistent() }

        reader.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testOnUse() {
        val queryCache = LRUQueryCache(
            1 + random().nextInt(5),
            1 + random().nextInt(1000).toLong(),
            { random().nextBoolean() },
            Float.POSITIVE_INFINITY
        )

        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val doc = Document()
        val f = StringField("color", "", Field.Store.NO)
        doc.add(f)
        val numDocs = atLeast(10)
        for (i in 0 until numDocs) {
            f.setStringValue(RandomPicks.randomFrom(random(), mutableListOf("red", "blue", "green", "yellow")))
            w.addDocument(doc)
            if (random().nextBoolean()) {
                w.reader.close()
            }
        }
        val reader = w.reader
        val searcher = IndexSearcher(reader)

        val actualCounts = hashMapOf<Query, Int>()
        val expectedCounts = hashMapOf<Query, Int>()

        val countingPolicy = object : QueryCachingPolicy {
            override fun shouldCache(query: Query): Boolean = random().nextBoolean()
            override fun onUse(query: Query) {
                expectedCounts[query] = 1 + (expectedCounts[query] ?: 0)
            }
        }

        val queries = Array(10 + random().nextInt(10)) {
            BoostQuery(
                TermQuery(
                    Term(
                        "color",
                        RandomPicks.randomFrom(random(), mutableListOf("red", "blue", "green", "yellow"))
                    )
                ),
                randomFloat()
            )
        }

        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = countingPolicy
        for (i in 0 until 20) {
            val idx = random().nextInt(queries.size)
            searcher.search(ConstantScoreQuery(queries[idx]), 1)
            var cacheKey: Query = queries[idx]
            while (cacheKey is BoostQuery) {
                cacheKey = cacheKey.query
            }
            actualCounts[cacheKey] = 1 + (actualCounts[cacheKey] ?: 0)
        }

        assertEquals(actualCounts, expectedCounts)

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testStats() {
        val queryCache = LRUQueryCache(1, 10000000, { true }, 1f)
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val colors = listOf("blue", "red", "green", "yellow")
        val doc = Document()
        val f = StringField("color", "", Field.Store.NO)
        doc.add(f)
        for (i in 0 until 10) {
            f.setStringValue(RandomPicks.randomFrom(random(), colors.toMutableList()))
            w.addDocument(doc)
            if (random().nextBoolean()) {
                w.reader.close()
            }
        }

        val reader = w.reader
        val segmentCount = reader.leaves().size
        val searcher = IndexSearcher(reader)
        val query: Query = TermQuery(Term("color", "red"))
        val query2: Query = TermQuery(Term("color", "blue"))

        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = NEVER_CACHE
        repeat(10) { searcher.search(ConstantScoreQuery(query), 1) }
        assertEquals((10 * segmentCount).toLong(), queryCache.totalCount)
        assertEquals(0, queryCache.getHitCount())
        assertEquals((10 * segmentCount).toLong(), queryCache.getMissCount())
        assertEquals(0, queryCache.cacheCount)
        assertEquals(0, queryCache.evictionCount)
        assertEquals(0, queryCache.cacheSize)

        searcher.queryCachingPolicy = ALWAYS_CACHE
        repeat(10) { searcher.search(ConstantScoreQuery(query), 1) }
        assertEquals((20 * segmentCount).toLong(), queryCache.totalCount)
        assertEquals((9 * segmentCount).toLong(), queryCache.getHitCount())
        assertEquals((11 * segmentCount).toLong(), queryCache.getMissCount())
        assertEquals((1 * segmentCount).toLong(), queryCache.cacheCount)
        assertEquals(0, queryCache.evictionCount)
        assertEquals((1 * segmentCount).toLong(), queryCache.cacheSize)

        searcher.queryCachingPolicy = NEVER_CACHE
        repeat(10) { searcher.search(ConstantScoreQuery(query), 1) }
        assertEquals((30 * segmentCount).toLong(), queryCache.totalCount)
        assertEquals((19 * segmentCount).toLong(), queryCache.getHitCount())
        assertEquals((11 * segmentCount).toLong(), queryCache.getMissCount())

        searcher.queryCachingPolicy = ALWAYS_CACHE
        repeat(10) { searcher.search(ConstantScoreQuery(query2), 1) }
        assertEquals((40 * segmentCount).toLong(), queryCache.totalCount)
        assertEquals((28 * segmentCount).toLong(), queryCache.getHitCount())
        assertEquals((12 * segmentCount).toLong(), queryCache.getMissCount())
        assertEquals((2 * segmentCount).toLong(), queryCache.cacheCount)
        assertEquals((1 * segmentCount).toLong(), queryCache.evictionCount)
        assertEquals((1 * segmentCount).toLong(), queryCache.cacheSize)

        reader.close()
        w.close()
        assertEquals((2 * segmentCount).toLong(), queryCache.evictionCount)
        assertEquals(0, queryCache.cacheSize)
        dir.close()
    }

    @Test
    fun testFineGrainedStats() {
        val dir1 = newDirectory()
        val w1 = RandomIndexWriter(random(), dir1)
        val dir2 = newDirectory()
        val w2 = RandomIndexWriter(random(), dir2)

        val colors = listOf("blue", "red", "green", "yellow")

        val doc = Document()
        val f = StringField("color", "", Field.Store.NO)
        doc.add(f)
        for (w in listOf(w1, w2)) {
            for (i in 0 until 10) {
                f.setStringValue(RandomPicks.randomFrom(random(), colors.toMutableList()))
                w.addDocument(doc)
                if (random().nextBoolean()) {
                    w.reader.close()
                }
            }
        }

        val reader1 = w1.reader
        val segmentCount1 = reader1.leaves().size
        val searcher1 = IndexSearcher(reader1)

        val reader2 = w2.reader
        val segmentCount2 = reader2.leaves().size
        val searcher2 = IndexSearcher(reader2)

        val indexId = hashMapOf<IndexReader.CacheKey, Int>()
        for (ctx in reader1.leaves()) {
            indexId[ctx.reader().coreCacheHelper!!.key] = 1
        }
        for (ctx in reader2.leaves()) {
            indexId[ctx.reader().coreCacheHelper!!.key] = 2
        }

        val hitCount1 = AtomicLong(0)
        val hitCount2 = AtomicLong(0)
        val missCount1 = AtomicLong(0)
        val missCount2 = AtomicLong(0)

        val ramBytesUsage = AtomicLong(0)
        val cacheSize = AtomicLong(0)

        val queryCache = object : LRUQueryCache(2, 10000000, { true }, 1f) {
            override fun onHit(readerCoreKey: Any, query: Query) {
                super.onHit(readerCoreKey, query)
                when (indexId[readerCoreKey]) {
                    1 -> hitCount1.updateAndGet { it + 1 }
                    2 -> hitCount2.updateAndGet { it + 1 }
                    else -> throw AssertionError()
                }
            }

            override fun onMiss(readerCoreKey: Any, query: Query) {
                super.onMiss(readerCoreKey, query)
                when (indexId[readerCoreKey]) {
                    1 -> missCount1.updateAndGet { it + 1 }
                    2 -> missCount2.updateAndGet { it + 1 }
                    else -> throw AssertionError()
                }
            }

            override fun onQueryCache(query: Query, ramBytesUsed: Long) {
                super.onQueryCache(query, ramBytesUsed)
                ramBytesUsage.updateAndGet { it + ramBytesUsed }
            }

            override fun onQueryEviction(query: Query, ramBytesUsed: Long) {
                super.onQueryEviction(query, ramBytesUsed)
                ramBytesUsage.updateAndGet { it - ramBytesUsed }
            }

            override fun onDocIdSetCache(readerCoreKey: Any, ramBytesUsed: Long) {
                super.onDocIdSetCache(readerCoreKey, ramBytesUsed)
                ramBytesUsage.updateAndGet { it + ramBytesUsed }
                cacheSize.updateAndGet { it + 1 }
            }

            override fun onDocIdSetEviction(readerCoreKey: Any, numEntries: Int, sumRamBytesUsed: Long) {
                super.onDocIdSetEviction(readerCoreKey, numEntries, sumRamBytesUsed)
                ramBytesUsage.updateAndGet { it - sumRamBytesUsed }
                cacheSize.updateAndGet { it - numEntries }
            }

            override fun onClear() {
                super.onClear()
                ramBytesUsage.store(0)
                cacheSize.store(0)
            }
        }

        val query: Query = TermQuery(Term("color", "red"))
        val query2: Query = TermQuery(Term("color", "blue"))
        val query3: Query = TermQuery(Term("color", "green"))

        for (searcher in listOf(searcher1, searcher2)) {
            searcher.queryCache = queryCache
            searcher.queryCachingPolicy = ALWAYS_CACHE
        }

        for (i in 0 until 10) {
            searcher1.search(ConstantScoreQuery(query), 1)
        }
        assertEquals((9 * segmentCount1).toLong(), hitCount1.load())
        assertEquals(0, hitCount2.load())
        assertEquals(segmentCount1.toLong(), missCount1.load())
        assertEquals(0, missCount2.load())

        for (i in 0 until 20) {
            searcher2.search(ConstantScoreQuery(query2), 1)
        }
        assertEquals((9 * segmentCount1).toLong(), hitCount1.load())
        assertEquals((19 * segmentCount2).toLong(), hitCount2.load())
        assertEquals(segmentCount1.toLong(), missCount1.load())
        assertEquals(segmentCount2.toLong(), missCount2.load())

        for (i in 0 until 30) {
            searcher1.search(ConstantScoreQuery(query3), 1)
        }
        assertEquals(segmentCount1.toLong(), queryCache.evictionCount)
        assertEquals((38 * segmentCount1).toLong(), hitCount1.load())
        assertEquals((19 * segmentCount2).toLong(), hitCount2.load())
        assertEquals((2 * segmentCount1).toLong(), missCount1.load())
        assertEquals(segmentCount2.toLong(), missCount2.load())

        assertEquals(
            queryCache.ramBytesUsed(),
            ((segmentCount1 + segmentCount2) * RamUsageEstimator.HASHTABLE_RAM_BYTES_PER_ENTRY) + ramBytesUsage.load()
        )
        assertEquals(queryCache.cacheSize, cacheSize.load())

        reader1.close()
        reader2.close()
        w1.close()
        w2.close()

        assertEquals(queryCache.ramBytesUsed(), ramBytesUsage.load())
        assertEquals(0, cacheSize.load())

        runBlocking { queryCache.clear() }
        assertEquals(0, ramBytesUsage.load())
        assertEquals(0, cacheSize.load())

        dir1.close()
        dir2.close()
    }

    @Test
    fun testUseRewrittenQueryAsCacheKey() {
        val expectedCacheKey: Query = TermQuery(Term("foo", "bar"))
        val query = BooleanQuery.Builder()
        query.add(BoostQuery(expectedCacheKey, 42f), Occur.MUST)

        val queryCache = LRUQueryCache(1000000, 10000000, { random().nextBoolean() }, Float.POSITIVE_INFINITY)
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(StringField("foo", "bar", Field.Store.YES))
        w.addDocument(doc)
        w.commit()
        val reader = w.reader
        val searcher = newSearcher(reader)
        w.close()

        val policy = object : QueryCachingPolicy {
            override fun shouldCache(query: Query): Boolean {
                assertEquals(expectedCacheKey, query)
                return true
            }

            override fun onUse(query: Query) {
                assertEquals(expectedCacheKey, query)
            }
        }

        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = policy
        searcher.search(query.build(), DummyTotalHitCountCollector.createManager())

        reader.close()
        dir.close()
    }

    @Test
    fun testBooleanQueryCachesSubClauses() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(StringField("foo", "bar", Field.Store.YES))
        doc.add(StringField("foo", "quux", Field.Store.YES))
        w.addDocument(doc)
        w.commit()
        val reader = w.reader
        val searcher = newSearcher(reader)
        w.close()

        val queryCache = LRUQueryCache(1000000, 10000000, { true }, Float.POSITIVE_INFINITY)
        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = ALWAYS_CACHE

        val bq = BooleanQuery.Builder()
        val must = TermQuery(Term("foo", "bar"))
        val filter = TermQuery(Term("foo", "quux"))
        val mustNot = TermQuery(Term("foo", "foo"))
        bq.add(must, Occur.MUST)
        bq.add(filter, Occur.FILTER)
        bq.add(mustNot, Occur.MUST_NOT)

        val bq2 = BooleanQuery.Builder()
        bq2.add(must, Occur.FILTER)
        bq2.add(filter, Occur.FILTER)
        bq2.add(mustNot, Occur.MUST_NOT)

        assertEquals(emptySet(), runBlocking { queryCache.cachedQueries() }.toSet())
        searcher.search(bq.build(), 1)
        assertEquals(setOf(filter, mustNot), runBlocking { queryCache.cachedQueries() }.toSet())

        runBlocking { queryCache.clear() }
        assertEquals(emptySet(), runBlocking { queryCache.cachedQueries() }.toSet())
        searcher.search(ConstantScoreQuery(bq.build()), 1)
        assertEquals(setOf(bq2.build(), must, filter, mustNot), runBlocking { queryCache.cachedQueries() }.toSet())

        reader.close()
        dir.close()
    }

    private fun randomTerm(): Term {
        val term = RandomPicks.randomFrom(random(), mutableListOf("foo", "bar", "baz"))
        return Term("foo", term)
    }

    private fun buildRandomQuery(level: Int): Query {
        if (level == 10) {
            return MatchAllDocsQuery()
        }
        return when (random().nextInt(6)) {
            0 -> TermQuery(randomTerm())
            1 -> {
                val bq = BooleanQuery.Builder()
                val numClauses = TestUtil.nextInt(random(), 1, 3)
                var numShould = 0
                for (i in 0 until numClauses) {
                    val occur = RandomPicks.randomFrom(random(), Occur.entries.toTypedArray())
                    bq.add(buildRandomQuery(level + 1), occur)
                    if (occur == Occur.SHOULD) numShould++
                }
                bq.setMinimumNumberShouldMatch(TestUtil.nextInt(random(), 0, numShould))
                bq.build()
            }
            2 -> {
                val t1 = randomTerm()
                val t2 = randomTerm()
                PhraseQuery(random().nextInt(2), t1.field(), t1.bytes(), t2.bytes())
            }
            3 -> MatchAllDocsQuery()
            4 -> ConstantScoreQuery(buildRandomQuery(level + 1))
            5 -> {
                val disjuncts = mutableListOf<Query>()
                val numQueries = TestUtil.nextInt(random(), 1, 3)
                for (i in 0 until numQueries) {
                    disjuncts.add(buildRandomQuery(level + 1))
                }
                DisjunctionMaxQuery(disjuncts, randomFloat())
            }
            else -> error("unreachable")
        }
    }

    @Test
    fun testRandom() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val f = TextField("foo", "foo", Field.Store.NO)
        doc.add(f)
        w.addDocument(doc)
        var reader: IndexReader = w.reader

        val maxSize: Int
        val maxRamBytesUsed: Long
        val iters: Int
        if (TEST_NIGHTLY) {
            maxSize = TestUtil.nextInt(random(), 1, 10000)
            maxRamBytesUsed = TestUtil.nextLong(random(), 1, 5_000_000)
            iters = atLeast(20000)
        } else {
            maxSize = TestUtil.nextInt(random(), 1, 1000)
            maxRamBytesUsed = TestUtil.nextLong(random(), 1, 500_000)
            iters = atLeast(2000)
        }

        val queryCache = LRUQueryCache(maxSize, maxRamBytesUsed, { random().nextBoolean() }, Float.POSITIVE_INFINITY)
        var uncachedSearcher: IndexSearcher? = null
        var cachedSearcher: IndexSearcher? = null

        for (i in 0 until iters) {
            if (i == 0 || random().nextInt(100) == 1) {
                reader.close()
                f.setStringValue(RandomPicks.randomFrom(random(), mutableListOf("foo", "bar", "bar baz")))
                w.addDocument(doc)
                if (random().nextBoolean()) {
                    w.deleteDocuments(buildRandomQuery(0))
                }
                reader = w.reader
                uncachedSearcher = newSearcher(reader)
                uncachedSearcher.queryCache = null
                cachedSearcher = newSearcher(reader)
                cachedSearcher.queryCache = queryCache
                cachedSearcher.queryCachingPolicy = ALWAYS_CACHE
            }
            val q = buildRandomQuery(0)
            assertEquals(uncachedSearcher!!.count(q), cachedSearcher!!.count(q))
            val size = 1 + random().nextInt(1000)
            CheckHits.checkEqual(q, uncachedSearcher.search(q, size).scoreDocs, cachedSearcher.search(q, size).scoreDocs)
            if (rarely()) {
                runBlocking { queryCache.assertConsistent() }
            }
        }
        runBlocking { queryCache.assertConsistent() }
        w.close()
        reader.close()
        dir.close()
        runBlocking { queryCache.assertConsistent() }
    }

    private class BadQuery : Query() {
        var i = intArrayOf(42)

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : ConstantScoreWeight(this, boost) {
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? = null
                override fun isCacheable(ctx: LeafReaderContext): Boolean = true
            }
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun toString(field: String?): String = "BadQuery"

        override fun hashCode(): Int = classHash() xor i[0]

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && i[0] == (other as BadQuery).i[0]
        }
    }

    @AwaitsFix(bugUrl = "https://issues.apache.org/jira/browse/LUCENE-7604")
    @Test
    fun testDetectMutatedQueries() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.addDocument(Document())
        val reader = w.reader

        val queryCache = LRUQueryCache(1, 10000, { true }, Float.POSITIVE_INFINITY)
        val searcher = newSearcher(reader)
        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = ALWAYS_CACHE

        val query = BadQuery()
        searcher.search(query, DummyTotalHitCountCollector.createManager())
        query.i[0] += 1

        try {
            searcher.search(MatchAllDocsQuery(), DummyTotalHitCountCollector.createManager())
            fail()
        } catch (_: ConcurrentModificationException) {
        } catch (e: RuntimeException) {
            val cause = e.cause
            assertTrue(cause is ExecutionException)
            assertTrue(cause.cause is ConcurrentModificationException)
        }

        w.close()
        reader.close()
        dir.close()
    }

    @Test
    fun testRefuseToCacheTooLargeEntries() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        repeat(100) {
            w.addDocument(Document())
        }
        val reader = w.reader

        val queryCache = LRUQueryCache(1, 1, { random().nextBoolean() }, Float.POSITIVE_INFINITY)
        val searcher = newSearcher(reader)
        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = ALWAYS_CACHE

        searcher.count(MatchAllDocsQuery())
        assertEquals(0, queryCache.cacheCount)
        assertEquals(0, queryCache.evictionCount)

        reader.close()
        w.close()
        dir.close()
    }

    /**
     * Tests CachingWrapperWeight.scorer() propagation of [QueryCachingPolicy.onUse] when
     * the first segment is skipped.
     *
     * #f:foo #f:bar causes all frequencies to increment #f:bar #f:foo does not increment the
     * frequency for f:foo
     */
    @Test
    fun testOnUseWithRandomFirstSegmentSkipping() {
        val directory = newDirectory()
        RandomIndexWriter(
            random(),
            directory,
            newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
        ).use { indexWriter ->
            var doc = Document()
            doc.add(StringField("f", "bar", Field.Store.NO))
            indexWriter.addDocument(doc)
            if (random().nextBoolean()) {
                indexWriter.reader.close()
            }
            doc = Document()
            doc.add(StringField("f", "foo", Field.Store.NO))
            doc.add(StringField("f", "bar", Field.Store.NO))
            indexWriter.addDocument(doc)
            indexWriter.commit()
        }
        DirectoryReader.open(directory).use { indexReader ->
            val policy = FrequencyCountingPolicy()
            val indexSearcher = IndexSearcher(indexReader)
            indexSearcher.queryCache =
                LRUQueryCache(100, 10240, { random().nextBoolean() }, Float.POSITIVE_INFINITY)
            indexSearcher.queryCachingPolicy = policy
            val foo: Query = TermQuery(Term("f", "foo"))
            val bar: Query = TermQuery(Term("f", "bar"))
            val query = BooleanQuery.Builder()
            if (random().nextBoolean()) {
                query.add(foo, Occur.FILTER)
                query.add(bar, Occur.FILTER)
            } else {
                query.add(bar, Occur.FILTER)
                query.add(foo, Occur.FILTER)
            }
            val builtQuery = query.build()
            indexSearcher.search(builtQuery, DummyTotalHitCountCollector.createManager())
            assertEquals(1, policy.frequency(builtQuery))
            assertEquals(1, policy.frequency(foo))
            assertEquals(1, policy.frequency(bar))
        }
        directory.close()
    }

    private class FrequencyCountingPolicy : QueryCachingPolicy {
        private val counts = hashMapOf<Query, AtomicInt>()

        fun frequency(query: Query): Int = counts[query]?.load() ?: 0

        override fun onUse(query: Query) {
            val count = counts.getOrPut(query) { AtomicInt(0) }
            count.fetchAndIncrement()
        }

        override fun shouldCache(query: Query): Boolean = true
    }

    private class WeightWrapper(
        `in`: Weight,
        private val scorerCalled: AtomicBoolean,
        private val bulkScorerCalled: AtomicBoolean
    ) : FilterWeight(`in`) {
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            val scorerSupplier = `in`.scorerSupplier(context) ?: return null
            val scorer = requireNotNull(scorerSupplier.get(Long.MAX_VALUE))
            return object : ScorerSupplier() {
                override fun get(leadCost: Long): Scorer {
                    scorerCalled.store(true)
                    return scorer
                }

                override fun bulkScorer(): BulkScorer? {
                    bulkScorerCalled.store(true)
                    return `in`.bulkScorer(context)
                }

                override fun cost(): Long = scorer.iterator().cost()
            }
        }
    }

    @Test
    fun testPropagateBulkScorer() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.addDocument(Document())
        val reader = w.reader
        w.close()
        val searcher = newSearcher(reader)
        val leaf = searcher.indexReader.leaves()[0]
        val scorerCalled = AtomicBoolean(false)
        val bulkScorerCalled = AtomicBoolean(false)
        val cache = LRUQueryCache(1, Long.MAX_VALUE, { true }, Float.POSITIVE_INFINITY)

        var weight = searcher.createWeight(MatchAllDocsQuery(), ScoreMode.COMPLETE_NO_SCORES, 1f)
        weight = WeightWrapper(weight, scorerCalled, bulkScorerCalled)
        weight = cache.doCache(weight, NEVER_CACHE)
        weight.bulkScorer(leaf)
        assertTrue(bulkScorerCalled.load())
        assertFalse(scorerCalled.load())
        assertEquals(0, cache.cacheCount)

        searcher.indexReader.close()
        dir.close()
    }

    @Test
    fun testEvictEmptySegmentCache() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.addDocument(Document())
        val reader = w.reader
        val searcher = newSearcher(reader)
        val queryCache = LRUQueryCache(2, 100000, { true }, Float.POSITIVE_INFINITY)

        searcher.queryCache = queryCache
        searcher.queryCachingPolicy = ALWAYS_CACHE

        val query: Query = DummyQuery()
        searcher.count(query)
        assertEquals(listOf(query), runBlocking { queryCache.cachedQueries() })
        runBlocking { queryCache.clearQuery(query) }

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMinSegmentSizePredicate() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
        val w = IndexWriter(dir, iwc)
        val newSegment: (Int) -> Unit = { numDocs ->
            repeat(numDocs) {
                w.addDocument(Document())
            }
            w.flush()
        }
        newSegment(1)
        newSegment(4)
        newSegment(10)
        newSegment(35)
        val numLargeSegments = TestUtil.nextInt(random(), 2, 40)
        repeat(numLargeSegments) {
            newSegment(TestUtil.nextInt(random(), 50, 55))
        }
        val reader = DirectoryReader.open(w)
        for (i in 0 until 3) {
            val predicate = minSegmentSizePredicate(TestUtil.nextInt(random(), 1, Int.MAX_VALUE))
            assertFalse(predicate(reader.context.leaves()[i]))
        }
        for (i in 3 until reader.context.leaves().size) {
            val leaf = reader.context.leaves()[i]
            val small = minSegmentSizePredicate(TestUtil.nextInt(random(), 60, Int.MAX_VALUE))
            assertFalse(small(leaf))
            val big = minSegmentSizePredicate(TestUtil.nextInt(random(), 10, 30))
            assertTrue(big(leaf))
        }
        reader.close()
        w.close()
        dir.close()
    }

    // a reader whose sole purpose is to not be cacheable
    private class DummyDirectoryReader(`in`: DirectoryReader) : FilterDirectoryReader(
        `in`,
        object : SubReaderWrapper() {
            override fun wrap(reader: LeafReader): LeafReader {
                return object : FilterLeafReader(reader) {
                    override val coreCacheHelper: IndexReader.CacheHelper? = null
                    override val readerCacheHelper: IndexReader.CacheHelper? = null
                }
            }
        }
    ) {
        override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
            return DummyDirectoryReader(`in`)
        }

        override val readerCacheHelper: IndexReader.CacheHelper? = null
    }

    @Test
    fun testReaderNotSuitedForCaching() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
        val w = RandomIndexWriter(random(), dir, iwc)
        w.addDocument(Document())
        val reader = DummyDirectoryReader(w.reader)
        val searcher = newSearcher(reader)
        searcher.queryCachingPolicy = ALWAYS_CACHE

        assertNull(reader.leaves()[0].reader().coreCacheHelper)
        val cache = LRUQueryCache(2, 10000, { true }, Float.POSITIVE_INFINITY)
        searcher.queryCache = cache
        assertEquals(0, searcher.count(DummyQuery()))
        assertEquals(0, cache.cacheCount)
        reader.close()
        w.close()
        dir.close()
    }

    // A query that returns null from Weight.getCacheHelper
    private class NoCacheQuery : Query() {
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : Weight(this) {
                override fun explain(context: LeafReaderContext, doc: Int): Explanation =
                    Explanation.noMatch("No match")
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? = null
                override fun isCacheable(ctx: LeafReaderContext): Boolean = false
            }
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun toString(field: String?): String = "NoCacheQuery"

        override fun equals(obj: Any?): Boolean = sameClassAs(obj)

        override fun hashCode(): Int = 0
    }

    @Test
    fun testQueryNotSuitedForCaching() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
        val w = RandomIndexWriter(random(), dir, iwc)
        w.addDocument(Document())
        val reader = w.reader
        val searcher = newSearcher(reader)
        searcher.queryCachingPolicy = ALWAYS_CACHE

        val cache = LRUQueryCache(2, 10000, { true }, Float.POSITIVE_INFINITY)
        searcher.queryCache = cache

        assertEquals(0, searcher.count(NoCacheQuery()))
        assertEquals(0, cache.cacheCount)

        val bq =
            BooleanQuery.Builder()
                .add(NoCacheQuery(), Occur.MUST)
                .add(TermQuery(Term("field", "term")), Occur.MUST)
                .build()
        assertEquals(0, searcher.count(bq))
        assertEquals(0, cache.cacheCount)

        reader.close()
        w.close()
        dir.close()
    }

    private class DummyQuery2(private val scorerCreated: AtomicBoolean) : Query() {
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : ConstantScoreWeight(this, boost) {
                override fun isCacheable(ctx: LeafReaderContext): Boolean = true

                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
                    val scorer = ConstantScoreScorer(boost, scoreMode, all(1))
                    return object : ScorerSupplier() {
                        override fun get(leadCost: Long): Scorer {
                            scorerCreated.store(true)
                            return scorer
                        }

                        override fun cost(): Long = 1
                    }
                }
            }
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun equals(other: Any?): Boolean = sameClassAs(other)

        override fun hashCode(): Int = 0

        override fun toString(field: String?): String = "DummyQuery2"
    }

    @Test
    fun testPropagatesScorerSupplier() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
        val w = RandomIndexWriter(random(), dir, iwc)
        w.addDocument(Document())
        val reader = w.reader
        val searcher = newSearcher(reader)
        searcher.queryCachingPolicy = NEVER_CACHE

        val cache = LRUQueryCache(1, 1000)
        searcher.queryCache = cache

        val scorerCreated = AtomicBoolean(false)
        val query: Query = DummyQuery2(scorerCreated)
        val weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f)
        val supplier = weight.scorerSupplier(searcher.indexReader.leaves()[0])
        assertFalse(scorerCreated.load())
        supplier!!.get(random().nextLong() and Long.MAX_VALUE)
        assertTrue(scorerCreated.load())

        reader.close()
        w.close()
        dir.close()
    }

    class DVCacheQuery(val field: String) : Query() {
        val scorerCreatedCount = AtomicInt(0)

        override fun toString(field: String?): String = "DVCacheQuery"

        override fun equals(obj: Any?): Boolean = sameClassAs(obj)

        override fun hashCode(): Int = 0

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : ConstantScoreWeight(this, 1f) {
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
                    return object : ScorerSupplier() {
                        override fun get(leadCost: Long): Scorer {
                            scorerCreatedCount.fetchAndIncrement()
                            return ConstantScoreScorer(1f, scoreMode, all(context.reader().maxDoc()))
                        }

                        override fun cost(): Long = context.reader().maxDoc().toLong()
                    }
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return org.gnit.lucenekmp.index.DocValues.isCacheable(ctx, field)
                }
            }
        }

        override fun visit(visitor: QueryVisitor) {}
    }

    @Test
    fun testDocValuesUpdatesDontBreakCache() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
        val w = IndexWriter(dir, iwc)
        w.addDocument(Document())
        w.commit()
        var reader = DirectoryReader.open(w)

        // IMPORTANT:
        // Don't use newSearcher(), because that will sometimes use an ExecutorService, and
        // we need to be single threaded to ensure that LRUQueryCache doesn't skip the cache
        // due to thread contention
        var searcher: IndexSearcher = org.gnit.lucenekmp.tests.search.AssertingIndexSearcher(random(), reader)
        searcher.queryCachingPolicy = ALWAYS_CACHE

        val cache = LRUQueryCache(1, 10000, { true }, Float.POSITIVE_INFINITY)
        searcher.queryCache = cache

        val query = DVCacheQuery("field")
        assertEquals(1, searcher.count(query))
        assertEquals(1, query.scorerCreatedCount.load())
        assertEquals(1, searcher.count(query))
        assertEquals(1, query.scorerCreatedCount.load())

        val doc = Document()
        doc.add(NumericDocValuesField("field", 1))
        doc.add(newTextField("text", "text", Field.Store.NO))
        w.addDocument(doc)
        reader.close()
        reader = DirectoryReader.open(w)
        searcher = org.gnit.lucenekmp.tests.search.AssertingIndexSearcher(random(), reader)
        searcher.queryCachingPolicy = ALWAYS_CACHE
        searcher.queryCache = cache

        assertEquals(2, searcher.count(query))
        assertEquals(2, query.scorerCreatedCount.load())

        reader.close()
        reader = DirectoryReader.open(w)
        searcher = org.gnit.lucenekmp.tests.search.AssertingIndexSearcher(random(), reader)
        searcher.queryCachingPolicy = ALWAYS_CACHE
        searcher.queryCache = cache

        assertEquals(2, searcher.count(query))
        assertEquals(2, query.scorerCreatedCount.load())

        w.updateNumericDocValue(Term("text", "text"), "field", 2L)
        reader.close()
        reader = DirectoryReader.open(w)
        searcher = org.gnit.lucenekmp.tests.search.AssertingIndexSearcher(random(), reader)
        searcher.queryCachingPolicy = ALWAYS_CACHE
        searcher.queryCache = cache

        assertEquals(2, searcher.count(query))
        assertEquals(3, query.scorerCreatedCount.load())
        assertEquals(2, searcher.count(query))
        assertEquals(4, query.scorerCreatedCount.load())

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testQueryCacheSoftUpdate() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig().setSoftDeletesField("soft_delete")
        val w = IndexWriter(dir, iwc)
        val queryCache = LRUQueryCache(10, 1000L * 1000L, { true }, Float.POSITIVE_INFINITY)
        val oldDefaultCache = IndexSearcher.defaultQueryCache
        val oldDefaultPolicy = IndexSearcher.defaultQueryCachingPolicy
        IndexSearcher.setDefaultQueryCache(queryCache)
        IndexSearcher.setDefaultQueryCachingPolicy(ALWAYS_CACHE)

        try {
            val sm = SearcherManager(w, SearcherFactory())

            var doc = Document()
            doc.add(StringField("id", "1", Field.Store.YES))
            w.addDocument(doc)

            doc = Document()
            doc.add(StringField("id", "2", Field.Store.YES))
            w.addDocument(doc)

            sm.maybeRefreshBlocking()

            val searcher = sm.acquire()
            val query = BooleanQuery.Builder()
                .add(TermQuery(Term("id", "1")), Occur.FILTER)
                .build()
            searcher.search(query, 10)
            assertEquals(1, queryCache.cacheSize)
            assertEquals(0, queryCache.evictionCount)

            val tombstone = Document()
            tombstone.add(NumericDocValuesField("soft_delete", 1))
            w.softUpdateDocument(Term("id", "1"), tombstone, NumericDocValuesField("soft_delete", 1))
            w.softUpdateDocument(Term("id", "2"), tombstone, NumericDocValuesField("soft_delete", 1))
            sm.maybeRefreshBlocking()
            sm.release(searcher)
            assertEquals(0, queryCache.cacheSize)
            assertEquals(1, queryCache.evictionCount)
            sm.close()
        } finally {
            IndexSearcher.setDefaultQueryCache(oldDefaultCache)
            IndexSearcher.setDefaultQueryCachingPolicy(oldDefaultPolicy)
            w.close()
            dir.close()
        }
    }

    @Test
    fun testBulkScorerLocking() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
            .setMergePolicy(NoMergePolicy.INSTANCE)
            // the test framework sometimes sets crazy low values, prevent this since we are
            // indexing many docs
            .setMaxBufferedDocs(-1)
        val w = IndexWriter(dir, iwc)

        val numDocs = atLeast(10)
        val emptyDoc = Document()
        for (d in 0 until numDocs) {
            for (i in random().nextInt(5000) downTo 0) {
                w.addDocument(emptyDoc)
            }
            val doc = Document()
            for (value in listOf("foo", "bar", "baz")) {
                if (random().nextBoolean()) {
                    doc.add(StringField("field", value, Field.Store.NO))
                }
            }
            w.addDocument(doc)
        }
        for (i in TestUtil.nextInt(random(), 3000, 5000) downTo 0) {
            w.addDocument(emptyDoc)
        }
        if (random().nextBoolean()) {
            w.forceMerge(1)
        }

        val reader = DirectoryReader.open(w)
        val noCacheReader = DummyDirectoryReader(reader)

        val cache = LRUQueryCache(1, 100000, { true }, Float.POSITIVE_INFINITY)
        val searcher = org.gnit.lucenekmp.tests.search.AssertingIndexSearcher(random(), reader)
        searcher.queryCache = cache
        searcher.queryCachingPolicy = ALWAYS_CACHE

        val query = ConstantScoreQuery(
            BooleanQuery.Builder()
                .add(BoostQuery(TermQuery(Term("field", "foo")), 3f), Occur.SHOULD)
                .add(BoostQuery(TermQuery(Term("field", "bar")), 3f), Occur.SHOULD)
                .add(BoostQuery(TermQuery(Term("field", "baz")), 3f), Occur.SHOULD)
                .build()
        )

        searcher.search(query, 1)

        val noCacheHelperSearcher = org.gnit.lucenekmp.tests.search.AssertingIndexSearcher(random(), noCacheReader)
        noCacheHelperSearcher.queryCache = cache
        noCacheHelperSearcher.queryCachingPolicy = ALWAYS_CACHE
        noCacheHelperSearcher.search(query, 1)

        val t = Thread {
            try {
                noCacheReader.close()
                w.close()
                dir.close()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        t.start()
        t.join()
    }

    @Test
    fun testSkipCachingForRangeQuery() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc1 = Document()
        doc1.add(StringField("name", "tom", Field.Store.YES))
        doc1.add(LongPoint("age", 15))
        doc1.add(SortedNumericDocValuesField("age", 15))
        val doc2 = Document()
        doc2.add(StringField("name", "alice", Field.Store.YES))
        doc2.add(LongPoint("age", 20))
        doc2.add(SortedNumericDocValuesField("age", 20))
        w.addDocuments(listOf(doc1, doc2))
        val reader = w.reader
        val searcher = newSearcher(reader)
        searcher.queryCachingPolicy = ALWAYS_CACHE
        w.close()

        val bq = BooleanQuery.Builder()
        val subQuery1 = TermQuery(Term("name", "tom"))
        val subQuery2 = IndexOrDocValuesQuery(
            LongPoint.newRangeQuery("age", 10, 30),
            SortedNumericDocValuesField.newSlowRangeQuery("age", 10, 30)
        )
        val query = bq.add(subQuery1, Occur.FILTER).add(subQuery2, Occur.FILTER).build()
        val cacheSet = hashSetOf<Query>()

        val partCache = LRUQueryCache(1000000, 10000000, { true }, 1f)
        searcher.queryCache = partCache
        searcher.search(query, 1)
        cacheSet.add(subQuery1)
        assertEquals(cacheSet, runBlocking { partCache.cachedQueries() }.toSet())

        val allCache = LRUQueryCache(1000000, 10000000, { true }, Float.POSITIVE_INFINITY)
        searcher.queryCache = allCache
        searcher.search(query, 1)
        cacheSet.add(subQuery2)
        assertEquals(cacheSet, runBlocking { allCache.cachedQueries() }.toSet())

        reader.close()
        dir.close()
    }

    @Test
    fun testCountDelegation() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(StringField("foo", "bar", Field.Store.NO))
        val numDocs = random().nextInt(100) + 20
        repeat(numDocs) { w.addDocument(doc) }
        val reader = w.reader
        val searcher = newSearcher(reader)
        searcher.queryCachingPolicy = ALWAYS_CACHE

        val q: Query = TermQuery(Term("foo", "bar"))
        searcher.count(q)

        val weight = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f)
        assertNotEquals(-1, weight.count(searcher.indexReader.leaves()[0]))

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testSkipCachingForTermQuery() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc1 = Document()
        doc1.add(StringField("name", "tom", Field.Store.YES))
        doc1.add(StringField("hobby", "movie", Field.Store.YES))
        val doc2 = Document()
        doc2.add(StringField("name", "alice", Field.Store.YES))
        doc2.add(StringField("hobby", "book", Field.Store.YES))
        val doc3 = Document()
        doc3.add(StringField("name", "alice", Field.Store.YES))
        doc3.add(StringField("hobby", "movie", Field.Store.YES))
        w.addDocuments(listOf(doc1, doc2, doc3))
        val reader = w.reader
        val searcher = newSearcher(reader)
        val policy = object : QueryCachingPolicy {
            override fun shouldCache(query: Query): Boolean = query::class != TermQuery::class
            override fun onUse(query: Query) {}
        }
        searcher.queryCachingPolicy = policy
        w.close()

        val inner = BooleanQuery.Builder()
        val innerSubQuery1 = TermQuery(Term("hobby", "book"))
        val innerSubQuery2 = TermQuery(Term("hobby", "movie"))
        val subQuery1 = inner.add(innerSubQuery1, Occur.SHOULD).add(innerSubQuery2, Occur.SHOULD).build()

        val bq = BooleanQuery.Builder()
        val subQuery2 = TermQuery(Term("name", "alice"))
        val query = bq.add(ConstantScoreQuery(subQuery1), Occur.FILTER).add(subQuery2, Occur.FILTER).build()
        val cacheSet = hashSetOf<Query>()

        val partCache = LRUQueryCache(1000000, 10000000, { true }, 1f)
        searcher.queryCache = partCache
        searcher.search(query, 1)
        assertEquals(cacheSet, runBlocking { partCache.cachedQueries() }.toSet())

        val allCache = LRUQueryCache(1000000, 10000000, { true }, Float.POSITIVE_INFINITY)
        searcher.queryCache = allCache
        searcher.search(query, 1)
        cacheSet.add(subQuery1)
        assertEquals(cacheSet, runBlocking { allCache.cachedQueries() }.toSet())

        reader.close()
        dir.close()
    }

    @Test
    fun testCacheHasFastCount() {
        val query: Query = PhraseQuery("words", BytesRef("alice"), BytesRef("ran"))

        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
        val doc1 = Document()
        doc1.add(TextField("words", "tom ran", Field.Store.NO))
        val doc2 = Document()
        doc2.add(TextField("words", "alice ran", Field.Store.NO))
        doc2.add(StringField("f", "a", Field.Store.NO))
        val doc3 = Document()
        doc3.add(TextField("words", "alice ran", Field.Store.NO))
        doc3.add(StringField("f", "b", Field.Store.NO))
        w.addDocuments(listOf(doc1, doc2, doc3))

        w.reader.use { reader ->
            val searcher = newSearcher(reader)
            searcher.queryCachingPolicy = ALWAYS_CACHE
            val allCache = LRUQueryCache(1000000, 10000000, { true }, Float.POSITIVE_INFINITY)
            searcher.queryCache = allCache
            val weight = searcher.createWeight(query, ScoreMode.COMPLETE_NO_SCORES, 1f)
            val context = getOnlyLeafReader(reader).context
            assertEquals(-1, weight.count(context))
            weight.scorer(context)
            assertEquals(listOf(query), runBlocking { allCache.cachedQueries() })
            assertEquals(2, weight.count(context))
        }

        w.deleteDocuments(TermQuery(Term("f", BytesRef("b"))))
        w.reader.use { reader ->
            val searcher = newSearcher(reader)
            searcher.queryCachingPolicy = ALWAYS_CACHE
            val allCache = LRUQueryCache(1000000, 10000000, { true }, Float.POSITIVE_INFINITY)
            searcher.queryCache = allCache
            val weight = searcher.createWeight(query, ScoreMode.COMPLETE_NO_SCORES, 1f)
            val context = getOnlyLeafReader(reader).context
            assertEquals(-1, weight.count(context))
            weight.scorer(context)
            assertEquals(listOf(query), runBlocking { allCache.cachedQueries() })
            assertEquals(-1, weight.count(context))
        }

        w.close()
        dir.close()
    }
}
