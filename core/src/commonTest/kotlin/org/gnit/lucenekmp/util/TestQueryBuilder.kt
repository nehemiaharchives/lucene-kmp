package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.AnalyzerWrapper
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostAttribute
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MultiPhraseQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.SynonymQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockSynonymAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class TestQueryBuilder : LuceneTestCase() {

    @Test
    fun testTerm() {
        val expected = TermQuery(Term("field", "test"))
        val builder = QueryBuilder(MockAnalyzer(random()))
        assertEquals(expected, builder.createBooleanQuery("field", "test"))
    }

    @Test
    fun testBoolean() {
        val expectedB = BooleanQuery.Builder()
        expectedB.add(TermQuery(Term("field", "foo")), BooleanClause.Occur.SHOULD)
        expectedB.add(TermQuery(Term("field", "bar")), BooleanClause.Occur.SHOULD)
        val builder = QueryBuilder(MockAnalyzer(random()))
        assertEquals(expectedB.build(), builder.createBooleanQuery("field", "foo bar"))
    }

    @Test
    fun testBooleanMust() {
        val expectedB = BooleanQuery.Builder()
        expectedB.add(TermQuery(Term("field", "foo")), BooleanClause.Occur.MUST)
        expectedB.add(TermQuery(Term("field", "bar")), BooleanClause.Occur.MUST)
        val builder = QueryBuilder(MockAnalyzer(random()))
        assertEquals(expectedB.build(), builder.createBooleanQuery("field", "foo bar", BooleanClause.Occur.MUST))
    }

    @Test
    fun testMinShouldMatchNone() {
        val builder = QueryBuilder(MockAnalyzer(random()))
        assertEquals(
            builder.createBooleanQuery("field", "one two three four"),
            builder.createMinShouldMatchQuery("field", "one two three four", 0f)
        )
    }

    @Test
    fun testMinShouldMatchAll() {
        val builder = QueryBuilder(MockAnalyzer(random()))
        assertEquals(
            builder.createBooleanQuery("field", "one two three four", BooleanClause.Occur.MUST),
            builder.createMinShouldMatchQuery("field", "one two three four", 1f)
        )
    }

    @Test
    fun testMinShouldMatch() {
        val expectedB = BooleanQuery.Builder()
        expectedB.add(TermQuery(Term("field", "one")), BooleanClause.Occur.SHOULD)
        expectedB.add(TermQuery(Term("field", "two")), BooleanClause.Occur.SHOULD)
        expectedB.add(TermQuery(Term("field", "three")), BooleanClause.Occur.SHOULD)
        expectedB.add(TermQuery(Term("field", "four")), BooleanClause.Occur.SHOULD)
        expectedB.setMinimumNumberShouldMatch(0)
        var expected: Query = expectedB.build()

        val builder = QueryBuilder(MockAnalyzer(random()))

        expectedB.setMinimumNumberShouldMatch(1)
        expected = expectedB.build()
        assertEquals(expected, builder.createMinShouldMatchQuery("field", "one two three four", 0.25f))
        assertEquals(expected, builder.createMinShouldMatchQuery("field", "one two three four", 0.49f))

        expectedB.setMinimumNumberShouldMatch(2)
        expected = expectedB.build()
        assertEquals(expected, builder.createMinShouldMatchQuery("field", "one two three four", 0.5f))
        assertEquals(expected, builder.createMinShouldMatchQuery("field", "one two three four", 0.74f))

        expectedB.setMinimumNumberShouldMatch(3)
        expected = expectedB.build()
        assertEquals(expected, builder.createMinShouldMatchQuery("field", "one two three four", 0.75f))
        assertEquals(expected, builder.createMinShouldMatchQuery("field", "one two three four", 0.99f))
    }

    @Test
    fun testPhraseQueryPositionIncrements() {
        val pqBuilder = PhraseQuery.Builder()
        pqBuilder.add(Term("field", "1"), 0)
        pqBuilder.add(Term("field", "2"), 2)
        val expected = pqBuilder.build()
        val stopList = CharacterRunAutomaton(RegExp("[sS][tT][oO][pP]").toAutomaton())
        val analyzer = MockAnalyzer(random(), MockTokenizer.WHITESPACE, false, stopList)
        val builder = QueryBuilder(analyzer)
        assertEquals(expected, builder.createPhraseQuery("field", "1 stop 2"))
    }

    @Test
    fun testEmpty() {
        val builder = QueryBuilder(MockAnalyzer(random()))
        assertNull(builder.createBooleanQuery("field", ""))
    }

    /** simple synonyms test */
    @Test
    fun testSynonyms() {
        val expected = SynonymQuery.Builder("field")
            .addTerm(Term("field", "dogs"))
            .addTerm(Term("field", "dog"))
            .build()
        val builder = QueryBuilder(MockSynonymAnalyzer())
        assertEquals(expected, builder.createBooleanQuery("field", "dogs"))
        assertEquals(expected, builder.createPhraseQuery("field", "dogs"))
        assertEquals(expected, builder.createBooleanQuery("field", "dogs", BooleanClause.Occur.MUST))
        assertEquals(expected, builder.createPhraseQuery("field", "dogs"))
    }

    @Test
    fun testSynonymsPhrase() {
        val expectedBuilder = MultiPhraseQuery.Builder()
        expectedBuilder.add(Term("field", "old"))
        expectedBuilder.add(arrayOf(Term("field", "dogs"), Term("field", "dog")))
        val builder = QueryBuilder(MockSynonymAnalyzer())
        assertEquals(expectedBuilder.build(), builder.createPhraseQuery("field", "old dogs"))
    }

    @Test
    fun testMultiWordSynonymsPhrase() {
        val expected = BooleanQuery.Builder()
            .add(PhraseQuery("field", "guinea", "pig"), BooleanClause.Occur.SHOULD)
            .add(TermQuery(Term("field", "cavy")), BooleanClause.Occur.SHOULD)
            .build()
        val queryBuilder = QueryBuilder(MockSynonymAnalyzer())
        assertEquals(expected, queryBuilder.createPhraseQuery("field", "guinea pig"))
    }

    @Test
    fun testMultiWordSynonymsPhraseWithSlop() {
        val expected = BooleanQuery.Builder()
            .add(PhraseQuery.Builder().setSlop(4).add(Term("field", "guinea")).add(Term("field", "pig")).build(), BooleanClause.Occur.SHOULD)
            .add(TermQuery(Term("field", "cavy")), BooleanClause.Occur.SHOULD)
            .build()
        val queryBuilder = QueryBuilder(MockSynonymAnalyzer())
        assertEquals(expected, queryBuilder.createPhraseQuery("field", "guinea pig", 4))
    }

    @Test
    fun testMultiWordSynonymsBoolean() {
        for (occur in arrayOf(BooleanClause.Occur.SHOULD, BooleanClause.Occur.MUST)) {
            val syn1 = BooleanQuery.Builder()
                .add(TermQuery(Term("field", "guinea")), BooleanClause.Occur.MUST)
                .add(TermQuery(Term("field", "pig")), BooleanClause.Occur.MUST)
                .build()
            val syn2 = TermQuery(Term("field", "cavy"))

            val synQuery = BooleanQuery.Builder().add(syn1, BooleanClause.Occur.SHOULD).add(syn2, BooleanClause.Occur.SHOULD).build()
            val expectedGraphQuery = BooleanQuery.Builder().add(synQuery, occur).build()
            val queryBuilder = QueryBuilder(MockSynonymAnalyzer())
            assertEquals(expectedGraphQuery, queryBuilder.createBooleanQuery("field", "guinea pig", occur))

            var expectedBooleanQuery = BooleanQuery.Builder().add(synQuery, occur).add(TermQuery(Term("field", "story")), occur).build()
            assertEquals(expectedBooleanQuery, queryBuilder.createBooleanQuery("field", "guinea pig story", occur))

            expectedBooleanQuery = BooleanQuery.Builder().add(TermQuery(Term("field", "the")), occur).add(synQuery, occur).add(TermQuery(Term("field", "story")), occur).build()
            assertEquals(expectedBooleanQuery, queryBuilder.createBooleanQuery("field", "the guinea pig story", occur))

            expectedBooleanQuery = BooleanQuery.Builder().add(TermQuery(Term("field", "the")), occur).add(synQuery, occur).add(TermQuery(Term("field", "story")), occur).add(synQuery, occur).build()
            assertEquals(expectedBooleanQuery, queryBuilder.createBooleanQuery("field", "the guinea pig story guinea pig", occur))
        }
    }

    @Test
    fun testMultiWordPhraseSynonymsBoolean() {
        for (occur in arrayOf(BooleanClause.Occur.SHOULD, BooleanClause.Occur.MUST)) {
            val syn1 = PhraseQuery.Builder().add(Term("field", "guinea")).add(Term("field", "pig")).build()
            val syn2 = TermQuery(Term("field", "cavy"))
            val synQuery = BooleanQuery.Builder().add(syn1, BooleanClause.Occur.SHOULD).add(syn2, BooleanClause.Occur.SHOULD).build()
            val expectedGraphQuery = BooleanQuery.Builder().add(synQuery, occur).build()
            val queryBuilder = QueryBuilder(MockSynonymAnalyzer())
            queryBuilder.autoGenerateMultiTermSynonymsPhraseQuery = true
            assertEquals(expectedGraphQuery, queryBuilder.createBooleanQuery("field", "guinea pig", occur))

            var expectedBooleanQuery = BooleanQuery.Builder().add(synQuery, occur).add(TermQuery(Term("field", "story")), occur).build()
            assertEquals(expectedBooleanQuery, queryBuilder.createBooleanQuery("field", "guinea pig story", occur))

            expectedBooleanQuery = BooleanQuery.Builder().add(TermQuery(Term("field", "the")), occur).add(synQuery, occur).add(TermQuery(Term("field", "story")), occur).build()
            assertEquals(expectedBooleanQuery, queryBuilder.createBooleanQuery("field", "the guinea pig story", occur))

            expectedBooleanQuery = BooleanQuery.Builder().add(TermQuery(Term("field", "the")), occur).add(synQuery, occur).add(TermQuery(Term("field", "story")), occur).add(synQuery, occur).build()
            assertEquals(expectedBooleanQuery, queryBuilder.createBooleanQuery("field", "the guinea pig story guinea pig", occur))
        }
    }

    // SimpleCJKTokenizer and SimpleCJKAnalyzer
    class SimpleCJKTokenizer : Tokenizer() {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
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
        val analyzer = SimpleCJKAnalyzer()
        val expected = BooleanQuery.Builder().add(TermQuery(Term("field", "中")), BooleanClause.Occur.SHOULD).add(TermQuery(Term("field", "国")), BooleanClause.Occur.SHOULD).build()
        val builder = QueryBuilder(analyzer)
        assertEquals(expected, builder.createBooleanQuery("field", "中国"))
    }

    @Test
    fun testCJKPhrase() {
        val analyzer = SimpleCJKAnalyzer()
        val expected = PhraseQuery("field", "中", "国")
        val builder = QueryBuilder(analyzer)
        assertEquals(expected, builder.createPhraseQuery("field", "中国"))
    }

    @Test
    fun testCJKSloppyPhrase() {
        val analyzer = SimpleCJKAnalyzer()
        val expected = PhraseQuery(3, "field", "中", "国")
        val builder = QueryBuilder(analyzer)
        assertEquals(expected, builder.createPhraseQuery("field", "中国", 3))
    }

    /** adds synonym of "國" for "国". */
    protected class MockCJKSynonymFilter(input: TokenStream) : TokenFilter(input) {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
        private var addSynonym = false
        override fun incrementToken(): Boolean {
            if (addSynonym) {
                clearAttributes()
                termAtt.setEmpty()!!.append("國")
                posIncAtt.setPositionIncrement(0)
                addSynonym = false
                return true
            }
            if (input.incrementToken()) {
                addSynonym = termAtt.toString() == "国"
                return true
            } else {
                return false
            }
        }
    }

    class MockCJKSynonymAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer = SimpleCJKTokenizer()
            return TokenStreamComponents(tokenizer, MockCJKSynonymFilter(tokenizer))
        }
    }

    @Test
    fun testCJKSynonym() {
        val expected = SynonymQuery.Builder("field").addTerm(Term("field", "国")).addTerm(Term("field", "國")).build()
        val builder = QueryBuilder(MockCJKSynonymAnalyzer())
        assertEquals(expected, builder.createBooleanQuery("field", "国"))
        assertEquals(expected, builder.createPhraseQuery("field", "国"))
        assertEquals(expected, builder.createBooleanQuery("field", "国", BooleanClause.Occur.MUST))
    }

    @Test
    fun testCJKSynonymsOR() {
        val expected = BooleanQuery.Builder().add(TermQuery(Term("field", "中")), BooleanClause.Occur.SHOULD).add(SynonymQuery.Builder("field").addTerm(Term("field", "国")).addTerm(Term("field", "國")).build(), BooleanClause.Occur.SHOULD).build()
        val builder = QueryBuilder(MockCJKSynonymAnalyzer())
        assertEquals(expected, builder.createBooleanQuery("field", "中国"))
    }

    @Test
    fun testCJKSynonymsOR2() {
        val expected = BooleanQuery.Builder().add(TermQuery(Term("field", "中")), BooleanClause.Occur.SHOULD).add(SynonymQuery.Builder("field").addTerm(Term("field", "国")).addTerm(Term("field", "國")).build(), BooleanClause.Occur.SHOULD).add(SynonymQuery.Builder("field").addTerm(Term("field", "国")).addTerm(Term("field", "國")).build(), BooleanClause.Occur.SHOULD).build()
        val builder = QueryBuilder(MockCJKSynonymAnalyzer())
        assertEquals(expected, builder.createBooleanQuery("field", "中国国"))
    }

    @Test
    fun testCJKSynonymsAND() {
        val expected = BooleanQuery.Builder().add(TermQuery(Term("field", "中")), BooleanClause.Occur.MUST).add(SynonymQuery.Builder("field").addTerm(Term("field", "国")).addTerm(Term("field", "國")).build(), BooleanClause.Occur.MUST).build()
        val builder = QueryBuilder(MockCJKSynonymAnalyzer())
        assertEquals(expected, builder.createBooleanQuery("field", "中国", BooleanClause.Occur.MUST))
    }

    @Test
    fun testCJKSynonymsAND2() {
        val expected = BooleanQuery.Builder().add(TermQuery(Term("field", "中")), BooleanClause.Occur.MUST).add(SynonymQuery.Builder("field").addTerm(Term("field", "国")).addTerm(Term("field", "國")).build(), BooleanClause.Occur.MUST).add(SynonymQuery.Builder("field").addTerm(Term("field", "国")).addTerm(Term("field", "國")).build(), BooleanClause.Occur.MUST).build()
        val builder = QueryBuilder(MockCJKSynonymAnalyzer())
        assertEquals(expected, builder.createBooleanQuery("field", "中国国", BooleanClause.Occur.MUST))
    }

    @Test
    fun testCJKSynonymsPhrase() {
        val expectedBuilder = MultiPhraseQuery.Builder()
        expectedBuilder.add(Term("field", "中"))
        expectedBuilder.add(arrayOf(Term("field", "国"), Term("field", "國")))
        val builder = QueryBuilder(MockCJKSynonymAnalyzer())
        assertEquals(expectedBuilder.build(), builder.createPhraseQuery("field", "中国"))
        expectedBuilder.setSlop(3)
        assertEquals(expectedBuilder.build(), builder.createPhraseQuery("field", "中国", 3))
    }

    @Test
    fun testNoTermAttribute() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                return TokenStreamComponents(object : Tokenizer() {
                    var wasReset = false
                    override fun reset() {
                        super.reset()
                        assertFalse(wasReset)
                        wasReset = true
                    }
                    override fun incrementToken(): Boolean {
                        assertTrue(wasReset)
                        return false
                    }
                })
            }
        }
        val builder = QueryBuilder(analyzer)
        assertNull(builder.createBooleanQuery("field", "whatever"))
    }

    @Test
    fun testMaxBooleanClause() {
        val size = 34
        val tokens = Array(size) { i -> org.gnit.lucenekmp.tests.analysis.Token("f", 1, 0, 0, 1) }
        val term1 = "ff"
        val term2 = "f"
        var i = 0
        while (i < size) {
            if (i % 2 == 0) {
                tokens[i] = org.gnit.lucenekmp.tests.analysis.Token(term2, 1, 0, 0, 1)
                tokens[i + 1] = org.gnit.lucenekmp.tests.analysis.Token(term1, 0, 0, 0, 2)
                i += 2
            } else {
                tokens[i] = org.gnit.lucenekmp.tests.analysis.Token(term2, 1, 0, 0, 1)
                i++
            }
        }
        val qb = object : QueryBuilder(MockAnalyzer(random())) {
            fun analyzeGraphBooleanPublic(field: String, source: TokenStream, operator: BooleanClause.Occur): Query {
                return analyzeGraphBoolean(field, source, operator)
            }

            fun analyzeGraphPhrasePublic(source: TokenStream, field: String, phraseSlop: Int): Query {
                return analyzeGraphPhrase(source, field, phraseSlop)
            }
        }
        CannedTokenStream(*tokens).use { ts ->
            assertFailsWith(IndexSearcher.TooManyClauses::class) { qb.analyzeGraphBooleanPublic("", ts, BooleanClause.Occur.MUST) }
        }
        CannedTokenStream(*tokens).use { ts ->
            assertFailsWith(IndexSearcher.TooManyClauses::class) { qb.analyzeGraphBooleanPublic("", ts, BooleanClause.Occur.SHOULD) }
        }
        CannedTokenStream(*tokens).use { ts ->
            assertFailsWith(IndexSearcher.TooManyClauses::class) { qb.analyzeGraphPhrasePublic(ts, "", 0) }
        }
    }

    // MockBoostTokenFilter
    private class MockBoostTokenFilter(input: TokenStream) : TokenFilter(input) {
        private val boostAtt: BoostAttribute = addAttribute(BoostAttribute::class)
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
            override fun incrementToken(): Boolean {
                if (!input.incrementToken()) return false
                if (termAtt.length == 3) {
                    boostAtt.boost = 0.5f
                }
            return true
        }
    }

    @Test
    fun testTokenStreamBoosts() {
        val msa = MockSynonymAnalyzer()
        val a: Analyzer = object : AnalyzerWrapper(msa.reuseStrategy) {
            override fun getWrappedAnalyzer(fieldName: String): Analyzer = msa
            override fun wrapComponents(fieldName: String, components: TokenStreamComponents): TokenStreamComponents {
                return TokenStreamComponents(components.getSource(), MockBoostTokenFilter(components.tokenStream))
            }
        }
        val builder = QueryBuilder(a)
        val q = builder.createBooleanQuery("field", "hot dogs")
        val expected = BooleanQuery.Builder()
            .add(BoostQuery(TermQuery(Term("field", "hot")), 0.5f), BooleanClause.Occur.SHOULD)
            .add(SynonymQuery.Builder("field").addTerm(Term("field", "dogs")).addTerm(Term("field", "dog"), 0.5f).build(), BooleanClause.Occur.SHOULD)
            .build()
        assertEquals(expected, q)
    }
}
