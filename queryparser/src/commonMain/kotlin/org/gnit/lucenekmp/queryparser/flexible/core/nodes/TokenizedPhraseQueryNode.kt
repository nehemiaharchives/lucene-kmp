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
 * A [TokenizedPhraseQueryNode] represents a node created by a code that
 * tokenizes/lemmatizes/analyzes.
 */
class TokenizedPhraseQueryNode : QueryNodeImpl(), FieldableNode {
    init {
        setLeaf(false)
        allocate()
    }

    override fun toString(): String {
        val children = children
        if (children == null || children.isEmpty()) return "<tokenizedphrase/>"
        val sb = StringBuilder()
        sb.append("<tokenizedphrase>")
        for (child in children) {
            sb.append("\n")
            sb.append(child.toString())
        }
        sb.append("\n</tokenizedphrase>")
        return sb.toString()
    }

    // This text representation is not re-parseable
    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        val children = children
        if (children == null || children.isEmpty()) return ""
        val sb = StringBuilder()
        var filler = ""
        for (child in children) {
            sb.append(filler).append(child.toQueryString(escapeSyntaxParser))
            filler = ","
        }
        return "[TP[$sb]]"
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as TokenizedPhraseQueryNode

        // nothing to do

        return clone
    }

    override var field: CharSequence?
        get() {
            val children = children
            if (children != null) {
                for (child in children) {
                    if (child is FieldableNode) {
                        return child.field
                    }
                }
            }
            return null
        }
        set(fieldName) {
            val children = children
            if (children != null) {
                for (child in children) {
                    if (child is FieldableNode) {
                        child.field = fieldName
                    }
                }
            }
        }

    override fun cloneNode(): QueryNodeImpl {
        return TokenizedPhraseQueryNode()
    }
}
