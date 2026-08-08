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

import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.core.util.UnescapedCharSequence
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.WildcardQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl

/**
 * This processor verifies if [ConfigurationKeys.ALLOW_LEADING_WILDCARD] is defined in the
 * [QueryConfigHandler]. If it is and leading wildcard is not allowed, it looks for every
 * [WildcardQueryNode] contained in the query node tree and throws an exception if any of them has
 * a leading wildcard ('*' or '?').
 *
 * @see ConfigurationKeys.ALLOW_LEADING_WILDCARD
 */
class AllowLeadingWildcardProcessor : QueryNodeProcessorImpl() {
    init {
        // empty constructor
    }

    override fun process(queryTree: QueryNode): QueryNode {
        val allowsLeadingWildcard =
            requireNotNull(queryConfigHandler).get(ConfigurationKeys.ALLOW_LEADING_WILDCARD)

        if (allowsLeadingWildcard != null) {
            if (!allowsLeadingWildcard) {
                return super.process(queryTree)
            }
        }

        return queryTree
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is WildcardQueryNode) {
            val wildcardNode = node
            if (!requireNotNull(wildcardNode.text).isEmpty()) {
                // Validate if the wildcard was escaped
                if (UnescapedCharSequence.wasEscaped(requireNotNull(wildcardNode.text), 0)) return node

                when (wildcardNode.text!![0]) {
                    '*', '?' ->
                        throw QueryNodeException(
                            MessageImpl(
                                QueryParserMessages.LEADING_WILDCARD_NOT_ALLOWED,
                                node.toQueryString(EscapeQuerySyntaxImpl()),
                            ),
                        )
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
