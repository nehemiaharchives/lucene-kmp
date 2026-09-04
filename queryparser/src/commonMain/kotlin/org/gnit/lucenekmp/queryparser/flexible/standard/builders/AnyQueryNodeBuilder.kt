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

import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.core.builders.QueryTreeBuilder
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.AnyQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.IndexSearcher.TooManyClauses
import org.gnit.lucenekmp.search.Query

/** Builds a BooleanQuery of SHOULD clauses, possibly with some minimum number to match. */
class AnyQueryNodeBuilder : StandardQueryBuilder {
    init {
        // empty constructor
    }

    override fun build(queryNode: QueryNode): BooleanQuery {
        val andNode = queryNode as AnyQueryNode

        val bQuery = BooleanQuery.Builder()
        val children = andNode.children

        if (children != null) {
            for (child in children) {
                val obj = child.getTag(QueryTreeBuilder.QUERY_TREE_BUILDER_TAGID)

                if (obj != null) {
                    val query = obj as Query

                    try {
                        bQuery.add(query, BooleanClause.Occur.SHOULD)
                    } catch (ex: TooManyClauses) {
                        throw QueryNodeException(
                            MessageImpl(/*
                             * IQQQ.Q0028E_TOO_MANY_BOOLEAN_CLAUSES,
                             * BooleanQuery.getMaxClauseCount()
                             */ QueryParserMessages.EMPTY_MESSAGE),
                            ex,
                        )
                    }
                }
            }
        }

        bQuery.setMinimumNumberShouldMatch(andNode.minimumMatchingElements)

        return bQuery.build()
    }
}
