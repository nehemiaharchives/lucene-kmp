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

import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode

/**
 * A [QueryNodeProcessor] is an interface for classes that process a [QueryNode] tree.
 *
 * The implementor of this class should perform some operation on a query node tree and return the
 * same or another query node tree.
 *
 * It also may carry a [QueryConfigHandler] object that contains configuration about the query
 * represented by the query tree or the collection/index where it's intended to be executed.
 *
 * In case there is any [QueryConfigHandler] associated to the query tree to be processed, it should
 * be set using [queryConfigHandler] before [process] is invoked.
 *
 * @see QueryNode
 * @see QueryNodeProcessor
 * @see QueryConfigHandler
 */
interface QueryNodeProcessor {
    /**
     * Processes a query node tree. It may return the same or another query tree. I should never
     * return `null`.
     *
     * @param queryTree tree root node
     * @return the processed query tree
     */
    fun process(queryTree: QueryNode): QueryNode

    /** Sets and returns the [QueryConfigHandler] associated to the query tree. */
    var queryConfigHandler: QueryConfigHandler?
}
