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
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.CompositeReaderContext
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.index.FilterDirectoryReader
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.DummyTotalHitCountCollector
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOBooleanSupplier
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalAtomicApi::class)
class TestTermQuery : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testEquals() {
        QueryUtils.checkEqual(
            TermQuery(Term("foo", "bar")), TermQuery(Term("foo", "bar"))
        )
        QueryUtils.checkUnequal(
            TermQuery(Term("foo", "bar")), TermQuery(Term("foo", "baz"))
        )
        val context: CompositeReaderContext
        MultiReader().use { multiReader ->
            context = multiReader.context
            val searcher = IndexSearcher(context)
            QueryUtils.checkEqual(
                TermQuery(Term("foo", "bar")),
                TermQuery(
                    Term("foo", "bar"), TermStates.build(searcher, Term("foo", "bar"), true)
                )
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testCreateWeightDoesNotSeekIfScoresAreNotNeeded() {
        val dir: Directory = newDirectory()
        val w =
            RandomIndexWriter(
                random(), dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
            )
        // segment that contains the term
        var doc = Document()
        doc.add(StringField("foo", "bar", Store.NO))
        w.addDocument(doc)
        w.reader.close()
        // segment that does not contain the term
        doc = Document()
        doc.add(StringField("foo", "baz", Store.NO))
        w.addDocument(doc)
        w.reader.close()
        // segment that does not contain the field
        w.addDocument(Document())

        val reader = w.reader
        val noSeekReader = NoSeekDirectoryReader(reader)
        val noSeekSearcher = IndexSearcher(noSeekReader)
        val query: Query = TermQuery(Term("foo", "bar"))
        val e =
            expectThrows(AssertionError::class) {
                noSeekSearcher.createWeight(noSeekSearcher.rewrite(query), ScoreMode.COMPLETE, 1f)
            }
        assertEquals("no seek", e.message)

        noSeekSearcher.createWeight(
            noSeekSearcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f
        ) // no exception
        val searcher = IndexSearcher(reader)
        // use a collector rather than searcher.count() which would just read the
        // doc freq instead of creating a scorer
        var totalHits = searcher.search(query, DummyTotalHitCountCollector.createManager())
        assertEquals(1, totalHits)
        val queryWithContext =
            TermQuery(
                Term("foo", "bar"), TermStates.build(searcher, Term("foo", "bar"), true)
            )
        totalHits = searcher.search(queryWithContext, DummyTotalHitCountCollector.createManager())
        assertEquals(1, totalHits)

        IOUtils.close(reader, w, dir)
    }

    // LUCENE-9620 Add Weight#count(LeafReaderContext)
    @Test
    @Throws(IOException::class)
    fun testQueryMatchesCount() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val randomNumDocs = TestUtil.nextInt(random(), 10, 100)
        var numMatchingDocs = 0

        for (i in 0..<randomNumDocs) {
            val doc = Document()
            if (random().nextBoolean()) {
                doc.add(StringField("foo", "bar", Store.NO))
                numMatchingDocs++
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)

        val reader = w.reader
        val searcher = IndexSearcher(reader)

        val testQuery: Query = TermQuery(Term("foo", "bar"))
        assertEquals(searcher.count(testQuery), numMatchingDocs)
        val weight = searcher.createWeight(testQuery, ScoreMode.COMPLETE, 1f)
        assertEquals(weight.count(reader.leaves()[0]), numMatchingDocs)

        IOUtils.close(reader, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testGetTermStates() {

        // no term states:
        assertNull(TermQuery(Term("foo", "bar")).termStates)

        val dir = newDirectory()
        val w =
            RandomIndexWriter(
                random(), dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
            )
        // segment that contains the term
        var doc = Document()
        doc.add(StringField("foo", "bar", Store.NO))
        w.addDocument(doc)
        w.reader.close()
        // segment that does not contain the term
        doc = Document()
        doc.add(StringField("foo", "baz", Store.NO))
        w.addDocument(doc)
        w.reader.close()
        // segment that does not contain the field
        w.addDocument(Document())

        val reader = w.reader
        val searcher = IndexSearcher(reader)
        val queryWithContext =
            TermQuery(
                Term("foo", "bar"), TermStates.build(searcher, Term("foo", "bar"), true)
            )
        assertNotNull(queryWithContext.termStates)
        IOUtils.close(reader, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testWithWithDifferentScoreModes() {
        val dir = newDirectory()
        val w =
            RandomIndexWriter(
                random(), dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
            )
        // segment that contains the term
        val doc = Document()
        doc.add(StringField("foo", "bar", Store.NO))
        w.addDocument(doc)
        w.reader.close()
        val reader = w.reader
        val searcher = IndexSearcher(reader)
        val existingSimilarity = searcher.similarity

        for (scoreMode in ScoreMode.entries) {
            val scoreModeInWeight = AtomicReference<ScoreMode?>(null)
            val scorerCalled = AtomicBoolean(false)
            searcher.similarity =
                object : Similarity() { // Wrapping existing similarity for testing
                    override fun computeNorm(state: FieldInvertState): Long {
                        return existingSimilarity.computeNorm(state)
                    }

                    override fun scorer(
                        boost: Float,
                        collectionStats: CollectionStatistics,
                        vararg termStats: TermStatistics
                    ): SimScorer {
                        scorerCalled.store(true)
                        return existingSimilarity.scorer(boost, collectionStats, *termStats)
                    }
                }
            val termQuery =
                object : TermQuery(Term("foo", "bar")) {
                    @Throws(IOException::class)
                    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
                        scoreModeInWeight.store(scoreMode)
                        return super.createWeight(searcher, scoreMode, boost)
                    }
                }
            termQuery.createWeight(searcher, scoreMode, 1f)
            assertEquals(scoreMode, scoreModeInWeight.load())
            assertEquals(scoreMode.needsScores(), scorerCalled.load())
        }
        IOUtils.close(reader, w, dir)
    }

    private class NoSeekDirectoryReader(`in`: DirectoryReader) : FilterDirectoryReader(
        `in`,
        object : SubReaderWrapper() {
            override fun wrap(reader: LeafReader): LeafReader {
                return NoSeekLeafReader(reader)
            }
        }
    ) {
        @Throws(IOException::class)
        override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
            return NoSeekDirectoryReader(`in`)
        }

        override val readerCacheHelper: CacheHelper?
            get() = `in`.readerCacheHelper
    }

    private class NoSeekLeafReader(`in`: LeafReader) : FilterLeafReader(`in`) {

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            val terms = super.terms(field)
            return if (terms == null) {
                null
            } else {
                object : FilterTerms(terms) {
                    @Throws(IOException::class)
                    override fun iterator(): TermsEnum {
                        val termsEnum = super.iterator()
                        return object : FilterTermsEnum(termsEnum) {
                            @Throws(IOException::class)
                            override fun seekCeil(text: BytesRef): TermsEnum.SeekStatus {
                                throw AssertionError("no seek")
                            }

                            @Throws(IOException::class)
                            override fun prepareSeekExact(text: BytesRef): IOBooleanSupplier? {
                                throw AssertionError("no seek")
                            }

                            @Throws(IOException::class)
                            override fun seekExact(term: BytesRef, state: TermState) {
                                throw AssertionError("no seek")
                            }

                            @Throws(IOException::class)
                            override fun seekExact(text: BytesRef): Boolean {
                                throw AssertionError("no seek")
                            }

                            @Throws(IOException::class)
                            override fun seekExact(ord: Long) {
                                throw AssertionError("no seek")
                            }
                        }
                    }
                }
            }
        }

        override val coreCacheHelper: CacheHelper?
            get() = `in`.coreCacheHelper

        override val readerCacheHelper: CacheHelper?
            get() = `in`.readerCacheHelper
    }
}
