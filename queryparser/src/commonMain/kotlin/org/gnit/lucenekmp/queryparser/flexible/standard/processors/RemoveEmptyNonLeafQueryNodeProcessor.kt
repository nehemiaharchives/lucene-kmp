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

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.GroupQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.MatchNoDocsQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl

/**
 * This processor removes every [QueryNode] that is not a leaf and has not children. If after
 * processing the entire tree the root node is not a leaf and has no children, a
 * [MatchNoDocsQueryNode] object is returned. <br> This processor is used at the end of a pipeline
 * to avoid invalid query node tree structures like a [GroupQueryNode] or [ModifierQueryNode] with
 * no children.
 *
 * @see QueryNode
 * @see MatchNoDocsQueryNode
 */
class RemoveEmptyNonLeafQueryNodeProcessor : QueryNodeProcessorImpl() {
    private val childrenBuffer: MutableList<QueryNode> = mutableListOf()

    init {
        // empty constructor
    }

    override fun process(queryTree: QueryNode): QueryNode {
        var queryTree = super.process(queryTree)

        if (!queryTree.isLeaf) {
            val children = queryTree.children

            if (children == null || children.size == 0) {
                return MatchNoDocsQueryNode()
            }
        }

        return queryTree
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        return node
    }

    override fun preProcessNode(node: QueryNode): QueryNode {
        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {
        try {
            for (child in children) {
                if (!child.isLeaf) {
                    val grandChildren = child.children

                    if (grandChildren != null && grandChildren.size > 0) {
                        childrenBuffer.add(child)
                    }
                } else {
                    childrenBuffer.add(child)
                }
            }

            children.clear()
            children.addAll(childrenBuffer)
        } finally {
            childrenBuffer.clear()
        }

        return children
    }
}
