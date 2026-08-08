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
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.IndexSearcher.TooManyClauses
import org.gnit.lucenekmp.search.Query

/**
 * Builds a [BooleanQuery] object from a [BooleanQueryNode] object. Every children in the
 * [BooleanQueryNode] object must be already tagged using [QueryTreeBuilder.QUERY_TREE_BUILDER_TAGID]
 * with a [Query] object. <br> <br> It takes in consideration if the children is a
 * [ModifierQueryNode] to define the [BooleanClause].
 */
class BooleanQueryNodeBuilder : StandardQueryBuilder {
    init {
        // empty constructor
    }

    override fun build(queryNode: QueryNode): BooleanQuery {
        val booleanNode = queryNode as BooleanQueryNode

        val bQuery = BooleanQuery.Builder()
        val children = booleanNode.children

        if (children != null) {
            for (child in children) {
                val obj = child.getTag(QueryTreeBuilder.QUERY_TREE_BUILDER_TAGID)

                if (obj != null) {
                    val query = obj as Query

                    try {
                        bQuery.add(query, getModifierValue(child))
                    } catch (ex: TooManyClauses) {
                        throw QueryNodeException(
                            MessageImpl(
                                QueryParserMessages.TOO_MANY_BOOLEAN_CLAUSES,
                                IndexSearcher.maxClauseCount,
                                queryNode.toQueryString(EscapeQuerySyntaxImpl()),
                            ),
                            ex,
                        )
                    }
                }
            }
        }

        return bQuery.build()
    }

    companion object {
        private fun getModifierValue(node: QueryNode): BooleanClause.Occur {
            if (node is ModifierQueryNode) {
                val mNode = node
                when (mNode.modifier) {
                    ModifierQueryNode.Modifier.MOD_REQ -> return BooleanClause.Occur.MUST
                    ModifierQueryNode.Modifier.MOD_NOT -> return BooleanClause.Occur.MUST_NOT
                    ModifierQueryNode.Modifier.MOD_NONE -> return BooleanClause.Occur.SHOULD
                }
            }

            return BooleanClause.Occur.SHOULD
        }
    }
}
