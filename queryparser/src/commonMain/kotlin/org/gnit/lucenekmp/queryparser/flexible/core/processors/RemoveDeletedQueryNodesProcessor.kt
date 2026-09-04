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

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.DeletedQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.MatchNoDocsQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode

/**
 * A [QueryNodeProcessorPipeline] class removes every instance of [DeletedQueryNode] from a query
 * node tree. If the resulting root node is a [DeletedQueryNode], [MatchNoDocsQueryNode] is returned.
 */
class RemoveDeletedQueryNodesProcessor : QueryNodeProcessorImpl() {
    init {
        // empty constructor
    }

    override fun process(queryTree: QueryNode): QueryNode {
        var queryTree = queryTree
        queryTree = super.process(queryTree)

        if (queryTree is DeletedQueryNode && queryTree !is MatchNoDocsQueryNode) {
            return MatchNoDocsQueryNode()
        }

        return queryTree
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (!node.isLeaf) {
            val children = node.children
            var removeBoolean = false

            if (children == null || children.size == 0) {
                removeBoolean = true
            } else {
                removeBoolean = true

                val it = children.iterator()
                while (it.hasNext()) {
                    if (it.next() !is DeletedQueryNode) {
                        removeBoolean = false
                        break
                    }
                }
            }

            if (removeBoolean) {
                return DeletedQueryNode()
            }
        }

        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {
        var i = 0
        while (i < children.size) {
            if (children[i] is DeletedQueryNode) {
                children.removeAt(i--)
            }
            i++
        }

        return children
    }

    override fun preProcessNode(node: QueryNode): QueryNode {
        return node
    }
}
