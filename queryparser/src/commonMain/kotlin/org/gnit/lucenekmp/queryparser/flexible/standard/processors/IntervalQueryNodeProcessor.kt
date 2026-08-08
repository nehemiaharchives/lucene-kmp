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

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.IntervalQueryNode

/**
 * This processor makes sure that [ConfigurationKeys.ANALYZER] is defined in the
 * [QueryConfigHandler] and injects this analyzer into [IntervalQueryNode]s.
 *
 * @see ConfigurationKeys.ANALYZER
 */
class IntervalQueryNodeProcessor : QueryNodeProcessorImpl() {
    private var analyzer: Analyzer? = null

    override fun process(queryTree: QueryNode): QueryNode {
        this.analyzer = requireNotNull(queryConfigHandler).get(ConfigurationKeys.ANALYZER)
        return super.process(queryTree)
    }

    override fun preProcessNode(node: QueryNode): QueryNode {
        if (node is IntervalQueryNode) {
            if (this.analyzer == null) {
                throw QueryNodeException(
                    MessageImpl(QueryParserMessages.ANALYZER_REQUIRED, node.toString())
                )
            }
            node.setAnalyzer(this.analyzer!!)
        }
        return node
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {
        return children
    }
}
