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

package org.gnit.lucenekmp.queries.intervals

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.FilterMatchesIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Matches
import org.gnit.lucenekmp.search.MatchesUtils
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.IOSupplier

/**
 * A query that retrieves documents containing intervals returned from an [IntervalsSource]
 *
 * <p>Static constructor functions for various different sources can be found in the
 * [Intervals] class
 *
 * <p>Scores for this query are computed as a function of the sloppy frequency of intervals
 * appearing in a particular document. Sloppy frequency is calculated from the number of matching
 * intervals, and their width, with wider intervals contributing lower values. The scores can be
 * adjusted with two optional parameters:
 *
 * <ul>
 *   <li>pivot - the sloppy frequency value at which the overall score of the document will equal
 *       0.5. The default value is 1
 *   <li>exp - higher values of this parameter make the function grow more slowly below the pivot
 *       and faster higher than the pivot. The default value is 1
 * </ul>
 *
 * Optimal values for both pivot and exp depend on the type of queries and corpus of documents being
 * queried.
 *
 * <p>Scores are bounded to between 0 and 1. For higher contributions, wrap the query in a
 * [org.gnit.lucenekmp.search.BoostQuery]
 */
class IntervalQuery private constructor(
    /** The field to query */
    val field: String,
    private val intervalsSource: IntervalsSource,
    private val scoreFunction: IntervalScoreFunction
) : Query() {

    /**
     * Create a new IntervalQuery
     *
     * @param field the field to query
     * @param intervalsSource an [IntervalsSource] to retrieve intervals from
     */
    constructor(field: String, intervalsSource: IntervalsSource) :
        this(field, intervalsSource, IntervalScoreFunction.saturationFunction(1f))

    /**
     * Create a new IntervalQuery with a scoring pivot
     *
     * @param field the field to query
     * @param intervalsSource an [IntervalsSource] to retrieve intervals from
     * @param pivot the sloppy frequency value at which the score will be 0.5, must be within (0,
     *     +Infinity)
     */
    constructor(field: String, intervalsSource: IntervalsSource, pivot: Float) :
        this(field, intervalsSource, IntervalScoreFunction.saturationFunction(pivot))

    /**
     * Create a new IntervalQuery with a scoring pivot and exponent
     *
     * @param field the field to query
     * @param intervalsSource an [IntervalsSource] to retrieve intervals from
     * @param pivot the sloppy frequency value at which the score will be 0.5, must be within (0,
     *     +Infinity)
     * @param exp exponent, higher values make the function grow slower before 'pivot' and faster
     *     after 'pivot', must be in (0, +Infinity)
     */
    constructor(field: String, intervalsSource: IntervalsSource, pivot: Float, exp: Float) :
        this(field, intervalsSource, IntervalScoreFunction.sigmoidFunction(pivot, exp))

    override fun toString(field: String?): String {
        return (if (this.field != field) this.field + ":" else "") + intervalsSource.toString()
    }

    @Throws(IOException::class)
    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        return IntervalWeight(this, boost)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            intervalsSource.visit(field, visitor)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntervalQuery) return false
        return field == other.field && intervalsSource == other.intervalsSource
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + field.hashCode()
        result = 31 * result + intervalsSource.hashCode()
        return result
    }

    private inner class IntervalWeight(
        query: Query,
        val boost: Float
    ) : Weight(query) {

        @Throws(IOException::class)
        override fun explain(context: LeafReaderContext, doc: Int): Explanation {
            val scorer = scorer(context) as IntervalScorer?
            if (scorer != null) {
                val newDoc = scorer.iterator().advance(doc)
                if (newDoc == doc) {
                    val freq = scorer.freq()
                    return scoreFunction.explain(this@IntervalQuery.toString(), boost, freq)
                }
            }
            return Explanation.noMatch("no matching intervals")
        }

        @Throws(IOException::class)
        override fun matches(context: LeafReaderContext, doc: Int): Matches? {
            return MatchesUtils.forField(
                field,
                IOSupplier {
                    val mi = intervalsSource.matches(field, context, doc)
                    if (mi == null) {
                        null
                    } else {
                        object : FilterMatchesIterator(mi) {
                            override val query: Query
                                get() =
                                    IntervalQuery(
                                        this@IntervalQuery.field,
                                        this@IntervalQuery.intervalsSource
                                    )
                        }
                    }
                }
            )
        }

        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            val intervals = intervalsSource.intervals(field, context)
            if (intervals == null) {
                return null
            }
            val scorer =
                IntervalScorer(intervals, intervalsSource.minExtent(), boost, scoreFunction)
            return DefaultScorerSupplier(scorer)
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return true
        }
    }
}
