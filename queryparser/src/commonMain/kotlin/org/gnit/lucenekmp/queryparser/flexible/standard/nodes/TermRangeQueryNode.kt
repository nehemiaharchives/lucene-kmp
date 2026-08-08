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

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl

/**
 * This query node represents a range query composed by [FieldQueryNode] bounds, which means the
 * bound values are strings.
 *
 * @see FieldQueryNode
 * @see AbstractRangeQueryNode
 */
class TermRangeQueryNode(
    /**
     * Constructs a [TermRangeQueryNode] object using the given [FieldQueryNode] as its bounds.
     *
     * @param lower the lower bound
     * @param upper the upper bound
     * @param lowerInclusive `true` if the lower bound is inclusive, otherwise, `false`
     * @param upperInclusive `true` if the upper bound is inclusive, otherwise, `false`
     */
    lower: FieldQueryNode,
    upper: FieldQueryNode,
    lowerInclusive: Boolean,
    upperInclusive: Boolean,
) : AbstractRangeQueryNode<FieldQueryNode>() {
    init {
        setBounds(lower, upper, lowerInclusive, upperInclusive)
    }

    override fun cloneNode(): QueryNodeImpl {
        return TermRangeQueryNode(
            lowerBound.cloneTree(),
            upperBound.cloneTree(),
            isLowerInclusive,
            isUpperInclusive,
        )
    }
}
