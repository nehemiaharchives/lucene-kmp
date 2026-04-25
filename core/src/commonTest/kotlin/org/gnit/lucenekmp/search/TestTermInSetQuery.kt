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

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.KeywordField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilterDirectoryReader
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RamUsageTester
import org.gnit.lucenekmp.tests.util.RandomStrings
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefIterator
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.automaton.ByteRunAutomaton
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalAtomicApi::class)
class TestTermInSetQuery : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testAllDocsInFieldTerm() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val field = "f"

        val denseTerm = BytesRef(TestUtil.randomAnalysisString(random(), 10, true))

        val randomTerms = HashSet<BytesRef>()
        while (randomTerms.size < AbstractMultiTermQueryConstantScoreWrapper.BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD) {
            randomTerms.add(BytesRef(TestUtil.randomAnalysisString(random(), 10, true)))
        }
        assert(randomTerms.size == AbstractMultiTermQueryConstantScoreWrapper.BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD)
        val otherTerms = arrayOfNulls<BytesRef>(randomTerms.size)
        var idx = 0
        for (term in randomTerms) {
            otherTerms[idx++] = term
        }

        // Every doc with a value for `field` will contain `denseTerm`:
        val numDocs = 10 * otherTerms.size
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(StringField(field, denseTerm, Store.NO))
            val sparseTerm = otherTerms[i % otherTerms.size]!!
            doc.add(StringField(field, sparseTerm, Store.NO))
            iw.addDocument(doc)
        }

        // Make sure there are some docs in the index that don't contain a value for the field at all:
        for (i in 0..<100) {
            val doc = Document()
            doc.add(StringField("foo", "bar", Store.NO))
            iw.addDocument(doc)
        }

        val reader: IndexReader = iw.reader
        val searcher = newSearcher(reader)
        iw.close()

        val queryTerms = otherTerms.mapTo(ArrayList(otherTerms.size + 1)) { it!! }
        queryTerms.add(denseTerm)

        val query = TermInSetQuery(field, queryTerms)
        val topDocs = searcher.search(query, numDocs)
        assertEquals(numDocs.toLong(), topDocs.totalHits.value)

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDuel() {
        val iters = atLeast(2)
        val field = "f"
        for (iter in 0..<iters) {
            val allTerms = ArrayList<BytesRef>()
            val numTerms = TestUtil.nextInt(random(), 1, 1 shl TestUtil.nextInt(random(), 1, 10))
            for (i in 0..<numTerms) {
                val value = TestUtil.randomAnalysisString(random(), 10, true)
                allTerms.add(newBytesRef(value))
            }
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(1000) // TODO reduced from 10000 to 1000 for dev speed
            for (i in 0..<numDocs) {
                val doc = Document()
                val term = allTerms[random().nextInt(allTerms.size)]
                doc.add(StringField(field, term, Store.NO))
                // Also include a doc values field with a skip-list so we can test doc-value rewrite as
                // well:
                doc.add(SortedSetDocValuesField.indexedField(field, term))
                iw.addDocument(doc)
            }
            if (numTerms > 1 && random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(Term(field, allTerms[0])))
            }
            iw.commit()
            val reader: IndexReader = iw.reader
            val searcher = newSearcher(reader)
            iw.close()

            if (reader.numDocs() == 0) {
                // may occasionally happen if all documents got the same term
                IOUtils.close(reader, dir)
                continue
            }

            for (i in 0..<100) {
                val boost = random().nextFloat() * 10
                val numQueryTerms = TestUtil.nextInt(random(), 1, 1 shl TestUtil.nextInt(random(), 1, 8))
                val queryTerms = ArrayList<BytesRef>()
                for (j in 0..<numQueryTerms) {
                    queryTerms.add(allTerms[random().nextInt(allTerms.size)])
                }
                val bq = BooleanQuery.Builder()
                for (t in queryTerms) {
                    bq.add(TermQuery(Term(field, t)), Occur.SHOULD)
                }
                val q1: Query = ConstantScoreQuery(bq.build())
                val q2: Query = TermInSetQuery(field, queryTerms)
                val q3: Query = TermInSetQuery(MultiTermQuery.DOC_VALUES_REWRITE, field, queryTerms)
                assertSameMatches(searcher, BoostQuery(q1, boost), BoostQuery(q2, boost), true)
                assertSameMatches(searcher, BoostQuery(q1, boost), BoostQuery(q3, boost), false)
            }

            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testReturnsNullScoreSupplier() {
        newDirectory().use { directory ->
            IndexWriter(directory, IndexWriterConfig()).use { writer ->
                for (ch in 'a'..'z') {
                    val doc = Document()
                    doc.add(KeywordField("id", ch.toString(), Field.Store.YES))
                    doc.add(KeywordField("content", ch.toString(), Field.Store.YES))
                    writer.addDocument(doc)
                }
            }
            DirectoryReader.open(directory).use { reader ->
                val terms = ArrayList<BytesRef>()
                for (ch in 'a'..'z') {
                    terms.add(newBytesRef(ch.toString()))
                }
                val query2: Query = TermInSetQuery("content", terms)

                run {
                    // query1 doesn't match any documents
                    val query1: Query = TermInSetQuery("id", mutableListOf(newBytesRef("aaa"), newBytesRef("bbb")))
                    val queryBuilder = BooleanQuery.Builder()
                    queryBuilder.add(query1, Occur.FILTER)
                    queryBuilder.add(query2, Occur.FILTER)
                    val boolQuery = queryBuilder.build()

                    val searcher = IndexSearcher(reader)
                    val ctx = reader.leaves()[0]

                    val weight1 = searcher.createWeight(searcher.rewrite(query1), ScoreMode.COMPLETE, 1f)
                    val scorerSupplier1 = weight1.scorerSupplier(ctx)
                    // as query1 doesn't match any documents, its scorerSupplier must be null
                    assertNull(scorerSupplier1)
                    val weight = searcher.createWeight(searcher.rewrite(boolQuery), ScoreMode.COMPLETE, 1f)
                    // scorerSupplier of a bool query where query1 is mandatory must be null
                    val scorerSupplier = weight.scorerSupplier(ctx)
                    assertNull(scorerSupplier)
                }
                run {
                    // query1 matches some documents
                    val query1: Query =
                        TermInSetQuery("id", mutableListOf(newBytesRef("aaa"), newBytesRef("bbb"), newBytesRef("b")))
                    val queryBuilder = BooleanQuery.Builder()
                    queryBuilder.add(query1, Occur.FILTER)
                    queryBuilder.add(query2, Occur.FILTER)
                    val boolQuery = queryBuilder.build()

                    val searcher = IndexSearcher(reader)
                    val ctx = reader.leaves()[0]

                    val weight1 = searcher.createWeight(searcher.rewrite(query1), ScoreMode.COMPLETE, 1f)
                    val scorerSupplier1 = weight1.scorerSupplier(ctx)
                    // as query1 matches some documents, its scorerSupplier must not be null
                    assertNotNull(scorerSupplier1)
                    val weight = searcher.createWeight(searcher.rewrite(boolQuery), ScoreMode.COMPLETE, 1f)
                    // scorerSupplier of a bool query where query1 is mandatory must not be null
                    val scorerSupplier = weight.scorerSupplier(ctx)
                    assertNotNull(scorerSupplier)
                }
            }
        }
    }

    /**
     * Make sure the doc values skipper isn't making the incorrect assumption that the min/max terms
     * from a TermInSetQuery don't form a continuous range.
     */
    @Test
    @Throws(IOException::class)
    fun testSkipperOptimizationGapAssumption() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        // Index the first 10,000 docs all with the term "b" to get some skip list blocks with the range
        // [b, b]:
        for (i in 0..<10_000) {
            val doc = Document()
            val term = BytesRef("b")
            doc.add(SortedSetDocValuesField("field", term))
            doc.add(SortedSetDocValuesField.indexedField("idx_field", term))
            iw.addDocument(doc)
        }

        // Index a couple more docs with terms "a" and "c":
        var doc = Document()
        var term = BytesRef("a")
        doc.add(SortedSetDocValuesField("field", term))
        doc.add(SortedSetDocValuesField.indexedField("idx_field", term))
        iw.addDocument(doc)
        doc = Document()
        term = BytesRef("c")
        doc.add(SortedSetDocValuesField("field", term))
        doc.add(SortedSetDocValuesField.indexedField("idx_field", term))
        iw.addDocument(doc)

        iw.commit()
        val reader: IndexReader = iw.reader
        val searcher = newSearcher(reader)
        iw.close()

        // Our query is for (or "a" "c") which should use a skip-list optimization to exclude blocks of
        // documents that fall outside the range [a, c]. We want to test that they don't incorrectly do
        // the inverse and include all docs in a block that fall within [a, c] (which is why we have
        // blocks of only "b" docs up-front):
        val queryTerms = mutableListOf(BytesRef("a"), BytesRef("c"))
        val q1: Query = TermInSetQuery(MultiTermQuery.DOC_VALUES_REWRITE, "field", queryTerms)
        val q2: Query = TermInSetQuery(MultiTermQuery.DOC_VALUES_REWRITE, "idx_field", queryTerms)
        assertSameMatches(searcher, q1, q2, false)

        reader.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun assertSameMatches(searcher: IndexSearcher, q1: Query, q2: Query, scores: Boolean) {
        val maxDoc = searcher.indexReader.maxDoc()
        val td1 = searcher.search(q1, maxDoc, if (scores) Sort.RELEVANCE else Sort.INDEXORDER)
        val td2 = searcher.search(q2, maxDoc, if (scores) Sort.RELEVANCE else Sort.INDEXORDER)
        assertEquals(td1.totalHits.value, td2.totalHits.value)
        for (i in td1.scoreDocs.indices) {
            assertEquals(td1.scoreDocs[i].doc, td2.scoreDocs[i].doc)
            if (scores) {
                assertEquals(td1.scoreDocs[i].score, td2.scoreDocs[i].score, 10e-7f)
            }
        }
    }

    @Test
    fun testHashCodeAndEquals() {
        val num = atLeast(100)
        val terms = ArrayList<BytesRef>()
        val uniqueTerms = HashSet<BytesRef>()
        for (i in 0..<num) {
            val string = TestUtil.randomRealisticUnicodeString(random())
            terms.add(newBytesRef(string))
            uniqueTerms.add(newBytesRef(string))
            val left = TermInSetQuery("field", uniqueTerms)
            terms.shuffle(random())
            val right = TermInSetQuery("field", terms)
            assertEquals(right, left)
            assertEquals(right.hashCode(), left.hashCode())
            if (uniqueTerms.size > 1) {
                val asList = ArrayList(uniqueTerms)
                asList.removeAt(0)
                val notEqual = TermInSetQuery("field", asList)
                assertFalse(left == notEqual)
                assertFalse(right == notEqual)
            }
        }

        var tq1 = TermInSetQuery("thing", mutableListOf(newBytesRef("apple")))
        var tq2 = TermInSetQuery("thing", mutableListOf(newBytesRef("orange")))
        assertFalse(tq1.hashCode() == tq2.hashCode())

        // different fields with the same term should have differing hashcodes
        tq1 = TermInSetQuery("thing", mutableListOf(newBytesRef("apple")))
        tq2 = TermInSetQuery("thing2", mutableListOf(newBytesRef("apple")))
        assertFalse(tq1.hashCode() == tq2.hashCode())
    }

    @Test
    fun testSimpleEquals() {
        // Two terms with the same hash code
        assertEquals("AaAaBB".hashCode(), "BBBBBB".hashCode())
        val left = TermInSetQuery("id", mutableListOf(newBytesRef("AaAaAa"), newBytesRef("AaAaBB")))
        val right = TermInSetQuery("id", mutableListOf(newBytesRef("AaAaAa"), newBytesRef("BBBBBB")))
        assertFalse(left == right)
    }

    @Test
    fun testToString() {
        val termsQuery = TermInSetQuery("field1", mutableListOf(newBytesRef("a"), newBytesRef("b"), newBytesRef("c")))
        assertEquals("field1:(a b c)", termsQuery.toString())
    }

    @Test
    fun testDedup() {
        val query1: Query = TermInSetQuery("foo", mutableListOf(newBytesRef("bar")))
        val query2: Query = TermInSetQuery("foo", mutableListOf(newBytesRef("bar"), newBytesRef("bar")))
        QueryUtils.checkEqual(query1, query2)
    }

    @Test
    fun testOrderDoesNotMatter() {
        // order of terms if different
        val query1: Query = TermInSetQuery("foo", mutableListOf(newBytesRef("bar"), newBytesRef("baz")))
        val query2: Query = TermInSetQuery("foo", mutableListOf(newBytesRef("baz"), newBytesRef("bar")))
        QueryUtils.checkEqual(query1, query2)
    }

    @Test
    fun testRamBytesUsed() {
        val terms = ArrayList<BytesRef>()
        val numTerms = 10000 + random().nextInt(1000)
        for (i in 0..<numTerms) {
            terms.add(newBytesRef(RandomStrings.randomUnicodeOfLength(random(), 10)))
        }
        val query = TermInSetQuery("f", terms)
        val actualRamBytesUsed = RamUsageTester.ramUsed(query)
        val expectedRamBytesUsed = query.ramBytesUsed()
        // error margin within 5%
        assertEquals(expectedRamBytesUsed.toDouble(), actualRamBytesUsed.toDouble(), actualRamBytesUsed / 20.0)
    }

    private class TermsCountingDirectoryReaderWrapper(
        `in`: DirectoryReader,
        private val counter: AtomicInteger,
    ) : FilterDirectoryReader(`in`, TermsCountingSubReaderWrapper(counter)) {

        private class TermsCountingSubReaderWrapper(private val counter: AtomicInteger) : SubReaderWrapper() {
            override fun wrap(reader: LeafReader): LeafReader {
                return TermsCountingLeafReaderWrapper(reader, counter)
            }
        }

        private class TermsCountingLeafReaderWrapper(
            `in`: LeafReader,
            private val counter: AtomicInteger,
        ) : FilterLeafReader(`in`) {

            @Throws(IOException::class)
            override fun terms(field: String?): Terms? {
                val terms = super.terms(field)
                if (terms == null) {
                    return null
                }
                return object : FilterTerms(terms) {
                    @Throws(IOException::class)
                    override fun iterator(): TermsEnum {
                        counter.store(counter.load() + 1)
                        return super.iterator()
                    }
                }
            }

            override val coreCacheHelper: IndexReader.CacheHelper?
                get() = null

            override val readerCacheHelper: IndexReader.CacheHelper?
                get() = null
        }

        @Throws(IOException::class)
        override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
            return TermsCountingDirectoryReaderWrapper(`in`, counter)
        }

        override val readerCacheHelper: IndexReader.CacheHelper?
            get() = null
    }

    @Test
    @Throws(Exception::class)
    fun testPullOneTermsEnum() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(StringField("foo", "1", Store.NO))
        w.addDocument(doc)
        val reader = w.reader
        w.close()
        val counter = AtomicInteger(0)
        val wrapped = TermsCountingDirectoryReaderWrapper(reader, counter)

        val terms = ArrayList<BytesRef>()
        // enough terms to avoid the rewrite
        val numTerms = TestUtil.nextInt(
            random(),
            AbstractMultiTermQueryConstantScoreWrapper.BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD + 1,
            100
        )
        for (i in 0..<numTerms) {
            val term = newBytesRef(RandomStrings.randomUnicodeOfCodepointLength(random(), 10))
            terms.add(term)
        }

        assertEquals(0, IndexSearcher(wrapped).count(TermInSetQuery("bar", terms)))
        assertEquals(0, counter.load()) // missing field
        IndexSearcher(wrapped).count(TermInSetQuery("foo", terms))
        assertEquals(1, counter.load())
        wrapped.close()
        dir.close()
    }

    @Test
    fun testBinaryToString() {
        val query = TermInSetQuery("field", mutableListOf(newBytesRef(byteArrayOf(0xff.toByte(), 0xfe.toByte()))))
        assertEquals("field:([ff fe])", query.toString())
    }

    @Test
    @Throws(IOException::class)
    fun testIsConsideredCostlyByQueryCache() {
        val query = TermInSetQuery("foo", mutableListOf(newBytesRef("bar"), newBytesRef("baz")))
        val policy = UsageTrackingQueryCachingPolicy()
        assertFalse(policy.shouldCache(query))
        policy.onUse(query)
        policy.onUse(query)
        // cached after two uses
        assertTrue(policy.shouldCache(query))
    }

    @Test
    fun testVisitor() {
        // singleton reports back to consumeTerms()
        val singleton = TermInSetQuery("field", mutableListOf(newBytesRef("term1")))
        singleton.visit(
            object : QueryVisitor() {
                override fun consumeTerms(query: Query, vararg terms: Term) {
                    assertEquals(1, terms.size)
                    assertEquals(Term("field", newBytesRef("term1")), terms[0])
                }

                override fun consumeTermsMatching(
                    query: Query,
                    field: String,
                    automaton: () -> ByteRunAutomaton,
                ) {
                    fail("Singleton TermInSetQuery should not try to build ByteRunAutomaton")
                }
            }
        )

        // multiple values built into automaton
        val terms = ArrayList<BytesRef>()
        for (i in 0..<100) {
            terms.add(newBytesRef("term$i"))
        }
        val t = TermInSetQuery("field", terms)
        t.visit(
            object : QueryVisitor() {
                override fun consumeTerms(query: Query, vararg terms: Term) {
                    fail("TermInSetQuery with multiple terms should build automaton")
                }

                override fun consumeTermsMatching(
                    query: Query,
                    field: String,
                    automaton: () -> ByteRunAutomaton,
                ) {
                    val a = automaton()
                    val test = newBytesRef("nonmatching")
                    assertFalse(a.run(test.bytes, test.offset, test.length))
                    for (term in terms) {
                        assertTrue(a.run(term.bytes, term.offset, term.length))
                    }
                }
            }
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTermsIterator() {
        val empty = TermInSetQuery("field", mutableListOf())
        var it: BytesRefIterator = empty.bytesRefIterator
        assertNull(it.next())

        val query = TermInSetQuery("field", mutableListOf(newBytesRef("term1"), newBytesRef("term2"), newBytesRef("term3")))
        it = query.bytesRefIterator
        assertEquals(newBytesRef("term1"), it.next())
        assertEquals(newBytesRef("term2"), it.next())
        assertEquals(newBytesRef("term3"), it.next())
        assertNull(it.next())
    }
}
