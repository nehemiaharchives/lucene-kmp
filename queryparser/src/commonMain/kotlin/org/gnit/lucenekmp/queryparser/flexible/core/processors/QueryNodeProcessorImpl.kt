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
package org.gnit.lucenekmp.queryparser.flexible.core.processors

import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode

/**
 * This is a default implementation for the [QueryNodeProcessor] interface, it's an abstract class,
 * so it should be extended by classes that want to process a [QueryNode] tree.
 *
 * This class process [QueryNode]s from left to right in the tree. While it's walking down the tree,
 * for every node, [preProcessNode] is invoked. After a node's children are processed,
 * [postProcessNode] is invoked for that node. [setChildrenOrder] is invoked before
 * [postProcessNode] only if the node has at least one child, in [setChildrenOrder] the implementor
 * might redefine the children order or remove any children from the children list.
 *
 * Here is an example about how it process the nodes:
 *
 * ```
 *      a
 *     / \
 *    b   e
 *   / \
 *  c   d
 * ```
 *
 * Here is the order the methods would be invoked for the tree described above:
 *
 * ```
 *      preProcessNode( a );
 *      preProcessNode( b );
 *      preProcessNode( c );
 *      postProcessNode( c );
 *      preProcessNode( d );
 *      postProcessNode( d );
 *      setChildrenOrder( bChildrenList );
 *      postProcessNode( b );
 *      preProcessNode( e );
 *      postProcessNode( e );
 *      setChildrenOrder( aChildrenList );
 *      postProcessNode( a )
 * ```
 *
 * @see org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessor
 */
abstract class QueryNodeProcessorImpl : QueryNodeProcessor {
    private val childrenListPool: ArrayList<ChildrenList> = ArrayList()

    final override var queryConfigHandler: QueryConfigHandler?

    constructor() {
        // empty constructor
        queryConfigHandler = null
    }

    constructor(queryConfigHandler: QueryConfigHandler) {
        this.queryConfigHandler = queryConfigHandler
    }

    override fun process(queryTree: QueryNode): QueryNode {
        return processIteration(queryTree)
    }

    private fun processIteration(queryTree: QueryNode): QueryNode {
        var queryTree = queryTree
        queryTree = preProcessNode(queryTree)

        processChildren(queryTree)

        queryTree = postProcessNode(queryTree)

        return queryTree
    }

    /**
     * This method is called every time a child is processed.
     *
     * @param queryTree the query node child to be processed
     * @throws QueryNodeException if something goes wrong during the query node processing
     */
    protected open fun processChildren(queryTree: QueryNode) {
        val children = queryTree.children
        val newChildren: ChildrenList

        if (children != null && children.size > 0) {
            newChildren = allocateChildrenList()

            try {
                for (originalChild in children) {
                    var child = originalChild
                    child = processIteration(child)

                    newChildren.add(child)
                }

                val orderedChildrenList = setChildrenOrder(newChildren)

                queryTree.set(orderedChildrenList)
            } finally {
                newChildren.beingUsed = false
            }
        }
    }

    private fun allocateChildrenList(): ChildrenList {
        var list: ChildrenList? = null

        for (auxList in childrenListPool) {
            if (!auxList.beingUsed) {
                list = auxList
                list.clear()

                break
            }
        }

        if (list == null) {
            list = ChildrenList()
            childrenListPool.add(list)
        }

        list.beingUsed = true

        return list
    }

    /**
     * This method is invoked for every node when walking down the tree.
     *
     * @param node the query node to be pre-processed
     * @return a query node
     * @throws QueryNodeException if something goes wrong during the query node processing
     */
    protected abstract fun preProcessNode(node: QueryNode): QueryNode

    /**
     * This method is invoked for every node when walking up the tree.
     *
     * @param node node the query node to be post-processed
     * @return a query node
     * @throws QueryNodeException if something goes wrong during the query node processing
     */
    protected abstract fun postProcessNode(node: QueryNode): QueryNode

    /**
     * This method is invoked for every node that has at least on child. It's invoked right before
     * [postProcessNode] is invoked.
     *
     * @param children the list containing all current node's children
     * @return a new list containing all children that should be set to the current node
     * @throws QueryNodeException if something goes wrong during the query node processing
     */
    protected abstract fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode>

    private class ChildrenList : MutableList<QueryNode> by mutableListOf() {
        var beingUsed: Boolean = false
    }
}
