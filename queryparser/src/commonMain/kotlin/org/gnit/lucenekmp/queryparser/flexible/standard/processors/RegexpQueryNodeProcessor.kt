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

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.RegexpQueryNode

/** Processor for Regexp queries. */
class RegexpQueryNodeProcessor : QueryNodeProcessorImpl() {
    override fun preProcessNode(node: QueryNode): QueryNode = node

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is RegexpQueryNode) {
            val regexpNode = node
            val analyzer = requireNotNull(queryConfigHandler).get(ConfigurationKeys.ANALYZER)
            if (analyzer != null) {
                var text = regexpNode.text.toString()
                // because we call utf8ToString, this will only work with the default
                // TermToBytesRefAttribute
                text = analyzer.normalize(regexpNode.getFieldAsString(), text).utf8ToString()
                regexpNode.text = text
            }
        }
        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> = children
}
