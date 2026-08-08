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

/** A [OrQueryNode] represents an OR boolean operation performed on a list of nodes. */
class OrQueryNode(
    /** @param clauses - the query nodes to be or'ed */
    clauses: List<QueryNode>,
) : BooleanQueryNode(clauses) {
    init {
        if (clauses.isEmpty()) {
            throw IllegalArgumentException("OR query must have at least one clause")
        }
    }

    override fun toString(): String {
        if (children == null || children!!.size == 0) return "<boolean operation='or'/>"
        val sb = StringBuilder()
        sb.append("<boolean operation='or'>")
        for (child in children!!) {
            sb.append("\n")
            sb.append(child.toString())
        }
        sb.append("\n</boolean>")
        return sb.toString()
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        if (children == null || children!!.size == 0) return ""

        val sb = StringBuilder()
        var filler = ""
        val it = children!!.iterator()
        while (it.hasNext()) {
            sb.append(filler).append(it.next().toQueryString(escapeSyntaxParser))
            filler = " OR "
        }

        // in case is root or the parent is a group node avoid parenthesis
        if ((parent != null && parent is GroupQueryNode) || isRoot())
            return sb.toString()
        else return "( $sb )"
    }

    override fun cloneNode(): QueryNodeImpl {
        return OrQueryNode(requireNotNull(children).map { it.cloneTree() })
    }
}
