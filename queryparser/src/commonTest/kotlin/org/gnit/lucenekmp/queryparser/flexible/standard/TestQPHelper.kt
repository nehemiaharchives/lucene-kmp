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

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.queryparser.charstream.FastCharStream
import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FuzzyQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorPipeline
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.WildcardQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParser
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TermRangeQuery
import org.gnit.lucenekmp.search.WildcardQuery
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * This test case is a copy of the core Lucene query parser test, it was adapted to use new
 * QueryParserHelper instead of the old query parser.
 *
 * Tests QueryParser.
 */
// TODO: really this should extend QueryParserTestBase too!
class TestQPHelper : LuceneTestCase() {

    class QPTestFilter(`in`: TokenStream) : TokenFilter(`in`) {
        private val termAtt = addAttribute(CharTermAttribute::class)
        private val offsetAtt = addAttribute(OffsetAttribute::class)

        /**
         * Filter which discards the token 'stop' and which expands the token 'phrase' into 'phrase1
         * phrase2'
         */

        private var inPhrase = false
        private var savedStart = 0
        private var savedEnd = 0

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (inPhrase) {
                inPhrase = false
                clearAttributes()
                termAtt.setEmpty()!!.append("phrase2")
                offsetAtt.setOffset(savedStart, savedEnd)
                return true
            } else {
                while (input.incrementToken()) {
                    if (termAtt.toString() == "phrase") {
                        inPhrase = true
                        savedStart = offsetAtt.startOffset()
                        savedEnd = offsetAtt.endOffset()
                        termAtt.setEmpty()!!.append("phrase1")
                        offsetAtt.setOffset(savedStart, savedEnd)
                        return true
                    } else if (termAtt.toString() != "stop") return true
                }
            }
            return false
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            this.inPhrase = false
            this.savedStart = 0
            this.savedEnd = 0
        }
    }

    class QPTestAnalyzer : Analyzer() {

        /** Filters MockTokenizer with StopFilter. */
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.SIMPLE, true)
            return TokenStreamComponents(tokenizer, QPTestFilter(tokenizer))
        }
    }

    class QPTestParser(a: Analyzer) : StandardQueryParser() {
        init {
            (queryNodeProcessor as QueryNodeProcessorPipeline).add(QPTestParserQueryNodeProcessor())
            this.analyzer = a
        }

        private class QPTestParserQueryNodeProcessor : QueryNodeProcessorImpl() {

            override fun postProcessNode(node: QueryNode): QueryNode {

                return node
            }

            override fun preProcessNode(node: QueryNode): QueryNode {

                if (node is WildcardQueryNode || node is FuzzyQueryNode) {

                    throw QueryNodeException(MessageImpl(QueryParserMessages.EMPTY_MESSAGE))
                }

                return node
            }

            override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {

                return children
            }
        }
    }

    private var originalMaxClauses: Int = 0

    @BeforeTest
    fun setUp() {
        originalMaxClauses = IndexSearcher.maxClauseCount
    }

    fun getParser(a: Analyzer?): StandardQueryParser {
        val analyzer = a ?: MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
        val qp = StandardQueryParser()
        qp.analyzer = analyzer

        qp.defaultOperator = StandardQueryConfigHandler.Operator.OR

        return qp
    }

    fun getQuery(query: String, a: Analyzer?): Query {
        return getParser(a).parse(query, "field")
    }

    fun getQueryAllowLeadingWildcard(query: String, a: Analyzer?): Query {
        val parser = getParser(a)
        parser.allowLeadingWildcard = true
        return parser.parse(query, "field")
    }

    fun assertQueryEquals(query: String, a: Analyzer?, result: String) {
        val q = getQuery(query, a)
        val s = q.toString("field")
        if (s != result) {
            fail("Query /$query/ yielded /$s/, expecting /$result/")
        }
    }

    fun assertMatchNoDocsQuery(queryString: String, a: Analyzer?) {
        assertMatchNoDocsQuery(getQuery(queryString, a))
    }

    fun assertMatchNoDocsQuery(query: Query) {
        if (query is MatchNoDocsQuery) {
            // good
        } else if (query is BooleanQuery && query.clauses().isEmpty()) {
            // good
        } else {
            fail("expected MatchNoDocsQuery or an empty BooleanQuery but got: $query")
        }
    }

    fun assertQueryEqualsAllowLeadingWildcard(query: String, a: Analyzer?, result: String) {
        val q = getQueryAllowLeadingWildcard(query, a)
        val s = q.toString("field")
        if (s != result) {
            fail("Query /$query/ yielded /$s/, expecting /$result/")
        }
    }

    fun assertQueryEquals(qp: StandardQueryParser, field: String, query: String, result: String) {
        val q = qp.parse(query, field)
        val s = q.toString(field)
        if (s != result) {
            fail("Query /$query/ yielded /$s/, expecting /$result/")
        }
    }

    fun assertEscapedQueryEquals(query: String, a: Analyzer?, result: String) {
        val escapedQuery = QueryParserUtil.escape(query)
        if (escapedQuery != result) {
            fail("Query /$query/ yielded /$escapedQuery/, expecting /$result/")
        }
    }

    fun assertWildcardQueryEquals(query: String, result: String, allowLeadingWildcard: Boolean) {
        val qp = getParser(null)
        qp.allowLeadingWildcard = allowLeadingWildcard
        val q = qp.parse(query, "field")
        val s = q.toString("field")
        if (s != result) {
            fail("WildcardQuery /$query/ yielded /$s/, expecting /$result/")
        }
    }

    fun assertWildcardQueryEquals(query: String, result: String) {
        assertWildcardQueryEquals(query, result, false)
    }

    fun getQueryDOA(query: String, a: Analyzer?): Query {
        val analyzer = a ?: MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
        val qp = StandardQueryParser()
        qp.analyzer = analyzer
        qp.defaultOperator = StandardQueryConfigHandler.Operator.AND

        return qp.parse(query, "field")
    }

    fun assertQueryEqualsDOA(query: String, a: Analyzer?, result: String) {
        val q = getQueryDOA(query, a)
        val s = q.toString("field")
        if (s != result) {
            fail("Query /$query/ yielded /$s/, expecting /$result/")
        }
    }

    @Test
    fun testConstantScoreAutoRewrite() {
        val qp = StandardQueryParser(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        var q = qp.parse("foo*bar", "field")
        assertTrue(q is WildcardQuery)
        assertEquals(MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE, (q as MultiTermQuery).rewriteMethod)

        q = qp.parse("foo*", "field")
        assertTrue(q is PrefixQuery)
        assertEquals(MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE, (q as MultiTermQuery).rewriteMethod)

        q = qp.parse("[a TO z]", "field")
        assertTrue(q is TermRangeQuery)
        assertEquals(MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE, (q as MultiTermQuery).rewriteMethod)
    }

    @Test
    fun testCJK() {
        // Test Ideographic Space - As wide as a CJK character cell (fullwidth)
        // used google to translate the word "term" to japanese -> ??
        assertQueryEquals("term\u3000term\u3000term", null, "term\u0020term\u0020term")
        assertQueryEqualsAllowLeadingWildcard("??\u3000??\u3000??", null, "??\u0020??\u0020??")
    }

    // individual CJK chars as terms, like StandardAnalyzer
    private class SimpleCJKTokenizer : Tokenizer() {
        private val termAtt = addAttribute(CharTermAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            val ch = input.read()
            if (ch < 0) return false
            clearAttributes()
            termAtt.setEmpty()!!.append(ch.toChar())
            return true
        }
    }

    private class SimpleCJKAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            return TokenStreamComponents(SimpleCJKTokenizer())
        }
    }

    @Test
    fun testCJKTerm() {
        // individual CJK chars as terms
        val analyzer = SimpleCJKAnalyzer()

        var expected = BooleanQuery.Builder()
        expected.add(TermQuery(Term("field", "中")), BooleanClause.Occur.SHOULD)
        expected.add(TermQuery(Term("field", "国")), BooleanClause.Occur.SHOULD)
        assertEquals(expected.build(), getQuery("中国", analyzer))

        expected = BooleanQuery.Builder()
        expected.add(TermQuery(Term("field", "中")), BooleanClause.Occur.MUST)
        val inner = BooleanQuery.Builder()
        inner.add(TermQuery(Term("field", "中")), BooleanClause.Occur.SHOULD)
        inner.add(TermQuery(Term("field", "国")), BooleanClause.Occur.SHOULD)
        expected.add(inner.build(), BooleanClause.Occur.MUST)
        assertEquals(expected.build(), getQuery("中 AND 中国", SimpleCJKAnalyzer()))
    }

    @Test
    fun testCJKBoostedTerm() {
        // individual CJK chars as terms
        val analyzer = SimpleCJKAnalyzer()

        val expectedB = BooleanQuery.Builder()
        expectedB.add(TermQuery(Term("field", "中")), BooleanClause.Occur.SHOULD)
        expectedB.add(TermQuery(Term("field", "国")), BooleanClause.Occur.SHOULD)
        var expected: Query = expectedB.build()
        expected = BoostQuery(expected, 0.5f)
        assertEquals(expected, getQuery("中国^0.5", analyzer))
    }

    @Test
    fun testCJKPhrase() {
        // individual CJK chars as terms
        val analyzer = SimpleCJKAnalyzer()

        val expected = PhraseQuery("field", "中", "国")

        assertEquals(expected, getQuery("\"中国\"", analyzer))
    }

    @Test
    fun testCJKBoostedPhrase() {
        // individual CJK chars as terms
        val analyzer = SimpleCJKAnalyzer()

        var expected: Query = PhraseQuery("field", "中", "国")
        expected = BoostQuery(expected, 0.5f)

        assertEquals(expected, getQuery("\"中国\"^0.5", analyzer))
    }

    @Test
    fun testCJKSloppyPhrase() {
        // individual CJK chars as terms
        val analyzer = SimpleCJKAnalyzer()

        val expected = PhraseQuery(3, "field", "中", "国")

        assertEquals(expected, getQuery("\"中国\"~3", analyzer))
    }

    // Remaining Java methods are ported below in source order in subsequent patches.
}
