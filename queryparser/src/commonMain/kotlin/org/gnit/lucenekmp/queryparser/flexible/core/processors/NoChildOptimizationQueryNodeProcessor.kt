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
package org.gnit.lucenekmp.queryparser.flexible.core.processors

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BoostQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.DeletedQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.MatchNoDocsQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.TokenizedPhraseQueryNode

/**
 * A [NoChildOptimizationQueryNodeProcessor] removes every BooleanQueryNode, BoostQueryNode,
 * TokenizedPhraseQueryNode or ModifierQueryNode that do not have a valid children.
 *
 * Example: When the children of these nodes are removed for any reason then the nodes may become
 * invalid.
 */
class NoChildOptimizationQueryNodeProcessor : QueryNodeProcessorImpl() {
    init {
        // empty constructor
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is BooleanQueryNode ||
            node is BoostQueryNode ||
            node is TokenizedPhraseQueryNode ||
            node is ModifierQueryNode
        ) {
            val children = node.children

            if (children != null && children.size > 0) {
                for (child in children) {
                    if (child !is DeletedQueryNode) {
                        return node
                    }
                }
            }

            return MatchNoDocsQueryNode()
        }

        return node
    }

    override fun preProcessNode(node: QueryNode): QueryNode {
        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {
        return children
    }
}
