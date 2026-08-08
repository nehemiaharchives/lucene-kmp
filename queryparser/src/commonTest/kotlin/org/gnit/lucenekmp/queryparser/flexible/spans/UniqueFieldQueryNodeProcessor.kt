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
package org.gnit.lucenekmp.queryparser.flexible.spans

import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldableNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl

/**
 * This processor changes every field name of each [FieldableNode] query node contained in the
 * query tree to the field name defined in the [UniqueFieldAttribute]. So, the
 * [UniqueFieldAttribute] must be defined in the [QueryConfigHandler] object set in this
 * processor, otherwise it throws an exception.
 *
 * @see UniqueFieldAttribute
 */
class UniqueFieldQueryNodeProcessor : QueryNodeProcessorImpl() {

    override fun postProcessNode(node: QueryNode): QueryNode {

        return node
    }

    override fun preProcessNode(node: QueryNode): QueryNode {

        if (node is FieldableNode) {
            val queryConfig = queryConfigHandler

            if (queryConfig == null) {
                throw IllegalArgumentException(
                    "A config handler is expected by the processor UniqueFieldQueryNodeProcessor!"
                )
            }

            if (!queryConfig.has(SpansQueryConfigHandler.UNIQUE_FIELD)) {
                throw IllegalArgumentException(
                    "UniqueFieldAttribute should be defined in the config handler!"
                )
            }

            val uniqueField: String = requireNotNull(queryConfig.get(SpansQueryConfigHandler.UNIQUE_FIELD))
            node.field = uniqueField
        }

        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {

        return children
    }
}
