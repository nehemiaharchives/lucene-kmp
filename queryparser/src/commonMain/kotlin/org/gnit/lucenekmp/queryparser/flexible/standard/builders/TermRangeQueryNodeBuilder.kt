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

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.util.StringUtils
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.TermRangeQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.processors.MultiTermRewriteMethodProcessor
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.TermRangeQuery

/** Builds a [TermRangeQuery] object from a [TermRangeQueryNode] object. */
class TermRangeQueryNodeBuilder : StandardQueryBuilder {
    init {
        // empty constructor
    }

    override fun build(queryNode: QueryNode): TermRangeQuery {
        val rangeNode = queryNode as TermRangeQueryNode
        val upper = rangeNode.upperBound
        val lower = rangeNode.lowerBound

        val field = requireNotNull(StringUtils.toString(rangeNode.field))
        var lowerText = lower.getTextAsString()
        var upperText = upper.getTextAsString()

        if (lowerText!!.length == 0) {
            lowerText = null
        }

        if (upperText!!.length == 0) {
            upperText = null
        }

        var method =
            queryNode.getTag(MultiTermRewriteMethodProcessor.TAG_ID) as MultiTermQuery.RewriteMethod?
        if (method == null) {
            method = MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE
        }

        return TermRangeQuery.newStringRange(
            field,
            lowerText,
            upperText,
            rangeNode.isLowerInclusive,
            rangeNode.isUpperInclusive,
            method,
        )
    }
}
