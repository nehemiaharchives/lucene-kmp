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
package org.gnit.lucenekmp.facet

import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.*
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.withLock

/** Only purpose is to punch through and return a DrillSidewaysScorer */

// TODO change the way DrillSidewaysScorer is used, this query does not work
// with filter caching
internal class DrillSidewaysQuery<K : Collector, R> private constructor(
    val baseQuery: Query,
    val drillSidewaysCollectorManagers: List<CollectorManager<K, R>>,
    val managedDrillSidewaysCollectors: MutableList<MutableList<K>>,
    val drillDownQueries: Array<Query>,
    val scoreSubDocsAtOnce: Boolean,
    private val managedCollectorsLock: ReentrantLock
) : Query() {
/**
   * Construct a new `DrillSidewaysQuery` that will create new [FacetsCollector]s for
   * each [LeafReaderContext] using the provided [FacetsCollectorManager]s.
   */
    constructor(
        baseQuery: Query,
        drillSidewaysCollectorManagers: List<CollectorManager<K, R>>,
        drillDownQueries: Array<Query>,
        scoreSubDocsAtOnce: Boolean
    ) : this(
        baseQuery,
        drillSidewaysCollectorManagers,
        ArrayList(),
        drillDownQueries,
        scoreSubDocsAtOnce,
        ReentrantLock()
    )
    // Note that the "managed" collector lists are synchronized here since bulkScorer()
    // can be invoked concurrently and needs to remain thread-safe. We're OK with synchronizing
    // on the whole list as contention is expected to remain very low:
    // Kotlin common uses managedCollectorsLock for the same list across rewrites.

/**
   * Needed for [Query.rewrite]. Ensures the same "managed" lists get used
   * since [DrillSideways] accesses references to these through the original `* DrillSidewaysQuery`.
   */
  
  override fun toString(field: String?): String {
    return "DrillSidewaysQuery"
  }

  
  override fun rewrite(indexSearcher: IndexSearcher): Query {
    var newQuery = baseQuery
    while (true) {
      val rewrittenQuery = newQuery.rewrite(indexSearcher)
      if (rewrittenQuery === newQuery) {
        break
      }
      newQuery = rewrittenQuery
    }
    if (newQuery === baseQuery) {
      return super.rewrite(indexSearcher)
    } else {
      return DrillSidewaysQuery(
          newQuery,
          drillSidewaysCollectorManagers,
          managedDrillSidewaysCollectors,
          drillDownQueries,
          scoreSubDocsAtOnce, managedCollectorsLock)
    }
  }

  
  override fun visit(visitor: QueryVisitor) {
    visitor.visitLeaf(this)
  }

  
  override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
    val baseWeight = baseQuery.createWeight(searcher, scoreMode, boost)
    val drillDowns = Array(drillDownQueries.size) { dim ->
      searcher.createWeight(searcher.rewrite(drillDownQueries[dim]), ScoreMode.COMPLETE_NO_SCORES, 1f)
    }

    return object : Weight(this@DrillSidewaysQuery) {
      
      override fun explain(context: LeafReaderContext, doc: Int): Explanation {
        return baseWeight.explain(context, doc)
      }

      
      override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
        val baseScorerSupplier = baseWeight.scorerSupplier(context)

        val drillDownCount = drillDowns.size

        val sidewaysCollectors = ArrayList<K>(drillDownCount)
        managedCollectorsLock.withLock { managedDrillSidewaysCollectors.add(sidewaysCollectors) }

        val dims = arrayOfNulls<DrillSidewaysScorer.DocsAndCost>(drillDownCount)

        var nullCount = 0
        for (dim in dims.indices) {
          var scorer = drillDowns[dim].scorer(context)
          if (scorer == null) {
            nullCount++
            scorer = ConstantScoreScorer(0f, scoreMode, DocIdSetIterator.empty())
          }

          val sidewaysCollector = drillSidewaysCollectorManagers.get(dim).newCollector()
          sidewaysCollectors.add(dim, sidewaysCollector)

          dims[dim] =
              DrillSidewaysScorer.DocsAndCost(
                  scorer, sidewaysCollector.getLeafCollector(context))
        }

        // If baseScorer is null or the dim nullCount > 1, then we have nothing to score. We return
        // a null scorer in this case, but we need to make sure #finish gets called on all facet
        // collectors since IndexSearcher won't handle this for us:
        if (baseScorerSupplier == null || nullCount > 1) {
          for (dim in dims) {
            dim!!.sidewaysLeafCollector.finish()
          }
          return null
        }

        // Sort drill-downs by most restrictive first:
        dims.sortWith(compareBy { it!!.approximation.cost() })

        return object : ScorerSupplier() {
          
          override fun get(leadCost: Long): Scorer {
            // We can only run as a top scorer:
            throw UnsupportedOperationException()
          }

          
          override fun bulkScorer(): BulkScorer {
            return DrillSidewaysScorer(
                context, baseScorerSupplier.get(Long.MAX_VALUE)!!, dims.requireNoNulls(), scoreSubDocsAtOnce)
          }

          
          override fun cost(): Long {
            throw UnsupportedOperationException()
          }
        }
      }

      
      override fun isCacheable(ctx: LeafReaderContext): Boolean {
        // We can never cache DSQ instances. It's critical that the BulkScorer produced by this
        // Weight runs through the "normal" execution path so that it has access to an
        // "acceptDocs" instance that accurately reflects deleted docs. During caching,
        // "acceptDocs" is null so that caching over-matches (since the final BulkScorer would
        // account for deleted docs). The problem is that this BulkScorer has a side-effect of
        // populating the "sideways" FacetsCollectors, so it will use deleted docs in its
        // sideways counting if caching kicks in. See LUCENE-10060:
        return false
      }
    }
  }

  // TODO: these should do "deeper" equals/hash on the 2-D drillDownTerms array

  
  override fun hashCode(): Int {
    val prime = 31
    var result = classHash()
    result = prime * result + baseQuery.hashCode()
    result = prime * result + drillDownQueries.contentHashCode()
    result = prime * result + drillSidewaysCollectorManagers.hashCode()
    return result
  }

  
  override fun equals(other: Any?): Boolean {
    return sameClassAs(other) && equalsTo(other as DrillSidewaysQuery<*, *>)
  }

  private fun equalsTo(other: DrillSidewaysQuery<*, *>): Boolean {
    return (baseQuery == other.baseQuery)
        && drillDownQueries.contentEquals(other.drillDownQueries)
        && (drillSidewaysCollectorManagers == other.drillSidewaysCollectorManagers)
  }
}
