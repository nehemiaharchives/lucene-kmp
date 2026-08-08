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

import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax.Type

/**
 * A [PathQueryNode] is used to store queries like /company/USA/California
 * /product/shoes/brown. QueryText are objects that contain the text, begin position and end position
 * in the query.
 *
 * Example how the text parser creates these objects:
 *
 * ```
 * val values = ArrayList<PathQueryNode.QueryText>()
 * values.add(PathQueryNode.QueryText("company", 1, 7))
 * values.add(PathQueryNode.QueryText("USA", 9, 12))
 * values.add(PathQueryNode.QueryText("California", 14, 23))
 * val q: QueryNode = PathQueryNode(values)
 * ```
 */
class PathQueryNode(
    /** @param pathElements - List of QueryText objects */
    pathElements: List<QueryText>,
) : QueryNodeImpl() {
    /** Term text with a beginning and end position */
    class QueryText(
        /** @return the value */
        var value: CharSequence,
        /** != null The term's begin position. */
        var begin: Int,
        /** The term's end position. */
        var end: Int,
    ) : Cloneable<QueryText> {
        /**
         * @param value - text value
         * @param begin - position in the query string
         * @param end - position in the query string
         */

        override fun clone(): QueryText {
            val clone = QueryText(value, begin, end)
            clone.value = value
            clone.begin = begin
            clone.end = end
            return clone
        }

        override fun toString(): String {
            return "$value, $begin, $end"
        }
    }

    /**
     * Returns the a List with all QueryText elements
     *
     * @return QueryText List size
     */
    var pathElements: List<QueryText> = pathElements

    init {
        if (pathElements.size <= 1) {
            // this should not happen
            throw RuntimeException("PathQuerynode requires more 2 or more path elements.")
        }
    }

    /**
     * Returns the a specific QueryText element
     *
     * @return QueryText List size
     */
    fun getPathElement(index: Int): QueryText {
        return pathElements[index]
    }

    /**
     * Returns the CharSequence value of a specific QueryText element
     *
     * @return the CharSequence for a specific QueryText element
     */
    val firstPathElement: CharSequence
        get() = pathElements[0].value

    /**
     * Returns a List QueryText element from position startIndex
     *
     * @return a List QueryText element from position startIndex
     */
    fun getPathElements(startIndex: Int): List<QueryText> {
        val rValues: MutableList<QueryText> = mutableListOf()
        for (i in startIndex..<pathElements.size) {
            try {
                rValues.add(pathElements[i].clone())
            } catch (e: Exception) {
                // this will not happen
            }
        }
        return rValues
    }

    private fun getPathString(): CharSequence {
        val path = StringBuilder()

        for (pathelement in pathElements) {
            path.append("/").append(pathelement.value)
        }
        return path.toString()
    }

    override fun toQueryString(escaper: EscapeQuerySyntax): CharSequence {
        val path = StringBuilder()
        path.append("/").append(firstPathElement)

        for (pathelement in getPathElements(1)) {
            val value = escaper.escape(pathelement.value, Locale.getDefault(), Type.STRING)
            path.append("/\"").append(value).append("\"")
        }
        return path.toString()
    }

    override fun toString(): String {
        val text = pathElements[0]

        return "<path start='${text.begin}' end='${text.end}' path='${getPathString()}'/>"
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as PathQueryNode

        // copy children
        if (pathElements.isNotEmpty()) {
            val localValues: MutableList<QueryText> = mutableListOf()
            for (value in pathElements) {
                localValues.add(value.clone())
            }
            clone.pathElements = localValues
        }

        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return PathQueryNode(pathElements)
    }
}
