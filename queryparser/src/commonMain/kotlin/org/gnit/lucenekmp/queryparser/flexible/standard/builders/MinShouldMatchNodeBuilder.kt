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

import org.gnit.lucenekmp.queryparser.flexible.core.builders.QueryBuilder
import org.gnit.lucenekmp.queryparser.flexible.core.builders.QueryTreeBuilder
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.MinShouldMatchNode
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.Query

/** Builds a [BooleanQuery] from a [MinShouldMatchNode]. */
class MinShouldMatchNodeBuilder : QueryBuilder {
    override fun build(queryNode: QueryNode): Query {
        val mmNode = queryNode as MinShouldMatchNode

        val children = requireNotNull(queryNode.children)
        if (children.size != 1) {
            throw RuntimeException("Unexpected number of node children: " + children.size)
        }

        val q = mmNode.groupQueryNode.getTag(QueryTreeBuilder.QUERY_TREE_BUILDER_TAGID) as Query

        val booleanQuery = q as BooleanQuery
        val builder = BooleanQuery.Builder()
        builder.setMinimumNumberShouldMatch(mmNode.minShouldMatch)
        booleanQuery.clauses().forEach { builder.add(it) }
        return builder.build()
    }
}
