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
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.util.StringUtils
import org.gnit.lucenekmp.queryparser.flexible.messages.NLS

/** A [QueryNodeImpl] is the default implementation of the interface [QueryNode] */
abstract class QueryNodeImpl : QueryNode, Cloneable<QueryNode> {
    /* index default field */
    // TODO remove PLAINTEXT_FIELD_NAME replacing it with configuration APIs

    private var leaf = true

    private var tags: MutableMap<String, Any> = mutableMapOf()

    private var clauses: MutableList<QueryNode>? = null

    protected fun allocate() {
        if (clauses == null) {
            clauses = mutableListOf()
        } else {
            clauses!!.clear()
        }
    }

    final override fun add(child: QueryNode) {
        if (isLeaf || clauses == null) {
            throw IllegalArgumentException(
                NLS.getLocalizedMessage(QueryParserMessages.NODE_ACTION_NOT_SUPPORTED),
            )
        }

        clauses!!.add(child)
        (child as QueryNodeImpl).setParent(this)
    }

    final override fun add(children: List<QueryNode>) {
        if (isLeaf || clauses == null) {
            throw IllegalArgumentException(
                NLS.getLocalizedMessage(QueryParserMessages.NODE_ACTION_NOT_SUPPORTED),
            )
        }

        for (child in children) {
            add(child)
        }
    }

    final override val isLeaf: Boolean
        get() = leaf

    final override fun set(children: List<QueryNode>) {
        if (isLeaf || clauses == null) {
            val message = NLS.getLocalizedMessage(QueryParserMessages.NODE_ACTION_NOT_SUPPORTED)

            throw IllegalArgumentException(message)
        }

        // reset parent value
        for (child in children) {
            child.removeFromParent()
        }

        val existingChildren = ArrayList(requireNotNull(this.children))
        for (existingChild in existingChildren) {
            existingChild.removeFromParent()
        }

        // allocate new children list
        allocate()

        // add new children and set parent
        add(children)
    }

    override fun cloneTree(): QueryNode {
        // Kotlin common has no Object.clone(). Concrete nodes create the same shallow runtime type.
        val clone = cloneNode()
        clone.leaf = leaf
        clone.parentNode = parentNode
        clone.toQueryStringIgnoreFields = toQueryStringIgnoreFields

        // Reset all tags
        clone.tags = mutableMapOf()

        // copy children
        if (clauses != null) {
            val localClauses: MutableList<QueryNode> = mutableListOf()
            for (clause in clauses!!) {
                localClauses.add(clause.cloneTree())
            }
            clone.clauses = localClauses
        }

        return clone
    }

    override fun clone(): QueryNode {
        return cloneTree()
    }

    /**
     * KMP replacement for Java's protected Object.clone(). Implementations return a shallow copy
     * of their own runtime type; [cloneTree] retains the upstream tag and child-copy behavior.
     */
    protected abstract fun cloneNode(): QueryNodeImpl

    protected fun setLeaf(isLeaf: Boolean) {
        leaf = isLeaf
    }

    /**
     * @return a List for QueryNode object. Returns null, for nodes that do not contain children. All
     * leaf Nodes return null.
     */
    final override val children: MutableList<QueryNode>?
        get() {
            if (isLeaf || clauses == null) {
                return null
            }
            return ArrayList(clauses!!)
        }

    override fun setTag(tagName: String, value: Any) {
        tags[tagName.lowercase()] = value
    }

    override fun unsetTag(tagName: String) {
        tags.remove(tagName.lowercase())
    }

    /** verify if a node contains a tag */
    override fun containsTag(tagName: String): Boolean {
        return tags.containsKey(tagName.lowercase())
    }

    override fun getTag(tagName: String): Any? {
        return tags[tagName.lowercase()]
    }

    private var parentNode: QueryNode? = null

    private fun setParent(parent: QueryNode) {
        if (parentNode !== parent) {
            removeFromParent()
            parentNode = parent
        }
    }

    final override val parent: QueryNode?
        get() = parentNode

    protected fun isRoot(): Boolean {
        return parent == null
    }

    /** If set to true the method toQueryString will not write field names */
    internal var toQueryStringIgnoreFields: Boolean = false

    /**
     * This method is use toQueryString to detect if fld is the default field
     *
     * @param fld - field name
     * @return true if fld is the default field
     */
    // TODO: remove this method, it's commonly used by {@link
    // #toQueryString(org.apache.lucene.queryParser.core.parser.EscapeQuerySyntax)}
    // to figure out what is the default field, however, {@link
    // #toQueryString(org.apache.lucene.queryParser.core.parser.EscapeQuerySyntax)}
    // should receive the default field value directly by parameter
    protected fun isDefaultField(fld: CharSequence?): Boolean {
        if (toQueryStringIgnoreFields) return true
        if (fld == null) return true
        if (PLAINTEXT_FIELD_NAME == StringUtils.toString(fld)) return true
        return false
    }

    /**
     * Every implementation of this class should return pseudo xml like this:
     *
     * For FieldQueryNode: &lt;field start='1' end='2' field='subject' text='foo'/&gt;
     *
     * @see QueryNode.toString
     */
    override fun toString(): String {
        return super.toString()
    }

    /**
     * Returns a map containing all tags attached to this query node.
     *
     * @return a map containing all tags attached to this query node
     */
    final override val tagMap: Map<String, Any>
        get() = tags.toMap()

    override fun removeChildren(childNode: QueryNode) {
        val it = clauses!!.iterator()
        while (it.hasNext()) {
            if (it.next() === childNode) {
                it.remove()
            }
        }
        childNode.removeFromParent()
    }

    override fun removeFromParent() {
        if (parentNode != null) {
            val parent = parentNode!!
            parentNode = null
            parent.removeChildren(this)
        }
    }

    companion object {
        const val PLAINTEXT_FIELD_NAME: String = "_plain"
    }
} // end class QueryNodeImpl
