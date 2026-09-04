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
package org.gnit.lucenekmp.queryparser.flexible.standard.builders

import org.gnit.lucenekmp.document.DoublePoint
import org.gnit.lucenekmp.document.FloatPoint
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.util.StringUtils
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PointRangeQueryNode
import org.gnit.lucenekmp.search.Query

/**
 * Builds [PointValues] range queries out of [PointRangeQueryNode]s.
 *
 * @see PointRangeQueryNode
 */
class PointRangeQueryNodeBuilder : StandardQueryBuilder {
    /** Constructs a [PointRangeQueryNodeBuilder] object. */
    init {
        // empty constructor
    }

    override fun build(queryNode: QueryNode): Query {
        val numericRangeNode = queryNode as PointRangeQueryNode

        val lowerNumericNode = numericRangeNode.lowerBound
        val upperNumericNode = numericRangeNode.upperBound

        val lowerNumber = lowerNumericNode.value
        val upperNumber = upperNumericNode.value

        val pointsConfig = numericRangeNode.getPointsConfig()
        val numberType = pointsConfig.type
        val field = requireNotNull(StringUtils.toString(numericRangeNode.field))
        val minInclusive = numericRangeNode.isLowerInclusive
        val maxInclusive = numericRangeNode.isUpperInclusive

        // TODO: push down cleaning up of crazy nulls and inclusive/exclusive elsewhere
        if (Int::class == numberType) {
            var lower = lowerNumber as Int?
            if (lower == null) {
                lower = Int.MIN_VALUE
            }
            if (minInclusive == false) {
                lower += 1
            }

            var upper = upperNumber as Int?
            if (upper == null) {
                upper = Int.MAX_VALUE
            }
            if (maxInclusive == false) {
                upper -= 1
            }
            return IntPoint.newRangeQuery(field, lower, upper)
        } else if (Long::class == numberType) {
            var lower = lowerNumber as Long?
            if (lower == null) {
                lower = Long.MIN_VALUE
            }
            if (minInclusive == false) {
                lower += 1
            }

            var upper = upperNumber as Long?
            if (upper == null) {
                upper = Long.MAX_VALUE
            }
            if (maxInclusive == false) {
                upper -= 1
            }
            return LongPoint.newRangeQuery(field, lower, upper)
        } else if (Float::class == numberType) {
            var lower = lowerNumber as Float?
            if (lower == null) {
                lower = Float.NEGATIVE_INFINITY
            }
            if (minInclusive == false) {
                lower = Math.nextUp(lower)
            }

            var upper = upperNumber as Float?
            if (upper == null) {
                upper = Float.POSITIVE_INFINITY
            }
            if (maxInclusive == false) {
                upper = Math.nextDown(upper)
            }
            return FloatPoint.newRangeQuery(field, lower, upper)
        } else if (Double::class == numberType) {
            var lower = lowerNumber as Double?
            if (lower == null) {
                lower = Double.NEGATIVE_INFINITY
            }
            if (minInclusive == false) {
                lower = Math.nextUp(lower)
            }

            var upper = upperNumber as Double?
            if (upper == null) {
                upper = Double.POSITIVE_INFINITY
            }
            if (maxInclusive == false) {
                upper = Math.nextDown(upper)
            }
            return DoublePoint.newRangeQuery(field, lower, upper)
        } else {
            throw QueryNodeException(
                MessageImpl(QueryParserMessages.UNSUPPORTED_NUMERIC_DATA_TYPE, numberType),
            )
        }
    }
}
