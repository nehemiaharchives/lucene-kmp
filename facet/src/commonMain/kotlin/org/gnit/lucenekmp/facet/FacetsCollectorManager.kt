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

import okio.IOException
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.CollectorManager
import org.gnit.lucenekmp.search.FieldDoc
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MultiCollectorManager
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TopFieldCollector
import org.gnit.lucenekmp.search.TopFieldCollectorManager
import org.gnit.lucenekmp.search.TopScoreDocCollectorManager
import org.gnit.lucenekmp.search.TotalHitCountCollectorManager
import org.gnit.lucenekmp.search.TotalHits
import org.gnit.lucenekmp.util.DocIdSetBuilder

/**
 * A [CollectorManager] implementation which produces FacetsCollector and produces a merged
 * FacetsCollector. This is used for concurrent FacetsCollection.
 */
open class FacetsCollectorManager : CollectorManager<FacetsCollector, FacetsCollector> {
    private val keepScores: Boolean

    /** Sole constructor. */
    constructor() : this(false)

    /**
   * Creates a new collector manager that in turn creates [FacetsCollector] using the provided
   * `keepScores` flag. hits.
   */
    constructor(keepScores: Boolean) {
        this.keepScores = keepScores
    }

    override fun newCollector(): FacetsCollector = FacetsCollector(keepScores)

    override fun reduce(collectors: MutableCollection<FacetsCollector>): FacetsCollector {
        if (collectors.isEmpty()) {
            return FacetsCollector()
        }
        if (collectors.size == 1) {
            return collectors.iterator().next()
        }
        assert(collectors.all { fc -> fc.getKeepScores() == keepScores })
        return ReducedFacetsCollector(collectors, keepScores)
    }

    private class ReducedFacetsCollector(facetsCollectors: Collection<FacetsCollector>, keepScores: Boolean) : FacetsCollector(keepScores) {
        init {
            this.getMatchingDocs().addAll(reduceMatchingDocs(facetsCollectors))
        }
    }

    companion object {
        /**
   * Reduces matching docs held by the provided facets collectors, merging matching docs for the
   * same leaf into a single matching docs instance
   *
   * @param facetsCollectors the facets collectors
   * @return the reduced matching docs, with one instance per leaf reader context
   */
        internal fun reduceMatchingDocs(facetsCollectors: Collection<FacetsCollector>): Collection<FacetsCollector.MatchingDocs> {
            // When a segment is split into partitions, each partition gets its own FacetsCollector that
            // pulls doc_values independently, and builds a bitset of the size of the entire segment. When
            // segments are partitioned, each partition will collect only the docs in its docid range, hence
            // there will be multiple MatchingDocs pointing to the same LeafReaderContext. As part of the
            // reduction we merge back partitions into a single MatchingDocs per segment.
            val matchingDocsMap = HashMap<LeafReaderContext, FacetsCollector.MatchingDocs>()
            for (facetsCollector in facetsCollectors) {
                for (matchingDocs in facetsCollector.getMatchingDocs()) {
                    val existing = matchingDocsMap[matchingDocs.context]
                    matchingDocsMap[matchingDocs.context] = if (existing == null) matchingDocs else merge(existing, matchingDocs)
                }
            }
            return matchingDocsMap.values
        }

        private fun merge(matchingDocs1: FacetsCollector.MatchingDocs, matchingDocs2: FacetsCollector.MatchingDocs): FacetsCollector.MatchingDocs {
            assert(matchingDocs1.context === matchingDocs2.context)
            val scores: FloatArray
            // scores array is null when keepScores is true, and may be null when there are no matches for a
            // segment partition, despite keepScores is true.
            if (matchingDocs1.scores == null && matchingDocs2.scores == null) {
                scores = FloatArray(0)
            } else {
                if (matchingDocs2.scores == null) {
                    scores = matchingDocs1.scores!!
                } else if (matchingDocs1.scores == null) {
                    scores = matchingDocs2.scores
                } else {
                    val length = maxOf(matchingDocs1.scores.size, matchingDocs2.scores.size)
                    // merge the arrays if both have values, their size is bound to the highest collected docid
                    scores = FloatArray(length)
                    for (i in 0 until length) {
                        val firstScore = if (i < matchingDocs1.scores.size) matchingDocs1.scores[i] else 0f
                        val secondScore = if (i < matchingDocs2.scores.size) matchingDocs2.scores[i] else 0f
                        assert(!(firstScore > 0 && secondScore > 0))
                        scores[i] = maxOf(firstScore, secondScore)
                    }
                }
            }
            val docIdSetBuilder = DocIdSetBuilder(matchingDocs1.context.reader().maxDoc())
            try {
                docIdSetBuilder.add(matchingDocs1.bits.iterator()!!)
                docIdSetBuilder.add(matchingDocs2.bits.iterator()!!)
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
            val totalHits = matchingDocs1.totalHits + matchingDocs2.totalHits
            return FacetsCollector.MatchingDocs(matchingDocs1.context, docIdSetBuilder.build(), totalHits, scores)
        }

        /**
   * Utility method, to search and also populate a `FacetsCollector` with hits. The provided
   * `FacetsCollectorManager` will be used for creating/reducing `FacetsCollector`
   * instances.
   */
        fun search(searcher: IndexSearcher, q: Query, n: Int, fcm: FacetsCollectorManager): FacetsResult {
            return doSearch(searcher, null, q, n, null, false, fcm)
        }

        /**
   * Utility method, to search and also populate a `FacetsCollector` with hits. The provided
   * `FacetsCollectorManager` will be used for creating/reducing `FacetsCollector`
   * instances.
   */
        fun search(searcher: IndexSearcher, q: Query, n: Int, sort: Sort?, fcm: FacetsCollectorManager): FacetsResult {
            if (sort == null) {
                throw IllegalArgumentException("sort must not be null")
            }
            return doSearch(searcher, null, q, n, sort, false, fcm)
        }

        /**
   * Utility method, to search and also populate a `FacetsCollector` with hits. The provided
   * `FacetsCollectorManager` will be used for creating/reducing `FacetsCollector`
   * instances.
   */
        fun search(searcher: IndexSearcher, q: Query, n: Int, sort: Sort?, doDocScores: Boolean, fcm: FacetsCollectorManager): FacetsResult {
            if (sort == null) {
                throw IllegalArgumentException("sort must not be null")
            }
            return doSearch(searcher, null, q, n, sort, doDocScores, fcm)
        }

        /**
   * Utility method, to search and also populate a `FacetsCollector` with hits. The provided
   * `FacetsCollectorManager` will be used for creating/reducing `FacetsCollector`
   * instances.
   */
        fun searchAfter(searcher: IndexSearcher, after: ScoreDoc?, q: Query, n: Int, fcm: FacetsCollectorManager): FacetsResult {
            return doSearch(searcher, after, q, n, null, false, fcm)
        }

        /**
   * Utility method, to search and also populate a `FacetsCollector` with hits. The provided
   * `FacetsCollectorManager` will be used for creating/reducing `FacetsCollector`
   * instances.
   */
        fun searchAfter(searcher: IndexSearcher, after: ScoreDoc?, q: Query, n: Int, sort: Sort?, fcm: FacetsCollectorManager): FacetsResult {
            if (sort == null) {
                throw IllegalArgumentException("sort must not be null")
            }
            return doSearch(searcher, after, q, n, sort, false, fcm)
        }

        /**
   * Utility method, to search and also populate a `FacetsCollector` with hits. The provided
   * `FacetsCollectorManager` will be used for creating/reducing `FacetsCollector`
   * instances.
   */
        fun searchAfter(searcher: IndexSearcher, after: ScoreDoc?, q: Query, n: Int, sort: Sort?, doDocScores: Boolean, fcm: FacetsCollectorManager): FacetsResult {
            if (sort == null) {
                throw IllegalArgumentException("sort must not be null")
            }
            return doSearch(searcher, after, q, n, sort, doDocScores, fcm)
        }

        private fun doSearch(searcher: IndexSearcher, after: ScoreDoc?, q: Query, n: Int, sort: Sort?, doDocScores: Boolean, fcm: FacetsCollectorManager): FacetsResult {
            var limit = searcher.indexReader.maxDoc()
            if (limit == 0) {
                limit = 1
            }
            val n = minOf(n, limit)
            if (after != null && after.doc >= limit) {
                throw IllegalArgumentException("after.doc exceeds the number of documents in the reader: after.doc=${after.doc} limit=$limit")
            }
            val topDocs: TopDocs
            val facetsCollector: FacetsCollector
            if (n == 0) {
                val hitCountCollectorManager = TotalHitCountCollectorManager(searcher.slices)
                val multiCollectorManager = MultiCollectorManager(hitCountCollectorManager, fcm)
                val result = searcher.search(q, multiCollectorManager)!!
                topDocs = TopDocs(TotalHits((result[0] as Int).toLong(), TotalHits.Relation.EQUAL_TO), emptyArray())
                facetsCollector = result[1] as FacetsCollector
            } else {
                val multiCollectorManager: MultiCollectorManager
                if (sort != null) {
                    if (after != null && after !is FieldDoc) {
                        // TODO: if we fix type safety of TopFieldDocs we can
                        // remove this
                        throw IllegalArgumentException("after must be a FieldDoc; got $after")
                    }
                    val topFieldCollectorManager = TopFieldCollectorManager(sort, n, after as FieldDoc?, Int.MAX_VALUE)
                    multiCollectorManager = MultiCollectorManager(topFieldCollectorManager, fcm)
                } else {
                    val topScoreDocCollectorManager = TopScoreDocCollectorManager(n, after, Int.MAX_VALUE)
                    multiCollectorManager = MultiCollectorManager(topScoreDocCollectorManager, fcm)
                }
                val result = searcher.search(q, multiCollectorManager)!!
                topDocs = result[0] as TopDocs
                if (doDocScores) {
                    TopFieldCollector.populateScores(topDocs.scoreDocs, searcher, q)
                }
                facetsCollector = result[1] as FacetsCollector
            }
            return FacetsResult(topDocs, facetsCollector)
        }
    }

    /**
   * Holds results of a search run via static utility methods exposed by this class. Those include
   * [TopDocs] as well as facets result included in the returned [FacetsCollector]
   *
   * @param topDocs the top docs
   * @param facetsCollector the facets result included in a [FacetsCollector] instance
   */
    data class FacetsResult(val topDocs: TopDocs, val facetsCollector: FacetsCollector)
}
