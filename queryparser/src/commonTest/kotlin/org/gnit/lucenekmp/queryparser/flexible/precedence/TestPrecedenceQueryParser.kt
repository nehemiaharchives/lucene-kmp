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
package org.gnit.lucenekmp.queryparser.flexible.precedence

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.DateTools.Resolution
import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.standard.CommonQueryParserConfiguration
import org.gnit.lucenekmp.queryparser.flexible.standard.StandardQueryParser
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.Operator
import org.gnit.lucenekmp.queryparser.util.QueryParserTestBase
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests [PrecedenceQueryParser]. */
class TestPrecedenceQueryParser : QueryParserTestBase() {

    // Upstream's precedence test duplicates these QueryParserTestBase cases. Expose them explicitly
    // because kotlin.test does not discover inherited JUnit 3-style test methods.
    @Test override fun testSimple() = super.testSimple()
    @Test override fun testPunct() = super.testPunct()
    @Test override fun testSlop() = super.testSlop()
    @Test override fun testNumber() = super.testNumber()
    @Test override fun testWildcard() = super.testWildcard()
    @Test override fun testQPA() = super.testQPA()
    @Test override fun testRange() = super.testRange()
    @Test override fun testDateRange() = super.testDateRange()
    @Test override fun testEscaped() = super.testEscaped()
    @Test override fun testTabNewlineCarriageReturn() = super.testTabNewlineCarriageReturn()
    @Test override fun testSimpleDAO() = super.testSimpleDAO()
    @Test override fun testBoost() = super.testBoost()
    @Test override fun testException() = super.testException()
    @Test override fun testBooleanQuery() = super.testBooleanQuery()

    fun getParser(a: Analyzer?): PrecedenceQueryParser {
        val analyzer = a ?: MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
        val qp = PrecedenceQueryParser(analyzer)
        qp.defaultOperator = Operator.OR
        return qp
    }

    private fun parse(query: String, qp: PrecedenceQueryParser): Query {
        return qp.parse(query, getDefaultField())
    }

    override fun getParserConfig(a: Analyzer?): CommonQueryParserConfiguration = getParser(a)

    override fun getQuery(query: String, cqpC: CommonQueryParserConfiguration): Query {
        assertTrue(cqpC is PrecedenceQueryParser, "Parameter must be instance of PrecedenceQueryParser")
        return parse(query, cqpC as PrecedenceQueryParser)
    }

    override fun getQuery(query: String, a: Analyzer?): Query = parse(query, getParser(a))

    override fun isQueryParserException(exception: Exception): Boolean = exception is QueryNodeException

    override fun setDefaultOperatorOR(cqpC: CommonQueryParserConfiguration) {
        assertTrue(cqpC is StandardQueryParser)
        (cqpC as StandardQueryParser).defaultOperator = Operator.OR
    }

    override fun setDefaultOperatorAND(cqpC: CommonQueryParserConfiguration) {
        assertTrue(cqpC is StandardQueryParser)
        (cqpC as StandardQueryParser).defaultOperator = Operator.AND
    }

    override fun setAutoGeneratePhraseQueries(cqpC: CommonQueryParserConfiguration, value: Boolean) {
        throw UnsupportedOperationException()
    }

    override fun setDateResolution(
        cqpC: CommonQueryParserConfiguration,
        field: CharSequence,
        value: Resolution,
    ) {
        assertTrue(cqpC is StandardQueryParser)
        val qp = cqpC as StandardQueryParser
        val map = qp.dateResolutionMap?.toMutableMap() ?: mutableMapOf()
        map[field] = value
        qp.dateResolutionMap = map
    }

    override fun testDefaultOperator() {
        val qp = getParser(MockAnalyzer(random()))
        assertEquals(Operator.OR, qp.defaultOperator)
        setDefaultOperatorAND(qp)
        assertEquals(Operator.AND, qp.defaultOperator)
        setDefaultOperatorOR(qp)
        assertEquals(Operator.OR, qp.defaultOperator)
    }

    override fun testNewFieldQuery() {
        // Not part of the upstream TestPrecedenceQueryParser suite.
    }

    override fun testStarParsing() {
        // Upstream implementation is intentionally empty.
    }

    /**
     * This differs from the standard parser test: boolean AND binds more tightly than OR.
     */
    @Test
    override fun testPrecedence() {
        val parser = getParser(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        val query1 = parser.parse("A AND B OR C AND D", "field")
        val query2 = parser.parse("(A AND B) OR (C AND D)", "field")
        assertEquals(query1, query2)
    }
}
