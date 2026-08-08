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
 * A [ModifierQueryNode] indicates the modifier value (+,-,?,NONE) for each term on the query string.
 * For example "+t1 -t2 t3" will have a tree of:
 *
 * &lt;BooleanQueryNode&gt; &lt;ModifierQueryNode modifier="MOD_REQ"&gt; &lt;t1/&gt;
 * &lt;/ModifierQueryNode&gt; &lt;ModifierQueryNode modifier="MOD_NOT"&gt; &lt;t2/&gt;
 * &lt;/ModifierQueryNode&gt; &lt;t3/&gt; &lt;/BooleanQueryNode&gt;
 */
open class ModifierQueryNode(
    /** @param query - QueryNode subtree */
    query: QueryNode,
    /** @param mod - Modifier Value */
    val modifier: Modifier,
) : QueryNodeImpl() {
    /** Modifier type: such as required (REQ), prohibited (NOT) */
    enum class Modifier {
        MOD_NONE,
        MOD_NOT,
        MOD_REQ;

        override fun toString(): String {
            return when (this) {
                MOD_NONE -> "MOD_NONE"
                MOD_NOT -> "MOD_NOT"
                MOD_REQ -> "MOD_REQ"
            }
            // this code is never executed
        }

        fun toDigitString(): String {
            return when (this) {
                MOD_NONE -> ""
                MOD_NOT -> "-"
                MOD_REQ -> "+"
            }
            // this code is never executed
        }

        fun toLargeString(): String {
            return when (this) {
                MOD_NONE -> ""
                MOD_NOT -> "NOT "
                MOD_REQ -> "+"
            }
            // this code is never executed
        }
    }

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
        return "<modifier operation='$modifier'>\n$child\n</modifier>"
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        var leftParenthensis = ""
        var rightParenthensis = ""

        if (child is ModifierQueryNode) {
            leftParenthensis = "("
            rightParenthensis = ")"
        }

        if (child is BooleanQueryNode) {
            return modifier.toLargeString() +
                leftParenthensis +
                child.toQueryString(escapeSyntaxParser) +
                rightParenthensis
        } else {
            return modifier.toDigitString() +
                leftParenthensis +
                child.toQueryString(escapeSyntaxParser) +
                rightParenthensis
        }
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as ModifierQueryNode

        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return ModifierQueryNode(child.cloneTree(), modifier)
    }
}
