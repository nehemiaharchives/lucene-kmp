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
package org.gnit.lucenekmp.queryparser.flexible.standard.nodes

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.PointsConfig

/**
 * This query node represents a range query composed by [PointQueryNode] bounds, which means the
 * bound values are [Number]s.
 *
 * @see PointQueryNode
 * @see AbstractRangeQueryNode
 */
class PointRangeQueryNode(
    /**
     * Constructs a [PointRangeQueryNode] object using the given [PointQueryNode] as its bounds and
     * [PointsConfig].
     *
     * @param lower the lower bound
     * @param upper the upper bound
     * @param lowerInclusive \`true\` if the lower bound is inclusive, otherwise, \`false\`
     * @param upperInclusive \`true\` if the upper bound is inclusive, otherwise, \`false\`
     * @param numericConfig the [PointsConfig] that represents associated with the upper and lower
     *     bounds
     * @see setBounds
     */
    lower: PointQueryNode,
    upper: PointQueryNode,
    lowerInclusive: Boolean,
    upperInclusive: Boolean,
    numericConfig: PointsConfig,
) : AbstractRangeQueryNode<PointQueryNode>() {
    var numericConfig: PointsConfig = numericConfig

    init {
        setBounds(lower, upper, lowerInclusive, upperInclusive, numericConfig)
    }

    /**
     * Sets the upper and lower bounds of this range query node and the [PointsConfig] associated
     * with these bounds.
     *
     * @param lower the lower bound
     * @param upper the upper bound
     * @param lowerInclusive \`true\` if the lower bound is inclusive, otherwise, \`false\`
     * @param upperInclusive \`true\` if the upper bound is inclusive, otherwise, \`false\`
     * @param pointsConfig the [PointsConfig] that represents associated with the upper and lower
     *     bounds
     */
    fun setBounds(
        lower: PointQueryNode?,
        upper: PointQueryNode?,
        lowerInclusive: Boolean,
        upperInclusive: Boolean,
        pointsConfig: PointsConfig,
    ) {
        val lowerNumberType = lower?.value?.let { it::class }
        val upperNumberType = upper?.value?.let { it::class }

        if (lowerNumberType != null && lowerNumberType != pointsConfig.type) {
            throw IllegalArgumentException(
                "lower value's type should be the same as numericConfig type: " +
                    lowerNumberType +
                    " != " +
                    pointsConfig.type,
            )
        }

        if (upperNumberType != null && upperNumberType != pointsConfig.type) {
            throw IllegalArgumentException(
                "upper value's type should be the same as numericConfig type: " +
                    upperNumberType +
                    " != " +
                    pointsConfig.type,
            )
        }

        super.setBounds(lower, upper, lowerInclusive, upperInclusive)
        this.numericConfig = pointsConfig
    }

    /**
     * Returns the [PointsConfig] associated with the lower and upper bounds.
     *
     * @return the [PointsConfig] associated with the lower and upper bounds
     */
    fun getPointsConfig(): PointsConfig {
        return numericConfig
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("<pointRange lowerInclusive='")
        sb.append(isLowerInclusive)
        sb.append("' upperInclusive='")
        sb.append(isUpperInclusive)
        sb.append("' type='")
        sb.append(numericConfig.type.simpleName)
        sb.append("'>\n")
        sb.append(lowerBound).append('\n')
        sb.append(upperBound).append('\n')
        sb.append("</pointRange>")
        return sb.toString()
    }

    override fun cloneNode(): QueryNodeImpl {
        return PointRangeQueryNode(
            lowerBound.cloneTree() as PointQueryNode,
            upperBound.cloneTree() as PointQueryNode,
            isLowerInclusive,
            isUpperInclusive,
            numericConfig,
        )
    }
}
