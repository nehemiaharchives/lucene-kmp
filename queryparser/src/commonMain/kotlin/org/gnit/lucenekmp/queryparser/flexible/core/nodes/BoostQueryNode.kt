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
package org.gnit.lucenekmp.queryparser.flexible.core.nodes

import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax

/**
 * A [BoostQueryNode] boosts the QueryNode tree which is under this node. So, it must only and always
 * have one child.
 *
 * The boost value may vary from 0.0 to 1.0.
 */
class BoostQueryNode(
    /** the query to be boosted */
    query: QueryNode,
    /** the boost value, it may vary from 0.0 to 1.0 */
    val value: Float,
) : QueryNodeImpl() {
    init {
        setLeaf(false)
        allocate()
        add(query)
    }

    /**
     * Returns the single child which this node boosts.
     *
     * @return the single child which this node boosts
     */
    val child: QueryNode?
        get() {
            val children = children

            if (children == null || children.size == 0) {
                return null
            }

            return children[0]
        }

    /**
     * Returns the boost value parsed to a string.
     *
     * @return the parsed value
     */
    private fun getValueString(): CharSequence {
        val f = value
        if (f == f.toLong().toFloat()) return "${f.toLong()}"
        else return "$f"
    }

    override fun toString(): String {
        return "<boost value='${getValueString()}'>\n$child\n</boost>"
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        if (child == null) return ""
        return "${requireNotNull(child).toQueryString(escapeSyntaxParser)}^${getValueString()}"
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as BoostQueryNode

        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return BoostQueryNode(requireNotNull(child).cloneTree(), value)
    }
}
