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

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesFacetCounts
import org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesFacetField
import org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesReaderState
import org.gnit.lucenekmp.facet.taxonomy.FastTaxonomyFacetCounts
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.search.*
import org.gnit.lucenekmp.util.ThreadInterruptedException
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.ExecutionException
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.assert

/**
 * Computes drill down and sideways counts for the provided [DrillDownQuery]. Drill sideways
 * counts include alternative values/aggregates for the drill-down dimensions so that a dimension
 * does not disappear after the user drills down into it.
 *
 * Use one of the static search methods to do the search, and then get the hits and facet results
 * from the returned [DrillSidewaysResult].
 *
 * There is both a "standard" and "concurrent" implementation for drill sideways. The concurrent
 * approach is enabled by providing an `ExecutorService` to the ctor. The concurrent
 * implementation may be a little faster but does duplicate work (which grows linearly with the
 * number of drill down dimensions specified on the provided [DrillDownQuery]). The duplicate
 * work may impact the overall throughput of a system. The standard approach may be a little slower
 * but avoids duplicate computations and query processing. Note that both approaches are compatible
 * with concurrent searching across segments (i.e., if using an [IndexSearcher] constructed
 * with an `Executor`).
 *
 * **NOTE**: this allocates one [FacetsCollector] for each drill-down, plus one. If your
 * index has high number of facet labels then this will multiply your memory usage.
 *
 * @lucene.experimental
 */
open class DrillSideways {

  /** [IndexSearcher] passed to constructor. */
  protected val searcher: IndexSearcher

  /** [TaxonomyReader] passed to constructor. */
  protected val taxoReader: TaxonomyReader?

  /** [SortedSetDocValuesReaderState] passed to constructor; can be null. */
  protected val state: SortedSetDocValuesReaderState?

  /** [FacetsConfig] passed to constructor. */
  protected val config: FacetsConfig

  /** (optional) [ExecutorService] used for "concurrent" drill sideways if desired. */
  private val executor: ExecutorService?

  /** Create a new `DrillSideways` instance. */
  constructor(searcher: IndexSearcher, config: FacetsConfig, taxoReader: TaxonomyReader?) : this(searcher, config, taxoReader, null)

  /**
   * Create a new `DrillSideways` instance, assuming the categories were indexed with [SortedSetDocValuesFacetField].
   */
  constructor(searcher: IndexSearcher, config: FacetsConfig, state: SortedSetDocValuesReaderState?) : this(searcher, config, null, state)

  /**
   * Create a new `DrillSideways` instance, where some dimensions were indexed with [SortedSetDocValuesFacetField] and others were indexed with [FacetField].
   */
  constructor(searcher: IndexSearcher, config: FacetsConfig, taxoReader: TaxonomyReader?, state: SortedSetDocValuesReaderState?) : this(searcher, config, taxoReader, state, null)

  /**
   * Create a new `DrillSideways` instance, where some dimensions were indexed with [SortedSetDocValuesFacetField] and others were indexed with [FacetField].
   *
   * Use this constructor to use the concurrent implementation
   */
  constructor(searcher: IndexSearcher, config: FacetsConfig, taxoReader: TaxonomyReader?, state: SortedSetDocValuesReaderState?, executor: ExecutorService?) {
    this.searcher = searcher
    this.config = config
    this.taxoReader = taxoReader
    this.state = state
    this.executor = executor
  }

  /**
   * Subclass can override to customize drill down facets collector. Returning `null` is valid
   * if no drill down facet collection is needed.
   */
  protected open fun createDrillDownFacetsCollectorManager(): FacetsCollectorManager? {
    return FacetsCollectorManager()
  }

  /**
   * Subclass can override to customize drill sideways facets collector. This should not return
   * `null` as we assume drill sideways is being used to collect "sideways" hits:
   */
  protected open fun createDrillSidewaysFacetsCollectorManager(): FacetsCollectorManager {
    return FacetsCollectorManager()
  }

  /** Subclass can override to customize per-dim Facets impl. */
  protected open fun buildFacetsResult(drillDowns: FacetsCollector?, drillSideways: Array<FacetsCollector>?, drillSidewaysDims: Array<String>?): Facets? {

    var drillDownFacets: Facets? = null
    val drillSidewaysFacets = HashMap<String, Facets>()

    if (taxoReader != null) {
      if (drillDowns != null) {
        drillDownFacets = FastTaxonomyFacetCounts(taxoReader, config, drillDowns)
      }
      if (drillSideways != null) {
        for (i in 0 until drillSideways.size) {
          drillSidewaysFacets.put(
              drillSidewaysDims!![i],
              FastTaxonomyFacetCounts(taxoReader, config, drillSideways[i]))
        }
      }
    } else {
      if (drillDowns != null) {
        drillDownFacets = SortedSetDocValuesFacetCounts(state!!, drillDowns)
      }
      if (drillSideways != null) {
        for (i in 0 until drillSideways.size) {
          drillSidewaysFacets.put(
              drillSidewaysDims!![i], SortedSetDocValuesFacetCounts(state!!, drillSideways[i]))
        }
      }
    }

    if (drillSidewaysFacets.isEmpty()) {
      return drillDownFacets
    } else {
      return MultiFacets(drillSidewaysFacets, drillDownFacets)
    }
  }

  /** Search, sorting by [Sort], and computing drill down and sideways counts. */
  open fun search(query: DrillDownQuery, filter: Query?, after: FieldDoc?, topN: Int, sort: Sort?, doDocScores: Boolean): DrillSidewaysResult {
    var query = query
    if (filter != null) {
      query = DrillDownQuery(config, filter, query)
    }
    if (sort != null) {
      var limit = searcher.indexReader.maxDoc()
      if (limit == 0) {
        limit = 1 // the collector does not alow numHits = 0
      }
      var fTopN = minOf(topN, limit)
      val collectorManager =
          TopFieldCollectorManager(sort, fTopN, after, Int.MAX_VALUE)
      val r = search(query, collectorManager)
      val topDocs = r.collectorResult

      if (doDocScores) {
        TopFieldCollector.populateScores(topDocs.scoreDocs, searcher, query)
      }
      return DrillSidewaysResult(
          r.facets,
          r.collectorResult,
          r.drillDownFacetsCollector,
          r.drillSidewaysFacetsCollector,
          r.drillSidewaysDims)
    } else {
      return search(after, query, topN)
    }
  }

  /** Search, sorting by score, and computing drill down and sideways counts. */
  open fun search(query: DrillDownQuery, topN: Int): DrillSidewaysResult {
    return search(null, query, topN)
  }

  /** Search, sorting by score, and computing drill down and sideways counts. */
  open fun search(after: ScoreDoc?, query: DrillDownQuery, topN: Int): DrillSidewaysResult {
    var limit = searcher.indexReader.maxDoc()
    if (limit == 0) {
      limit = 1 // the collector does not alow numHits = 0
    }
    var fTopN = minOf(topN, limit)
    val collectorManager =
        TopScoreDocCollectorManager(fTopN, after, Int.MAX_VALUE)
    val r = search(query, collectorManager)
    return DrillSidewaysResult(
        r.facets,
        r.collectorResult,
        r.drillDownFacetsCollector,
        r.drillSidewaysFacetsCollector,
        r.drillSidewaysDims)
  }

  /**
   * Override this and return true if your collector (e.g., `ToParentBlockJoinCollector`)
   * expects all sub-scorers to be positioned on the document being collected. This will cause some
   * performance loss; default is false.
   */
  protected open fun scoreSubDocsAtOnce(): Boolean {
    return false
  }

  /**
   * Result of a drill sideways search, including the [Facets] and [TopDocs]. The [FacetsCollector]s for the drill down and drill sideways dimensions are also exposed for
   * advanced use-cases that need access to them as an alternative to accessing the `Facets`.
   */
  open class DrillSidewaysResult /** Sole constructor. */ constructor(
/** Combined drill down and sideways results. */
    val facets: Facets?,
/** Hits. */
    val hits: TopDocs?,
/**
     * FacetsCollector populated based on hits that match the full DrillDownQuery, treating all
     * drill down dimensions as required clauses. Useful for advanced use-cases that want to compute
     * Facets results separate from the provided Facets in this result.
     */
    val drillDownFacetsCollector: FacetsCollector?,
/**
     * FacetsCollectors populated for each drill sideways dimension. Each collector exposes the hits
     * that match on all DrillDownQuery dimensions, but treating their corresponding sideways
     * dimension as optional. This array provides a FacetsCollector for each drill down dimension
     * present in the original DrillDownQuery, and the associated dimension for each FacetsCollector
     * can be determined using the parallel [DrillSidewaysResult.drillSidewaysDims] array.
     * Useful for advanced use-cases that want to compute Facets results separate from the provided
     * Facets in this result.
     */
    val drillSidewaysFacetsCollector: Array<FacetsCollector>?,
/**
     * Dimensions that correspond to to the [DrillSidewaysResult.drillSidewaysFacetsCollector]
     */
    val drillSidewaysDims: Array<String>?,
  )

  private class CallableCollector<R>(
    val searcher: IndexSearcher, val query: Query, val collectorManager: CollectorManager<*, R>
  ) : Callable<R> {
    @Suppress("UNCHECKED_CAST")
    override fun call(): R = searcher.search(query, collectorManager) as R
  }

  private fun getDrillDownQuery(query: DrillDownQuery, queries: Array<Query>, excludedDimension: String): DrillDownQuery? {
    val ddl = DrillDownQuery(config, query.getBaseQuery())
    query.getDims().forEach { (dim, pos) ->
      if (dim != excludedDimension) ddl.add(dim, queries[pos])
    }
    return if (ddl.getDims().size == queries.size) null else ddl
  }

  /** Runs a search, using a [CollectorManager] to gather and merge search results */
  @Suppress("UNCHECKED_CAST")
  open fun <R> search(query: DrillDownQuery, hitCollectorManager: CollectorManager<*, R>): ConcurrentDrillSidewaysResult<R> {
    // Main query
    val drillDownFacetsCollectorManager =
        createDrillDownFacetsCollectorManager()
    val mainCollectorManager: CollectorManager<*, *>
    if (drillDownFacetsCollectorManager != null) {
      // Make sure we populate a facet collector corresponding to the base query if desired:
      mainCollectorManager =
          MultiCollectorManager(drillDownFacetsCollectorManager, hitCollectorManager)
    } else {
      mainCollectorManager = hitCollectorManager
    }
    // Drill sideways dimensions
    val drillSidewaysCollectorManagers: MutableList<FacetsCollectorManager>?
    if (query.getDims().isEmpty() == false) {
      drillSidewaysCollectorManagers = ArrayList(query.getDims().size)
      for (i in 0 until query.getDims().size) {
        drillSidewaysCollectorManagers.add(createDrillSidewaysFacetsCollectorManager())
      }
    } else {
      drillSidewaysCollectorManagers = null
    }
    // Execute query
    val result: Result<*, FacetsCollector>
    if (executor != null) {
      result = searchConcurrently(query, mainCollectorManager, drillSidewaysCollectorManagers)
    } else {
      result = searchSequentially(query, mainCollectorManager, drillSidewaysCollectorManagers)
    }

    // Collect results
    val facetsCollectorResult: FacetsCollector?
    val hitCollectorResult: R
    if (drillDownFacetsCollectorManager != null) {
      // drill down collected using MultiCollector
      // Extract the results:
      val drillDownResult = result.drillDownResult as Array<*>
      facetsCollectorResult = drillDownResult[0] as FacetsCollector
      hitCollectorResult = drillDownResult[1] as R
    } else {
      facetsCollectorResult = null
      hitCollectorResult = result.drillDownResult as R
    }

    // Getting results for drill sideways dimensions (if any)
    val drillSidewaysDims: Array<String>?
    val drillSidewaysCollectors: Array<FacetsCollector>?
    if (query.getDims().isEmpty() == false) {
      drillSidewaysDims = query.getDims().keys.toTypedArray()
      var numDims = query.getDims().size
      assert(drillSidewaysCollectorManagers != null)
      assert(drillSidewaysCollectorManagers!!.size == numDims)
      drillSidewaysCollectors = Array(numDims) { dim -> result.drillSidewaysResults!![dim] }
    } else {
      drillSidewaysDims = null
      drillSidewaysCollectors = null
    }

    return ConcurrentDrillSidewaysResult(
        buildFacetsResult(facetsCollectorResult, drillSidewaysCollectors, drillSidewaysDims),
        null,
        hitCollectorResult,
        facetsCollectorResult,
        drillSidewaysCollectors,
        drillSidewaysDims)
  }

  /**
   * Search using DrillDownQuery with custom collectors. This method can be used with any [CollectorManager]s.
   *
   * Note: Use [MultiCollectorManager] to collect both hits and facets for the entire query
   * and/or for drill-sideways dimensions. You can also use it to wrap different types of [CollectorManager] for drill-sideways dimensions.
   */
  open fun <C : Collector, T, K : Collector, R> search(query: DrillDownQuery, drillDownCollectorManager: CollectorManager<C, T>?, drillSidewaysCollectorManagers: List<CollectorManager<K, R>>?): Result<T, R> {
    if (drillDownCollectorManager == null) {
      throw IllegalArgumentException(
          "This search method requires client to provide drill down collector manager")
    }
    if (drillSidewaysCollectorManagers == null) {
      if (query.getDims().isEmpty() == false) {
        throw IllegalArgumentException(
            "The query requires not null drillSidewaysCollectorManagers")
      }
    } else if (drillSidewaysCollectorManagers!!.size != query.getDims().size) {
      throw IllegalArgumentException(
          "drillSidewaysCollectorManagers size must be equal to number of dimensions in the query.")
    }
    if (executor != null) {
      return searchConcurrently(query, drillDownCollectorManager, drillSidewaysCollectorManagers)
    } else {
      return searchSequentially(query, drillDownCollectorManager, drillSidewaysCollectorManagers)
    }
  }

  /**
   * [search] result. It doesn't depend on [Facets] to allow users to use any type of [CollectorManager] for drill-down or
   * drill-sideways dimension.
   *
   * @param drillDownResult result from drill down (main) [CollectorManager]
   * @param drillSidewaysResults results from drill sideways [CollectorManager]s
   */
  data class Result<T, R>(val drillDownResult: T, val drillSidewaysResults: List<R>?)

  @Suppress("UNCHECKED_CAST")
  private fun <C : Collector, T, K : Collector, R> searchSequentially(query: DrillDownQuery, drillDownCollectorManager: CollectorManager<C, T>, drillSidewaysCollectorManagers: List<CollectorManager<K, R>>?): Result<T, R> {

    val drillDownDims = query.getDims()

    if (drillDownDims.isEmpty()) {
      // There are no drill-down dims, so there is no
      // drill-sideways to compute:
      return Result(searcher.search(query, drillDownCollectorManager) as T, null)
    }

    var baseQuery = query.getBaseQuery()
    if (baseQuery == null) {
      // TODO: we could optimize this pure-browse case by
      // making a custom scorer instead:
      baseQuery = MatchAllDocsQuery()
    }
    val drillDownQueries = query.getDrillDownQueries()

    val dsq = DrillSidewaysQuery(
            baseQuery, drillSidewaysCollectorManagers!!, drillDownQueries, scoreSubDocsAtOnce())

    val collectorResult = searcher.search(dsq, drillDownCollectorManager) as T
    val drillSidewaysResults = ArrayList<R>(drillDownDims.size)
    assert(drillSidewaysCollectorManagers != null
        ) { "Case without drill sideways dimensions is handled above" }
    var numSlices = dsq.managedDrillSidewaysCollectors.size
    for (dim in 0 until drillDownDims.size) {
      val collectorsForDim = ArrayList<K>(numSlices)
      for (slice in 0 until numSlices) {
        collectorsForDim.add(dsq.managedDrillSidewaysCollectors.get(slice).get(dim))
      }
      drillSidewaysResults.add(
          dim, drillSidewaysCollectorManagers!![dim].reduce(collectorsForDim) as R)
    }
    return Result(collectorResult, drillSidewaysResults)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <C : Collector, T, K : Collector, R> searchConcurrently(query: DrillDownQuery, drillDownCollectorManager: CollectorManager<C, T>, drillSidewaysCollectorManagers: List<CollectorManager<K, R>>?): Result<T, R> {

    val drillDownDims = query.getDims()
    val drillDownCallableCollector = CallableCollector(searcher, query, drillDownCollectorManager)
    val drillSidewaysCallableCollectors = ArrayList<Callable<R>>(drillDownDims.size)

    var i = 0
    val filters = query.getDrillDownQueries()
    for (dim in drillDownDims.keys) {
      drillSidewaysCallableCollectors.add(
          CallableCollector(
              searcher,
              getDrillDownQuery(query, filters, dim)!!,
              drillSidewaysCollectorManagers!![i]))
      i++
    }

    try {
      val drillDownFuture = executor!!.submit(drillDownCallableCollector)
      val drillSidewaysFutures = runBlocking { executor.invokeAll(drillSidewaysCallableCollectors) }

      val collectorResult = runBlocking { drillDownFuture.get() }
      val drillSidewaysResults = ArrayList<R>(drillDownDims.size)

      for (i in drillSidewaysFutures.indices) {
        drillSidewaysResults.add(i, runBlocking { drillSidewaysFutures[i].get() })
      }
      return Result(collectorResult, drillSidewaysResults)
    } catch (e: InterruptedException) {
      throw ThreadInterruptedException(e)
    } catch (e: ExecutionException) {
      throw RuntimeException(e)
    }
  }

  /**
   * Result of a concurrent drill sideways search, including the [Facets] and [TopDocs].
   */
  class ConcurrentDrillSidewaysResult<R> /** Sole constructor. */ internal constructor(
    facets: Facets?,
    hits: TopDocs?,
    /** The merged search results */
    val collectorResult: R,
    drillDownFacetsCollector: FacetsCollector?,
    drillSidewaysFacetsCollector: Array<FacetsCollector>?,
    drillSidewaysDims: Array<String>?
  ) : DrillSidewaysResult(facets, hits, drillDownFacetsCollector, drillSidewaysFacetsCollector, drillSidewaysDims)
}
