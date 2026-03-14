package org.gnit.lucenekmp.queryparser.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.document.DateTools
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.queryparser.classic.QueryParser
import org.gnit.lucenekmp.queryparser.classic.QueryParserBase
import org.gnit.lucenekmp.queryparser.classic.TestQueryParser
import org.gnit.lucenekmp.queryparser.flexible.standard.CommonQueryParserConfiguration
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.FuzzyQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.RegexpQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TermRangeQuery
import org.gnit.lucenekmp.search.WildcardQuery
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockSynonymFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Base Test class for QueryParser subclasses */
// TODO: it would be better to refactor the parts that are specific really
// to the core QP and subclass/use the parts that are not in the flexible QP
@OptIn(ExperimentalTime::class)
abstract class QueryParserTestBase : LuceneTestCase() {
    companion object {
        var qpAnalyzer: Analyzer? = QPTestAnalyzer()
    }

    class QPTestFilter(`in`: TokenStream) : TokenFilter(`in`) {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

        var inPhrase = false
        var savedStart = 0
        var savedEnd = 0

        @Throws(okio.IOException::class)
        override fun incrementToken(): Boolean {
            if (inPhrase) {
                inPhrase = false
                clearAttributes()
                termAtt.append("phrase2")
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
                    } else if (termAtt.toString() != "stop") {
                        return true
                    }
                }
            }
            return false
        }
    }

    class QPTestAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer = MockTokenizer(MockTokenizer.SIMPLE, true)
            return TokenStreamComponents(tokenizer, QPTestFilter(tokenizer))
        }
    }

    private var originalMaxClauses = 0
    private var defaultField = "field"

    protected fun getDefaultField(): String {
        return defaultField
    }

    protected fun setDefaultField(defaultField: String) {
        this.defaultField = defaultField
    }

    @BeforeTest
    fun setUp() {
        originalMaxClauses = IndexSearcher.maxClauseCount
    }

    @AfterTest
    fun tearDown() {
        IndexSearcher.maxClauseCount = originalMaxClauses
    }

    abstract fun getParserConfig(a: Analyzer?): CommonQueryParserConfiguration

    abstract fun setDefaultOperatorOR(cqpC: CommonQueryParserConfiguration)

    abstract fun setDefaultOperatorAND(cqpC: CommonQueryParserConfiguration)

    abstract fun setAutoGeneratePhraseQueries(cqpC: CommonQueryParserConfiguration, value: Boolean)

    abstract fun setDateResolution(
        cqpC: CommonQueryParserConfiguration,
        field: CharSequence,
        value: DateTools.Resolution,
    )

    abstract fun getQuery(query: String, cqpC: CommonQueryParserConfiguration): Query?

    abstract fun getQuery(query: String, a: Analyzer?): Query?

    abstract fun isQueryParserException(exception: Exception): Boolean

    open fun getQuery(query: String): Query? {
        return getQuery(query, null)
    }

    fun assertQueryEquals(query: String, a: Analyzer?, result: String) {
        val q = getQuery(query, a)
        val s = q?.toString("field")
        if (s != result) {
            fail("Query /$query/ yielded /$s/, expecting /$result/")
        }
    }

    fun assertMatchNoDocsQuery(queryString: String, a: Analyzer?) {
        assertMatchNoDocsQuery(getQuery(queryString, a))
    }

    fun assertMatchNoDocsQuery(query: Query?) {
        if (query is MatchNoDocsQuery) {
            return
        } else if (query is BooleanQuery && query.clauses().size == 0) {
            return
        }
        fail("expected MatchNoDocsQuery or an empty BooleanQuery but got: $query")
    }

    fun assertQueryEquals(
        cqpC: CommonQueryParserConfiguration,
        field: String,
        query: String,
        result: String,
    ) {
        val q = getQuery(query, cqpC)
        val s = q?.toString(field)
        if (s != result) {
            fail("Query /$query/ yielded /$s/, expecting /$result/")
        }
    }

    fun assertEscapedQueryEquals(query: String, a: Analyzer?, result: String) {
        val escapedQuery = QueryParserBase.escape(query)
        if (escapedQuery != result) {
            fail("Query /$query/ yielded /$escapedQuery/, expecting /$result/")
        }
    }

    fun assertWildcardQueryEquals(
        query: String,
        result: String,
        allowLeadingWildcard: Boolean,
    ) {
        val cqpC = getParserConfig(null)
        cqpC.allowLeadingWildcard = allowLeadingWildcard
        val q = getQuery(query, cqpC)
        val s = q?.toString("field")
        if (s != result) {
            fail("WildcardQuery /$query/ yielded /$s/, expecting /$result/")
        }
    }

    fun assertWildcardQueryEquals(query: String, result: String) {
        assertWildcardQueryEquals(query, result, false)
    }

    fun getQueryDOA(query: String, a: Analyzer?): Query? {
        val analyzer = a ?: MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
        val qp = getParserConfig(analyzer)
        setDefaultOperatorAND(qp)
        return getQuery(query, qp)
    }

    fun assertQueryEqualsDOA(query: String, a: Analyzer?, result: String) {
        val q = getQueryDOA(query, a)
        val s = q?.toString("field")
        if (s != result) {
            fail("Query /$query/ yielded /$s/, expecting /$result/")
        }
    }

    open fun testCJK() {
        // Test Ideographic Space - As wide as a CJK character cell (fullwidth)
        // used google to translate the word "term" to japanese -> 用語
        assertQueryEquals("term\u3000term\u3000term", null, "term term term")
        assertQueryEquals("用語\u3000用語\u3000用語", null, "用語 用語 用語")
    }

    // individual CJK chars as terms, like StandardAnalyzer
    protected class SimpleCJKTokenizer : Tokenizer() {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

        @Throws(okio.IOException::class)
        override fun incrementToken(): Boolean {
            val ch = input.read()
            if (ch < 0) {
                return false
            }
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

    open fun testCJKTerm() {
        val analyzer = SimpleCJKAnalyzer()
        val expected = BooleanQuery.Builder()
        expected.add(TermQuery(Term("field", "中")), BooleanClause.Occur.SHOULD)
        expected.add(TermQuery(Term("field", "国")), BooleanClause.Occur.SHOULD)
        assertEquals(expected.build(), getQuery("中国", analyzer))
    }

    open fun testCJKBoostedTerm() {
        val analyzer = SimpleCJKAnalyzer()
        val expectedB = BooleanQuery.Builder()
        expectedB.add(TermQuery(Term("field", "中")), BooleanClause.Occur.SHOULD)
        expectedB.add(TermQuery(Term("field", "国")), BooleanClause.Occur.SHOULD)
        var expected: Query = expectedB.build()
        expected = BoostQuery(expected, 0.5f)
        assertEquals(expected, getQuery("中国^0.5", analyzer))
    }

    open fun testCJKPhrase() {
        val analyzer = SimpleCJKAnalyzer()
        val expected = PhraseQuery("field", "中", "国")
        assertEquals(expected, getQuery("\"中国\"", analyzer))
    }

    open fun testCJKBoostedPhrase() {
        val analyzer = SimpleCJKAnalyzer()
        var expected: Query = PhraseQuery("field", "中", "国")
        expected = BoostQuery(expected, 0.5f)
        assertEquals(expected, getQuery("\"中国\"^0.5", analyzer))
    }

    open fun testCJKSloppyPhrase() {
        val analyzer = SimpleCJKAnalyzer()
        val expected = PhraseQuery(3, "field", "中", "国")
        assertEquals(expected, getQuery("\"中国\"~3", analyzer))
    }

    open fun testAutoGeneratePhraseQueriesOn() {
        val analyzer = SimpleCJKAnalyzer()
        val expected = PhraseQuery("field", "中", "国")
        val qp = getParserConfig(analyzer)
        if (qp is QueryParser) {
            qp.setSplitOnWhitespace(true)
        }
        setAutoGeneratePhraseQueries(qp, true)
        assertEquals(expected, getQuery("中国", qp))
    }

    open fun testSimple() {
        assertQueryEquals("term term term", null, "term term term")
        assertQueryEquals("türm term term", MockAnalyzer(random()), "türm term term")
        assertQueryEquals("ümlaut", MockAnalyzer(random()), "ümlaut")

        assertQueryEquals("a AND b", null, "+a +b")
        assertQueryEquals("(a AND b)", null, "+a +b")
        assertQueryEquals("c OR (a AND b)", null, "c (+a +b)")
        assertQueryEquals("a AND NOT b", null, "+a -b")
        assertQueryEquals("a AND -b", null, "+a -b")
        assertQueryEquals("a AND !b", null, "+a -b")
        assertQueryEquals("a && b", null, "+a +b")

        assertQueryEquals("a OR b", null, "a b")
        assertQueryEquals("a || b", null, "a b")
        assertQueryEquals("a OR !b", null, "a -b")
        assertQueryEquals("a OR -b", null, "a -b")

        assertQueryEquals("+term -term term", null, "+term -term term")
        assertQueryEquals("foo:term AND field:anotherTerm", null, "+foo:term +anotherterm")
        assertQueryEquals("term AND \"phrase phrase\"", null, "+term +\"phrase phrase\"")
        assertQueryEquals("\"hello there\"", null, "\"hello there\"")
        assertTrue(getQuery("a AND b") is BooleanQuery)
        assertTrue(getQuery("hello") is TermQuery)
        assertTrue(getQuery("\"hello there\"") is PhraseQuery)

        assertQueryEquals("germ term^2.0", null, "germ (term)^2.0")
        assertQueryEquals("(term)^2.0", null, "(term)^2.0")
        assertQueryEquals("(germ term)^2.0", null, "(germ term)^2.0")
        assertQueryEquals("term^2.0", null, "(term)^2.0")
        assertQueryEquals("term^2", null, "(term)^2.0")
        assertQueryEquals("\"germ term\"^2.0", null, "(\"germ term\")^2.0")
        assertQueryEquals("\"term germ\"^2", null, "(\"term germ\")^2.0")

        assertQueryEquals("(foo OR bar) AND (baz OR boo)", null, "+(foo bar) +(baz boo)")
        assertQueryEquals("((a OR b) AND NOT c) OR d", null, "(+(a b) -c) d")
        assertQueryEquals(
            "+(apple \"steve jobs\") -(foo bar baz)",
            null,
            "+(apple \"steve jobs\") -(foo bar baz)",
        )
        assertQueryEquals(
            "+title:(dog OR cat) -author:\"bob dole\"",
            null,
            "+(title:dog title:cat) -author:\"bob dole\"",
        )
    }

    abstract fun testDefaultOperator()

    open fun testOperatorVsWhitespace() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    return TokenStreamComponents(MockTokenizer(MockTokenizer.WHITESPACE, false))
                }
            }
        assertQueryEquals("a - b", a, "a - b")
        assertQueryEquals("a + b", a, "a + b")
        assertQueryEquals("a ! b", a, "a ! b")
    }

    open fun testPunct() {
        val a = MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)
        assertQueryEquals("a&b", a, "a&b")
        assertQueryEquals("a&&b", a, "a&&b")
        assertQueryEquals(".NET", a, ".NET")
    }

    open fun testSlop() {
        assertQueryEquals("\"term germ\"~2", null, "\"term germ\"~2")
        assertQueryEquals("\"term germ\"~2 flork", null, "\"term germ\"~2 flork")
        assertQueryEquals("\"term\"~2", null, "term")
        assertQueryEquals("\" \"~2 germ", null, "germ")
        assertQueryEquals("\"term germ\"~2^2", null, "(\"term germ\"~2)^2.0")
    }

    open fun testNumber() {
        assertMatchNoDocsQuery("3", null)
        assertQueryEquals("term 1.0 1 2", null, "term")
        assertQueryEquals("term term1 term2", null, "term term term")

        val a = MockAnalyzer(random(), MockTokenizer.WHITESPACE, true)
        assertQueryEquals("3", a, "3")
        assertQueryEquals("term 1.0 1 2", a, "term 1.0 1 2")
        assertQueryEquals("term term1 term2", a, "term term1 term2")
    }

    open fun testWildcard() {
        assertQueryEquals("term*", null, "term*")
        assertQueryEquals("term*^2", null, "(term*)^2.0")
        assertQueryEquals("term~", null, "term~2")
        assertQueryEquals("term~1", null, "term~1")
        assertQueryEquals("term~0.7", null, "term~1")
        assertQueryEquals("term~^3", null, "(term~2)^3.0")
        assertQueryEquals("term*germ", null, "term*germ")
        assertQueryEquals("term*germ^3", null, "(term*germ)^3.0")

        assertTrue(getQuery("term*") is PrefixQuery)
        assertTrue(getQuery("term*^2") is BoostQuery)
        assertTrue((getQuery("term*^2") as BoostQuery).query is PrefixQuery)
        assertTrue(getQuery("term~") is FuzzyQuery)
        assertTrue(getQuery("term~0.7") is FuzzyQuery)
        var fq = getQuery("term~0.7") as FuzzyQuery
        assertEquals(1, fq.maxEdits)
        assertEquals(FuzzyQuery.defaultPrefixLength, fq.prefixLength)
        fq = getQuery("term~") as FuzzyQuery
        assertEquals(2, fq.maxEdits)
        assertEquals(FuzzyQuery.defaultPrefixLength, fq.prefixLength)

        assertParseException("term~1.1")
        assertTrue(getQuery("term*germ") is WildcardQuery)

        assertWildcardQueryEquals("Term*", "term*")
        assertWildcardQueryEquals("term*", "term*")
        assertWildcardQueryEquals("Term*", "term*")
        assertWildcardQueryEquals("TERM*", "term*")
        assertWildcardQueryEquals("Te?m", "te?m")
        assertWildcardQueryEquals("te?m", "te?m")
        assertWildcardQueryEquals("Te?m", "te?m")
        assertWildcardQueryEquals("TE?M", "te?m")
        assertWildcardQueryEquals("Te?m*gerM", "te?m*germ")
        assertWildcardQueryEquals("Term~", "term~2")
        assertWildcardQueryEquals("[A TO C]", "[a TO c]")

        var ex =
            expectThrows(Exception::class) {
                assertWildcardQueryEquals("*Term", "*term", false)
            }
        assertTrue(isQueryParserException(ex))

        ex =
            expectThrows(Exception::class) {
                assertWildcardQueryEquals("?Term", "?term")
            }
        assertTrue(isQueryParserException(ex))

        assertWildcardQueryEquals("*Term", "*term", true)
        assertWildcardQueryEquals("?Term", "?term", true)
    }

    open fun testLeadingWildcardType() {
        val cqpC = getParserConfig(null)
        cqpC.allowLeadingWildcard = true
        assertEquals(WildcardQuery::class, getQuery("t*erm*", cqpC)?.let { it::class })
        assertEquals(WildcardQuery::class, getQuery("?term*", cqpC)?.let { it::class })
        assertEquals(WildcardQuery::class, getQuery("*term*", cqpC)?.let { it::class })
    }

    open fun testQPA() {
        assertQueryEquals("term term^3.0 term", qpAnalyzer, "term (term)^3.0 term")
        assertQueryEquals("term stop^3.0 term", qpAnalyzer, "term term")
        assertQueryEquals("term term term", qpAnalyzer, "term term term")
        assertQueryEquals("term +stop term", qpAnalyzer, "term term")
        assertQueryEquals("term -stop term", qpAnalyzer, "term term")
        assertQueryEquals("drop AND (stop) AND roll", qpAnalyzer, "+drop +roll")
        assertQueryEquals("term +(stop) term", qpAnalyzer, "term term")
        assertQueryEquals("term -(stop) term", qpAnalyzer, "term term")
        assertQueryEquals("drop AND stop AND roll", qpAnalyzer, "+drop +roll")
        assertQueryEquals("term AND NOT phrase term", qpAnalyzer, "+term -(phrase1 phrase2) term")
        assertMatchNoDocsQuery("stop^3", qpAnalyzer)
        assertMatchNoDocsQuery("stop", qpAnalyzer)
        assertMatchNoDocsQuery("(stop)^3", qpAnalyzer)
        assertMatchNoDocsQuery("((stop))^3", qpAnalyzer)
        assertMatchNoDocsQuery("(stop^3)", qpAnalyzer)
        assertMatchNoDocsQuery("((stop)^3)", qpAnalyzer)
        assertMatchNoDocsQuery("(stop)", qpAnalyzer)
        assertMatchNoDocsQuery("((stop))", qpAnalyzer)
        assertTrue(getQuery("term term term", qpAnalyzer) is BooleanQuery)
        assertTrue(getQuery("term +stop", qpAnalyzer) is TermQuery)

        val cqpc = getParserConfig(qpAnalyzer)
        setDefaultOperatorAND(cqpc)
        assertQueryEquals(cqpc, "field", "phrase", "+phrase1 +phrase2")
    }

    open fun testRange() {
        assertQueryEquals("[ a TO z]", null, "[a TO z]")
        assertQueryEquals("[ a TO z}", null, "[a TO z}")
        assertQueryEquals("{ a TO z]", null, "{a TO z]")

        assertEquals(
            MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE,
            (getQuery("[ a TO z]") as TermRangeQuery).rewriteMethod,
        )

        val qp = getParserConfig(MockAnalyzer(random(), MockTokenizer.SIMPLE, true))
        qp.multiTermRewriteMethod = MultiTermQuery.SCORING_BOOLEAN_REWRITE
        assertEquals(
            MultiTermQuery.SCORING_BOOLEAN_REWRITE,
            (getQuery("[ a TO z]", qp) as TermRangeQuery).rewriteMethod,
        )

        assertQueryEquals("[ a TO * ]", null, "[a TO *]")
        assertQueryEquals("[ * TO z ]", null, "[* TO z]")
        assertQueryEquals("[ * TO * ]", null, "[* TO *]")
        assertQueryEquals("{ a TO z ]", null, "{a TO z]")
        assertQueryEquals("[ a TO z }", null, "[a TO z}")
        assertQueryEquals("{ a TO * ]", null, "{a TO *]")
        assertQueryEquals("[ * TO z }", null, "[* TO z}")
        assertQueryEquals("[ a TO z ]", null, "[a TO z]")
        assertQueryEquals("{ a TO z}", null, "{a TO z}")
        assertQueryEquals("{ a TO z }", null, "{a TO z}")
        assertQueryEquals("{ a TO z }^2.0", null, "({a TO z})^2.0")
        assertQueryEquals("[ a TO z] OR bar", null, "[a TO z] bar")
        assertQueryEquals("[ a TO z] AND bar", null, "+[a TO z] +bar")
        assertQueryEquals("( bar blar { a TO z}) ", null, "bar blar {a TO z}")
        assertQueryEquals("gack ( bar blar { a TO z}) ", null, "gack (bar blar {a TO z})")
        assertQueryEquals("[* TO Z]", null, "[* TO z]")
        assertQueryEquals("[A TO *]", null, "[a TO *]")
        assertQueryEquals("[* TO *]", null, "[* TO *]")
    }

    open fun testRangeWithPhrase() {
        assertQueryEquals("[\\* TO \"*\"]", null, "[\\* TO \\*]")
        assertQueryEquals("[\"*\" TO *]", null, "[\\* TO *]")
    }

    open fun testRangeQueryEndpointTO() {
        val a = MockAnalyzer(random())
        assertQueryEquals("[to TO to]", a, "[to TO to]")
        assertQueryEquals("[to TO TO]", a, "[to TO to]")
        assertQueryEquals("[TO TO to]", a, "[to TO to]")
        assertQueryEquals("[TO TO TO]", a, "[to TO to]")
        assertQueryEquals("[\"TO\" TO \"TO\"]", a, "[to TO to]")
        assertQueryEquals("[\"TO\" TO TO]", a, "[to TO to]")
        assertQueryEquals("[TO TO \"TO\"]", a, "[to TO to]")
        assertQueryEquals("[to TO xx]", a, "[to TO xx]")
        assertQueryEquals("[\"TO\" TO xx]", a, "[to TO xx]")
        assertQueryEquals("[TO TO xx]", a, "[to TO xx]")
        assertQueryEquals("[xx TO to]", a, "[xx TO to]")
        assertQueryEquals("[xx TO \"TO\"]", a, "[xx TO to]")
        assertQueryEquals("[xx TO TO]", a, "[xx TO to]")
    }

    open fun testRangeQueryRequiresTO() {
        val a = MockAnalyzer(random())
        assertQueryEquals("{A TO B}", a, "{a TO b}")
        assertQueryEquals("[A TO B}", a, "[a TO b}")
        assertQueryEquals("{A TO B]", a, "{a TO b]")
        assertQueryEquals("[A TO B]", a, "[a TO b]")

        val exceptionClass =
            if (this is TestQueryParser) {
                org.gnit.lucenekmp.queryparser.classic.ParseException::class
            } else {
                Exception::class
            }

        expectThrows(exceptionClass) { getQuery("{A B}") }
        expectThrows(exceptionClass) { getQuery("[A B}") }
        expectThrows(exceptionClass) { getQuery("{A B]") }
        expectThrows(exceptionClass) { getQuery("[A B]") }
        expectThrows(exceptionClass) { getQuery("{TO B}") }
        expectThrows(exceptionClass) { getQuery("[TO B}") }
        expectThrows(exceptionClass) { getQuery("{TO B]") }
        expectThrows(exceptionClass) { getQuery("[TO B]") }
        expectThrows(exceptionClass) { getQuery("{A TO}") }
        expectThrows(exceptionClass) { getQuery("[A TO}") }
        expectThrows(exceptionClass) { getQuery("{A TO]") }
        expectThrows(exceptionClass) { getQuery("[A TO]") }
    }

    private fun escapeDateString(s: String): String {
        return if (s.indexOf(' ') > -1) {
            "\"$s\""
        } else {
            s
        }
    }

    /** for testing DateTools support */
    private fun getDate(s: String, resolution: DateTools.Resolution): String {
        // we use the default Locale since LuceneTestCase randomizes it
        return getDate(parseLocalizedDate(s), resolution)
    }

    /** for testing DateTools support */
    private fun getDate(d: Instant, resolution: DateTools.Resolution): String {
        return DateTools.dateToString(d, resolution)
    }

    private fun getLocalizedDate(year: Int, month: Int, day: Int): String {
        // we use the default Locale/TZ since LuceneTestCase randomizes it
        return "${month + 1}/$day/${year % 100}"
    }

    private fun parseLocalizedDate(s: String): Instant {
        val parts = s.split("/")
        val month = parts[0].toInt()
        val day = parts[1].toInt()
        val year = 2000 + parts[2].toInt()
        return LocalDateTime(year, month, day, 0, 0, 0, 0).toInstant(TimeZone.UTC)
    }

    open fun testDateRange() {
        val startDate = getLocalizedDate(2002, 1, 1)
        val endDate = getLocalizedDate(2002, 1, 4)
        // we use the default Locale/TZ since LuceneTestCase randomizes it
        val endDateExpected =
            LocalDateTime(2002, 2, 4, 23, 59, 59, 999_000_000).toInstant(TimeZone.UTC)
        val defaultField = "default"
        val monthField = "month"
        val hourField = "hour"
        val a = MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
        val qp = getParserConfig(a)
        setDateResolution(qp, monthField, DateTools.Resolution.MONTH)
        qp.dateResolution = DateTools.Resolution.MILLISECOND
        setDateResolution(qp, hourField, DateTools.Resolution.HOUR)

        assertDateRangeQueryEquals(
            qp,
            defaultField,
            startDate,
            endDate,
            endDateExpected,
            DateTools.Resolution.MILLISECOND,
        )
        assertDateRangeQueryEquals(
            qp,
            monthField,
            startDate,
            endDate,
            endDateExpected,
            DateTools.Resolution.MONTH,
        )
        assertDateRangeQueryEquals(
            qp,
            hourField,
            startDate,
            endDate,
            endDateExpected,
            DateTools.Resolution.HOUR,
        )
    }

    fun assertDateRangeQueryEquals(
        cqpC: CommonQueryParserConfiguration,
        field: String,
        startDate: String,
        endDate: String,
        endDateInclusive: Instant,
        resolution: DateTools.Resolution,
    ) {
        assertQueryEquals(
            cqpC,
            field,
            "$field:[${escapeDateString(startDate)} TO ${escapeDateString(endDate)}]",
            "[${getDate(startDate, resolution)} TO ${getDate(endDateInclusive, resolution)}]",
        )
        assertQueryEquals(
            cqpC,
            field,
            "$field:{${escapeDateString(startDate)} TO ${escapeDateString(endDate)}}",
            "{${getDate(startDate, resolution)} TO ${getDate(endDate, resolution)}}",
        )
    }

    open fun testEscaped() {
        val a = MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)

        /*assertQueryEquals("\\[brackets", a, "\\[brackets")
        assertQueryEquals("\\[brackets", null, "brackets")
        assertQueryEquals("\\\\", a, "\\\\")
        assertQueryEquals("\\+blah", a, "\\+blah")
        assertQueryEquals("\\(blah", a, "\\(blah")

        assertQueryEquals("\\-blah", a, "\\-blah")
        assertQueryEquals("\\!blah", a, "\\!blah")
        assertQueryEquals("\\{blah", a, "\\{blah")
        assertQueryEquals("\\}blah", a, "\\}blah")
        assertQueryEquals("\\:blah", a, "\\:blah")
        assertQueryEquals("\\^blah", a, "\\^blah")
        assertQueryEquals("\\[blah", a, "\\[blah")
        assertQueryEquals("\\]blah", a, "\\]blah")
        assertQueryEquals("\\\"blah", a, "\\\"blah")
        assertQueryEquals("\\(blah", a, "\\(blah")
        assertQueryEquals("\\)blah", a, "\\)blah")
        assertQueryEquals("\\~blah", a, "\\~blah")
        assertQueryEquals("\\*blah", a, "\\*blah")
        assertQueryEquals("\\?blah", a, "\\?blah")
        //assertQueryEquals("foo \\&\\& bar", a, "foo \\&\\& bar")
        //assertQueryEquals("foo \\|| bar", a, "foo \\|| bar")
        //assertQueryEquals("foo \\AND bar", a, "foo \\AND bar")*/

        assertQueryEquals("\\a", a, "a")
        assertQueryEquals("a\\-b:c", a, "a-b:c")
        assertQueryEquals("a\\+b:c", a, "a+b:c")
        assertQueryEquals("a\\:b:c", a, "a:b:c")
        assertQueryEquals("a\\\\b:c", a, "a\\b:c")
        assertQueryEquals("a:b\\-c", a, "a:b-c")
        assertQueryEquals("a:b\\+c", a, "a:b+c")
        assertQueryEquals("a:b\\:c", a, "a:b:c")
        assertQueryEquals("a:b\\\\c", a, "a:b\\c")
        assertQueryEquals("a:b\\-c*", a, "a:b-c*")
        assertQueryEquals("a:b\\+c*", a, "a:b+c*")
        assertQueryEquals("a:b\\:c*", a, "a:b:c*")
        assertQueryEquals("a:b\\\\c*", a, "a:b\\c*")
        assertQueryEquals("a:b\\-c~", a, "a:b-c~2")
        assertQueryEquals("a:b\\+c~", a, "a:b+c~2")
        assertQueryEquals("a:b\\:c~", a, "a:b:c~2")
        assertQueryEquals("a:b\\\\c~", a, "a:b\\c~2")
        assertQueryEquals("[ a\\- TO a\\+ ]", null, "[a- TO a+]")
        assertQueryEquals("[ a\\: TO a\\~ ]", null, "[a: TO a~]")
        assertQueryEquals("[ a\\\\ TO a\\* ]", null, "[a\\ TO a*]")
        assertQueryEquals(
            "[\"c\\:\\\\temp\\\\\\~foo0.txt\" TO \"c\\:\\\\temp\\\\\\~foo9.txt\"]",
            a,
            "[c:\\temp\\~foo0.txt TO c:\\temp\\~foo9.txt]",
        )
        assertQueryEquals("a\\\\\\+b", a, "a\\+b")
        assertQueryEquals("a \\\"b c\\\" d", a, "a \"b c\" d")
        assertQueryEquals("\"a \\\"b c\\\" d\"", a, "\"a \"b c\" d\"")
        assertQueryEquals("\"a \\+b c d\"", a, "\"a +b c d\"")
        assertQueryEquals("c\\:\\\\temp\\\\\\~foo.txt", a, "c:\\temp\\~foo.txt")
        assertParseException("XY\\")
        assertQueryEquals("a\\u0062c", a, "abc")
        assertQueryEquals("XY\\u005a", a, "XYZ")
        assertQueryEquals("XY\\u005A", a, "XYZ")
        assertQueryEquals("\"a \\\\\\u0028\\u0062\\\" c\"", a, "\"a \\(b\" c\"")
        assertParseException("XY\\u005G")
        assertParseException("XY\\u005")
        assertQueryEquals("(item:\\\\ item:ABCD\\\\)", a, "item:\\ item:ABCD\\")
        assertParseException("(item:\\\\ item:ABCD\\\\))")
        assertQueryEquals("\\*", a, "*")
        assertQueryEquals("\\\\", a, "\\")
        assertParseException("\\")
        assertQueryEquals("(\"a\\\\\") or (\"b\")", a, "a\\ or b")
    }

    open fun testEscapedVsQuestionMarkAsWildcard() {
        val a = MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)
        assertQueryEquals("a:b\\-?c", a, "a:b\\-?c")
        assertQueryEquals("a:b\\+?c", a, "a:b\\+?c")
        assertQueryEquals("a:b\\:?c", a, "a:b\\:?c")
        assertQueryEquals("a:b\\\\?c", a, "a:b\\\\?c")
    }

    open fun testQueryStringEscaping() {
        val a = MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)
        assertEscapedQueryEquals("a-b:c", a, "a\\-b\\:c")
        assertEscapedQueryEquals("a+b:c", a, "a\\+b\\:c")
        assertEscapedQueryEquals("a:b:c", a, "a\\:b\\:c")
        assertEscapedQueryEquals("a\\b:c", a, "a\\\\b\\:c")
        assertEscapedQueryEquals("a:b-c", a, "a\\:b\\-c")
        assertEscapedQueryEquals("a:b+c", a, "a\\:b\\+c")
        assertEscapedQueryEquals("a:b:c", a, "a\\:b\\:c")
        assertEscapedQueryEquals("a:b\\c", a, "a\\:b\\\\c")
        assertEscapedQueryEquals("a:b-c*", a, "a\\:b\\-c\\*")
        assertEscapedQueryEquals("a:b+c*", a, "a\\:b\\+c\\*")
        assertEscapedQueryEquals("a:b:c*", a, "a\\:b\\:c\\*")
        assertEscapedQueryEquals("a:b\\\\c*", a, "a\\:b\\\\\\\\c\\*")
        assertEscapedQueryEquals("a:b-?c", a, "a\\:b\\-\\?c")
        assertEscapedQueryEquals("a:b+?c", a, "a\\:b\\+\\?c")
        assertEscapedQueryEquals("a:b:?c", a, "a\\:b\\:\\?c")
        assertEscapedQueryEquals("a:b?c", a, "a\\:b\\?c")
        assertEscapedQueryEquals("a:b-c~", a, "a\\:b\\-c\\~")
        assertEscapedQueryEquals("a:b+c~", a, "a\\:b\\+c\\~")
        assertEscapedQueryEquals("a:b:c~", a, "a\\:b\\:c\\~")
        assertEscapedQueryEquals("a:b\\c~", a, "a\\:b\\\\c\\~")
        assertEscapedQueryEquals("[ a - TO a+ ]", null, "\\[ a \\- TO a\\+ \\]")
        assertEscapedQueryEquals("[ a : TO a~ ]", null, "\\[ a \\: TO a\\~ \\]")
        assertEscapedQueryEquals("[ a\\ TO a* ]", null, "\\[ a\\\\ TO a\\* \\]")
        assertEscapedQueryEquals("|| abc ||", a, "\\|\\| abc \\|\\|")
        assertEscapedQueryEquals("&& abc &&", a, "\\&\\& abc \\&\\&")
    }

    open fun testTabNewlineCarriageReturn() {
        assertQueryEqualsDOA("+weltbank +worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("+weltbank\n+worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("weltbank \n+worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("weltbank \n +worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("+weltbank\r+worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("weltbank \r+worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("weltbank \r +worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("+weltbank\r\n+worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("weltbank \r\n+worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("weltbank \r\n +worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("weltbank \r \n +worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("+weltbank\t+worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("weltbank \t+worlbank", null, "+weltbank +worlbank")
        assertQueryEqualsDOA("weltbank \t +worlbank", null, "+weltbank +worlbank")
    }

    open fun testSimpleDAO() {
        assertQueryEqualsDOA("term term term", null, "+term +term +term")
        assertQueryEqualsDOA("term +term term", null, "+term +term +term")
        assertQueryEqualsDOA("term term +term", null, "+term +term +term")
        assertQueryEqualsDOA("term +term +term", null, "+term +term +term")
        assertQueryEqualsDOA("-term term term", null, "-term +term +term")
    }

    open fun testBoost() {
        val stopWords = CharacterRunAutomaton(Automata.makeString("on"))
        val oneStopAnalyzer = MockAnalyzer(random(), MockTokenizer.SIMPLE, true, stopWords)
        val qp = getParserConfig(oneStopAnalyzer)
        var q = getQuery("on^1.0", qp)
        assertNotNull(q)
        q = getQuery("\"hello\"^2.0", qp)
        assertNotNull(q)
        assertEquals(2.0f, (q as BoostQuery).boost, 0.5f)
        q = getQuery("hello^2.0", qp)
        assertNotNull(q)
        assertEquals(2.0f, (q as BoostQuery).boost, 0.5f)
        q = getQuery("\"on\"^1.0", qp)
        assertNotNull(q)

        val a2 = MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET)
        val qp2 = getParserConfig(a2)
        q = getQuery("the^3", qp2)
        assertNotNull(q)
        assertMatchNoDocsQuery(q)
        assertFalse(q is BoostQuery)
    }

    fun assertParseException(queryString: String) {
        try {
            getQuery(queryString)
        } catch (expected: Exception) {
            if (isQueryParserException(expected)) {
                return
            }
        }
        fail("ParseException expected, not thrown")
    }

    fun assertParseException(queryString: String, a: Analyzer?) {
        try {
            getQuery(queryString, a)
        } catch (expected: Exception) {
            if (isQueryParserException(expected)) {
                return
            }
        }
        fail("ParseException expected, not thrown")
    }

    open fun testException() {
        assertParseException("\"some phrase")
        assertParseException("(foo bar")
        assertParseException("foo bar))")
        assertParseException("field:term:with:colon some more terms")
        assertParseException("(sub query)^5.0^2.0 plus more")
        assertParseException("secret AND illegal) AND access:confidential")
    }

    open fun testBooleanQuery() {
        IndexSearcher.maxClauseCount = 2
        val pureWhitespaceAnalyzer = MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)
        assertParseException("one two three", pureWhitespaceAnalyzer)
    }

    open fun testPrecedence() {
        val qp = getParserConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        val query1 = getQuery("A AND B OR C AND D", qp)
        val query2 = getQuery("+A +B +C +D", qp)
        assertEquals(query1, query2)
    }

    open fun testParsesBracketsIfQuoted() {
        val a = MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)
        assertQueryEquals("[\"a[i]\" TO \"b[i]\"]", a, "[a[i] TO b[i]]")
        assertQueryEquals("{\"a[i]\" TO \"b[i]\"}", a, "{a[i] TO b[i]}")
        assertQueryEquals("[\"a[i]\" TO \"b[i]\"}", a, "[a[i] TO b[i]}")
        assertQueryEquals("{\"a[i]\" TO \"b[i]\"]", a, "{a[i] TO b[i]]")
        assertQueryEquals("[\"a[i\\]\" TO \"b[i\\]\"]", a, "[a[i] TO b[i]]")
        assertQueryEquals("[\"a\\[i\\]\" TO \"b\\[i\\]\"]", a, "[a[i] TO b[i]]")
        assertQueryEquals("[\"a[i][j]\" TO \"b[i][j]\"]", a, "[a[i][j] TO b[i][j]]")
        assertQueryEquals(
            "[ \"2024-01-01T01:01:01+01:00[Europe/Warsaw]\" TO \"2025-01-01T01:01:01+01:00[Europe/Warsaw]\" ]",
            null,
            "[2024-01-01t01:01:01+01:00[europe/warsaw] TO 2025-01-01t01:01:01+01:00[europe/warsaw]]",
        )
        assertParseException("[a[i] TO b[i]]")
        assertParseException("[a\\[i\\] TO b\\[i\\]]")
    }

    abstract fun testStarParsing()

    open fun testEscapedWildcard() {
        val qp = getParserConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        val q = WildcardQuery(Term("field", "foo\\?ba?r"))
        assertEquals(q, getQuery("foo\\?ba?r", qp))
    }

    open fun testRegexps() {
        val qp = getParserConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, true))
        var q: Query = RegexpQuery(Term("field", "[a-z][123]"))
        assertEquals(q, getQuery("/[a-z][123]/", qp))
        assertEquals(q, getQuery("/[A-Z][123]/", qp))
        assertEquals(BoostQuery(q, 0.5f), getQuery("/[A-Z][123]/^0.5", qp))

        val escaped: Query = RegexpQuery(Term("field", "[a-z]\\/[123]"))
        assertEquals(escaped, getQuery("/[a-z]\\/[123]/", qp))
        val escaped2: Query = RegexpQuery(Term("field", "[a-z]\\*[123]"))
        assertEquals(escaped2, getQuery("/[a-z]\\*[123]/", qp))

        val complex = BooleanQuery.Builder()
        complex.add(RegexpQuery(Term("field", "[a-z]\\/[123]")), Occur.MUST)
        complex.add(TermQuery(Term("path", "/etc/init.d/")), Occur.MUST)
        complex.add(TermQuery(Term("field", "/etc/init[.]d/lucene/")), Occur.SHOULD)
        assertEquals(
            complex.build(),
            getQuery("/[a-z]\\/[123]/ AND path:\"/etc/init.d/\" OR \"/etc\\/init\\[.\\]d/lucene/\" ", qp),
        )

        var re: Query = RegexpQuery(Term("field", "http.*"))
        assertEquals(re, getQuery("field:/http.*/", qp))
        assertEquals(re, getQuery("/http.*/", qp))
        re = RegexpQuery(Term("field", "http~0.5"))
        assertEquals(re, getQuery("field:/http~0.5/", qp))
        assertEquals(re, getQuery("/http~0.5/", qp))
        re = RegexpQuery(Term("field", "boo"))
        assertEquals(re, getQuery("field:/boo/", qp))
        assertEquals(re, getQuery("/boo/", qp))
        assertEquals(TermQuery(Term("field", "/boo/")), getQuery("\"/boo/\"", qp))
        assertEquals(TermQuery(Term("field", "/boo/")), getQuery("\\/boo\\/", qp))

        val two = BooleanQuery.Builder()
        two.add(RegexpQuery(Term("field", "foo")), Occur.SHOULD)
        two.add(RegexpQuery(Term("field", "bar")), Occur.SHOULD)
        assertEquals(two.build(), getQuery("field:/foo/ field:/bar/", qp))
        assertEquals(two.build(), getQuery("/foo/ /bar/", qp))

        qp.multiTermRewriteMethod = MultiTermQuery.SCORING_BOOLEAN_REWRITE
        q = RegexpQuery(
            Term("field", "[a-z][123]"),
            RegExp.ALL,
            0,
            RegexpQuery.DEFAULT_PROVIDER,
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
            MultiTermQuery.SCORING_BOOLEAN_REWRITE,
        )
        assertTrue(getQuery("/[A-Z][123]/^0.5", qp) is BoostQuery)
        assertTrue((getQuery("/[A-Z][123]/^0.5", qp) as BoostQuery).query is RegexpQuery)
        assertEquals(
            MultiTermQuery.SCORING_BOOLEAN_REWRITE,
            ((getQuery("/[A-Z][123]/^0.5", qp) as BoostQuery).query as RegexpQuery).rewriteMethod,
        )
        assertEquals(BoostQuery(q, 0.5f), getQuery("/[A-Z][123]/^0.5", qp))
    }

    open fun testStopwords() {
        val stopSet = CharacterRunAutomaton(RegExp("the|foo").toAutomaton())
        val qp = getParserConfig(MockAnalyzer(random(), MockTokenizer.SIMPLE, true, stopSet))
        var result = getQuery("field:the OR field:foo", qp)
        assertNotNull(result, "result is null and it shouldn't be")
        assertTrue(result is BooleanQuery || result is MatchNoDocsQuery, "result is not a BooleanQuery")
        if (result is BooleanQuery) {
            assertEquals(0, result.clauses().size)
        }
        result = getQuery("field:woo OR field:the", qp)
        assertNotNull(result, "result is null and it shouldn't be")
        assertTrue(result is TermQuery, "result is not a TermQuery")
        result = getQuery("(fieldX:xxxxx OR fieldy:xxxxxxxx)^2 AND (fieldx:the OR fieldy:foo)", qp)
        assertNotNull(result, "result is null and it shouldn't be")
        assertTrue(result is BoostQuery, "result is not a BoostQuery")
        result = (result as BoostQuery).query
        assertTrue(result is BooleanQuery, "result is not a BooleanQuery")
        assertTrue((result as BooleanQuery).clauses().size == 2, "${result.clauses().size} does not equal: 2")
    }

    open fun testPositionIncrement() {
        val qp =
            getParserConfig(MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET))
        qp.enablePositionIncrements = true
        val qtxt = "\"the words in poisitions pos02578 are stopped in this phrasequery\""
        val expectedPositions = intArrayOf(1, 3, 4, 6, 9)
        val pq = getQuery(qtxt, qp) as PhraseQuery
        val t = pq.terms
        val pos = pq.positions
        for (i in t.indices) {
            assertEquals(expectedPositions[i], pos[i], "term $i = ${t[i]} has wrong term-position!")
        }
    }

    open fun testMatchAllDocs() {
        val qp = getParserConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        assertEquals(MatchAllDocsQuery(), getQuery("*:*", qp))
        assertEquals(MatchAllDocsQuery(), getQuery("(*:*)", qp))
        val bq = getQuery("+*:* -*:*", qp) as BooleanQuery
        assertEquals(2, bq.clauses().size)
        for (clause in bq) {
            assertTrue(clause.query is MatchAllDocsQuery)
        }
    }

    private fun assertHits(expected: Int, query: String, `is`: IndexSearcher) {
        val oldDefaultField = getDefaultField()
        setDefaultField("date")
        val qp = getParserConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        qp.locale = Locale.US
        val q = getQuery(query, qp)
        val hits = `is`.search(q!!, 1000).scoreDocs
        assertEquals(expected, hits.size)
        setDefaultField(oldDefaultField)
    }

    open fun testPositionIncrements() {
        val dir = newDirectory()
        val a = MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET)
        val w = IndexWriter(dir, newIndexWriterConfig(a))
        val doc = Document()
        doc.add(newTextField("field", "the wizard of ozzy", Field.Store.NO))
        w.addDocument(doc)
        val r = DirectoryReader.open(w)
        w.close()
        val s = newSearcher(r)
        val q = getQuery("\"wizard of ozzy\"", a)
        assertEquals(1L, s.search(q!!, 1).totalHits.value)
        r.close()
        dir.close()
    }

    /** whitespace+lowercase analyzer with synonyms */
    protected class Analyzer1 : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
            return TokenStreamComponents(tokenizer, MockSynonymFilter(tokenizer))
        }
    }

    /** whitespace+lowercase analyzer without synonyms */
    protected class Analyzer2 : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            return TokenStreamComponents(MockTokenizer(MockTokenizer.WHITESPACE, true))
        }
    }

    abstract fun testNewFieldQuery()

    /** Mock collation analyzer: indexes terms as "collated" + term */
    private class MockCollationFilter(input: TokenStream) : TokenFilter(input) {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

        @Throws(okio.IOException::class)
        override fun incrementToken(): Boolean {
            if (input.incrementToken()) {
                val term = termAtt.toString()
                termAtt.setEmpty()!!.append("collated").append(term)
                return true
            }
            return false
        }
    }

    private class MockCollationAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
            return TokenStreamComponents(tokenizer, MockCollationFilter(tokenizer))
        }

        override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
            return MockCollationFilter(LowerCaseFilter(`in`))
        }
    }

    open fun testCollatedRange() {
        val qp = getParserConfig(MockCollationAnalyzer())
        val expected = TermRangeQuery.newStringRange(getDefaultField(), "collatedabc", "collateddef", true, true)
        val actual = getQuery("[abc TO def]", qp)
        assertEquals(expected, actual)
    }

    open fun testDistanceAsEditsParsing() {
        val q = getQuery("foobar~2", MockAnalyzer(random())) as FuzzyQuery
        assertEquals(2, q.maxEdits)
    }

    open fun testPhraseQueryToString() {
        val analyzer = MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET)
        val qp = getParserConfig(analyzer)
        qp.enablePositionIncrements = true
        val q = getQuery("\"this hi this is a test is\"", qp) as PhraseQuery
        assertEquals("field:\"? hi ? ? ? test\"", q.toString())
    }

    open fun testParseWildcardAndPhraseQueries() {
        val field = "content"
        val oldDefaultField = getDefaultField()
        setDefaultField(field)
        val qp = getParserConfig(MockAnalyzer(random()))
        qp.allowLeadingWildcard = true

        val prefixQueries = arrayOf(
            arrayOf("a*", "ab*", "abc*"),
            arrayOf("h*", "hi*", "hij*", "\\\\7*"),
            arrayOf("o*", "op*", "opq*", "\\\\\\\\*"),
        )
        val wildcardQueries = arrayOf(
            arrayOf("*a*", "*ab*", "*abc**", "ab*e*", "*g?", "*f?1", "abc**"),
            arrayOf("*h*", "*hi*", "*hij**", "hi*k*", "*n?", "*m?1", "hij**"),
            arrayOf("*o*", "*op*", "*opq**", "op*q*", "*u?", "*t?1", "opq**"),
        )

        for (i in prefixQueries.indices) {
            for (j in prefixQueries[i].indices) {
                val queryString = prefixQueries[i][j]
                val q = getQuery(queryString, qp)
                assertEquals(PrefixQuery::class, q?.let { it::class })
            }
        }
        for (i in wildcardQueries.indices) {
            for (j in wildcardQueries[i].indices) {
                val qtxt = wildcardQueries[i][j]
                val q = getQuery(qtxt, qp)
                assertEquals(WildcardQuery::class, q?.let { it::class })
            }
        }
        setDefaultField(oldDefaultField)
    }

    open fun testPhraseQueryPositionIncrements() {
        val stopStopList = CharacterRunAutomaton(RegExp("[sS][tT][oO][pP]").toAutomaton())
        val qp = getParserConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false, stopStopList))
        qp.enablePositionIncrements = true

        val phraseQuery = PhraseQuery.Builder()
        phraseQuery.add(Term("field", "1"))
        phraseQuery.add(Term("field", "2"), 2)
        assertEquals(phraseQuery.build(), getQuery("\"1 stop 2\"", qp))
    }

    open fun testMatchAllQueryParsing() {
        val oldDefaultField = getDefaultField()
        setDefaultField("key")
        val qp = getParserConfig(MockAnalyzer(random()))
        assertEquals(MatchAllDocsQuery(), getQuery(MatchAllDocsQuery().toString(), qp))
        var query: Query = MatchAllDocsQuery()
        query = BoostQuery(query, 2.3f)
        assertEquals(query, getQuery(query.toString(), qp))
        setDefaultField(oldDefaultField)
    }

    open fun testNestedAndClausesFoo() {
        val query = "(field1:[1 TO *] AND field1:[* TO 2]) AND field2:(z)"
        val q = BooleanQuery.Builder()
        val bq = BooleanQuery.Builder()
        bq.add(TermRangeQuery.newStringRange("field1", "1", null, true, true), BooleanClause.Occur.MUST)
        bq.add(TermRangeQuery.newStringRange("field1", null, "2", true, true), BooleanClause.Occur.MUST)
        q.add(bq.build(), BooleanClause.Occur.MUST)
        q.add(TermQuery(Term("field2", "z")), BooleanClause.Occur.MUST)
        assertEquals(q.build(), getQuery(query, MockAnalyzer(random())))
    }
}
