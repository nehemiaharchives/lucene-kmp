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
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldableNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.GroupQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.OrQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys

/**
 * This processor is used to expand terms so the query looks for the same term in different fields.
 * It also boosts a query based on its field. <br> <br> This processor looks for every
 * [FieldableNode] contained in the query node tree. If a [FieldableNode] is found, it checks if
 * there is a [ConfigurationKeys.MULTI_FIELDS] defined in the [QueryConfigHandler]. If there is, the
 * [FieldableNode] is cloned N times and the clones are added to a [BooleanQueryNode] together with
 * the original node. N is defined by the number of fields that it will be expanded to. The
 * [BooleanQueryNode] is returned.
 *
 * @see ConfigurationKeys.MULTI_FIELDS
 */
class MultiFieldQueryNodeProcessor : QueryNodeProcessorImpl() {
    private var processChildren = true

    init {
        // empty constructor
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        return node
    }

    override fun processChildren(queryTree: QueryNode) {
        if (processChildren) {
            super.processChildren(queryTree)
        } else {
            processChildren = true
        }
    }

    override fun preProcessNode(node: QueryNode): QueryNode {
        if (node is FieldableNode) {
            processChildren = false
            var fieldNode = node

            if (fieldNode.field == null) {
                val fields =
                    requireNotNull(queryConfigHandler).get(ConfigurationKeys.MULTI_FIELDS)

                if (fields == null) {
                    throw IllegalArgumentException(
                        "StandardQueryConfigHandler.ConfigurationKeys.MULTI_FIELDS should be set on the QueryConfigHandler",
                    )
                }

                if (fields.isNotEmpty()) {
                    fieldNode.field = fields[0]

                    if (fields.size == 1) {
                        return fieldNode as QueryNode
                    } else {
                        val children = ArrayList<QueryNode>(fields.size)

                        children.add(fieldNode as QueryNode)
                        for (i in 1 until fields.size) {
                            // Kotlin common cloneTree does not declare CloneNotSupportedException.
                            fieldNode = fieldNode.cloneTree() as FieldableNode
                            fieldNode.field = fields[i]

                            children.add(fieldNode as QueryNode)
                        }

                        return GroupQueryNode(OrQueryNode(children))
                    }
                }
            }
        }

        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {
        return children
    }
}
