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

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode.Modifier
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.BooleanModifierNode

/**
 * This processor removes every [BooleanQueryNode] that contains only one child and returns this
 * child. If this child is [ModifierQueryNode] that was defined by the user. A modifier is not
 * defined by the user when it's a [BooleanModifierNode]
 *
 * @see ModifierQueryNode
 */
class BooleanSingleChildOptimizationQueryNodeProcessor : QueryNodeProcessorImpl() {
    init {
        // empty constructor
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is BooleanQueryNode) {
            val children = node.children

            if (children != null && children.size == 1) {
                val child = children[0]

                if (child is ModifierQueryNode) {
                    val modNode = child
                    if (modNode is BooleanModifierNode ||
                        modNode.modifier == Modifier.MOD_NONE
                    ) {
                        return child
                    }
                } else {
                    return child
                }
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
