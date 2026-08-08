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
package org.gnit.lucenekmp.queryparser.flexible.precedence.processors

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.AndQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.OrQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.Operator

/**
 * This processor is used to apply the correct [ModifierQueryNode] to [BooleanQueryNode]s children.
 *
 * It walks through the query node tree looking for [BooleanQueryNode]s. If an [AndQueryNode] is
 * found, every child, which is not a [ModifierQueryNode] or the [ModifierQueryNode] is
 * [ModifierQueryNode.Modifier.MOD_NONE], becomes a [ModifierQueryNode.Modifier.MOD_REQ]. For any
 * other [BooleanQueryNode] which is not an [OrQueryNode], it checks the default operator is
 * [Operator.AND], if it is, the same operation when an [AndQueryNode] is found is applied to it.
 *
 * @see ConfigurationKeys.DEFAULT_OPERATOR
 * @see PrecedenceQueryParser.setDefaultOperator
 */
class BooleanModifiersQueryNodeProcessor : QueryNodeProcessorImpl() {
    private val childrenBuffer: MutableList<QueryNode> = ArrayList()

    private var usingAnd = false

    init {
        // empty constructor
    }

    override fun process(queryTree: QueryNode): QueryNode {
        val op = requireNotNull(queryConfigHandler).get(ConfigurationKeys.DEFAULT_OPERATOR)

        if (op == null) {
            throw IllegalArgumentException(
                "StandardQueryConfigHandler.ConfigurationKeys.DEFAULT_OPERATOR should be set on the QueryConfigHandler",
            )
        }

        usingAnd = StandardQueryConfigHandler.Operator.AND == op

        return super.process(queryTree)
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is AndQueryNode) {
            childrenBuffer.clear()
            val children = requireNotNull(node.children)

            for (child in children) {
                childrenBuffer.add(applyModifier(child, ModifierQueryNode.Modifier.MOD_REQ))
            }

            node.set(childrenBuffer)
        } else if (usingAnd && node is BooleanQueryNode && node !is OrQueryNode) {
            childrenBuffer.clear()
            val children = requireNotNull(node.children)

            for (child in children) {
                childrenBuffer.add(applyModifier(child, ModifierQueryNode.Modifier.MOD_REQ))
            }

            node.set(childrenBuffer)
        }

        return node
    }

    private fun applyModifier(
        node: QueryNode,
        mod: ModifierQueryNode.Modifier,
    ): QueryNode {
        // check if modifier is not already defined and is default
        if (node !is ModifierQueryNode) {
            return ModifierQueryNode(node, mod)
        } else {
            val modNode = node

            if (modNode.modifier == ModifierQueryNode.Modifier.MOD_NONE) {
                return ModifierQueryNode(modNode.child, mod)
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
