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
package org.gnit.lucenekmp.facet.range

import org.gnit.lucenekmp.facet.MultiLongValuesSource
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.*
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.math.nextUp
import kotlin.math.nextDown

/**
 * Represents a range over long values.
 *
 * @lucene.experimental
 */
class LongRange : Range {
    /** Minimum (inclusive). */
    val min: Long
    /** Maximum (inclusive). */
    val max: Long
    // TODO: can we require fewer args? (same for
    // Double/FloatRange too)
    /** Create a LongRange. */
    constructor(label: String, minIn: Long, minInclusive: Boolean, maxIn: Long, maxInclusive: Boolean) : super(label) {
        var minIn = minIn
        var maxIn = maxIn
        if (!minInclusive) {
            if (minIn != Long.MAX_VALUE) minIn++ else failNoMatch()
        }
        if (!maxInclusive) {
            if (maxIn != Long.MIN_VALUE) maxIn-- else failNoMatch()
        }
        if (minIn > maxIn) failNoMatch()
        min = minIn
        max = maxIn
    }
    /** True if this range accepts the provided value. */
    fun accept(value: Long): Boolean = value >= min && value <= max
    override fun toString(): String = "LongRange($label: $min to $max)"
    override fun equals(other: Any?): Boolean {
        if (other !is LongRange) return false
        return other.label == label && other.min == min && other.max == max
    }
    override fun hashCode(): Int = 31 * (31 * (31 + label.hashCode()) + min.hashCode()) + max.hashCode()

    private class ValueSourceQuery(private val range: LongRange, private val fastMatchQuery: Query?, private val valueSource: LongValuesSource) : Query() {
        override fun equals(other: Any?): Boolean = sameClassAs(other) && equalsTo(other as ValueSourceQuery)
        private fun equalsTo(other: ValueSourceQuery): Boolean = range == other.range && fastMatchQuery == other.fastMatchQuery && valueSource == other.valueSource
        override fun hashCode(): Int = classHash() + 31 * (31 * (31 * (31 + range.hashCode()) + (fastMatchQuery?.hashCode() ?: 0)) + valueSource.hashCode())
        override fun toString(field: String?): String = "Filter($range)"
        override fun visit(visitor: QueryVisitor) { visitor.visitLeaf(this) }
        override fun rewrite(indexSearcher: IndexSearcher): Query {
            if (fastMatchQuery != null) {
                val fastMatchRewritten = fastMatchQuery.rewrite(indexSearcher)
                if (fastMatchRewritten !== fastMatchQuery) return ValueSourceQuery(range, fastMatchRewritten, valueSource)
            }
            return super.rewrite(indexSearcher)
        }
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            val fastMatchWeight = if (fastMatchQuery == null) null else searcher.createWeight(fastMatchQuery, ScoreMode.COMPLETE_NO_SCORES, 1f)
            return object : ConstantScoreWeight(this, boost) {
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                    val maxDoc = context.reader().maxDoc()
                    val approximation: DocIdSetIterator
                    if (fastMatchWeight == null) {
                        approximation = DocIdSetIterator.all(maxDoc)
                    } else {
                        val s = fastMatchWeight.scorer(context)
                        if (s == null) return null
                        approximation = s.iterator()
                    }
                    val values = valueSource.getValues(context, null)
                    val twoPhase = object : TwoPhaseIterator(approximation) {
                        override fun matches(): Boolean {
                            return values.advanceExact(approximation.docID()) && range.accept(values.longValue())
                        }
                        override fun matchCost(): Float = 100f // TODO: use cost of range.accept()
                    }
                    val scorer = ConstantScoreScorer(score(), scoreMode, twoPhase)
                    return DefaultScorerSupplier(scorer)
                }
                override fun isCacheable(ctx: LeafReaderContext): Boolean = valueSource.isCacheable(ctx)
            }
        }
    }

    private class MultiValueSourceQuery(private val range: LongRange, private val fastMatchQuery: Query?, private val valuesSource: MultiLongValuesSource) : Query() {
        override fun equals(other: Any?): Boolean = sameClassAs(other) && equalsTo(other as MultiValueSourceQuery)
        private fun equalsTo(other: MultiValueSourceQuery): Boolean = range == other.range && fastMatchQuery == other.fastMatchQuery && valuesSource == other.valuesSource
        override fun hashCode(): Int = classHash() + 31 * (31 * (31 * (31 + range.hashCode()) + (fastMatchQuery?.hashCode() ?: 0)) + valuesSource.hashCode())
        override fun toString(field: String?): String = "Filter($range)"
        override fun visit(visitor: QueryVisitor) { visitor.visitLeaf(this) }
        override fun rewrite(indexSearcher: IndexSearcher): Query {
            if (fastMatchQuery != null) {
                val fastMatchRewritten = fastMatchQuery.rewrite(indexSearcher)
                if (fastMatchRewritten !== fastMatchQuery) return MultiValueSourceQuery(range, fastMatchRewritten, valuesSource)
            }
            return super.rewrite(indexSearcher)
        }
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            val fastMatchWeight = if (fastMatchQuery == null) null else searcher.createWeight(fastMatchQuery, ScoreMode.COMPLETE_NO_SCORES, 1f)
            return object : ConstantScoreWeight(this, boost) {
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                    val maxDoc = context.reader().maxDoc()
                    val approximation: DocIdSetIterator
                    if (fastMatchWeight == null) {
                        approximation = DocIdSetIterator.all(maxDoc)
                    } else {
                        val s = fastMatchWeight.scorer(context)
                        if (s == null) return null
                        approximation = s.iterator()
                    }
                    val values = valuesSource.getValues(context)
                    val twoPhase = object : TwoPhaseIterator(approximation) {
                        override fun matches(): Boolean {
                            if (values.advanceExact(approximation.docID()) == false) return false
                            var i = 0
                            while (i < values.getValueCount()) {
                                if (range.accept(values.nextValue())) return true
                                i++
                            }
                            return false
                        }
                        override fun matchCost(): Float = 100f // TODO: use cost of range.accept()
                    }
                    val scorer = ConstantScoreScorer(score(), scoreMode, twoPhase)
                    return DefaultScorerSupplier(scorer)
                }
                override fun isCacheable(ctx: LeafReaderContext): Boolean = valuesSource.isCacheable(ctx)
            }
        }
    }

    /**
   * Create a Query that matches documents in this range
   *
   * The query will check all documents that match the provided match query, or every document in
   * the index if the match query is null.
   *
   * If the value source is static, eg an indexed numeric field, it may be faster to use [org.gnit.lucenekmp.search.PointRangeQuery]
   *
   * @param fastMatchQuery a query to use as a filter
   * @param valueSource the source of values for the range check
   */
    fun getQuery(fastMatchQuery: Query?, valueSource: LongValuesSource): Query {
        return ValueSourceQuery(this, fastMatchQuery, valueSource)
    }

    /**
   * Create a Query that matches documents in this range
   *
   * The query will check all documents that match the provided match query, or every document in
   * the index if the match query is null.
   *
   * If the value source is static, eg an indexed numeric field, it may be faster to use [org.gnit.lucenekmp.search.PointRangeQuery]
   *
   * @param fastMatchQuery a query to use as a filter
   * @param valuesSource the source of values for the range check
   */
    fun getQuery(fastMatchQuery: Query?, valuesSource: MultiLongValuesSource): Query {
        val singleValues = MultiLongValuesSource.unwrapSingleton(valuesSource)
        if (singleValues != null) return ValueSourceQuery(this, fastMatchQuery, singleValues)
        else return MultiValueSourceQuery(this, fastMatchQuery, valuesSource)
    }
}
