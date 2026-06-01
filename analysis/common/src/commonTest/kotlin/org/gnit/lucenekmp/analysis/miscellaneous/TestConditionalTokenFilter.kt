package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.CharacterUtils
import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.classic.ClassicTokenizer
import org.gnit.lucenekmp.analysis.core.TypeTokenFilter
import org.gnit.lucenekmp.analysis.de.GermanStemFilter
import org.gnit.lucenekmp.analysis.`in`.IndicNormalizationFilter
import org.gnit.lucenekmp.analysis.ngram.NGramTokenizer
import org.gnit.lucenekmp.analysis.shingle.FixedShingleFilter
import org.gnit.lucenekmp.analysis.shingle.ShingleFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.synonym.SolrSynonymParser
import org.gnit.lucenekmp.analysis.synonym.SynonymGraphFilter
import org.gnit.lucenekmp.analysis.synonym.SynonymMap
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.analysis.ValidatingTokenFilter
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestConditionalTokenFilter : BaseTokenStreamTestCase() {
    private var closed = false
    private var ended = false
    private var reset = false

    private inner class AssertingLowerCaseFilter(input: TokenStream) : TokenFilter(input) {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (input.incrementToken()) {
                CharacterUtils.toLowerCase(termAtt.buffer(), 0, termAtt.length)
                return true
            }
            return false
        }

        @Throws(IOException::class)
        override fun end() {
            super.end()
            ended = true
        }

        override fun close() {
            super.close()
            closed = true
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            reset = true
        }
    }

    private inner class SkipMatchingFilter(
        input: TokenStream,
        inputFactory: (TokenStream) -> TokenStream,
        termRegex: String
    ) : ConditionalTokenFilter(input, inputFactory) {
        private val pattern = termRegex.toRegex()
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

        @Throws(IOException::class)
        override fun shouldFilter(): Boolean {
            return !pattern.matches(termAtt.toString())
        }
    }

    @Test
    fun testSimple() {
        val stream = whitespaceMockTokenizer("Alice Bob Clara David")
        val t: TokenStream = SkipMatchingFilter(stream, ::AssertingLowerCaseFilter, ".*o.*")
        assertTokenStreamContents(t, arrayOf("alice", "Bob", "clara", "david"))
        assertTrue(closed)
        assertTrue(reset)
        assertTrue(ended)
    }

    private class TokenSplitter(input: TokenStream) : TokenFilter(input) {
        val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        var state: State? = null
        var half: String? = null

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (half == null) {
                state = captureState()
                if (!input.incrementToken()) {
                    return false
                }
                half = termAtt.toString().substring(4)
                termAtt.setLength(4)
                return true
            }
            restoreState(state)
            termAtt.setEmpty()
            termAtt.append(half!!)
            half = null
            return true
        }
    }

    @Test
    fun testMultitokenWrapping() {
        val stream = whitespaceMockTokenizer("tokenpos1 tokenpos2 tokenpos3 tokenpos4")
        val ts: TokenStream = SkipMatchingFilter(stream, ::TokenSplitter, ".*2.*")
        assertTokenStreamContents(
            ts,
            arrayOf("toke", "npos1", "tokenpos2", "toke", "npos3", "toke", "npos4")
        )
    }

    private class EndTrimmingFilter(input: TokenStream) : FilteringTokenFilter(input) {
        val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

        @Throws(IOException::class)
        override fun accept(): Boolean = true

        @Throws(IOException::class)
        override fun end() {
            super.end()
            offsetAtt.setOffset(0, offsetAtt.endOffset() - 2)
        }
    }

    @Test
    fun testEndPropagation() {
        val cts2 = CannedTokenStream(0, 20, Token("alice", 0, 5), Token("bob", 6, 8))
        val ts2 =
            object : ConditionalTokenFilter(cts2, ::EndTrimmingFilter) {
                override fun shouldFilter(): Boolean = true
            }
        assertTokenStreamContents(ts2, arrayOf("alice", "bob"), finalOffset = 18)

        val cts1 = CannedTokenStream(0, 20, Token("alice", 0, 5), Token("bob", 6, 8))
        val ts1 =
            object : ConditionalTokenFilter(cts1, ::EndTrimmingFilter) {
                override fun shouldFilter(): Boolean = false
            }
        assertTokenStreamContents(ts1, arrayOf("alice", "bob"), finalOffset = 20)
    }

    @Test
    fun testWrapGraphs() {
        val stream = whitespaceMockTokenizer("a b c d e")

        val sm: SynonymMap
        MockAnalyzer(random()).use { analyzer ->
            val parser = SolrSynonymParser(true, true, analyzer)
            parser.parse(StringReader("a b, f\nc d, g"))
            sm = parser.build()
        }

        val ts = SkipMatchingFilter(stream, { input -> SynonymGraphFilter(input, sm, true) }, "c")

        assertTokenStreamContents(
            ts,
            arrayOf("f", "a", "b", "c", "d", "e"),
            posIncrements = intArrayOf(1, 0, 1, 1, 1, 1),
            posLengths = intArrayOf(2, 1, 1, 1, 1, 1)
        )
    }

    @Test
    fun testReadaheadWithNoFiltering() {
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val source: Tokenizer = ClassicTokenizer()
                    val sink: TokenStream =
                        object : ConditionalTokenFilter(source, { input -> ShingleFilter(input, 2) }) {
                            override fun shouldFilter(): Boolean = true
                        }
                    return TokenStreamComponents(source, sink)
                }
            }

        val input = "one two three four"
        analyzer.tokenStream("", input).use { ts ->
            assertTokenStreamContents(
                ts,
                arrayOf("one", "one two", "two", "two three", "three", "three four", "four")
            )
        }
    }

    @Test
    fun testReadaheadWithFiltering() {
        val protectedTerms = CharArraySet(2, true)
        protectedTerms.add("three")

        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val source: Tokenizer = ClassicTokenizer()
                    var sink: TokenStream =
                        ProtectedTermFilter(protectedTerms, source) { input -> ShingleFilter(input, 2) }
                    sink = ValidatingTokenFilter(sink, "1")
                    return TokenStreamComponents(source, sink)
                }
            }

        val input = "one two three four"
        analyzer.tokenStream("", input).use { ts ->
            assertTokenStreamContents(
                ts,
                arrayOf("one", "one two", "two", "three", "four"),
                intArrayOf(0, 0, 4, 8, 14),
                intArrayOf(3, 7, 7, 13, 18),
                posIncrements = intArrayOf(1, 0, 1, 1, 1),
                posLengths = intArrayOf(1, 2, 1, 1, 1),
                finalOffset = 18
            )
        }
    }

    @Test
    fun testFilteringWithReadahead() {
        val protectedTerms = CharArraySet(2, true)
        protectedTerms.add("two")
        protectedTerms.add("two three")

        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val source: Tokenizer = StandardTokenizer()
                    var sink: TokenStream = ShingleFilter(source, 3)
                    sink =
                        ProtectedTermFilter(protectedTerms, sink) { input ->
                            TypeTokenFilter(input, setOf("ALL"), true)
                        }
                    return TokenStreamComponents(source, sink)
                }
            }

        val input = "one two three four"
        analyzer.tokenStream("", input).use { ts ->
            assertTokenStreamContents(
                ts,
                arrayOf("two", "two three"),
                intArrayOf(4, 4),
                intArrayOf(7, 13),
                posIncrements = intArrayOf(2, 0),
                posLengths = intArrayOf(1, 2),
                finalOffset = 18
            )
        }
    }

    @Test
    fun testMultipleConditionalFilters() {
        val stream = whitespaceMockTokenizer("Alice Bob Clara David")
        val t =
            SkipMatchingFilter(
                stream,
                { input ->
                    val truncateFilter = TruncateTokenFilter(input, 2)
                    AssertingLowerCaseFilter(truncateFilter)
                },
                ".*o.*"
            )

        assertTokenStreamContents(t, arrayOf("al", "Bob", "cl", "da"))
        assertTrue(closed)
        assertTrue(reset)
        assertTrue(ended)
    }

    @Test
    fun testFilteredTokenFilters() {
        val protectedTerms = CharArraySet(2, true)
        protectedTerms.add("foobar")

        var ts: TokenStream = whitespaceMockTokenizer("wuthering foobar abc")
        ts = ProtectedTermFilter(protectedTerms, ts) { input -> LengthFilter(input, 1, 4) }
        assertTokenStreamContents(ts, arrayOf("foobar", "abc"))

        ts = whitespaceMockTokenizer("foobar abc")
        ts = ProtectedTermFilter(protectedTerms, ts) { input -> LengthFilter(input, 1, 4) }
        assertTokenStreamContents(ts, arrayOf("foobar", "abc"))
    }

    @Test
    fun testConsistentOffsets() {
        val seed = random().nextLong()
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val source: Tokenizer = NGramTokenizer()
                    var sink: TokenStream = ValidatingTokenFilter(KeywordRepeatFilter(source), "stage 0")
                    sink = ValidatingTokenFilter(sink, "stage 1")
                    sink =
                        RandomSkippingFilter(
                            sink,
                            seed
                        ) { input -> TypeTokenFilter(input, setOf("word")) }
                    sink = ValidatingTokenFilter(sink, "last stage")
                    return TokenStreamComponents(source, sink)
                }
            }

        checkRandomData(random(), analyzer, 1)
    }

    @Test
    fun testEndWithShingles() {
        var ts: TokenStream = whitespaceMockTokenizer("cyk jvboq \u092e\u0962\u093f")
        ts = GermanStemFilter(ts)
        ts = NonRandomSkippingFilter(ts, { input -> FixedShingleFilter(input, 2) }, true, false, true)
        ts = NonRandomSkippingFilter(ts, ::IndicNormalizationFilter, true)

        assertTokenStreamContents(ts, arrayOf("jvboq"))
    }

    @Test
    fun testInternalPositionAdjustment() {
        var ts: TokenStream = whitespaceMockTokenizer("one two three")
        ts = KeywordRepeatFilter(ts)
        ts =
            NonRandomSkippingFilter(
                ts,
                ::PositionAssertingTokenFilter,
                false,
                true,
                true,
                true,
                true,
                false
            )

        assertTokenStreamContents(
            ts,
            arrayOf("one", "one", "two", "two", "three", "three"),
            posIncrements = intArrayOf(1, 0, 1, 0, 1, 0)
        )
    }

    private class PositionAssertingTokenFilter(input: TokenStream) : TokenFilter(input) {
        var reset = false
        val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            this.reset = true
        }

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            val more = input.incrementToken()
            if (more && reset) {
                assertEquals(1, posIncAtt.getPositionIncrement())
            }
            reset = false
            return more
        }
    }

    private class RandomSkippingFilter(
        input: TokenStream,
        private val seed: Long,
        inputFactory: (TokenStream) -> TokenStream
    ) : ConditionalTokenFilter(input, inputFactory) {
        private var random = Random(seed)

        override fun shouldFilter(): Boolean {
            return random.nextBoolean()
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            random = Random(seed)
        }
    }

    private class NonRandomSkippingFilter(
        input: TokenStream,
        inputFactory: (TokenStream) -> TokenStream,
        private vararg val shouldFilters: Boolean
    ) : ConditionalTokenFilter(input, inputFactory) {
        private var pos = 0

        override fun shouldFilter(): Boolean {
            return shouldFilters[pos++ % shouldFilters.size]
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            pos = 0
        }
    }
}
