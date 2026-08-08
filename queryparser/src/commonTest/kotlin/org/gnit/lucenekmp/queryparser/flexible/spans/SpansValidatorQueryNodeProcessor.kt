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

import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.AndQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.OrQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl

/**
 * Validates every query node in a query node tree. This processor will pass fine if the query nodes
 * are only [BooleanQueryNode]s, [OrQueryNode]s or [FieldQueryNode]s, otherwise an
 * exception will be thrown. <br></br>
 * <br></br>
 * If they are [AndQueryNode] or an instance of anything else that implements
 * [FieldQueryNode] the exception will also be thrown.
 */
class SpansValidatorQueryNodeProcessor : QueryNodeProcessorImpl() {

    override fun postProcessNode(node: QueryNode): QueryNode {

        return node
    }

    override fun preProcessNode(node: QueryNode): QueryNode {

        if (!((node is BooleanQueryNode && node !is AndQueryNode) ||
                node::class == FieldQueryNode::class)) {
            throw QueryNodeException(MessageImpl(QueryParserMessages.NODE_ACTION_NOT_SUPPORTED))
        }

        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {

        return children
    }
}
