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

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.queryparser.flexible.core.builders.QueryTreeBuilder
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.MultiPhraseQueryNode
import org.gnit.lucenekmp.search.MultiPhraseQuery
import org.gnit.lucenekmp.search.TermQuery

/** Builds a [MultiPhraseQuery] object from a [MultiPhraseQueryNode] object. */
class MultiPhraseQueryNodeBuilder : StandardQueryBuilder {
    init {
        // empty constructor
    }

    override fun build(queryNode: QueryNode): MultiPhraseQuery {
        val phraseNode = queryNode as MultiPhraseQueryNode

        val phraseQueryBuilder = MultiPhraseQuery.Builder()

        val children = phraseNode.children

        if (children != null) {
            val positionTermMap = TreeMap<Int, MutableList<Term>>()

            for (child in children) {
                val termNode = child as FieldQueryNode
                val termQuery =
                    termNode.getTag(QueryTreeBuilder.QUERY_TREE_BUILDER_TAGID) as TermQuery
                var termList = positionTermMap[termNode.positionIncrement]

                if (termList == null) {
                    termList = mutableListOf()
                    positionTermMap[termNode.positionIncrement] = termList
                }

                termList.add(termQuery.getTerm())
            }

            for (entry in positionTermMap.entries) {
                val termList = entry.value
                phraseQueryBuilder.add(termList.toTypedArray(), entry.key)
            }
        }

        return phraseQueryBuilder.build()
    }
}
