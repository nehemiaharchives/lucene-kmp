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
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.AbstractRangeQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.RegexpQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.WildcardQueryNode
import org.gnit.lucenekmp.search.MultiTermQuery

/**
 * This processor instates the default [MultiTermQuery.RewriteMethod],
 * [MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE], for multi-term query nodes.
 */
class MultiTermRewriteMethodProcessor : QueryNodeProcessorImpl() {
    companion object {
        const val TAG_ID = "MultiTermRewriteMethodConfiguration"
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        // set setMultiTermRewriteMethod for WildcardQueryNode and
        // PrefixWildcardQueryNode
        if (node is WildcardQueryNode ||
            node is AbstractRangeQueryNode<*> ||
            node is RegexpQueryNode
        ) {
            val rewriteMethod =
                requireNotNull(queryConfigHandler).get(ConfigurationKeys.MULTI_TERM_REWRITE_METHOD)

            if (rewriteMethod == null) {
                // This should not happen, this configuration is set in the
                // StandardQueryConfigHandler
                throw IllegalArgumentException(
                    "StandardQueryConfigHandler.ConfigurationKeys.MULTI_TERM_REWRITE_METHOD should be set on the QueryConfigHandler",
                )
            }

            // use a TAG to take the value to the Builder
            node.setTag(TAG_ID, rewriteMethod)
        }

        return node
    }

    override fun preProcessNode(node: QueryNode): QueryNode = node

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> = children
}
