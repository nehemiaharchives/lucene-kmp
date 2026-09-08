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

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.internal.hppc.IntHashSet
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.assert

/**
 * A [Query] for drill-down over facet categories. You should call [add] for every group of categories you want to drill-down over.
 *
 * **NOTE:** if you choose to create your own [Query] by calling [term], it is
 * recommended to wrap it in a [BoostQuery] with a boost of `0.0f`, so that it does not
 * affect the scores of the documents.
 *
 * @lucene.experimental
 */
class DrillDownQuery : Query {
    companion object {
        /** Creates a drill-down term. */
        fun term(field: String, dim: String, vararg path: String): Term = Term(field, FacetsConfig.pathToString(dim, *path))
    }

    private val config: FacetsConfig
    private val baseQuery: Query?
    private val dimQueries = mutableListOf<BooleanQuery.Builder>()
    private val drillDownDims = LinkedHashMap<String, Int>()
    private val builtDimQueries = mutableListOf<Query?>()
    private val dirtyDimQueryIndex = IntHashSet()

    /** Used by clone() and DrillSideways */
    internal constructor(config: FacetsConfig, baseQuery: Query?, dimQueries: List<BooleanQuery.Builder>, drillDownDims: Map<String, Int>) {
        this.baseQuery = baseQuery
        this.dimQueries.addAll(dimQueries)
        this.drillDownDims.putAll(drillDownDims)
        this.config = config
        for (i in this.dimQueries.indices) {
            this.builtDimQueries.add(null)
            this.dirtyDimQueryIndex.add(i)
        }
    }

    /** Used by DrillSideways */
    internal constructor(config: FacetsConfig, filter: Query, other: DrillDownQuery) {
        this.baseQuery = BooleanQuery.Builder()
            .add(other.baseQuery ?: MatchAllDocsQuery(), Occur.MUST)
            .add(filter, Occur.FILTER).build()
        this.dimQueries.addAll(other.dimQueries)
        this.drillDownDims.putAll(other.drillDownDims)
        this.config = config
        for (i in this.dimQueries.indices) {
            this.builtDimQueries.add(null)
            this.dirtyDimQueryIndex.add(i)
        }
    }

    /**
   * Creates a new `DrillDownQuery` without a base query, to perform a pure browsing query
   * (equivalent to using [MatchAllDocsQuery] as base).
   */
    constructor(config: FacetsConfig) : this(config, null)

    /**
   * Creates a new `DrillDownQuery` over the given base query. Can be `null`, in which
   * case the result [Query] from [Query.rewrite] will be a pure browsing
   * query, filtering on the added categories only.
   */
    constructor(config: FacetsConfig, baseQuery: Query?) {
        this.baseQuery = baseQuery
        this.config = config
    }

    /**
   * Adds one dimension of drill downs; if you pass the same dimension more than once it is OR'd
   * with the previous constraints on that dimension, and all dimensions are AND'd against each
   * other and the base query.
   */
    fun add(dim: String, vararg path: String) {
        val indexedField = config.getDimConfig(dim).indexFieldName
        add(dim, TermQuery(term(indexedField, dim, *path)))
    }

    /**
   * Expert: add a custom drill-down subQuery. Use this when you have a separate way to drill-down
   * on the dimension than the indexed facet ordinals.
   */
    fun add(dim: String, subQuery: Query) {
        assert(dimQueries.size == builtDimQueries.size)
        assert(drillDownDims.size == dimQueries.size)
        if (!drillDownDims.containsKey(dim)) {
            drillDownDims[dim] = drillDownDims.size
            val builder = BooleanQuery.Builder()
            dimQueries.add(builder)
            builtDimQueries.add(null)
        }
        val index = drillDownDims[dim]!!
        dimQueries[index].add(subQuery, Occur.SHOULD)
        dirtyDimQueryIndex.add(index)
    }

    fun clone(): DrillDownQuery = DrillDownQuery(config, baseQuery, dimQueries, drillDownDims)

    override fun hashCode(): Int = classHash() + Objects.hash(baseQuery, dimQueries)

    override fun equals(other: Any?): Boolean = sameClassAs(other) && equalsTo(other as DrillDownQuery)

    private fun equalsTo(other: DrillDownQuery): Boolean = baseQuery == other.baseQuery && dimQueries == other.dimQueries

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewritten = getBooleanQuery()
        if (rewritten.clauses().isEmpty()) {
            return MatchAllDocsQuery()
        }
        return rewritten
    }

    override fun toString(field: String?): String = getBooleanQuery().toString(field)

    override fun visit(visitor: QueryVisitor) {
        visitor.visitLeaf(this)
    }

    private fun getBooleanQuery(): BooleanQuery {
        val bq = BooleanQuery.Builder()
        if (baseQuery != null) {
            bq.add(baseQuery, Occur.MUST)
        }
        for (query in getDrillDownQueries()) {
            bq.add(query, Occur.FILTER)
        }
        return bq.build()
    }

    /**
   * Returns the internal baseQuery of the DrillDownQuery
   *
   * @return The baseQuery used on initialization of DrillDownQuery
   */
    fun getBaseQuery(): Query? = baseQuery

    /**
   * Returns the dimension queries added either via [add] or [add]
   *
   * @return The array of dimQueries
   */
    fun getDrillDownQueries(): Array<Query> {
        for (dirtyDimIndex in dirtyDimQueryIndex) {
            builtDimQueries[dirtyDimIndex!!.value] = this.dimQueries[dirtyDimIndex!!.value].build()
        }
        dirtyDimQueryIndex.clear()
        return Array(builtDimQueries.size) { builtDimQueries[it]!! }
    }

    internal fun getDims(): Map<String, Int> = drillDownDims
}
