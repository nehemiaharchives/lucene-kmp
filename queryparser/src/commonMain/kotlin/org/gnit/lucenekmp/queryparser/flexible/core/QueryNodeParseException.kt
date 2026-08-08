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

import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.parser.SyntaxParser
import org.gnit.lucenekmp.queryparser.flexible.messages.Message
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl

/**
 * This should be thrown when an exception happens during the query parsing from string to the query
 * node tree.
 *
 * @see QueryNodeException
 * @see SyntaxParser
 * @see QueryNode
 */
open class QueryNodeParseException : QueryNodeException {
    var query: CharSequence? = null
        set(value) {
            field = value
            messageObject = MessageImpl(QueryParserMessages.INVALID_SYNTAX_CANNOT_PARSE, value, "")
        }

    /**
     * For EndOfLine and EndOfFile ("&lt;EOF&gt;") parsing problems the last char in the string is
     * returned For the case where the parser is not able to figure out the line and column number -1
     * will be returned
     *
     * @return column of the first char where the problem was found
     * @param beginColumn the beginColumn to set
     */
    var beginColumn: Int = -1
        protected set

    /**
     * For EndOfLine and EndOfFile ("&lt;EOF&gt;") parsing problems the last char in the string is
     * returned For the case where the parser is not able to figure out the line and column number -1
     * will be returned
     *
     * @return line where the problem was found
     * @param beginLine the beginLine to set
     */
    var beginLine: Int = -1
        protected set

    /**
     * @param errorToken the errorToken in the query
     */
    var errorToken: String = ""
        protected set

    constructor(message: Message) : super(message)

    constructor(throwable: Throwable) : super(throwable)

    constructor(message: Message, throwable: Throwable) : super(message, throwable)

    fun setNonLocalizedMessage(message: Message) {
        messageObject = message
    }
}
