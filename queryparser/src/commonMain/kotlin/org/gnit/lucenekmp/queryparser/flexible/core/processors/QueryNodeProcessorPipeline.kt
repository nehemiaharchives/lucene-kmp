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
 * A [QueryNodeProcessorPipeline] class should be used to build a query node processor pipeline.
 *
 * When a query node tree is processed using this class, it passes the query node tree to each
 * processor on the pipeline and the result from each processor is passed to the next one, always
 * following the order the processors were on the pipeline.
 *
 * When a [QueryConfigHandler] object is set on a [QueryNodeProcessorPipeline], it also takes care
 * of setting this [QueryConfigHandler] on all processor on pipeline.
 */
open class QueryNodeProcessorPipeline : QueryNodeProcessor, MutableList<QueryNodeProcessor> {
    private val processors: MutableList<QueryNodeProcessor> = mutableListOf()

    /**
     * For reference about this property check: [QueryNodeProcessor.queryConfigHandler].
     *
     * Setting this property sets the query configuration handler on every processor in the
     * pipeline. Reading it returns the query configuration handler associated with the pipeline.
     *
     * @see QueryNodeProcessor.queryConfigHandler
     * @see QueryConfigHandler
     */
    override var queryConfigHandler: QueryConfigHandler? = null
        set(value) {
            field = value

            for (processor in processors) {
                processor.queryConfigHandler = field
            }
        }

    /** Constructs an empty query node processor pipeline. */
    constructor() {
        // empty constructor
    }

    /** Constructs with a [QueryConfigHandler] object. */
    constructor(queryConfigHandler: QueryConfigHandler) {
        this.queryConfigHandler = queryConfigHandler
    }

    /**
     * For reference about this method check: [QueryNodeProcessor.process].
     *
     * @param queryTree the query node tree to be processed
     * @throws QueryNodeException if something goes wrong during the query node processing
     * @see QueryNode
     */
    override fun process(queryTree: QueryNode): QueryNode {
        var queryTree = queryTree

        for (processor in processors) {
            queryTree = processor.process(queryTree)
        }

        return queryTree
    }

    /**
     * @see MutableList.add
     */
    override fun add(element: QueryNodeProcessor): Boolean {
        val added = processors.add(element)

        if (added) {
            element.queryConfigHandler = queryConfigHandler
        }

        return added
    }

    /**
     * @see MutableList.add
     */
    override fun add(index: Int, element: QueryNodeProcessor) {
        processors.add(index, element)
        element.queryConfigHandler = queryConfigHandler
    }

    /**
     * @see MutableList.addAll
     */
    override fun addAll(elements: Collection<QueryNodeProcessor>): Boolean {
        val anyAdded = processors.addAll(elements)

        for (processor in elements) {
            processor.queryConfigHandler = queryConfigHandler
        }

        return anyAdded
    }

    /**
     * @see MutableList.addAll
     */
    override fun addAll(index: Int, elements: Collection<QueryNodeProcessor>): Boolean {
        val anyAdded = processors.addAll(index, elements)

        for (processor in elements) {
            processor.queryConfigHandler = queryConfigHandler
        }

        return anyAdded
    }

    /**
     * @see MutableList.clear
     */
    override fun clear() {
        processors.clear()
    }

    /**
     * @see MutableList.contains
     */
    override fun contains(element: QueryNodeProcessor): Boolean {
        return processors.contains(element)
    }

    /**
     * @see MutableList.containsAll
     */
    override fun containsAll(elements: Collection<QueryNodeProcessor>): Boolean {
        return processors.containsAll(elements)
    }

    /**
     * @see MutableList.get
     */
    override fun get(index: Int): QueryNodeProcessor {
        return processors[index]
    }

    /**
     * @see MutableList.indexOf
     */
    override fun indexOf(element: QueryNodeProcessor): Int {
        return processors.indexOf(element)
    }

    /**
     * @see MutableList.isEmpty
     */
    override fun isEmpty(): Boolean {
        return processors.isEmpty()
    }

    /**
     * @see MutableList.iterator
     */
    override fun iterator(): MutableIterator<QueryNodeProcessor> {
        return processors.iterator()
    }

    /**
     * @see MutableList.lastIndexOf
     */
    override fun lastIndexOf(element: QueryNodeProcessor): Int {
        return processors.lastIndexOf(element)
    }

    /**
     * @see MutableList.listIterator
     */
    override fun listIterator(): MutableListIterator<QueryNodeProcessor> {
        return processors.listIterator()
    }

    /**
     * @see MutableList.listIterator
     */
    override fun listIterator(index: Int): MutableListIterator<QueryNodeProcessor> {
        return processors.listIterator(index)
    }

    /**
     * @see MutableList.remove
     */
    override fun remove(element: QueryNodeProcessor): Boolean {
        return processors.remove(element)
    }

    /**
     * @see MutableList.removeAt
     */
    override fun removeAt(index: Int): QueryNodeProcessor {
        return processors.removeAt(index)
    }

    /**
     * @see MutableList.removeAll
     */
    override fun removeAll(elements: Collection<QueryNodeProcessor>): Boolean {
        return processors.removeAll(elements)
    }

    /**
     * @see MutableList.retainAll
     */
    override fun retainAll(elements: Collection<QueryNodeProcessor>): Boolean {
        return processors.retainAll(elements)
    }

    /**
     * @see MutableList.set
     */
    override fun set(index: Int, element: QueryNodeProcessor): QueryNodeProcessor {
        val oldProcessor = processors.set(index, element)

        if (oldProcessor !== element) {
            element.queryConfigHandler = queryConfigHandler
        }

        return oldProcessor
    }

    /**
     * @see MutableList.size
     */
    override val size: Int
        get() = processors.size

    /**
     * @see MutableList.subList
     */
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<QueryNodeProcessor> {
        return processors.subList(fromIndex, toIndex)
    }
}
