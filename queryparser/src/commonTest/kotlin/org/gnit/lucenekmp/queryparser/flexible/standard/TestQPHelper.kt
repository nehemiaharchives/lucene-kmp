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
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.DateTools.Resolution
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FuzzyQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorPipeline
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.WildcardQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParser
import org.gnit.lucenekmp.queryparser.util.QueryParserTestBase
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.MultiPhraseQuery
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.RegexpQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TermRangeQuery
import org.gnit.lucenekmp.search.WildcardQuery
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * This test case is a copy of the core Lucene query parser test, adapted to use
 * [QueryParserHelper] instead of the classic parser.
 */
class TestQPHelper : LuceneTestCase() {

    class QPTestFilter(`in`: TokenStream) : TokenFilter(`in`) {
        private val termAtt = addAttribute(CharTermAttribute::class)
        private val offsetAtt = addAttribute(OffsetAttribute::class)
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
            }
            while (input.incrementToken()) {
                if (termAtt.toString() == "phrase") {
                    inPhrase = true
                    savedStart = offsetAtt.startOffset()
                    savedEnd = offsetAtt.endOffset()
                    termAtt.setEmpty()!!.append("phrase1")
                    offsetAtt.setOffset(savedStart, savedEnd)
                    return true
                } else if (termAtt.toString() != "stop") {
                    return true
                }
            }
            return false
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            inPhrase = false
            savedStart = 0
            savedEnd = 0
        }
    }

    class QPTestAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer = MockTokenizer(MockTokenizer.SIMPLE, true)
            return TokenStreamComponents(tokenizer, QPTestFilter(tokenizer))
        }
    }

    class QPTestParser(a: Analyzer) : StandardQueryParser() {
        init {
            (queryNodeProcessor as QueryNodeProcessorPipeline).add(QPTestParserQueryNodeProcessor())
            analyzer = a
        }

        private class QPTestParserQueryNodeProcessor : QueryNodeProcessorImpl() {
            override fun postProcessNode(node: QueryNode): QueryNode = node

            override fun preProcessNode(node: QueryNode): QueryNode {
                if (node is WildcardQueryNode || node is FuzzyQueryNode) {
                    throw QueryNodeException(MessageImpl(QueryParserMessages.EMPTY_MESSAGE))
                }
                return node
            }

            override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> = children
        }
    }

    private var originalMaxClauses: Int = 0

    @BeforeTest
    fun setUp() {
        originalMaxClauses = IndexSearcher.maxClauseCount
    }

    @AfterTest
    fun tearDown() {
        IndexSearcher.maxClauseCount = originalMaxClauses
    }

    fun getParser(a: Analyzer?): StandardQueryParser {
        val analyzer = a ?: MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
        return StandardQueryParser(analyzer).also {
            it.defaultOperator = StandardQueryConfigHandler.Operator.OR
        }
    }

    fun getQuery(query: String, a: Analyzer?): Query = getParser(a).parse(query, "field")

    fun getQueryAllowLeadingWildcard(query: String, a: Analyzer?): Query {
        val parser = getParser(a)
        parser.allowLeadingWildcard = true
        return parser.parse(query, "field")
    }

    fun assertQueryEquals(query: String, a: Analyzer?, result: String) {
        val s = getQuery(query, a).toString("field")
        if (s != result) fail("Query /$query/ yielded /$s/, expecting /$result/")
    }

    fun assertMatchNoDocsQuery(queryString: String, a: Analyzer?) {
        assertMatchNoDocsQuery(getQuery(queryString, a))
    }

    fun assertMatchNoDocsQuery(query: Query) {
        if (query is MatchNoDocsQuery) return
        if (query is BooleanQuery && query.clauses().isEmpty()) return
        fail("expected MatchNoDocsQuery or an empty BooleanQuery but got: $query")
    }

    fun assertQueryEqualsAllowLeadingWildcard(query: String, a: Analyzer?, result: String) {
        val s = getQueryAllowLeadingWildcard(query, a).toString("field")
        if (s != result) fail("Query /$query/ yielded /$s/, expecting /$result/")
    }

    fun assertQueryEquals(qp: StandardQueryParser, field: String, query: String, result: String) {
        val s = qp.parse(query, field).toString(field)
        if (s != result) fail("Query /$query/ yielded /$s/, expecting /$result/")
    }

    fun assertEscapedQueryEquals(query: String, a: Analyzer?, result: String) {
        val escapedQuery = QueryParserUtil.escape(query)
        if (escapedQuery != result) fail("Query /$query/ yielded /$escapedQuery/, expecting /$result/")
    }

    fun assertWildcardQueryEquals(query: String, result: String, allowLeadingWildcard: Boolean) {
        val qp = getParser(null)
        qp.allowLeadingWildcard = allowLeadingWildcard
        val s = qp.parse(query, "field").toString("field")
        if (s != result) fail("WildcardQuery /$query/ yielded /$s/, expecting /$result/")
    }

    fun assertWildcardQueryEquals(query: String, result: String) {
        assertWildcardQueryEquals(query, result, false)
    }

    fun getQueryDOA(query: String, a: Analyzer?): Query {
        val analyzer = a ?: MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
        val qp = StandardQueryParser(analyzer)
        qp.defaultOperator = StandardQueryConfigHandler.Operator.AND
        return qp.parse(query, "field")
    }

    fun assertQueryEqualsDOA(query: String, a: Analyzer?, result: String) {
        val s = getQueryDOA(query, a).toString("field")
        if (s != result) fail("Query /$query/ yielded /$s/, expecting /$result/")
    }

    fun assertQueryNodeException(queryString: String) {
        expectThrows<QueryNodeException>(QueryNodeException::class) {
            getQuery(queryString, null)
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
        assertQueryEquals("term\u3000term\u3000term", null, "term\u0020term\u0020term")
        assertQueryEqualsAllowLeadingWildcard("??\u3000??\u3000??", null, "??\u0020??\u0020??")
    }

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
        override fun createComponents(fieldName: String): TokenStreamComponents =
            TokenStreamComponents(SimpleCJKTokenizer())
    }

    @Test
    fun testCJKTerm() {
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
        val analyzer = SimpleCJKAnalyzer()
        val expectedB = BooleanQuery.Builder()
        expectedB.add(TermQuery(Term("field", "中")), BooleanClause.Occur.SHOULD)
        expectedB.add(TermQuery(Term("field", "国")), BooleanClause.Occur.SHOULD)
        val expected: Query = BoostQuery(expectedB.build(), 0.5f)
        assertEquals(expected, getQuery("中国^0.5", analyzer))
    }

    @Test
    fun testCJKPhrase() {
        assertEquals(PhraseQuery("field", "中", "国"), getQuery("\"中国\"", SimpleCJKAnalyzer()))
    }

    @Test
    fun testCJKBoostedPhrase() {
        val expected: Query = BoostQuery(PhraseQuery("field", "中", "国"), 0.5f)
        assertEquals(expected, getQuery("\"中国\"^0.5", SimpleCJKAnalyzer()))
    }

    @Test
    fun testCJKSloppyPhrase() {
        assertEquals(PhraseQuery(3, "field", "中", "国"), getQuery("\"中国\"~3", SimpleCJKAnalyzer()))
    }

    /*
     * The original Java class duplicates much of QueryParserTestBase. Reuse the existing KMP port
     * for those assertions and keep the TestQPHelper-specific cases below in this class.
     */
    private class StandardBaseAdapter : QueryParserTestBase() {
        private fun parser(a: Analyzer?): StandardQueryParser {
            val analyzer = a ?: MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
            return StandardQueryParser(analyzer).also {
                it.defaultOperator = StandardQueryConfigHandler.Operator.OR
            }
        }

        override fun getParserConfig(a: Analyzer?): CommonQueryParserConfiguration = parser(a)

        override fun getQuery(query: String, cqpC: CommonQueryParserConfiguration): Query =
            (cqpC as StandardQueryParser).parse(query, getDefaultField())

        override fun getQuery(query: String, a: Analyzer?): Query =
            parser(a).parse(query, getDefaultField())

        override fun isQueryParserException(exception: Exception): Boolean = exception is QueryNodeException

        override fun setDefaultOperatorOR(cqpC: CommonQueryParserConfiguration) {
            (cqpC as StandardQueryParser).defaultOperator = StandardQueryConfigHandler.Operator.OR
        }

        override fun setDefaultOperatorAND(cqpC: CommonQueryParserConfiguration) {
            (cqpC as StandardQueryParser).defaultOperator = StandardQueryConfigHandler.Operator.AND
        }

        override fun setAutoGeneratePhraseQueries(cqpC: CommonQueryParserConfiguration, value: Boolean) {
            throw UnsupportedOperationException()
        }

        override fun setDateResolution(
            cqpC: CommonQueryParserConfiguration,
            field: CharSequence,
            value: Resolution,
        ) {
            val qp = cqpC as StandardQueryParser
            val map = qp.dateResolutionMap?.toMutableMap() ?: mutableMapOf()
            map[field] = value
            qp.dateResolutionMap = map
        }

        override fun testDefaultOperator() {}
        override fun testNewFieldQuery() {}
        override fun testStarParsing() {}
    }

    private fun runBase(block: StandardBaseAdapter.() -> Unit) {
        val adapter = StandardBaseAdapter()
        adapter.setUp()
        try {
            adapter.block()
        } finally {
            adapter.tearDown()
        }
    }

    @Test
    fun testSimple() {
        runBase { testSimple() }
        assertQueryEquals("field=a", null, "a")
    }

    @Test
    fun testParse() {
        StandardSyntaxParser().parse("title:(dog OR cat)", "_fld_")
    }

    @Test
    fun testPunct() = runBase { testPunct() }

    @Test
    fun testGroup() {
        assertQueryEquals("!(a AND b) OR c", null, "-(+a +b) c")
        assertQueryEquals("!(a AND b) AND c", null, "-(+a +b) +c")
        assertQueryEquals("((a AND b) AND c)", null, "+(+a +b) +c")
        assertQueryEquals("(a AND b) AND c", null, "+(+a +b) +c")
        assertQueryEquals("b !(a AND b)", null, "b -(+a +b)")
        assertQueryEquals("(a AND b)^4 OR c", null, "(+a +b)^4.0 c")
    }

    @Test
    fun testSlop() = runBase { testSlop() }

    @Test
    fun testNumber() = runBase { testNumber() }

    @Test
    fun testLeadingNegation() {
        assertQueryEquals("-term", null, "-term")
        assertQueryEquals("!term", null, "-term")
        assertQueryEquals("NOT term", null, "-term")
    }

    @Test
    fun testNegationInParentheses() {
        assertQueryEquals("(-a)", null, "-a")
        assertQueryEquals("(!a)", null, "-a")
        assertQueryEquals("(NOT a)", null, "-a")
        assertQueryEquals("a (!b)", null, "a (-b)")
        assertQueryEquals("+a +(!b)", null, "+a +(-b)")
        assertQueryEquals("a AND (!b)", null, "+a +(-b)")
        assertQueryEquals("a (NOT b)", null, "a (-b)")
        assertQueryEquals("a AND (NOT b)", null, "+a +(-b)")
    }

    @Test
    fun testWildcard() {
        runBase { testWildcard() }
        assertQueryNodeException("term^3~")
    }

    @Test
    fun testLeadingWildcardType() = runBase { testLeadingWildcardType() }

    @Test
    fun testQPA() = runBase { testQPA() }

    @Test
    fun testRange() {
        runBase { testRange() }
        assertQueryEquals("field>=a", null, "[a TO *]")
        assertQueryEquals("field>a", null, "{a TO *]")
        assertQueryEquals("field<=a", null, "[* TO a]")
        assertQueryEquals("field<a", null, "[* TO a}")
    }

    @Test
    fun testDateRange() = runBase { testDateRange() }

    @Test
    fun testEscaped() = runBase { testEscaped() }

    @Test
    fun testQueryStringEscaping() = runBase { testQueryStringEscaping() }

    @Ignore
    @Test
    fun testEscapedWildcard() {
        val qp = StandardQueryParser(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        val q = WildcardQuery(Term("field", "foo\\?ba?r"))
        assertEquals(q, qp.parse("foo\\?ba?r", "field"))
    }

    @Test
    fun testTabNewlineCarriageReturn() = runBase { testTabNewlineCarriageReturn() }

    @Test
    fun testSimpleDAO() = runBase { testSimpleDAO() }

    @Test
    fun testBoost() = runBase { testBoost() }

    @Test
    fun testException() {
        runBase { testException() }
        assertQueryNodeException("*leadingWildcard")
    }

    @Test
    fun testCustomQueryParserWildcard() {
        expectThrows<QueryNodeException>(QueryNodeException::class) {
            QPTestParser(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)).parse("a?t", "contents")
        }
    }

    @Test
    fun testCustomQueryParserFuzzy() {
        expectThrows<QueryNodeException>(QueryNodeException::class) {
            QPTestParser(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)).parse("xunit~", "contents")
        }
    }

    @Test
    fun testBooleanQuery() = runBase { testBooleanQuery() }

    @Test
    fun testPrecedence() = runBase { testPrecedence() }

    @Test
    fun testStarParsing() {
        // Upstream implementation contains only commented-out legacy subclass hooks.
    }

    @Test
    fun testRegexps() {
        runBase { testRegexps() }
        assertQueryNodeException("/http/~2")
    }

    @Test
    fun testStopwords() = runBase { testStopwords() }

    @Test
    fun testPositionIncrement() = runBase { testPositionIncrement() }

    @Test
    fun testMatchAllDocs() = runBase { testMatchAllDocs() }

    private class CannedTokenizer : Tokenizer() {
        private var upto = 0
        private val posIncr = addAttribute(PositionIncrementAttribute::class)
        private val term = addAttribute(CharTermAttribute::class)

        override fun incrementToken(): Boolean {
            clearAttributes()
            when (upto) {
                0 -> {
                    posIncr.setPositionIncrement(1)
                    term.setEmpty()!!.append("a")
                }
                1 -> {
                    posIncr.setPositionIncrement(1)
                    term.setEmpty()!!.append("b")
                }
                2 -> {
                    posIncr.setPositionIncrement(0)
                    term.setEmpty()!!.append("c")
                }
                3 -> {
                    posIncr.setPositionIncrement(0)
                    term.setEmpty()!!.append("d")
                }
                else -> return false
            }
            upto++
            return true
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            upto = 0
        }
    }

    private class CannedAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents =
            TokenStreamComponents(CannedTokenizer())
    }

    @Test
    fun testMultiPhraseQuery() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(CannedAnalyzer()))
        val doc = Document()
        doc.add(newTextField("field", "", Field.Store.NO))
        w.addDocument(doc)
        val r = DirectoryReader.open(w)
        val searcher = newSearcher(r)

        val q = StandardQueryParser(CannedAnalyzer()).parse("\"a\"", "field")
        assertTrue(q is MultiPhraseQuery)
        assertEquals(1L, searcher.search(q, 10).totalHits.value)

        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testRegexQueryParsing() {
        val fields = arrayOf<CharSequence>("b", "t")
        val parser = StandardQueryParser()
        parser.multiFields = fields
        parser.defaultOperator = StandardQueryConfigHandler.Operator.AND
        parser.analyzer = MockAnalyzer(random())

        val expected = BooleanQuery.Builder()
        expected.add(RegexpQuery(Term("b", "ab.+")), BooleanClause.Occur.SHOULD)
        expected.add(RegexpQuery(Term("t", "ab.+")), BooleanClause.Occur.SHOULD)
        assertEquals(expected.build(), parser.parse("/ab.+/", null))

        val regexpQuery = RegexpQuery(Term("test", "[abc]?[0-9]"))
        assertEquals(regexpQuery, parser.parse("test:/[abc]?[0-9]/", null))
    }
}
