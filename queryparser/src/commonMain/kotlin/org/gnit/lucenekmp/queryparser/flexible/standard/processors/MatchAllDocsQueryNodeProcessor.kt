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

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.MatchAllDocsQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.WildcardQueryNode
import org.gnit.lucenekmp.search.MatchAllDocsQuery

/**
 * This processor converts every [WildcardQueryNode] that is "*:*" to [MatchAllDocsQueryNode].
 *
 * @see MatchAllDocsQueryNode
 * @see MatchAllDocsQuery
 */
class MatchAllDocsQueryNodeProcessor : QueryNodeProcessorImpl() {
    init {
        // empty constructor
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is FieldQueryNode) {
            val fqn = node
            if (fqn.field.toString() == "*" && fqn.text.toString() == "*") {
                return MatchAllDocsQueryNode()
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
