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
package org.gnit.lucenekmp.queryparser.flexible.standard

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.Query

/** This class defines utility methods to (help) parse query strings into [Query] objects. */
object QueryParserUtil {

    /**
     * Parses a query which searches on the fields specified.
     *
     * <p>If x fields are specified, this effectively constructs:
     *
     * <pre>
     * <code>
     * (field1:query1) (field2:query2) (field3:query3)...(fieldx:queryx)
     * </code>
     * </pre>
     *
     * @param queries Queries strings to parse
     * @param fields Fields to search on
     * @param analyzer Analyzer to use
     * @throws IllegalArgumentException if the length of the queries array differs from the length
     *     of the fields array
     */
    fun parse(queries: Array<String>, fields: Array<String>, analyzer: Analyzer): Query {
        if (queries.size != fields.size) {
            throw IllegalArgumentException("queries.length != fields.length")
        }
        val bQuery = BooleanQuery.Builder()

        val qp = StandardQueryParser()
        qp.analyzer = analyzer

        for (i in fields.indices) {
            val q: Query? = qp.parse(queries[i], fields[i])

            if (q != null) { // q never null, just being defensive
                bQuery.add(q, BooleanClause.Occur.SHOULD)
            }
        }
        return bQuery.build()
    }

    /**
     * Parses a query, searching on the fields specified. Use this if you need to specify certain
     * fields as required, and others as prohibited.
     *
     * <p>Usage:
     *
     * <pre class="prettyprint">
     * <code>
     * String[] fields = {&quot;filename&quot;, &quot;contents&quot;, &quot;description&quot;};
     * BooleanClause.Occur[] flags = {BooleanClause.Occur.SHOULD,
     *                BooleanClause.Occur.MUST,
     *                BooleanClause.Occur.MUST_NOT};
     * MultiFieldQueryParser.parse(&quot;query&quot;, fields, flags, analyzer);
     * </code>
     * </pre>
     *
     * <p>The code above would construct a query:
     *
     * <pre>
     * <code>
     * (filename:query) +(contents:query) -(description:query)
     * </code>
     * </pre>
     *
     * @param query Query string to parse
     * @param fields Fields to search on
     * @param flags Flags describing the fields
     * @param analyzer Analyzer to use
     * @throws IllegalArgumentException if the length of the fields array differs from the length of
     *     the flags array
     */
    fun parse(
        query: String,
        fields: Array<String>,
        flags: Array<BooleanClause.Occur>,
        analyzer: Analyzer
    ): Query {
        if (fields.size != flags.size) {
            throw IllegalArgumentException("fields.length != flags.length")
        }
        val bQuery = BooleanQuery.Builder()

        val qp = StandardQueryParser()
        qp.analyzer = analyzer

        for (i in fields.indices) {
            val q: Query? = qp.parse(query, fields[i])

            if (q != null) { // q never null, just being defensive
                bQuery.add(q, flags[i])
            }
        }
        return bQuery.build()
    }

    /**
     * Parses a query, searching on the fields specified. Use this if you need to specify certain
     * fields as required, and others as prohibited.
     *
     * <p>Usage:
     *
     * <pre class="prettyprint">
     * <code>
     * String[] query = {&quot;query1&quot;, &quot;query2&quot;, &quot;query3&quot;};
     * String[] fields = {&quot;filename&quot;, &quot;contents&quot;, &quot;description&quot;};
     * BooleanClause.Occur[] flags = {BooleanClause.Occur.SHOULD,
     *                BooleanClause.Occur.MUST,
     *                BooleanClause.Occur.MUST_NOT};
     * MultiFieldQueryParser.parse(query, fields, flags, analyzer);
     * </code>
     * </pre>
     *
     * <p>The code above would construct a query:
     *
     * <pre>
     * <code>
     * (filename:query1) +(contents:query2) -(description:query3)
     * </code>
     * </pre>
     *
     * @param queries Queries string to parse
     * @param fields Fields to search on
     * @param flags Flags describing the fields
     * @param analyzer Analyzer to use
     * @throws IllegalArgumentException if the length of the queries, fields, and flags array differ
     */
    fun parse(
        queries: Array<String>,
        fields: Array<String>,
        flags: Array<BooleanClause.Occur>,
        analyzer: Analyzer
    ): Query {
        if (!(queries.size == fields.size && queries.size == flags.size)) {
            throw IllegalArgumentException(
                "queries, fields, and flags array have have different length"
            )
        }
        val bQuery = BooleanQuery.Builder()

        val qp = StandardQueryParser()
        qp.analyzer = analyzer

        for (i in fields.indices) {
            val q: Query? = qp.parse(queries[i], fields[i])

            if (q != null) { // q never null, just being defensive
                bQuery.add(q, flags[i])
            }
        }
        return bQuery.build()
    }

    /**
     * Returns a String where those characters that TextParser expects to be escaped are escaped by
     * a preceding `\`.
     */
    fun escape(s: String): String {
        val sb = StringBuilder()
        for (i in s.indices) {
            val c = s[i]
            // These characters are part of the query syntax and must be escaped
            if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' ||
                c == ':' || c == '^' || c == '[' || c == ']' || c == '"' || c == '{' ||
                c == '}' || c == '~' || c == '*' || c == '?' || c == '|' || c == '&' ||
                c == '/'
            ) {
                sb.append('\\')
            }
            sb.append(c)
        }
        return sb.toString()
    }
}
