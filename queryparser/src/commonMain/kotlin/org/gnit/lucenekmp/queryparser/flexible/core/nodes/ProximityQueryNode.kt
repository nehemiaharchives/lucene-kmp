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

import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeError
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl

/**
 * A [ProximityQueryNode] represents a query where the terms should meet specific distance
 * conditions. (a b c) WITHIN [SENTENCE|PARAGRAPH|NUMBER] [INORDER] ("a" "b" "c") WITHIN
 * [SENTENCE|PARAGRAPH|NUMBER] [INORDER]
 *
 * TODO: Add this to the future standard Lucene parser/processor/builder
 */
class ProximityQueryNode : BooleanQueryNode {
    /** Distance condition: PARAGRAPH, SENTENCE, or NUMBER */
    enum class Type {
        PARAGRAPH {
            override fun toQueryString(): CharSequence {
                return "WITHIN PARAGRAPH"
            }
        },
        SENTENCE {
            override fun toQueryString(): CharSequence {
                return "WITHIN SENTENCE"
            }
        },
        NUMBER {
            override fun toQueryString(): CharSequence {
                return "WITHIN"
            }
        };

        abstract fun toQueryString(): CharSequence
    }

    /** utility class containing the distance condition and number */
    class ProximityType {
        var pDistance: Int

        var pType: Type

        constructor(type: Type) : this(type, 0)

        constructor(type: Type, distance: Int) {
            pType = type
            pDistance = distance
        }
    }

    var proximityType: Type

    var distance: Int = -1
        private set

    val inorder: Boolean

    /**
     * returns null if the field was not specified in the query string
     *
     * @return the field
     * @param field the field to set
     */
    var field: CharSequence?

    /**
     * @param clauses - QueryNode children
     * @param field - field name
     * @param type - type of proximity query
     * @param distance - positive integer that specifies the distance
     * @param inorder - true, if the tokens should be matched in the order of the clauses
     */
    constructor(
        clauses: List<QueryNode>,
        field: CharSequence?,
        type: Type,
        distance: Int,
        inorder: Boolean,
    ) : super(clauses) {
        setLeaf(false)
        proximityType = type
        this.inorder = inorder
        this.field = field
        if (type == Type.NUMBER) {
            if (distance <= 0) {
                throw QueryNodeError(
                    MessageImpl(
                        QueryParserMessages.PARAMETER_VALUE_NOT_SUPPORTED,
                        "distance",
                        distance,
                    ),
                )
            } else {
                this.distance = distance
            }
        }
        clearFields(clauses, field)
    }

    /**
     * @param clauses - QueryNode children
     * @param field - field name
     * @param type - type of proximity query
     * @param inorder - true, if the tokens should be matched in the order of the clauses
     */
    constructor(
        clauses: List<QueryNode>,
        field: CharSequence?,
        type: Type,
        inorder: Boolean,
    ) : this(clauses, field, type, -1, inorder)

    override fun toString(): String {
        val distanceSTR = if (distance == -1) "" else " distance='$distance'"

        if (children == null || children!!.size == 0)
            return "<proximity field='$field' inorder='$inorder' type='$proximityType'$distanceSTR/>"
        val sb = StringBuilder()
        sb.append("<proximity field='")
            .append(field)
            .append("' inorder='")
            .append(inorder)
            .append("' type='")
            .append(proximityType.toString())
            .append("'")
            .append(distanceSTR)
            .append(">")
        for (child in children!!) {
            sb.append("\n")
            sb.append(child.toString())
        }
        sb.append("\n</proximity>")
        return sb.toString()
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        val withinSTR =
            proximityType.toQueryString().toString() +
                (if (distance == -1) "" else " $distance") +
                (if (inorder) " INORDER" else "")

        val sb = StringBuilder()
        if (children == null || children!!.size == 0) {
            // no children case
        } else {
            var filler = ""
            for (child in children!!) {
                sb.append(filler).append(child.toQueryString(escapeSyntaxParser))
                filler = " "
            }
        }

        if (isDefaultField(field)) {
            return "( $sb ) $withinSTR"
        } else {
            return "$field:(( $sb ) $withinSTR)"
        }
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as ProximityQueryNode

        clone.proximityType = proximityType
        clone.distance = distance
        clone.field = field

        return clone
    }

    /**
     * returns null if the field was not specified in the query string
     *
     * @return the field
     */
    fun getFieldAsString(): String? {
        if (field == null) return null
        else return field.toString()
    }

    /**
     * @return terms must be matched in the specified order
     */

    override fun cloneNode(): QueryNodeImpl {
        return ProximityQueryNode(
            requireNotNull(children).map { it.cloneTree() },
            field,
            proximityType,
            distance,
            inorder,
        )
    }

    companion object {
        private fun clearFields(nodes: List<QueryNode>?, field: CharSequence?) {
            if (nodes == null || nodes.size == 0) return

            for (clause in nodes) {
                if (clause is FieldQueryNode) {
                    clause.toQueryStringIgnoreFields = true
                    clause.field = field
                }
            }
        }
    }
}
