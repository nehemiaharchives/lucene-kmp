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

import org.gnit.lucenekmp.queryparser.flexible.core.builders.QueryTreeBuilder
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.TokenizedPhraseQueryNode
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery

/** Builds a [PhraseQuery] object from a [TokenizedPhraseQueryNode] object. */
class PhraseQueryNodeBuilder : StandardQueryBuilder {
    init {
        // empty constructor
    }

    override fun build(queryNode: QueryNode): Query {
        val phraseNode = queryNode as TokenizedPhraseQueryNode

        val builder = PhraseQuery.Builder()

        val children = phraseNode.children

        if (children != null) {
            for (child in children) {
                val termQuery = child.getTag(QueryTreeBuilder.QUERY_TREE_BUILDER_TAGID) as TermQuery
                val termNode = child as FieldQueryNode

                builder.add(termQuery.getTerm(), termNode.positionIncrement)
            }
        }

        return builder.build()
    }
}
