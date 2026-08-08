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
import org.gnit.lucenekmp.document.DateTools.Resolution
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.Operator
import org.gnit.lucenekmp.queryparser.util.QueryParserTestBase
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.WildcardQuery
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests QueryParser. */
class TestStandardQP : QueryParserTestBase() {

    // kotlin.test does not discover inherited JUnit 3-style test methods, so expose the inherited
    // Java test methods explicitly while leaving their logic in QueryParserTestBase.
    @Test override fun testCJK() = super.testCJK()
    @Test override fun testCJKTerm() = super.testCJKTerm()
    @Test override fun testCJKBoostedTerm() = super.testCJKBoostedTerm()
    @Test override fun testCJKPhrase() = super.testCJKPhrase()
    @Test override fun testCJKBoostedPhrase() = super.testCJKBoostedPhrase()
    @Test override fun testCJKSloppyPhrase() = super.testCJKSloppyPhrase()
    @Test override fun testSimple() = super.testSimple()
    @Test override fun testPunct() = super.testPunct()
    @Test override fun testSlop() = super.testSlop()
    @Test override fun testNumber() = super.testNumber()
    @Test override fun testWildcard() = super.testWildcard()
    @Test override fun testLeadingWildcardType() = super.testLeadingWildcardType()
    @Test override fun testRange() = super.testRange()
    @Test override fun testRangeQueryEndpointTO() = super.testRangeQueryEndpointTO()
    @Test override fun testRangeQueryRequiresTO() = super.testRangeQueryRequiresTO()
    @Test override fun testDateRange() = super.testDateRange()
    @Test override fun testEscaped() = super.testEscaped()
    @Test override fun testQueryStringEscaping() = super.testQueryStringEscaping()
    @Test override fun testTabNewlineCarriageReturn() = super.testTabNewlineCarriageReturn()
    @Test override fun testSimpleDAO() = super.testSimpleDAO()
    @Test override fun testBoost() = super.testBoost()
    @Test override fun testException() = super.testException()
    @Test override fun testBooleanQuery() = super.testBooleanQuery()
    @Test override fun testPrecedence() = super.testPrecedence()
    @Test override fun testParsesBracketsIfQuoted() = super.testParsesBracketsIfQuoted()
    @Test override fun testRegexps() = super.testRegexps()
    @Test override fun testStopwords() = super.testStopwords()
    @Test override fun testPositionIncrement() = super.testPositionIncrement()
    @Test override fun testMatchAllDocs() = super.testMatchAllDocs()
    @Test override fun testPositionIncrements() = super.testPositionIncrements()
    @Test override fun testCollatedRange() = super.testCollatedRange()
    @Test override fun testDistanceAsEditsParsing() = super.testDistanceAsEditsParsing()
    @Test override fun testPhraseQueryToString() = super.testPhraseQueryToString()
    @Test override fun testParseWildcardAndPhraseQueries() = super.testParseWildcardAndPhraseQueries()
    @Test override fun testPhraseQueryPositionIncrements() = super.testPhraseQueryPositionIncrements()
    @Test override fun testMatchAllQueryParsing() = super.testMatchAllQueryParsing()
    @Test override fun testNestedAndClausesFoo() = super.testNestedAndClausesFoo()

    fun getParser(a: Analyzer?): StandardQueryParser {
        val analyzer = a ?: MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
        val qp = StandardQueryParser(analyzer)
        qp.defaultOperator = Operator.OR

        return qp
    }

    fun parse(query: String, qp: StandardQueryParser): Query {
        return qp.parse(query, getDefaultField())
    }

    override fun getParserConfig(a: Analyzer?): CommonQueryParserConfiguration {
        return getParser(a)
    }

    override fun getQuery(query: String, cqpC: CommonQueryParserConfiguration): Query {
        assertTrue(cqpC is StandardQueryParser, "Parameter must be instance of StandardQueryParser")
        val qp = cqpC as StandardQueryParser
        return parse(query, qp)
    }

    override fun getQuery(query: String, a: Analyzer?): Query {
        return parse(query, getParser(a))
    }

    override fun isQueryParserException(exception: Exception): Boolean {
        return exception is QueryNodeException
    }

    override fun setDefaultOperatorOR(cqpC: CommonQueryParserConfiguration) {
        assertTrue(cqpC is StandardQueryParser)
        val qp = cqpC as StandardQueryParser
        qp.defaultOperator = Operator.OR
    }

    override fun setDefaultOperatorAND(cqpC: CommonQueryParserConfiguration) {
        assertTrue(cqpC is StandardQueryParser)
        val qp = cqpC as StandardQueryParser
        qp.defaultOperator = Operator.AND
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
        val dateResolutionMap = qp.dateResolutionMap?.toMutableMap() ?: mutableMapOf()
        dateResolutionMap[field] = value
        qp.dateResolutionMap = dateResolutionMap
    }

    @Test
    override fun testOperatorVsWhitespace() {
        // LUCENE-2566 is not implemented for StandardQueryParser
        // TODO implement LUCENE-2566 and remove this (override)method
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    return TokenStreamComponents(MockTokenizer(MockTokenizer.WHITESPACE, false))
                }
            }
        assertQueryEquals("a - b", a, "a -b")
        assertQueryEquals("a + b", a, "a +b")
        assertQueryEquals("a ! b", a, "a -b")
    }

    @Test
    override fun testRangeWithPhrase() {
        // StandardSyntaxParser does not differentiate between a term and a
        // one-term-phrase in a range query.
        // Is this an issue? Should StandardSyntaxParser mark the text as
        // wasEscaped=true ?
        assertQueryEquals("[\\* TO \"*\"]", null, "[\\* TO *]")
    }

    @Test
    override fun testEscapedVsQuestionMarkAsWildcard() {
        val a = MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)
        assertQueryEquals("a:b\\-?c", a, "a:b-?c")
        assertQueryEquals("a:b\\+?c", a, "a:b+?c")
        assertQueryEquals("a:b\\:?c", a, "a:b:?c")

        assertQueryEquals("a:b\\\\?c", a, "a:b\\?c")
    }

    @Test
    override fun testEscapedWildcard() {
        val qp = getParserConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        val q = WildcardQuery(Term("field", "foo?ba?r")) // TODO not correct!!
        assertEquals(q, getQuery("foo\\?ba?r", qp))
    }

    @Test
    override fun testAutoGeneratePhraseQueriesOn() {
        expectThrows<UnsupportedOperationException>(UnsupportedOperationException::class) {
            setAutoGeneratePhraseQueries(getParser(null), true)
            super.testAutoGeneratePhraseQueriesOn()
        }
    }

    @Test
    override fun testStarParsing() {}

    @Test
    override fun testDefaultOperator() {
        val qp = getParser(MockAnalyzer(random()))
        // make sure OR is the default:
        assertEquals(StandardQueryConfigHandler.Operator.OR, qp.defaultOperator)
        setDefaultOperatorAND(qp)
        assertEquals(StandardQueryConfigHandler.Operator.AND, qp.defaultOperator)
        setDefaultOperatorOR(qp)
        assertEquals(StandardQueryConfigHandler.Operator.OR, qp.defaultOperator)
    }

    @Test
    override fun testNewFieldQuery() {
        /* ordinary behavior, synonyms form uncoordinated boolean query */
        val dumb = getParser(Analyzer1())
        val expanded = BooleanQuery.Builder()
        expanded.add(TermQuery(Term("field", "dogs")), BooleanClause.Occur.SHOULD)
        expanded.add(TermQuery(Term("field", "dog")), BooleanClause.Occur.SHOULD)
        assertEquals(expanded.build(), dumb.parse("\"dogs\"", "field"))
        /* even with the phrase operator the behavior is the same */
        assertEquals(expanded.build(), dumb.parse("dogs", "field"))

        /* custom behavior, the synonyms are expanded, unless you use quote operator */
        // TODO test something like "SmartQueryParser()"
    }

    // TODO: Remove this specialization once the flexible standard parser gets multi-word synonym
    // support
    @Test
    override fun testQPA() {
        super.testQPA()

        assertQueryEquals("term phrase term", qpAnalyzer, "term (phrase1 phrase2) term")

        val cqpc = getParserConfig(qpAnalyzer)
        setDefaultOperatorAND(cqpc)
        assertQueryEquals(cqpc, "field", "term phrase term", "+term +(+phrase1 +phrase2) +term")
    }
}
