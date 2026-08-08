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
package org.gnit.lucenekmp.queryparser.flexible.core.builders

import kotlin.reflect.KClass
import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldableNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl

/**
 * This class should be used when there is a builder for each type of node.
 *
 * The type of node may be defined in 2 different ways: - by the field name, when the node implements
 * the [FieldableNode] interface - by its class, it keeps checking the class and all the interfaces
 * and classes this class implements/extends until it finds a builder for that class/interface
 *
 * This class always check if there is a builder for the field name before it checks for the node
 * class. So, field name builders have precedence over class builders.
 *
 * When a builder is found for a node, it's called and the node is passed to the builder. If the
 * returned built object is not `null`, it's tagged on the node using the tag
 * [QUERY_TREE_BUILDER_TAGID].
 *
 * The children are usually built before the parent node. However, if a builder associated to a node
 * is an instance of [QueryTreeBuilder], the node is delegated to this builder and it's responsible
 * to build the node and its children.
 *
 * @see QueryBuilder
 */
open class QueryTreeBuilder : QueryBuilder {
    private var queryNodeBuilders: MutableMap<KClass<out QueryNode>, QueryBuilder>? = null

    private var fieldNameBuilders: MutableMap<String, QueryBuilder>? = null

    /** [QueryTreeBuilder] constructor. */
    init {
        // empty constructor
    }

    /**
     * Associates a field name with a builder.
     *
     * @param fieldName the field name
     * @param builder the builder to be associated
     */
    fun setBuilder(fieldName: CharSequence, builder: QueryBuilder) {
        if (fieldNameBuilders == null) {
            fieldNameBuilders = mutableMapOf()
        }

        fieldNameBuilders!![fieldName.toString()] = builder
    }

    /**
     * Associates a class with a builder
     *
     * @param queryNodeClass the class
     * @param builder the builder to be associated
     */
    fun setBuilder(queryNodeClass: KClass<out QueryNode>, builder: QueryBuilder) {
        if (queryNodeBuilders == null) {
            queryNodeBuilders = mutableMapOf()
        }

        queryNodeBuilders!![queryNodeClass] = builder
    }

    private fun process(node: QueryNode) {
        val builder = getBuilder(node)

        if (builder !is QueryTreeBuilder) {
            val children = node.children

            if (children != null) {
                for (child in children) {
                    process(child)
                }
            }
        }

        processNode(node, builder)
    }

    private fun getBuilder(node: QueryNode): QueryBuilder? {
        var builder: QueryBuilder? = null

        if (fieldNameBuilders != null && node is FieldableNode) {
            var field = node.field

            if (field != null) {
                field = field.toString()
                builder = fieldNameBuilders!![field]
            }
        }

        if (builder == null && queryNodeBuilders != null) {
            val clazz = node::class

            builder = getQueryBuilder(clazz)

            if (builder == null) {
                // Kotlin common has no superclass/interface traversal API. KClass.isInstance keeps
                // the Java behavior for registered superclasses and interfaces.
                for ((actualClass, actualBuilder) in queryNodeBuilders!!) {
                    if (actualClass.isInstance(node)) {
                        builder = actualBuilder
                        break
                    }
                }
            }
        }

        return builder
    }

    private fun processNode(node: QueryNode, builder: QueryBuilder?) {
        if (builder == null) {
            throw QueryNodeException(
                MessageImpl(
                    QueryParserMessages.LUCENE_QUERY_CONVERSION_ERROR,
                    node.toQueryString(EscapeQuerySyntaxImpl()),
                    node::class.qualifiedName,
                ),
            )
        }

        val obj = builder.build(node)

        if (obj != null) {
            node.setTag(QUERY_TREE_BUILDER_TAGID, obj)
        }
    }

    private fun getQueryBuilder(clazz: KClass<out QueryNode>): QueryBuilder? {
        return queryNodeBuilders!![clazz]
    }

    /**
     * Builds some kind of object from a query tree. Each node in the query tree is built using an
     * specific builder associated to it.
     *
     * @param queryNode the query tree root node
     * @return the built object
     * @throws QueryNodeException if some node builder throws a [QueryNodeException] or if there is a
     * node which had no builder associated to it
     */
    override fun build(queryNode: QueryNode): Any? {
        process(queryNode)

        return queryNode.getTag(QUERY_TREE_BUILDER_TAGID)
    }

    companion object {
        /**
         * This tag is used to tag the nodes in a query tree with the built objects produced from
         * their own associated builder.
         */
        val QUERY_TREE_BUILDER_TAGID: String = requireNotNull(QueryTreeBuilder::class.qualifiedName)
    }
}
