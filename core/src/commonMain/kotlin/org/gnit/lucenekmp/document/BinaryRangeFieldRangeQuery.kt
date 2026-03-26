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

package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.ConstantScoreScorer
import org.gnit.lucenekmp.search.ConstantScoreWeight
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator

abstract class BinaryRangeFieldRangeQuery(
    private val field: String,
    private var queryPackedValue: ByteArray,
    private val numBytesPerDimension: Int,
    private val numDims: Int,
    private val queryType: RangeFieldQuery.QueryType,
) : Query() {
    private val comparator: ByteArrayComparator = ArrayUtil.getUnsignedComparator(numBytesPerDimension)

    init {
        if (queryType != RangeFieldQuery.QueryType.INTERSECTS) {
            throw UnsupportedOperationException(
                "INTERSECTS is the only query type supported for this field type right now",
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (sameClassAs(other).not()) {
            return false
        }
        other as BinaryRangeFieldRangeQuery
        return field == other.field && queryPackedValue.contentEquals(other.queryPackedValue)
    }

    override fun hashCode(): Int {
        var h = classHash()
        h = 31 * h + field.hashCode()
        h = 31 * h + queryPackedValue.contentHashCode()
        return h
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    @Throws(Exception::class)
    override fun rewrite(indexSearcher: IndexSearcher): Query {
        return super.rewrite(indexSearcher)
    }

    @Throws(IOException::class)
    private fun getValues(reader: LeafReader, field: String): BinaryRangeDocValues? {
        if (reader.fieldInfos.fieldInfo(field) == null) {
            return null
        }

        return BinaryRangeDocValues(DocValues.getBinary(reader, field), numDims, numBytesPerDimension)
    }

    @Throws(Exception::class)
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return object : ConstantScoreWeight(this, boost) {
            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val values = getValues(context.reader(), field) ?: return null

                val iterator =
                    object : TwoPhaseIterator(values) {
                        override fun matches(): Boolean {
                            return queryType.matches(
                                queryPackedValue,
                                values.getPackedValue(),
                                numDims,
                                numBytesPerDimension,
                                comparator,
                            )
                        }

                        override fun matchCost(): Float {
                            return queryPackedValue.size.toFloat()
                        }
                    }

                val scorer = ConstantScoreScorer(score(), scoreMode, iterator)
                return DefaultScorerSupplier(scorer)
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return DocValues.isCacheable(ctx, field)
            }
        }
    }
}
