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

import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.AndQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessor
import org.gnit.lucenekmp.queryparser.flexible.precedence.processors.BooleanModifiersQueryNodeProcessor
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.Operator
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.BooleanModifierNode

/**
 * This processor is used to apply the correct [ModifierQueryNode] to [BooleanQueryNode]s children.
 * This is a variant of [BooleanModifiersQueryNodeProcessor] which ignores precedence.
 *
 * The StandardSyntaxParser knows the rules of precedence, but lucene does not. e.g.
 * `(A AND B OR C AND D)` ist treated like `(+A +B +C +D)`.
 *
 * This processor walks through the query node tree looking for [BooleanQueryNode]s. If an
 * [AndQueryNode] is found, every child, which is not a [ModifierQueryNode] or the
 * [ModifierQueryNode] is [ModifierQueryNode.Modifier.MOD_NONE], becomes a
 * [ModifierQueryNode.Modifier.MOD_REQ]. For default [BooleanQueryNode], it checks the default
 * operator is [Operator.AND], if it is, the same operation when an [AndQueryNode] is found is
 * applied to it. Each [BooleanQueryNode] which direct parent is also a [BooleanQueryNode] is removed
 * (to ignore the rules of precedence).
 *
 * @see ConfigurationKeys.DEFAULT_OPERATOR
 * @see BooleanModifiersQueryNodeProcessor
 */
class BooleanQuery2ModifierNodeProcessor : QueryNodeProcessor {
    override var queryConfigHandler: QueryConfigHandler? = null

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

        return processIteration(queryTree)
    }

    protected fun processChildren(queryTree: QueryNode) {
        val children = queryTree.children
        if (children != null && children.size > 0) {
            for (originalChild in children) {
                var child = originalChild
                child = processIteration(child)
            }
        }
    }

    private fun processIteration(queryTree: QueryNode): QueryNode {
        var queryTree = preProcessNode(queryTree)

        processChildren(queryTree)

        queryTree = postProcessNode(queryTree)

        return queryTree
    }

    protected fun fillChildrenBufferAndApplyModifiery(parent: QueryNode) {
        for (node in requireNotNull(parent.children)) {
            if (node.containsTag(TAG_REMOVE)) {
                fillChildrenBufferAndApplyModifiery(node)
            } else if (node.containsTag(TAG_MODIFIER)) {
                childrenBuffer.add(
                    applyModifier(
                        node,
                        node.getTag(TAG_MODIFIER) as ModifierQueryNode.Modifier,
                    ),
                )
            } else {
                childrenBuffer.add(node)
            }
        }
    }

    protected fun postProcessNode(node: QueryNode): QueryNode {
        if (node.containsTag(TAG_BOOLEAN_ROOT)) {
            childrenBuffer.clear()
            fillChildrenBufferAndApplyModifiery(node)
            node.set(childrenBuffer)
        }
        return node
    }

    protected fun preProcessNode(node: QueryNode): QueryNode {
        val parent = node.parent
        if (node is BooleanQueryNode) {
            if (parent is BooleanQueryNode) {
                node.setTag(TAG_REMOVE, true) // no precedence
            } else {
                node.setTag(TAG_BOOLEAN_ROOT, true)
            }
        } else if (parent is BooleanQueryNode) {
            if (parent is AndQueryNode || (usingAnd && isDefaultBooleanQueryNode(parent))) {
                tagModifierButDoNotOverride(node, ModifierQueryNode.Modifier.MOD_REQ)
            }
        }
        return node
    }

    protected fun isDefaultBooleanQueryNode(toTest: QueryNode?): Boolean {
        return toTest != null && toTest::class == BooleanQueryNode::class
    }

    private fun applyModifier(
        node: QueryNode,
        mod: ModifierQueryNode.Modifier,
    ): QueryNode {
        // check if modifier is not already defined and is default
        if (node !is ModifierQueryNode) {
            return BooleanModifierNode(node, mod)
        } else {
            val modNode = node

            if (modNode.modifier == ModifierQueryNode.Modifier.MOD_NONE) {
                return ModifierQueryNode(modNode.child, mod)
            }
        }

        return node
    }

    protected fun tagModifierButDoNotOverride(
        node: QueryNode,
        mod: ModifierQueryNode.Modifier,
    ) {
        if (node is ModifierQueryNode) {
            val modNode = node
            if (modNode.modifier == ModifierQueryNode.Modifier.MOD_NONE) {
                node.setTag(TAG_MODIFIER, mod)
            }
        } else {
            node.setTag(TAG_MODIFIER, ModifierQueryNode.Modifier.MOD_REQ)
        }
    }

    companion object {
        internal const val TAG_REMOVE = "remove"
        internal const val TAG_MODIFIER = "wrapWithModifier"
        internal const val TAG_BOOLEAN_ROOT = "booleanRoot"
    }
}
