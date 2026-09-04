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
package org.gnit.lucenekmp.queryparser.flexible.core.messages

/** Flexible Query Parser message bundle class */
object QueryParserMessages {
    // Do not instantiate

    // register all string ids with NLS class and initialize static string
    // values

    // static string must match the strings in the property files.
    const val INVALID_SYNTAX: String = "INVALID_SYNTAX"
    const val INVALID_SYNTAX_CANNOT_PARSE: String = "INVALID_SYNTAX_CANNOT_PARSE"
    const val INVALID_SYNTAX_FUZZY_LIMITS: String = "INVALID_SYNTAX_FUZZY_LIMITS"
    const val INVALID_SYNTAX_FUZZY_EDITS: String = "INVALID_SYNTAX_FUZZY_EDITS"
    const val INVALID_SYNTAX_ESCAPE_UNICODE_TRUNCATION: String = "INVALID_SYNTAX_ESCAPE_UNICODE_TRUNCATION"
    const val INVALID_SYNTAX_ESCAPE_CHARACTER: String = "INVALID_SYNTAX_ESCAPE_CHARACTER"
    const val INVALID_SYNTAX_ESCAPE_NONE_HEX_UNICODE: String = "INVALID_SYNTAX_ESCAPE_NONE_HEX_UNICODE"
    const val NODE_ACTION_NOT_SUPPORTED: String = "NODE_ACTION_NOT_SUPPORTED"
    const val PARAMETER_VALUE_NOT_SUPPORTED: String = "PARAMETER_VALUE_NOT_SUPPORTED"
    const val LUCENE_QUERY_CONVERSION_ERROR: String = "LUCENE_QUERY_CONVERSION_ERROR"
    const val EMPTY_MESSAGE: String = "EMPTY_MESSAGE"
    const val WILDCARD_NOT_SUPPORTED: String = "WILDCARD_NOT_SUPPORTED"
    const val TOO_MANY_BOOLEAN_CLAUSES: String = "TOO_MANY_BOOLEAN_CLAUSES"
    const val LEADING_WILDCARD_NOT_ALLOWED: String = "LEADING_WILDCARD_NOT_ALLOWED"
    const val COULD_NOT_PARSE_NUMBER: String = "COULD_NOT_PARSE_NUMBER"
    const val NUMBER_CLASS_NOT_SUPPORTED_BY_NUMERIC_RANGE_QUERY: String = "NUMBER_CLASS_NOT_SUPPORTED_BY_NUMERIC_RANGE_QUERY"
    const val UNSUPPORTED_NUMERIC_DATA_TYPE: String = "UNSUPPORTED_NUMERIC_DATA_TYPE"
    const val NUMERIC_CANNOT_BE_EMPTY: String = "NUMERIC_CANNOT_BE_EMPTY"
    const val ANALYZER_REQUIRED: String = "ANALYZER_REQUIRED"
}
