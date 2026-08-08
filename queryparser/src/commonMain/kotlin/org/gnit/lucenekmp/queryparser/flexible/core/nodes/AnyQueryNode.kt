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

/** A [AnyQueryNode] represents an ANY operator performed on a list of nodes. */
class AnyQueryNode(
    /** @param clauses - the query nodes to be or'ed */
    clauses: List<QueryNode>,
    /**
     * returns null if the field was not specified
     *
     * @return the field
     * @param field - the field to set
     */
    var field: CharSequence?,
    var minimumMatchingElements: Int,
) : AndQueryNode(clauses) {
    init {
        for (clause in clauses) {
            if (clause is FieldQueryNode) {
                clause.toQueryStringIgnoreFields = true
                clause.field = field
            }
        }
    }

    /**
     * returns - null if the field was not specified
     *
     * @return the field as a String
     */
    fun getFieldAsString(): String? {
        if (field == null) return null
        else return field.toString()
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as AnyQueryNode

        clone.field = field
        clone.minimumMatchingElements = minimumMatchingElements

        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return AnyQueryNode(
            requireNotNull(children).map { it.cloneTree() },
            field,
            minimumMatchingElements,
        )
    }

    override fun toString(): String {
        if (children == null || children!!.isEmpty())
            return "<any field='$field'  matchelements=$minimumMatchingElements/>"
        val sb = StringBuilder()
        sb.append("<any field='")
            .append(field)
            .append("'  matchelements=")
            .append(minimumMatchingElements)
            .append('>')
        for (clause in children!!) {
            sb.append("\n")
            sb.append(clause.toString())
        }
        sb.append("\n</any>")
        return sb.toString()
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        val anySTR = "ANY $minimumMatchingElements"

        val sb = StringBuilder()
        if (children == null || children!!.isEmpty()) {
            // no children case
        } else {
            var filler = ""
            for (clause in children!!) {
                sb.append(filler).append(clause.toQueryString(escapeSyntaxParser))
                filler = " "
            }
        }

        if (isDefaultField(field)) {
            return "( $sb ) $anySTR"
        } else {
            return "$field:(( $sb ) $anySTR)"
        }
    }
}
