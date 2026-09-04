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

import org.gnit.lucenekmp.queryparser.flexible.core.config.FieldConfig
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BoostQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldableNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.core.util.StringUtils
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys

/**
 * This processor iterates the query node tree looking for every [FieldableNode] that has
 * [ConfigurationKeys.BOOST] in its config. If there is, the boost is applied to that
 * [FieldableNode].
 *
 * @see ConfigurationKeys.BOOST
 * @see QueryConfigHandler
 * @see FieldableNode
 */
class BoostQueryNodeProcessor : QueryNodeProcessorImpl() {
    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is FieldableNode &&
            (node.parent == null || node.parent !is FieldableNode)
        ) {
            val fieldNode = node
            val config = queryConfigHandler

            if (config != null) {
                val field = fieldNode.field
                val fieldConfig: FieldConfig? =
                    config.getFieldConfig(requireNotNull(StringUtils.toString(field)))

                if (fieldConfig != null) {
                    val boost = fieldConfig.get(ConfigurationKeys.BOOST)

                    if (boost != null) {
                        return BoostQueryNode(node, boost)
                    }
                }
            }
        }

        return node
    }

    override fun preProcessNode(node: QueryNode): QueryNode = node

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> = children
}
