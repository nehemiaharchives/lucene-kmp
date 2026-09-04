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
package org.gnit.lucenekmp.queryparser.flexible.core

import org.gnit.lucenekmp.queryparser.flexible.core.builders.QueryBuilder
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.parser.SyntaxParser
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessor

/**
 * This class is a helper for the query parser framework, it does all the three query parser phrases
 * at once: text parsing, query processing and query building.
 *
 * It contains methods that allows the user to change the implementation used on the three phases.
 *
 * @see QueryNodeProcessor
 * @see SyntaxParser
 * @see QueryBuilder
 * @see QueryConfigHandler
 */
open class QueryParserHelper(
    /**
     * Creates a query parser helper object using the specified configuration, text parser,
     * processor and builder.
     *
     * @param queryConfigHandler the query configuration handler that will be initially set to this
     *     helper
     * @param syntaxParser the text parser that will be initially set to this helper
     * @param processor the query processor that will be initially set to this helper
     * @param builder the query builder that will be initially set to this helper
     * @see QueryNodeProcessor
     * @see SyntaxParser
     * @see QueryBuilder
     * @see QueryConfigHandler
     */
    queryConfigHandler: QueryConfigHandler?,
    syntaxParser: SyntaxParser,
    processor: QueryNodeProcessor?,
    builder: QueryBuilder,
) {
    /**
     * Returns the processor object used to process the query node tree, it returns `null` if no
     * processor is used.
     *
     * @return the actual processor used to process the query node tree, `null` if no processor is
     *     used
     * @see QueryNodeProcessor
     */
    var queryNodeProcessor: QueryNodeProcessor? = processor
        /**
         * Sets the processor that will be used to process the query node tree. If there is any
         * [QueryConfigHandler] returned by [queryConfigHandler], it will be set on the processor.
         * The argument can be `null`, which means that no processor will be used to process the
         * query node tree.
         *
         * @param processor the processor that will be used to process the query node tree, this
         *     argument can be `null`
         * @see QueryNodeProcessor
         */
        set(processor) {
            field = processor
            field!!.queryConfigHandler = queryConfigHandler
        }

    /**
     * Returns the text parser used to build a query node tree from a query string. The default text
     * parser instance returned by this method is a [SyntaxParser].
     *
     * @return the text parse used to build query node trees.
     * @see SyntaxParser
     */
    var syntaxParser: SyntaxParser = syntaxParser
        /**
         * Sets the text parser that will be used to parse the query string, it cannot be `null`.
         *
         * @param syntaxParser the text parser that will be used to parse the query string
         * @see SyntaxParser
         */
        set(syntaxParser) {
            field = syntaxParser
        }

    /**
     * Returns the query builder used to build a object from the query node tree. The object produced
     * by this builder is returned by [parse].
     *
     * @return the query builder
     * @see QueryBuilder
     */
    var queryBuilder: QueryBuilder = builder
        /**
         * The query builder that will be used to build an object from the query node tree. It cannot
         * be `null`.
         *
         * @param queryBuilder the query builder used to build something from the query node tree
         * @see QueryBuilder
         */
        set(queryBuilder) {
            field = queryBuilder
        }

    /**
     * Returns the query configuration handler, which is used during the query node tree processing.
     * It can be `null`.
     *
     * @return the query configuration handler used on the query processing, `null` if not query
     *     configuration handler is defined
     * @see QueryConfigHandler
     */
    var queryConfigHandler: QueryConfigHandler? = queryConfigHandler
        /**
         * Sets the query configuration handler that will be used during query processing. It can be
         * `null`. It's also set to the processor returned by [queryNodeProcessor].
         *
         * @param config the query configuration handler used during query processing, it can be
         *     `null`
         * @see QueryConfigHandler
         */
        set(config) {
            field = config
            val processor = queryNodeProcessor

            if (processor != null) {
                processor.queryConfigHandler = config
            }
        }

    init {
        if (processor != null) {
            processor.queryConfigHandler = queryConfigHandler
        }
    }

    /**
     * Parses a query string to an object, usually some query object.<br> <br> In this method the
     * three phases are executed: <br> <br> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1st - the query string is
     * parsed using the text parser returned by [syntaxParser], the result is a query node tree <br>
     * <br> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2nd - the query node tree is processed by the processor
     * returned by [queryNodeProcessor] <br> <br> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;3th - a object is
     * built from the query node tree using the builder returned by [queryBuilder]
     *
     * @param query the query string
     * @param defaultField the default field used by the text parser
     * @return the object built from the query
     * @throws QueryNodeException if something wrong happens along the three phases
     */
    open fun parse(query: String, defaultField: String?): Any? {
        var queryTree = syntaxParser.parse(query, defaultField)

        val processor = queryNodeProcessor

        if (processor != null) {
            queryTree = processor.process(queryTree)
        }

        return queryBuilder.build(queryTree)
    }
}
