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

import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax

/** A [FieldQueryNode] represents a element that contains field/text tuple */
open class FieldQueryNode(
    /** The term's field */
    override var field: CharSequence?,
    /** The term's text. */
    override var text: CharSequence?,
    /** The term's begin position. */
    var begin: Int,
    /** The term's end position. */
    var end: Int,
) : QueryNodeImpl(), FieldValuePairQueryNode<CharSequence>, TextableQueryNode {
    /** The term's position increment. */
    var positionIncrement: Int = 0

    init {
        setLeaf(true)
    }

    protected fun getTermEscaped(escaper: EscapeQuerySyntax): CharSequence {
        return escaper.escape(requireNotNull(text), Locale.getDefault(), EscapeQuerySyntax.Type.NORMAL)
    }

    protected fun getTermEscapeQuoted(escaper: EscapeQuerySyntax): CharSequence {
        return escaper.escape(requireNotNull(text), Locale.getDefault(), EscapeQuerySyntax.Type.STRING)
    }

    override fun toQueryString(escaper: EscapeQuerySyntax): CharSequence {
        if (isDefaultField(field)) {
            return getTermEscaped(escaper)
        } else {
            return "$field:${getTermEscaped(escaper)}"
        }
    }

    override fun toString(): String {
        return "<field start='$begin' end='$end' field='$field' text='$text'/>"
    }

    /**
     * @return the term
     */
    fun getTextAsString(): String? {
        if (text == null) return null
        else return text.toString()
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

    override fun cloneTree(): FieldQueryNode {
        val fqn = super.cloneTree() as FieldQueryNode
        fqn.begin = begin
        fqn.end = end
        fqn.field = field
        fqn.text = text
        fqn.positionIncrement = positionIncrement
        fqn.toQueryStringIgnoreFields = toQueryStringIgnoreFields

        return fqn
    }

    /**
     * Returns the term.
     *
     * @return The "original" form of the term.
     */
    override var value: CharSequence?
        get() = text
        set(value) {
            text = value
        }

    override fun cloneNode(): QueryNodeImpl {
        return FieldQueryNode(field, text, begin, end)
    }
}
