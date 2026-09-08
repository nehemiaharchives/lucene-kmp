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

import org.gnit.lucenekmp.index.ReaderUtil
import org.gnit.lucenekmp.search.ConjunctionUtils
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreMode

/**
 * Base class for facet counts. It allows for a query to be passed in to filter the match set.
 *
 * @lucene.experimental
 */
abstract class FacetCountsWithFilterQuery : Facets {
    /**
   * Optional: if specified, we first test this Query to see whether the document should be checked
   * for matching ranges. If this is null, all documents are checked.
   */
    protected val fastMatchQuery: Query?

    /** Create `FacetCounts` */
    protected constructor(fastMatchQuery: Query?) {
        this.fastMatchQuery = fastMatchQuery
    }

    /**
   * Create a [DocIdSetIterator] from the provided `hits` that relies on `* fastMatchQuery` if available for first-pass filtering. If `iterators` is not empty then
   * all iterators are intersected. If any of the iterators is null, it indicates no documents will
   * be matched by it, and therefore no documents will be matched overall. A null response indicates
   * no documents will match.
   */
    protected fun createIterator(hits: FacetsCollector.MatchingDocs, vararg iterators: DocIdSetIterator?): DocIdSetIterator? {
        val allIterators = mutableListOf<DocIdSetIterator?>()
        allIterators.add(hits.bits.iterator())
        allIterators.addAll(iterators)
        if (allIterators.any { it == null }) {
            // if any of the iterators are null, there are no matching docs
            return null
        }
        if (fastMatchQuery != null) {
            val topLevelContext = ReaderUtil.getTopLevelContext(hits.context)
            val searcher = IndexSearcher(topLevelContext)
            searcher.queryCache = null
            val fastMatchWeight = searcher.createWeight(searcher.rewrite(fastMatchQuery), ScoreMode.COMPLETE_NO_SCORES, 1f)
            val s = fastMatchWeight.scorer(hits.context)
            if (s == null) {
                // no matching docs by the fast match query
                return null
            } else {
                val fastMatchQueryIterator = s.iterator()
                allIterators.add(fastMatchQueryIterator)
            }
        }
        if (allIterators.size == 1) {
            return allIterators[0]
        } else {
            return ConjunctionUtils.intersectIterators(allIterators.filterNotNull().toMutableList())
        }
    }
}
