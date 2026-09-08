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
import org.gnit.lucenekmp.search.Collector
import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.SimpleCollector
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.jdkport.assert

/**
 * Collects hits for subsequent faceting. Once you've run a search and collect hits into this,
 * instantiate one of the [Facets] subclasses to do the facet counting. Use the `search`
 * utility methods to perform an "ordinary" search but also collect into a [Collector].
 */
open class FacetsCollector : SimpleCollector {
    // Kotlin Collector exposes the upstream default setWeight method as an abstract property.
    override var weight: org.gnit.lucenekmp.search.Weight? = null

    private var context: LeafReaderContext? = null
    final override var scorer: Scorable? = null
    private var totalHits = 0
    private var scores: FloatArray? = null
    private val keepScores: Boolean
    private val matchingDocs = mutableListOf<MatchingDocs>()
    private var docsBuilder: DocIdSetBuilder? = null

    /**
   * Holds the documents that were matched in the [LeafReaderContext]. If scores were
   * required, then `scores` is not null.
   *
   * @param context Context for this segment.
   * @param bits Which documents were seen.
   * @param scores Non-sparse scores array.
   * @param totalHits Total number of hits
   */
    data class MatchingDocs(val context: LeafReaderContext, val bits: DocIdSet, val totalHits: Int, val scores: FloatArray?)

    /** Default constructor */
    constructor() : this(false)

    /**
   * Create this; if `keepScores` is true then a float[] is allocated to hold score of all
   * hits.
   */
    constructor(keepScores: Boolean) {
        this.keepScores = keepScores
    }

    /** True if scores were saved. */
    fun getKeepScores(): Boolean = keepScores

    /** Returns the documents matched by the query, one [MatchingDocs] per visited segment. */
    open fun getMatchingDocs(): MutableList<MatchingDocs> = matchingDocs

    override fun collect(doc: Int) {
        docsBuilder!!.grow(1).add(doc)
        if (keepScores) {
            if (doc >= scores!!.size) {
                val newScores = FloatArray(ArrayUtil.oversize(doc + 1, 4))
                scores!!.copyInto(newScores, 0, 0, scores!!.size)
                scores = newScores
            }
            scores!![doc] = scorer!!.score()
        }
        totalHits++
    }

    override fun scoreMode(): ScoreMode = if (keepScores) ScoreMode.COMPLETE else ScoreMode.COMPLETE_NO_SCORES

    override fun doSetNextReader(context: LeafReaderContext) {
        assert(docsBuilder == null)
        docsBuilder = DocIdSetBuilder(context.reader().maxDoc())
        totalHits = 0
        if (keepScores) {
            scores = FloatArray(64) // some initial size
        }
        this.context = context
    }

    override fun finish() {
        val bits: DocIdSet
        if (docsBuilder != null) {
            bits = docsBuilder!!.build()
            docsBuilder = null
        } else {
            bits = DocIdSet.EMPTY
        }
        matchingDocs.add(MatchingDocs(this.context!!, bits, totalHits, scores))
        scores = null
        context = null
    }
}
