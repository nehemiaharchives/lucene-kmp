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

/** A [QueryNode] is an interface implemented by all nodes on a QueryNode tree. */
interface QueryNode {
    /** convert to a query string understood by the query parser */
    // TODO: this interface might be changed in the future
    fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence

    /** for printing */

    /** get Children nodes */
    val children: MutableList<QueryNode>?

    /** verify if a node is a Leaf node */
    val isLeaf: Boolean

    /** verify if a node contains a tag */
    fun containsTag(tagName: String): Boolean

    /** Returns object stored under that tag name */
    fun getTag(tagName: String): Any?

    val parent: QueryNode?

    /**
     * Recursive clone the QueryNode tree The tags are not copied to the new tree when you call the
     * cloneTree() method
     *
     * @return the cloned tree
     */
    fun cloneTree(): QueryNode

    // Below are the methods that can change state of a QueryNode
    // Write Operations (not Thread Safe)

    // add a new child to a non Leaf node
    fun add(child: QueryNode)

    fun add(children: List<QueryNode>)

    // reset the children of a node
    fun set(children: List<QueryNode>)

    /**
     * Associate the specified value with the specified tagName. If the tagName already exists, the
     * old value is replaced. The tagName and value cannot be null. tagName will be converted to
     * lowercase.
     */
    fun setTag(tagName: String, value: Any)

    /** Unset a tag. tagName will be converted to lowercase. */
    fun unsetTag(tagName: String)

    /**
     * Returns a map containing all tags attached to this query node.
     *
     * @return a map containing all tags attached to this query node
     */
    val tagMap: Map<String, Any>

    /** Removes this query node from its parent. */
    fun removeFromParent()

    /**
     * Remove a child node
     *
     * @param childNode Which child to remove
     */
    fun removeChildren(childNode: QueryNode)
}
