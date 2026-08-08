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
package org.gnit.lucenekmp.queryparser.flexible.standard.processors

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.SlopQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.TokenizedPhraseQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.MultiPhraseQueryNode

/**
 * This processor removes invalid [SlopQueryNode] objects in the query node tree. A [SlopQueryNode]
 * is invalid if its child is neither a [TokenizedPhraseQueryNode] nor a [MultiPhraseQueryNode].
 *
 * @see SlopQueryNode
 */
class PhraseSlopQueryNodeProcessor : QueryNodeProcessorImpl() {
    init {
        // empty constructor
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is SlopQueryNode) {
            val phraseSlopNode = node
            if (phraseSlopNode.child !is TokenizedPhraseQueryNode &&
                phraseSlopNode.child !is MultiPhraseQueryNode
            ) {
                return phraseSlopNode.child
            }
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
