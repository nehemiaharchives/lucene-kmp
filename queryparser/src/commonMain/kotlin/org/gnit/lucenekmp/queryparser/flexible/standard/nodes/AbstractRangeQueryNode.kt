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
package org.gnit.lucenekmp.queryparser.flexible.standard.nodes

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldValuePairQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.RangeQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax
import org.gnit.lucenekmp.queryparser.flexible.core.util.StringUtils

/**
 * This class should be extended by nodes intending to represent range queries.
 *
 * @param T the type of the range query bounds (lower and upper)
 */
open class AbstractRangeQueryNode<T : FieldValuePairQueryNode<*>> protected constructor() :
    QueryNodeImpl(), RangeQueryNode<FieldValuePairQueryNode<*>> {
    private var lowerInclusive: Boolean = false
    private var upperInclusive: Boolean = false

    /** Constructs an [AbstractRangeQueryNode], it should be invoked only by its extenders. */
    init {
        setLeaf(false)
        allocate()
    }

    /**
     * Returns the field associated with this node.
     *
     * @return the field associated with this node
     * @see org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldableNode
     */
    override var field: CharSequence?
        get() {
            var field: CharSequence? = null
            val lower = lowerBound
            val upper = upperBound

            if (lower != null) {
                field = lower.field
            } else if (upper != null) {
                field = upper.field
            }

            return field
        }
        /**
         * Sets the field associated with this node.
         *
         * @param fieldName the field associated with this node
         */
        set(fieldName) {
            val lower = lowerBound
            val upper = upperBound

            if (lower != null) {
                lower.field = fieldName
            }

            if (upper != null) {
                upper.field = fieldName
            }
        }

    /**
     * Returns the lower bound node.
     *
     * @return the lower bound node.
     */
    @Suppress("UNCHECKED_CAST")
    override val lowerBound: T
        get() = requireNotNull(children)[0] as T

    /**
     * Returns the upper bound node.
     *
     * @return the upper bound node.
     */
    @Suppress("UNCHECKED_CAST")
    override val upperBound: T
        get() = requireNotNull(children)[1] as T

    /**
     * Returns whether the lower bound is inclusive or exclusive.
     *
     * @return `true` if the lower bound is inclusive, otherwise, `false`
     */
    override val isLowerInclusive: Boolean
        get() = lowerInclusive

    /**
     * Returns whether the upper bound is inclusive or exclusive.
     *
     * @return `true` if the upper bound is inclusive, otherwise, `false`
     */
    override val isUpperInclusive: Boolean
        get() = upperInclusive

    /**
     * Sets the lower and upper bounds.
     *
     * @param lower the lower bound, `null` if lower bound is open
     * @param upper the upper bound, `null` if upper bound is open
     * @param lowerInclusive `true` if the lower bound is inclusive, otherwise, `false`
     * @param upperInclusive `true` if the upper bound is inclusive, otherwise, `false`
     * @see lowerBound
     * @see upperBound
     * @see isLowerInclusive
     * @see isUpperInclusive
     */
    fun setBounds(lower: T?, upper: T?, lowerInclusive: Boolean, upperInclusive: Boolean) {
        if (lower != null && upper != null) {
            val lowerField = StringUtils.toString(lower.field)
            val upperField = StringUtils.toString(upper.field)

            if ((upperField != null || lowerField != null) &&
                ((upperField != null && upperField != lowerField) || lowerField != upperField)
            ) {
                throw IllegalArgumentException("lower and upper bounds should have the same field name!")
            }

            this.lowerInclusive = lowerInclusive
            this.upperInclusive = upperInclusive

            val children = ArrayList<QueryNode>(2)
            children.add(lower)
            children.add(upper)

            set(children)
        }
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        val sb = StringBuilder()

        val lower = lowerBound
        val upper = upperBound

        if (lowerInclusive) {
            sb.append('[')
        } else {
            sb.append('{')
        }

        if (lower != null) {
            sb.append(lower.toQueryString(escapeSyntaxParser))
        } else {
            sb.append("...")
        }

        sb.append(' ')

        if (upper != null) {
            sb.append(upper.toQueryString(escapeSyntaxParser))
        } else {
            sb.append("...")
        }

        if (upperInclusive) {
            sb.append(']')
        } else {
            sb.append('}')
        }

        return sb.toString()
    }

    override fun toString(): String {
        val canonicalName = this::class.qualifiedName
        val sb = StringBuilder("<").append(canonicalName)
        sb.append(" lowerInclusive=").append(isLowerInclusive)
        sb.append(" upperInclusive=").append(isUpperInclusive)
        sb.append(">\n\t")
        sb.append(upperBound).append("\n\t")
        sb.append(lowerBound).append("\n")
        sb.append("</").append(canonicalName).append(">\n")

        return sb.toString()
    }

    override fun cloneNode(): QueryNodeImpl {
        return AbstractRangeQueryNode<T>().also {
            @Suppress("UNCHECKED_CAST")
            it.setBounds(
                lowerBound.cloneTree() as T,
                upperBound.cloneTree() as T,
                isLowerInclusive,
                isUpperInclusive,
            )
        }
    }
}
