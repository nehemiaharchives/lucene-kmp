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
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.queryparser.charstream.FastCharStream
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.IntervalQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParser
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Test interval sub-query support in [StandardQueryParser]. */
class TestStandardQPEnhancements : LuceneTestCase() {

    private fun getQueryParser(): StandardQueryParser {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                return TokenStreamComponents(MockTokenizer(MockTokenizer.WHITESPACE, true))
            }
        }

        val qp = StandardQueryParser(analyzer)
        qp.defaultOperator = StandardQueryConfigHandler.Operator.AND
        qp.multiFields = emptyArray()
        return qp
    }

    @Test
    fun testMinShouldMatchOperator() {
        val parsed = parsedQuery(
            "($FLD_WHITESPACE:foo OR $FLD_WHITESPACE:bar OR $FLD_WHITESPACE:baz)@2"
        )

        assertEquals(2, assertIs<BooleanQuery>(parsed).minimumNumberShouldMatch)
    }

    @Test
    fun testAtLeast() = checkIntervalQueryNode("fn:atleast(3 FOO BAR baz)")

    @Test
    fun testMaxWidth() = checkIntervalQueryNode("fn:maxwidth(3 fn:atleast(2 foo bar baz))")

    @Test
    fun testQuotedTerm() = checkIntervalQueryNode("fn:atleast(2 \"foo\" \"BAR baz\")")

    @Test
    fun testMaxGaps() = checkIntervalQueryNode("fn:maxgaps(2 fn:unordered(foo BAR baz))")

    @Test
    fun testOrdered() = checkIntervalQueryNode("fn:ordered(foo BAR baz)")

    @Test
    fun testUnordered() = checkIntervalQueryNode("fn:unordered(foo BAR baz)")

    @Test
    fun testOr() = checkIntervalQueryNode("fn:or(foo baz)")

    @Test
    fun testWildcard() {
        checkIntervalQueryNode("fn:wildcard(foo*)")

        // Explicit maxExpansions.
        checkIntervalQueryNode("fn:wildcard(foo* 128)")
    }

    @Test
    fun testPhrase() = checkIntervalQueryNode("fn:phrase(abc def fn:or(baz boo))")

    @Test
    fun testBefore() = checkIntervalQueryNode("fn:before(abc fn:ordered(foo bar))")

    @Test
    fun testAfter() = checkIntervalQueryNode("fn:after(abc fn:ordered(foo bar))")

    @Test
    fun testContaining() = checkIntervalQueryNode("fn:containing(big small)")

    @Test
    fun testContainedBy() = checkIntervalQueryNode("fn:containedBy(small big)")

    @Test
    fun testNotContaining() = checkIntervalQueryNode("fn:notContaining(minuend subtrahend)")

    @Test
    fun testNotContainedBy() = checkIntervalQueryNode("fn:notContainedBy(small big)")

    @Test
    fun testWithin() = checkIntervalQueryNode("fn:within(small 2 fn:ordered(big foo))")

    @Test
    fun testNotWithin() = checkIntervalQueryNode("fn:notWithin(small 2 fn:ordered(big foo))")

    @Test
    fun testOverlapping() = checkIntervalQueryNode("fn:overlapping(fn:ordered(big foo) small)")

    @Test
    fun testNonOverlapping() =
        checkIntervalQueryNode("fn:nonOverlapping(fn:ordered(big foo) small)")

    @Test
    fun testUnorderedNoOverlaps() =
        checkIntervalQueryNode("fn:unorderedNoOverlaps(fn:ordered(big foo) small)")

    @Test
    fun testExtend() = checkIntervalQueryNode("fn:extend(fn:ordered(big foo) 2 5)")

    @Test
    fun testFuzzy() {
        checkIntervalQueryNode("fn:fuzzyTerm(dfe)")
        // Explicit maxEdits
        checkIntervalQueryNode("fn:fuzzyTerm(dfe 2)")
        // Explicit maxExpansions
        checkIntervalQueryNode("fn:fuzzyTerm(dfe 2 128)")
    }

    private fun checkIntervalQueryNode(query: String) {
        // Check raw parser first.
        val syntaxParser = StandardSyntaxParser(FastCharStream(StringReader(query)))
        val queryNode: QueryNode = syntaxParser.TopLevelQuery(FLD_DEFAULT)
        val intervalQueryNode = assertIs<IntervalQueryNode>(queryNode)

        val queryParser = getQueryParser()
        val parsedQuery: Query
        if (random().nextBoolean()) {
            queryParser.multiFields = arrayOf(FLD_DEFAULT)
            parsedQuery = queryParser.parse(query, null)
        } else {
            parsedQuery = queryParser.parse(query, FLD_DEFAULT)
        }
        assertNotNull(parsedQuery)

        // Emit toString() for visual diagnostics.
        intervalQueryNode.setAnalyzer(requireNotNull(queryParser.analyzer))
        println("query: $query\n  node: $queryNode\n  query: $parsedQuery")
    }

    private fun parsed(query: String): String {
        return parsedQuery(query).toString("<no-default>")
    }

    private fun parsedQuery(query: String): Query {
        return getQueryParser().parse(query, /* no default field. */ null)
    }

    companion object {
        private const val FLD_DEFAULT = "defaultField"
        private const val FLD_WHITESPACE = "whitespaceField"
    }
}
