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
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestUsageTrackingFilterCachingPolicy : LuceneTestCase() {

  @Test
  fun testCostlyFilter() {
    assertTrue(
      UsageTrackingQueryCachingPolicy.isCostly(PrefixQuery(Term("field", "prefix")))
    )
    assertTrue(
      UsageTrackingQueryCachingPolicy.isCostly(IntPoint.newRangeQuery("intField", 1, 1000))
    )
    assertFalse(
      UsageTrackingQueryCachingPolicy.isCostly(TermQuery(Term("field", "value")))
    )
  }

  @Test
  @Throws(Exception::class)
  fun testNeverCacheMatchAll() {
    val q: Query = MatchAllDocsQuery()
    val policy = UsageTrackingQueryCachingPolicy()
    for (i in 0..<1000) {
      policy.onUse(q)
    }
    assertFalse(policy.shouldCache(q))
  }

  @Test
  @Throws(IOException::class)
  fun testNeverCacheTermFilter() {
    val q: Query = TermQuery(Term("foo", "bar"))
    val policy = UsageTrackingQueryCachingPolicy()
    for (i in 0..<1000) {
      policy.onUse(q)
    }
    assertFalse(policy.shouldCache(q))
  }

  @Test
  @Throws(IOException::class)
  fun testNeverCacheDocValuesFieldExistsFilter() {
    val q: Query = FieldExistsQuery("foo")
    val policy = UsageTrackingQueryCachingPolicy()
    for (i in 0..<1000) {
      policy.onUse(q)
    }
    assertFalse(policy.shouldCache(q))
  }

  @Test
  @Throws(IOException::class)
  fun testBooleanQueries() {
    val dir: Directory = newDirectory()
    val w = RandomIndexWriter(random(), dir)
    w.addDocument(Document())
    val reader: IndexReader = w.reader
    w.close()

    val searcher = IndexSearcher(reader)
    val policy = UsageTrackingQueryCachingPolicy()
    val cache = LRUQueryCache(10, Long.MAX_VALUE, { true }, Float.POSITIVE_INFINITY)
    searcher.queryCache = cache
    searcher.queryCachingPolicy = policy

    val q1 = DummyQuery(1)
    val q2 = DummyQuery(2)
    val bq =
      BooleanQuery.Builder().add(q1, Occur.SHOULD).add(q2, Occur.SHOULD).build()

    for (i in 0..<3) {
      searcher.count(bq)
    }
    assertEquals(0, cache.cacheSize) // nothing cached yet, too early

    searcher.count(bq)
    assertEquals(1, cache.cacheSize) // the bq got cached, but not q1 and q2

    for (i in 0..<10) {
      searcher.count(bq)
    }
    assertEquals(
      1, cache.cacheSize
    ) // q1 and q2 still not cached since we do not pull scorers on them

    searcher.count(q1)
    assertEquals(2, cache.cacheSize) // q1 used on its own -> cached

    reader.close()
    dir.close()
  }

  private class DummyQuery(private val id: Int) : Query() {

    override fun toString(field: String?): String {
      return "dummy"
    }

    override fun equals(other: Any?): Boolean {
      return sameClassAs(other) && (other as DummyQuery).id == id
    }

    override fun hashCode(): Int {
      return id
    }

    @Throws(Exception::class)
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
      return object : ConstantScoreWeight(this, boost) {
        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
          val scorer = ConstantScoreScorer(score(), scoreMode, DocIdSetIterator.all(1))
          return DefaultScorerSupplier(scorer)
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
          return true
        }
      }
    }

    override fun visit(visitor: QueryVisitor) {}
  }
}
