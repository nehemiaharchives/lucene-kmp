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

import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.jdkport.NumberFormat
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldValuePairQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax.Type
import org.gnit.lucenekmp.queryparser.flexible.standard.config.PointsConfig

/**
 * This query node represents a field query that holds a point value. It is similar to
 * [FieldQueryNode], however the [value] returns a [Number].
 *
 * @see PointsConfig
 */
class PointQueryNode(
    /**
     * Creates a [PointQueryNode] object using the given field, [Number] value and [NumberFormat]
     * used to convert the value to [String].
     *
     * @param field the field associated with this query node
     * @param value the value hold by this node
     * @param numberFormat the [NumberFormat] used to convert the value to [String]
     */
    field: CharSequence?,
    value: Number?,
    numberFormat: NumberFormat,
) : QueryNodeImpl(), FieldValuePairQueryNode<Number> {
    /**
     * Sets the [NumberFormat] used to convert the value to [String].
     *
     * @param format the [NumberFormat] used to convert the value to [String]
     */
    var numberFormat: NumberFormat = numberFormat

    /**
     * Returns the field associated with this node.
     *
     * @return the field associated with this node
     */
    override var field: CharSequence? = field

    /**
     * Returns the numeric value as [Number].
     *
     * @return the numeric value
     */
    override var value: Number? = value

    /**
     * This method is used to get the value converted to [String] and escaped using the given
     * [EscapeQuerySyntax].
     *
     * @param escaper the [EscapeQuerySyntax] used to escape the value [String]
     * @return the value converted to [String] and escaped
     */
    protected fun getTermEscaped(escaper: EscapeQuerySyntax): CharSequence {
        return escaper.escape(
            numberFormat.format(requireNotNull(value)),
            Locale.ROOT,
            Type.NORMAL,
        )
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        if (isDefaultField(field)) {
            return getTermEscaped(escapeSyntaxParser)
        } else {
            return "$field:${getTermEscaped(escapeSyntaxParser)}"
        }
    }

    override fun toString(): String {
        return "<numeric field='$field' number='${numberFormat.format(requireNotNull(value))}'/>"
    }

    override fun cloneNode(): QueryNodeImpl {
        return PointQueryNode(field, value, numberFormat)
    }
}
