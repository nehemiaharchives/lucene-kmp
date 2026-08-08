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
 * A [GroupQueryNode] represents a location where the original user typed real parenthesis on the
 * query string. This class is useful for queries like: a) a AND b OR c b) ( a AND b) OR c
 *
 * Parenthesis might be used to define the boolean operation precedence.
 */
class GroupQueryNode(
    /** This QueryNode is used to identify parenthesis on the original query string */
    query: QueryNode,
) : QueryNodeImpl() {
    init {
        allocate()
        setLeaf(false)
        add(query)
    }

    var child: QueryNode
        get() = requireNotNull(children)[0]
        set(child) {
            val list: MutableList<QueryNode> = mutableListOf()
            list.add(child)
            set(list)
        }

    override fun toString(): String {
        return "<group>\n$child\n</group>"
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        return "( ${child.toQueryString(escapeSyntaxParser)} )"
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as GroupQueryNode

        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return GroupQueryNode(child.cloneTree())
    }
}
