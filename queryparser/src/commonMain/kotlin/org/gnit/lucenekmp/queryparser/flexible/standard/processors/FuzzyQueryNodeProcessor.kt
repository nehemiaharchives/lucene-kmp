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
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FuzzyQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.FuzzyConfig
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.search.FuzzyQuery

/**
 * This processor iterates the query node tree looking for every [FuzzyQueryNode], when this kind of
 * node is found, it checks on the query configuration for [ConfigurationKeys.FUZZY_CONFIG], gets
 * the fuzzy prefix length and default similarity from it and set to the fuzzy node. For more
 * information about fuzzy prefix length check: [FuzzyQuery].
 *
 * @see ConfigurationKeys.FUZZY_CONFIG
 * @see FuzzyQuery
 * @see FuzzyQueryNode
 */
class FuzzyQueryNodeProcessor : QueryNodeProcessorImpl() {
    override fun postProcessNode(node: QueryNode): QueryNode {
        return node
    }

    override fun preProcessNode(node: QueryNode): QueryNode {
        if (node is FuzzyQueryNode) {
            val fuzzyNode = node
            val config: QueryConfigHandler = requireNotNull(queryConfigHandler)

            val analyzer = requireNotNull(queryConfigHandler).get(ConfigurationKeys.ANALYZER)
            if (analyzer != null) {
                // because we call utf8ToString, this will only work with the default
                // TermToBytesRefAttribute
                var text = requireNotNull(fuzzyNode.getTextAsString())
                text =
                    analyzer.normalize(requireNotNull(fuzzyNode.getFieldAsString()), text).utf8ToString()
                fuzzyNode.text = text
            }

            var fuzzyConfig: FuzzyConfig? = null

            if (config.get(ConfigurationKeys.FUZZY_CONFIG).also { fuzzyConfig = it } != null) {
                fuzzyNode.prefixLength = requireNotNull(fuzzyConfig).prefixLength

                if (fuzzyNode.similarity < 0) {
                    fuzzyNode.similarity = requireNotNull(fuzzyConfig).minSimilarity
                }
            } else if (fuzzyNode.similarity < 0) {
                throw IllegalArgumentException("No FUZZY_CONFIG set in the config")
            }
        }

        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {
        return children
    }
}
