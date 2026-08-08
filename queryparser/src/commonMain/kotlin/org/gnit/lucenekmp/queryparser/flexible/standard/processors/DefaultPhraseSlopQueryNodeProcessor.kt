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
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.SlopQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.TokenizedPhraseQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.MultiPhraseQueryNode

/**
 * This processor verifies if [ConfigurationKeys.PHRASE_SLOP] is defined in the
 * [QueryConfigHandler]. If it is, it looks for every [TokenizedPhraseQueryNode] and
 * [MultiPhraseQueryNode] that does not have any [SlopQueryNode] applied to it and creates a
 * [SlopQueryNode] and apply to it. The new [SlopQueryNode] has the same slop value defined in the
 * configuration.
 *
 * @see SlopQueryNode
 * @see ConfigurationKeys.PHRASE_SLOP
 */
class DefaultPhraseSlopQueryNodeProcessor : QueryNodeProcessorImpl() {
    private var processChildren = true

    private var defaultPhraseSlop = 0

    init {
        // empty constructor
    }

    override fun process(queryTree: QueryNode): QueryNode {
        val queryConfig = queryConfigHandler

        if (queryConfig != null) {
            val defaultPhraseSlop = queryConfig.get(ConfigurationKeys.PHRASE_SLOP)

            if (defaultPhraseSlop != null) {
                this.defaultPhraseSlop = defaultPhraseSlop

                return super.process(queryTree)
            }
        }

        return queryTree
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is TokenizedPhraseQueryNode || node is MultiPhraseQueryNode) {
            return SlopQueryNode(node, defaultPhraseSlop)
        }

        return node
    }

    override fun preProcessNode(node: QueryNode): QueryNode {
        if (node is SlopQueryNode) {
            processChildren = false
        }

        return node
    }

    override fun processChildren(queryTree: QueryNode) {
        if (processChildren) {
            super.processChildren(queryTree)
        } else {
            processChildren = true
        }
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> = children
}
