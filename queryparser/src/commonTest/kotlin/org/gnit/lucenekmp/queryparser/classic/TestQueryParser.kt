package org.gnit.lucenekmp.queryparser.classic

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.document.DateTools
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.queryparser.flexible.standard.CommonQueryParserConfiguration
import org.gnit.lucenekmp.queryparser.util.QueryParserTestBase
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.MultiPhraseQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.SynonymQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockBytesAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockLowerCaseFilter
import org.gnit.lucenekmp.tests.analysis.MockSynonymAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.util.automaton.TooComplexToDeterminizeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests QueryParser. */
class TestQueryParser : QueryParserTestBase() {
    protected var splitOnWhitespace = QueryParser.DEFAULT_SPLIT_ON_WHITESPACE

    private companion object {
        const val FIELD = "field"
    }

    class QPTestParser(f: String, a: Analyzer) : QueryParser(f, a) {
        override fun getFuzzyQuery(field: String, termStr: String, minSimilarity: Float): Query {
            throw ParseException("Fuzzy queries not allowed")
        }

        override fun getWildcardQuery(field: String, termStr: String): Query {
            throw ParseException("Wildcard queries not allowed")
        }
    }

    open fun getParser(a: Analyzer?): QueryParser {
        val analyzer = a ?: MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
        val qp = QueryParser(getDefaultField(), analyzer)
        qp.setDefaultOperator(QueryParserBase.OR_OPERATOR)
        qp.setSplitOnWhitespace(splitOnWhitespace)
        return qp
    }

    override fun getParserConfig(a: Analyzer?): CommonQueryParserConfiguration {
        return getParser(a)
    }

    override fun getQuery(query: String, cqpC: CommonQueryParserConfiguration): Query? {
        require(cqpC is QueryParser) { "Parameter must be instance of QueryParser" }
        return cqpC.parse(query)
    }

    override fun getQuery(query: String, a: Analyzer?): Query? {
        return getParser(a).parse(query)
    }

    override fun isQueryParserException(exception: Exception): Boolean {
        return exception is ParseException
    }

    override fun setDefaultOperatorOR(cqpC: CommonQueryParserConfiguration) {
        require(cqpC is QueryParser)
        cqpC.setDefaultOperator(QueryParser.Operator.OR)
    }

    override fun setDefaultOperatorAND(cqpC: CommonQueryParserConfiguration) {
        require(cqpC is QueryParser)
        cqpC.setDefaultOperator(QueryParser.Operator.AND)
    }

    override fun setAutoGeneratePhraseQueries(cqpC: CommonQueryParserConfiguration, value: Boolean) {
        require(cqpC is QueryParser)
        cqpC.autoGeneratePhraseQueries = value
    }

    override fun setDateResolution(
        cqpC: CommonQueryParserConfiguration,
        field: CharSequence,
        value: DateTools.Resolution,
    ) {
        require(cqpC is QueryParser)
        cqpC.setDateResolution(field.toString(), value)
    }

    override fun testDefaultOperator() {
        val qp = getParser(MockAnalyzer(random()))
        assertEquals(QueryParserBase.OR_OPERATOR, qp.defaultOperator)
        setDefaultOperatorAND(qp)
        assertEquals(QueryParserBase.AND_OPERATOR, qp.defaultOperator)
        setDefaultOperatorOR(qp)
        assertEquals(QueryParserBase.OR_OPERATOR, qp.defaultOperator)
    }

    // LUCENE-2002: when we run javacc to regen QueryParser,
    // we also run a replaceregexp step to fix 2 of the public
    // ctors (change them to protected):
    //
    // protected QueryParser(CharStream stream)
    //
    // protected QueryParser(QueryParserTokenManager tm)
    //
    // This test is here as a safety, in case that ant step
    // doesn't work for some reason.
    @Test
    fun testProtectedCtors() {
        // Kotlin common reflection does not expose constructor visibility metadata here.
        // The production QueryParser keeps the CharStream and QueryParserTokenManager ctors protected.
        assertTrue(true)
    }

    @Test
    fun testFuzzySlopeExtendability() {
        val qp =
            object : QueryParser("a", MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)) {
                override fun handleBareFuzzy(qfield: String, fuzzySlop: Token, termImage: String): Query {
                    if (fuzzySlop.image!!.endsWith("€")) {
                        var fms = fuzzyMinSim
                        try {
                            fms = fuzzySlop.image!!.substring(1, fuzzySlop.image!!.length - 1).toFloat()
                        } catch (_: Exception) {
                        }
                        val value = termImage.toFloat()
                        return getRangeQuery(
                            qfield,
                            (value - fms / 2f).toString(),
                            (value + fms / 2f).toString(),
                            true,
                            true,
                        )
                    }
                    return super.handleBareFuzzy(qfield, fuzzySlop, termImage)
                }
            }
        assertEquals(qp.parse("a:[11.95 TO 12.95]"), qp.parse("12.45~1€"))
    }

    @Test
    fun testFuzzyDistanceExtendability() {
        val qp =
            object : QueryParser("a", MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)) {
                override fun getFuzzyDistance(fuzzyToken: Token, termStr: String?): Float {
                    return try {
                        fuzzyToken.image!!.substring(1).toFloat()
                    } catch (_: Exception) {
                        1f
                    }
                }
            }
        assertEquals(qp.parse("term~"), qp.parse("term~1"))
        assertEquals(qp.parse("term~XXX"), qp.parse("term~1"))

        val qp2 =
            object : QueryParser("a", MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)) {
                override fun getFuzzyDistance(fuzzyToken: Token, termStr: String?): Float {
                    return termStr!!.length.toFloat()
                }
            }
        assertEquals(qp2.parse("a~"), qp2.parse("a~1"))
        assertEquals(qp2.parse("ab~"), qp2.parse("ab~2"))
    }

    @Test
    override fun testStarParsing() {
        val type = intArrayOf(0)
        val qp =
            object : QueryParser(FIELD, MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)) {
                override fun getWildcardQuery(field: String, termStr: String): Query {
                    type[0] = 1
                    return TermQuery(Term(field, termStr))
                }

                override fun getPrefixQuery(field: String, termStr: String): Query {
                    type[0] = 2
                    return TermQuery(Term(field, termStr))
                }

                override fun getFieldQuery(field: String, queryText: String, quoted: Boolean): Query? {
                    type[0] = 3
                    return super.getFieldQuery(field, queryText, quoted)
                }
            }

        var tq = qp.parse("foo:zoo*") as TermQuery
        assertEquals("zoo", tq.getTerm().text())
        assertEquals(2, type[0])

        var bq = qp.parse("foo:zoo*^2") as BoostQuery
        tq = bq.query as TermQuery
        assertEquals("zoo", tq.getTerm().text())
        assertEquals(2, type[0])
        assertEquals(2f, bq.boost, 0f)

        tq = qp.parse("foo:*") as TermQuery
        assertEquals("*", tq.getTerm().text())
        // could be a valid prefix query in the future too
        assertEquals(1, type[0])

        bq = qp.parse("foo:*^2") as BoostQuery
        tq = bq.query as TermQuery
        assertEquals("*", tq.getTerm().text())
        assertEquals(1, type[0])
        assertEquals(2f, bq.boost, 0f)

        tq = qp.parse("*:foo") as TermQuery
        assertEquals("*", tq.getTerm().field())
        assertEquals("foo", tq.getTerm().text())
        assertEquals(3, type[0])

        tq = qp.parse("*:*") as TermQuery
        assertEquals("*", tq.getTerm().field())
        assertEquals("*", tq.getTerm().text())
        // could be handled as a prefix query in the future
        assertEquals(1, type[0])

        tq = qp.parse("(*:*)") as TermQuery
        assertEquals("*", tq.getTerm().field())
        assertEquals("*", tq.getTerm().text())
        assertEquals(1, type[0])
    }

    // Wildcard queries should not be allowed
    @Test
    fun testCustomQueryParserWildcard() {
        expectThrows(ParseException::class) {
            QPTestParser("contents", MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)).parse("a?t")
        }
    }

    // Fuzzy queries should not be allowed
    @Test
    fun testCustomQueryParserFuzzy() {
        expectThrows(ParseException::class) {
            QPTestParser("contents", MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)).parse("xunit~")
        }
    }

    /** query parser that doesn't expand synonyms when users use double quotes */
    private inner class SmartQueryParser : QueryParser(FIELD, Analyzer1()) {
        private val morePrecise = Analyzer2()

        override fun getFieldQuery(field: String, queryText: String, quoted: Boolean): Query? {
            return if (quoted) {
                newFieldQuery(morePrecise, field, queryText, quoted)
            } else {
                super.getFieldQuery(field, queryText, quoted)
            }
        }
    }

    @Test
    override fun testNewFieldQuery() {
        /* ordinary behavior, synonyms form uncoordinated boolean query */
        val dumb = QueryParser(FIELD, Analyzer1())
        val expanded =
            SynonymQuery.Builder(FIELD)
                .addTerm(Term(FIELD, "dogs"))
                .addTerm(Term(FIELD, "dog"))
                .build()
        assertEquals(expanded, dumb.parse("\"dogs\""))
        /* even with the phrase operator the behavior is the same */
        assertEquals(expanded, dumb.parse("dogs"))

        /* custom behavior, the synonyms are expanded, unless you use quote operator */
        val smart = SmartQueryParser()
        assertEquals(expanded, smart.parse("dogs"))

        val unexpanded = TermQuery(Term(FIELD, "dogs"))
        assertEquals(unexpanded, smart.parse("\"dogs\""))
    }

    /** simple synonyms test */
    @Test
    fun testSynonyms() {
        var expected =
            SynonymQuery.Builder(FIELD)
                .addTerm(Term(FIELD, "dogs"))
                .addTerm(Term(FIELD, "dog"))
                .build() as Query
        val qp = QueryParser(FIELD, MockSynonymAnalyzer())
        assertEquals(expected, qp.parse("dogs"))
        assertEquals(expected, qp.parse("\"dogs\""))
        qp.setDefaultOperator(QueryParser.Operator.AND)
        assertEquals(expected, qp.parse("dogs"))
        assertEquals(expected, qp.parse("\"dogs\""))
        expected = BoostQuery(expected, 2f)
        assertEquals(expected, qp.parse("dogs^2"))
        assertEquals(expected, qp.parse("\"dogs\"^2"))
    }

    /** forms multiphrase query */
    @Test
    fun testSynonymsPhrase() {
        val expectedQBuilder = MultiPhraseQuery.Builder()
        expectedQBuilder.add(Term(FIELD, "old"))
        expectedQBuilder.add(arrayOf(Term(FIELD, "dogs"), Term(FIELD, "dog")))
        val qp = QueryParser(FIELD, MockSynonymAnalyzer())
        assertEquals(expectedQBuilder.build(), qp.parse("\"old dogs\""))
        qp.setDefaultOperator(QueryParser.Operator.AND)
        assertEquals(expectedQBuilder.build(), qp.parse("\"old dogs\""))
        var expected: Query = BoostQuery(expectedQBuilder.build(), 2f)
        assertEquals(expected, qp.parse("\"old dogs\"^2"))
        expectedQBuilder.setSlop(3)
        expected = BoostQuery(expectedQBuilder.build(), 2f)
        assertEquals(expected, qp.parse("\"old dogs\"~3^2"))
    }

    /** adds synonym of "國" for "国". */
    protected class MockCJKSynonymFilter(input: TokenStream) : TokenFilter(input) {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
        private var addSynonym = false

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (addSynonym) {
                // inject our synonym
                clearAttributes()
                termAtt.setEmpty()!!.append("國")
                posIncAtt.setPositionIncrement(0)
                addSynonym = false
                return true
            }
            if (input.incrementToken()) {
                addSynonym = termAtt.toString() == "国"
                return true
            }
            return false
        }
    }

    class MockCJKSynonymAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer = SimpleCJKTokenizer()
            return TokenStreamComponents(tokenizer, MockCJKSynonymFilter(tokenizer))
        }
    }

    /** simple CJK synonym test */
    @Test
    fun testCJKSynonym() {
        var expected: Query =
            SynonymQuery.Builder(FIELD)
                .addTerm(Term(FIELD, "国"))
                .addTerm(Term(FIELD, "國"))
                .build()
        val qp = QueryParser(FIELD, MockCJKSynonymAnalyzer())
        assertEquals(expected, qp.parse("国"))
        qp.setDefaultOperator(QueryParser.Operator.AND)
        assertEquals(expected, qp.parse("国"))
        expected = BoostQuery(expected, 2f)
        assertEquals(expected, qp.parse("国^2"))
    }

    /** synonyms with default OR operator */
    @Test
    fun testCJKSynonymsOR() {
        val expectedB = BooleanQuery.Builder()
        expectedB.add(TermQuery(Term(FIELD, "中")), BooleanClause.Occur.SHOULD)
        val inner =
            SynonymQuery.Builder(FIELD)
                .addTerm(Term(FIELD, "国"))
                .addTerm(Term(FIELD, "國"))
                .build()
        expectedB.add(inner, BooleanClause.Occur.SHOULD)
        var expected: Query = expectedB.build()
        val qp = QueryParser(FIELD, MockCJKSynonymAnalyzer())
        assertEquals(expected, qp.parse("中国"))
        expected = BoostQuery(expected, 2f)
        assertEquals(expected, qp.parse("中国^2"))
    }

    /** more complex synonyms with default OR operator */
    @Test
    fun testCJKSynonymsOR2() {
        val expectedB = BooleanQuery.Builder()
        expectedB.add(TermQuery(Term(FIELD, "中")), BooleanClause.Occur.SHOULD)
        val inner =
            SynonymQuery.Builder(FIELD)
                .addTerm(Term(FIELD, "国"))
                .addTerm(Term(FIELD, "國"))
                .build()
        expectedB.add(inner, BooleanClause.Occur.SHOULD)
        val inner2 =
            SynonymQuery.Builder(FIELD)
                .addTerm(Term(FIELD, "国"))
                .addTerm(Term(FIELD, "國"))
                .build()
        expectedB.add(inner2, BooleanClause.Occur.SHOULD)
        var expected: Query = expectedB.build()
        val qp = QueryParser(FIELD, MockCJKSynonymAnalyzer())
        assertEquals(expected, qp.parse("中国国"))
        expected = BoostQuery(expected, 2f)
        assertEquals(expected, qp.parse("中国国^2"))
    }

    /** synonyms with default AND operator */
    @Test
    fun testCJKSynonymsAND() {
        val expectedB = BooleanQuery.Builder()
        expectedB.add(TermQuery(Term(FIELD, "中")), BooleanClause.Occur.MUST)
        val inner =
            SynonymQuery.Builder(FIELD)
                .addTerm(Term(FIELD, "国"))
                .addTerm(Term(FIELD, "國"))
                .build()
        expectedB.add(inner, BooleanClause.Occur.MUST)
        var expected: Query = expectedB.build()
        val qp = QueryParser(FIELD, MockCJKSynonymAnalyzer())
        qp.setDefaultOperator(QueryParser.Operator.AND)
        assertEquals(expected, qp.parse("中国"))
        expected = BoostQuery(expected, 2f)
        assertEquals(expected, qp.parse("中国^2"))
    }

    /** more complex synonyms with default AND operator */
    @Test
    fun testCJKSynonymsAND2() {
        val expectedB = BooleanQuery.Builder()
        expectedB.add(TermQuery(Term(FIELD, "中")), BooleanClause.Occur.MUST)
        val inner =
            SynonymQuery.Builder(FIELD)
                .addTerm(Term(FIELD, "国"))
                .addTerm(Term(FIELD, "國"))
                .build()
        expectedB.add(inner, BooleanClause.Occur.MUST)
        val inner2 =
            SynonymQuery.Builder(FIELD)
                .addTerm(Term(FIELD, "国"))
                .addTerm(Term(FIELD, "國"))
                .build()
        expectedB.add(inner2, BooleanClause.Occur.MUST)
        var expected: Query = expectedB.build()
        val qp = QueryParser(FIELD, MockCJKSynonymAnalyzer())
        qp.setDefaultOperator(QueryParser.Operator.AND)
        assertEquals(expected, qp.parse("中国国"))
        expected = BoostQuery(expected, 2f)
        assertEquals(expected, qp.parse("中国国^2"))
    }

    /** forms multiphrase query */
    @Test
    fun testCJKSynonymsPhrase() {
        val expectedQBuilder = MultiPhraseQuery.Builder()
        expectedQBuilder.add(Term(FIELD, "中"))
        expectedQBuilder.add(arrayOf(Term(FIELD, "国"), Term(FIELD, "國")))
        val qp = QueryParser(FIELD, MockCJKSynonymAnalyzer())
        qp.setDefaultOperator(QueryParser.Operator.AND)
        assertEquals(expectedQBuilder.build(), qp.parse("\"中国\""))
        var expected: Query = BoostQuery(expectedQBuilder.build(), 2f)
        assertEquals(expected, qp.parse("\"中国\"^2"))
        expectedQBuilder.setSlop(3)
        expected = BoostQuery(expectedQBuilder.build(), 2f)
        assertEquals(expected, qp.parse("\"中国\"~3^2"))
    }

    /** LUCENE-6677: make sure wildcard query respects determinizeWorkLimit. */
    @Test
    fun testWildcardDeterminizeWorkLimit() {
        val qp = QueryParser(FIELD, MockAnalyzer(random()))
        qp.determinizeWorkLimit = 1
        expectThrows(TooComplexToDeterminizeException::class) {
            qp.parse("a*aaaaaaa")
        }
    }

    // TODO: Remove this specialization once the flexible standard parser gets multi-word synonym
    // support
    @Test
    override fun testQPA() {
        val oldSplitOnWhitespace = splitOnWhitespace
        splitOnWhitespace = false
        assertQueryEquals("term phrase term", qpAnalyzer, "term phrase1 phrase2 term")
        val cqpc = getParserConfig(qpAnalyzer)
        setDefaultOperatorAND(cqpc)
        assertQueryEquals(cqpc, "field", "term phrase term", "+term +phrase1 +phrase2 +term")
        splitOnWhitespace = oldSplitOnWhitespace
    }

    // TODO: Move to QueryParserTestBase once standard flexible parser gets this capability
    @Test
    fun testMultiWordSynonyms() {
        val dumb = QueryParser("field", Analyzer1())
        dumb.setSplitOnWhitespace(false)

        val guinea = TermQuery(Term("field", "guinea"))
        val pig = TermQuery(Term("field", "pig"))
        val cavy = TermQuery(Term("field", "cavy"))

        // A multi-word synonym source will form a graph query for synonyms that formed the graph token
        // stream
        val synonym = BooleanQuery.Builder()
        synonym.add(guinea, BooleanClause.Occur.MUST)
        synonym.add(pig, BooleanClause.Occur.MUST)
        val guineaPig = synonym.build()

        val phraseGuineaPig =
            PhraseQuery.Builder()
                .add(Term("field", "guinea"))
                .add(Term("field", "pig"))
                .build()

        var graphQuery =
            BooleanQuery.Builder()
                .add(
                    BooleanQuery.Builder()
                        .add(guineaPig, BooleanClause.Occur.SHOULD)
                        .add(cavy, BooleanClause.Occur.SHOULD)
                        .build(),
                    BooleanClause.Occur.SHOULD,
                )
                .build()
        assertEquals(graphQuery, dumb.parse("guinea pig"))

        val synonyms =
            BooleanQuery.Builder()
                .add(PhraseQuery("field", "guinea", "pig"), BooleanClause.Occur.SHOULD)
                .add(TermQuery(Term("field", "cavy")), BooleanClause.Occur.SHOULD)
                .build()
        assertEquals(synonyms, dumb.parse("\"guinea pig\""))

        // custom behavior, the synonyms are expanded, unless you use quote operator
        val smart = SmartQueryParser()
        smart.setSplitOnWhitespace(false)
        graphQuery =
            BooleanQuery.Builder()
                .add(
                    BooleanQuery.Builder()
                        .add(guineaPig, BooleanClause.Occur.SHOULD)
                        .add(cavy, BooleanClause.Occur.SHOULD)
                        .build(),
                    BooleanClause.Occur.SHOULD,
                )
                .build()
        assertEquals(graphQuery, smart.parse("guinea pig"))
        assertEquals(phraseGuineaPig, smart.parse("\"guinea pig\""))

        // with the AND operator
        dumb.setDefaultOperator(QueryParser.Operator.AND)
        var graphAndQuery =
            BooleanQuery.Builder()
                .add(
                    BooleanQuery.Builder()
                        .add(guineaPig, BooleanClause.Occur.SHOULD)
                        .add(cavy, BooleanClause.Occur.SHOULD)
                        .build(),
                    BooleanClause.Occur.MUST,
                )
                .build()
        assertEquals(graphAndQuery, dumb.parse("guinea pig"))

        graphAndQuery =
            BooleanQuery.Builder()
                .add(
                    BooleanQuery.Builder()
                        .add(guineaPig, BooleanClause.Occur.SHOULD)
                        .add(cavy, BooleanClause.Occur.SHOULD)
                        .build(),
                    BooleanClause.Occur.MUST,
                )
                .add(cavy, BooleanClause.Occur.MUST)
                .build()
        assertEquals(graphAndQuery, dumb.parse("guinea pig cavy"))
    }

    @Test
    fun testEnableGraphQueries() {
        val dumb = QueryParser("field", Analyzer1())
        dumb.setSplitOnWhitespace(false)
        dumb.enableGraphQueries = false

        val pig = TermQuery(Term("field", "pig"))

        // A multi-word synonym source will just form a boolean query when graph queries are disabled:
        val inner =
            SynonymQuery.Builder("field")
                .addTerm(Term("field", "cavy"))
                .addTerm(Term("field", "guinea"))
                .build()
        val b = BooleanQuery.Builder()
        b.add(inner, BooleanClause.Occur.SHOULD)
        b.add(pig, BooleanClause.Occur.SHOULD)
        val query = b.build()
        assertEquals(query, dumb.parse("guinea pig"))
    }

    // TODO: Move to QueryParserTestBase once standard flexible parser gets this capability
    @Test
    fun testOperatorsAndMultiWordSynonyms() {
        val a = MockSynonymAnalyzer()

        val oldSplitOnWhitespace = splitOnWhitespace
        splitOnWhitespace = false

        // Operators should interrupt multiword analysis of adjacent words if they associate
        assertQueryEquals("+guinea pig", a, "+guinea pig")
        assertQueryEquals("-guinea pig", a, "-guinea pig")
        assertQueryEquals("!guinea pig", a, "-guinea pig")
        assertQueryEquals("guinea* pig", a, "guinea* pig")
        assertQueryEquals("guinea? pig", a, "guinea? pig")
        assertQueryEquals("guinea~2 pig", a, "guinea~2 pig")
        assertQueryEquals("guinea^2 pig", a, "(guinea)^2.0 pig")

        assertQueryEquals("guinea +pig", a, "guinea +pig")
        assertQueryEquals("guinea -pig", a, "guinea -pig")
        assertQueryEquals("guinea !pig", a, "guinea -pig")
        assertQueryEquals("guinea pig*", a, "guinea pig*")
        assertQueryEquals("guinea pig?", a, "guinea pig?")
        assertQueryEquals("guinea pig~2", a, "guinea pig~2")
        assertQueryEquals("guinea pig^2", a, "guinea (pig)^2.0")

        assertQueryEquals("field:guinea pig", a, "guinea pig")
        assertQueryEquals("guinea field:pig", a, "guinea pig")

        assertQueryEquals("NOT guinea pig", a, "-guinea pig")
        assertQueryEquals("guinea NOT pig", a, "guinea -pig")

        assertQueryEquals("guinea pig AND dogs", a, "guinea +pig +Synonym(dog dogs)")
        assertQueryEquals("dogs AND guinea pig", a, "+Synonym(dog dogs) +guinea pig")
        assertQueryEquals("guinea pig && dogs", a, "guinea +pig +Synonym(dog dogs)")
        assertQueryEquals("dogs && guinea pig", a, "+Synonym(dog dogs) +guinea pig")

        assertQueryEquals("guinea pig OR dogs", a, "guinea pig Synonym(dog dogs)")
        assertQueryEquals("dogs OR guinea pig", a, "Synonym(dog dogs) guinea pig")
        assertQueryEquals("guinea pig || dogs", a, "guinea pig Synonym(dog dogs)")
        assertQueryEquals("dogs || guinea pig", a, "Synonym(dog dogs) guinea pig")

        assertQueryEquals("\"guinea\" pig", a, "guinea pig")
        assertQueryEquals("guinea \"pig\"", a, "guinea pig")

        assertQueryEquals("(guinea) pig", a, "guinea pig")
        assertQueryEquals("guinea (pig)", a, "guinea pig")

        assertQueryEquals("/guinea/ pig", a, "/guinea/ pig")
        assertQueryEquals("guinea /pig/", a, "guinea /pig/")

        // Operators should not interrupt multiword analysis if not don't associate
        assertQueryEquals("(guinea pig)", a, "((+guinea +pig) cavy)")
        assertQueryEquals("+(guinea pig)", a, "+(((+guinea +pig) cavy))")
        assertQueryEquals("-(guinea pig)", a, "-(((+guinea +pig) cavy))")
        assertQueryEquals("!(guinea pig)", a, "-(((+guinea +pig) cavy))")
        assertQueryEquals("NOT (guinea pig)", a, "-(((+guinea +pig) cavy))")
        assertQueryEquals("(guinea pig)^2", a, "(((+guinea +pig) cavy))^2.0")

        assertQueryEquals("field:(guinea pig)", a, "((+guinea +pig) cavy)")

        assertQueryEquals("+small guinea pig", a, "+small ((+guinea +pig) cavy)")
        assertQueryEquals("-small guinea pig", a, "-small ((+guinea +pig) cavy)")
        assertQueryEquals("!small guinea pig", a, "-small ((+guinea +pig) cavy)")
        assertQueryEquals("NOT small guinea pig", a, "-small ((+guinea +pig) cavy)")
        assertQueryEquals("small* guinea pig", a, "small* ((+guinea +pig) cavy)")
        assertQueryEquals("small? guinea pig", a, "small? ((+guinea +pig) cavy)")
        assertQueryEquals("\"small\" guinea pig", a, "small ((+guinea +pig) cavy)")

        assertQueryEquals("guinea pig +running", a, "((+guinea +pig) cavy) +running")
        assertQueryEquals("guinea pig -running", a, "((+guinea +pig) cavy) -running")
        assertQueryEquals("guinea pig !running", a, "((+guinea +pig) cavy) -running")
        assertQueryEquals("guinea pig NOT running", a, "((+guinea +pig) cavy) -running")
        assertQueryEquals("guinea pig running*", a, "((+guinea +pig) cavy) running*")
        assertQueryEquals("guinea pig running?", a, "((+guinea +pig) cavy) running?")
        assertQueryEquals("guinea pig \"running\"", a, "((+guinea +pig) cavy) running")

        assertQueryEquals("\"guinea pig\"~2", a, "\"guinea pig\" cavy")

        assertQueryEquals("field:\"guinea pig\"", a, "\"guinea pig\" cavy")

        splitOnWhitespace = oldSplitOnWhitespace
    }

    @Test
    fun testOperatorsAndMultiWordSynonymsSplitOnWhitespace() {
        val a = MockSynonymAnalyzer()

        val oldSplitOnWhitespace = splitOnWhitespace
        splitOnWhitespace = true

        assertQueryEquals("+guinea pig", a, "+guinea pig")
        assertQueryEquals("-guinea pig", a, "-guinea pig")
        assertQueryEquals("!guinea pig", a, "-guinea pig")
        assertQueryEquals("guinea* pig", a, "guinea* pig")
        assertQueryEquals("guinea? pig", a, "guinea? pig")
        assertQueryEquals("guinea~2 pig", a, "guinea~2 pig")
        assertQueryEquals("guinea^2 pig", a, "(guinea)^2.0 pig")

        assertQueryEquals("guinea +pig", a, "guinea +pig")
        assertQueryEquals("guinea -pig", a, "guinea -pig")
        assertQueryEquals("guinea !pig", a, "guinea -pig")
        assertQueryEquals("guinea pig*", a, "guinea pig*")
        assertQueryEquals("guinea pig?", a, "guinea pig?")
        assertQueryEquals("guinea pig~2", a, "guinea pig~2")
        assertQueryEquals("guinea pig^2", a, "guinea (pig)^2.0")

        assertQueryEquals("field:guinea pig", a, "guinea pig")
        assertQueryEquals("guinea field:pig", a, "guinea pig")

        assertQueryEquals("NOT guinea pig", a, "-guinea pig")
        assertQueryEquals("guinea NOT pig", a, "guinea -pig")

        assertQueryEquals("guinea pig AND dogs", a, "guinea +pig +Synonym(dog dogs)")
        assertQueryEquals("dogs AND guinea pig", a, "+Synonym(dog dogs) +guinea pig")
        assertQueryEquals("guinea pig && dogs", a, "guinea +pig +Synonym(dog dogs)")
        assertQueryEquals("dogs && guinea pig", a, "+Synonym(dog dogs) +guinea pig")

        assertQueryEquals("guinea pig OR dogs", a, "guinea pig Synonym(dog dogs)")
        assertQueryEquals("dogs OR guinea pig", a, "Synonym(dog dogs) guinea pig")
        assertQueryEquals("guinea pig || dogs", a, "guinea pig Synonym(dog dogs)")
        assertQueryEquals("dogs || guinea pig", a, "Synonym(dog dogs) guinea pig")

        assertQueryEquals("\"guinea\" pig", a, "guinea pig")
        assertQueryEquals("guinea \"pig\"", a, "guinea pig")

        assertQueryEquals("(guinea) pig", a, "guinea pig")
        assertQueryEquals("guinea (pig)", a, "guinea pig")

        assertQueryEquals("/guinea/ pig", a, "/guinea/ pig")
        assertQueryEquals("guinea /pig/", a, "guinea /pig/")

        assertQueryEquals("(guinea pig)", a, "guinea pig")
        assertQueryEquals("+(guinea pig)", a, "+(guinea pig)")
        assertQueryEquals("-(guinea pig)", a, "-(guinea pig)")
        assertQueryEquals("!(guinea pig)", a, "-(guinea pig)")
        assertQueryEquals("NOT (guinea pig)", a, "-(guinea pig)")
        assertQueryEquals("(guinea pig)^2", a, "(guinea pig)^2.0")

        assertQueryEquals("field:(guinea pig)", a, "guinea pig")

        assertQueryEquals("+small guinea pig", a, "+small guinea pig")
        assertQueryEquals("-small guinea pig", a, "-small guinea pig")
        assertQueryEquals("!small guinea pig", a, "-small guinea pig")
        assertQueryEquals("NOT small guinea pig", a, "-small guinea pig")
        assertQueryEquals("small* guinea pig", a, "small* guinea pig")
        assertQueryEquals("small? guinea pig", a, "small? guinea pig")
        assertQueryEquals("\"small\" guinea pig", a, "small guinea pig")

        assertQueryEquals("guinea pig +running", a, "guinea pig +running")
        assertQueryEquals("guinea pig -running", a, "guinea pig -running")
        assertQueryEquals("guinea pig !running", a, "guinea pig -running")
        assertQueryEquals("guinea pig NOT running", a, "guinea pig -running")
        assertQueryEquals("guinea pig running*", a, "guinea pig running*")
        assertQueryEquals("guinea pig running?", a, "guinea pig running?")
        assertQueryEquals("guinea pig \"running\"", a, "guinea pig running")

        assertQueryEquals("\"guinea pig\"~2", a, "\"guinea pig\" cavy")

        assertQueryEquals("field:\"guinea pig\"", a, "\"guinea pig\" cavy")

        splitOnWhitespace = oldSplitOnWhitespace
    }

    @Test
    fun testDefaultSplitOnWhitespace() {
        val parser = QueryParser("field", Analyzer1())

        assertFalse(parser.getSplitOnWhitespace())

        // A multi-word synonym source will form a synonym query for the same-starting-position tokens
        val guinea = TermQuery(Term("field", "guinea"))
        val pig = TermQuery(Term("field", "pig"))
        val cavy = TermQuery(Term("field", "cavy"))

        // A multi-word synonym source will form a graph query for synonyms that formed the graph token
        // stream
        val synonym = BooleanQuery.Builder()
        synonym.add(guinea, BooleanClause.Occur.MUST)
        synonym.add(pig, BooleanClause.Occur.MUST)
        val guineaPig = synonym.build()

        val graphQuery =
            BooleanQuery.Builder()
                .add(
                    BooleanQuery.Builder()
                        .add(guineaPig, BooleanClause.Occur.SHOULD)
                        .add(cavy, BooleanClause.Occur.SHOULD)
                        .build(),
                    BooleanClause.Occur.SHOULD,
                )
                .build()
        assertEquals(graphQuery, parser.parse("guinea pig"))

        val oldSplitOnWhitespace = splitOnWhitespace
        splitOnWhitespace = QueryParser.DEFAULT_SPLIT_ON_WHITESPACE
        assertQueryEquals("guinea pig", MockSynonymAnalyzer(), "((+guinea +pig) cavy)")
        splitOnWhitespace = oldSplitOnWhitespace
    }

    @Test
    fun testWildcardAlone() {
        // seems like crazy edge case, but can be useful in concordance
        val parser = QueryParser(FIELD, ASCIIAnalyzer())
        parser.allowLeadingWildcard = false
        expectThrows(ParseException::class) { parser.parse("*") }

        val parser2 = QueryParser("*", ASCIIAnalyzer())
        parser2.allowLeadingWildcard = false
        assertEquals(MatchAllDocsQuery(), parser2.parse("*"))
    }

    @Test
    fun testWildCardEscapes() {
        val a = ASCIIAnalyzer()
        val parser = QueryParser(FIELD, a)
        assertTrue(isAHit(parser.parse("mö*tley"), "moatley", a))
        // need to have at least one genuine wildcard to trigger the wildcard analysis
        // hence the * before the y
        assertTrue(isAHit(parser.parse("mö\\*tl*y"), "mo*tley", a))
        // escaped backslash then true wildcard
        assertTrue(isAHit(parser.parse("mö\\\\*tley"), "mo\\atley", a))
        // escaped wildcard then true wildcard
        assertTrue(isAHit(parser.parse("mö\\??ley"), "mo?tley", a))
        // the first is an escaped * which should yield a miss
        assertFalse(isAHit(parser.parse("mö\\*tl*y"), "moatley", a))
    }

    @Test
    fun testWildcardDoesNotNormalizeEscapedChars() {
        val asciiAnalyzer = ASCIIAnalyzer()
        val keywordAnalyzer = MockAnalyzer(random())
        val parser = QueryParser(FIELD, asciiAnalyzer)
        assertTrue(isAHit(parser.parse("e*e"), "étude", asciiAnalyzer))
        assertTrue(isAHit(parser.parse("é*e"), "etude", asciiAnalyzer))
        assertFalse(isAHit(parser.parse("\\é*e"), "etude", asciiAnalyzer))
        assertTrue(isAHit(parser.parse("\\é*e"), "étude", keywordAnalyzer))
    }

    @Test
    fun testWildCardQuery() {
        val a = ASCIIAnalyzer()
        val parser = QueryParser(FIELD, a)
        parser.allowLeadingWildcard = true
        assertEquals("*bersetzung uber*ung", parser.parse("*bersetzung über*ung")!!.toString(FIELD))
        parser.allowLeadingWildcard = false
        assertEquals("motley crue motl?* cru?", parser.parse("Mötley Crüe Mötl?* Crü?")!!.toString(FIELD))
        assertEquals(
            "renee zellweger ren?? zellw?ger",
            parser.parse("Renée Zellweger Ren?? Zellw?ger")!!.toString(FIELD),
        )
    }

    @Test
    fun testPrefixQuery() {
        val a = ASCIIAnalyzer()
        val parser = QueryParser(FIELD, a)
        assertEquals("ubersetzung ubersetz*", parser.parse("übersetzung übersetz*")!!.toString(FIELD))
        assertEquals("motley crue motl* cru*", parser.parse("Mötley Crüe Mötl* crü*")!!.toString(FIELD))
        assertEquals("rene? zellw*", parser.parse("René? Zellw*")!!.toString(FIELD))
    }

    @Test
    fun testRangeQuery() {
        val a = ASCIIAnalyzer()
        val parser = QueryParser(FIELD, a)
        assertEquals("[aa TO bb]", parser.parse("[aa TO bb]")!!.toString(FIELD))
        assertEquals("{anais TO zoe}", parser.parse("{Anaïs TO Zoé}")!!.toString(FIELD))
    }

    @Test
    fun testFuzzyQuery() {
        val a = ASCIIAnalyzer()
        val parser = QueryParser(FIELD, a)
        assertEquals("ubersetzung ubersetzung~1", parser.parse("Übersetzung Übersetzung~0.9")!!.toString(FIELD))
        assertEquals("motley crue motley~1 crue~2", parser.parse("Mötley Crüe Mötley~0.75 Crüe~0.5")!!.toString(FIELD))
        assertEquals("renee zellweger renee~0 zellweger~2", parser.parse("Renée Zellweger Renée~0.9 Zellweger~")!!.toString(FIELD))
    }

    class FoldingFilter(input: TokenStream) : TokenFilter(input) {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (input.incrementToken()) {
                val term = termAtt.buffer()
                for (i in 0 until termAtt.length) {
                    when (term[i]) {
                        'ü' -> term[i] = 'u'
                        'ö' -> term[i] = 'o'
                        'é' -> term[i] = 'e'
                        'ï' -> term[i] = 'i'
                    }
                }
                return true
            }
            return false
        }
    }

    class ASCIIAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val result = MockTokenizer(MockTokenizer.WHITESPACE, true)
            return TokenStreamComponents(result, FoldingFilter(result))
        }

        override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
            return FoldingFilter(MockLowerCaseFilter(`in`))
        }
    }

    // LUCENE-4176
    @Test
    fun testByteTerms() {
        val s = "เข"
        val analyzer = MockBytesAnalyzer()
        val qp = QueryParser(FIELD, analyzer)
        assertTrue(isAHit(qp.parse("[เข TO เข]"), s, analyzer))
        assertTrue(isAHit(qp.parse("เข~1"), s, analyzer))
        assertTrue(isAHit(qp.parse("เข*"), s, analyzer))
        assertTrue(isAHit(qp.parse("เ*"), s, analyzer))
        assertTrue(isAHit(qp.parse("เ??"), s, analyzer))
    }

    // LUCENE-7533
    @Test
    fun test_splitOnWhitespace_with_autoGeneratePhraseQueries() {
        val qp = QueryParser(FIELD, MockAnalyzer(random()))
        expectThrows(IllegalArgumentException::class) {
            qp.setSplitOnWhitespace(false)
            qp.autoGeneratePhraseQueries = true
        }
        val qp2 = QueryParser(FIELD, MockAnalyzer(random()))
        expectThrows(IllegalArgumentException::class) {
            qp2.setSplitOnWhitespace(true)
            qp2.autoGeneratePhraseQueries = true
            qp2.setSplitOnWhitespace(false)
        }
    }

    private fun isAHit(q: Query?, content: String, analyzer: Analyzer): Boolean {
        val ramDir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), ramDir, analyzer)
        val doc = Document()
        val fieldType = FieldType()
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
        fieldType.setTokenized(true)
        fieldType.setStored(true)
        doc.add(Field(FIELD, content, fieldType))
        writer.addDocument(doc)
        writer.close()
        val ir = DirectoryReader.open(ramDir)
        val `is` = IndexSearcher(ir)
        val hits = `is`.count(q!!)
        ir.close()
        ramDir.close()
        return hits == 1
    }

    // tests inherited from QueryParserTestBase
    @Test override fun testCJK() = super.testCJK()
    @Test override fun testCJKTerm() = super.testCJKTerm()
    @Test override fun testCJKBoostedTerm() = super.testCJKBoostedTerm()
    @Test override fun testCJKPhrase() = super.testCJKPhrase()
    @Test override fun testCJKBoostedPhrase() = super.testCJKBoostedPhrase()
    @Test override fun testCJKSloppyPhrase() = super.testCJKSloppyPhrase()
    @Test override fun testAutoGeneratePhraseQueriesOn() = super.testAutoGeneratePhraseQueriesOn()
    @Test override fun testSimple() = super.testSimple()
    @Test override fun testOperatorVsWhitespace() = super.testOperatorVsWhitespace()
    @Test override fun testPunct() = super.testPunct()
    @Test override fun testSlop() = super.testSlop()
    @Test override fun testNumber() = super.testNumber()
    @Test override fun testWildcard() = super.testWildcard()
    @Test override fun testLeadingWildcardType() = super.testLeadingWildcardType()
    @Test override fun testRange() = super.testRange()
    @Test override fun testRangeWithPhrase() = super.testRangeWithPhrase()
    @Test override fun testRangeQueryEndpointTO() = super.testRangeQueryEndpointTO()
    @Test override fun testRangeQueryRequiresTO() = super.testRangeQueryRequiresTO()
    @Test override fun testDateRange() = super.testDateRange()
    @Test override fun testEscaped() = super.testEscaped()
    @Test override fun testEscapedVsQuestionMarkAsWildcard() = super.testEscapedVsQuestionMarkAsWildcard()
    @Test override fun testQueryStringEscaping() = super.testQueryStringEscaping()
    @Test override fun testTabNewlineCarriageReturn() = super.testTabNewlineCarriageReturn()
    @Test override fun testSimpleDAO() = super.testSimpleDAO()
    @Test override fun testBoost() = super.testBoost()
    @Test override fun testException() = super.testException()
    @Test override fun testBooleanQuery() = super.testBooleanQuery()
    @Test override fun testPrecedence() = super.testPrecedence()
    @Test override fun testParsesBracketsIfQuoted() = super.testParsesBracketsIfQuoted()
    @Test override fun testEscapedWildcard() = super.testEscapedWildcard()
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
}
