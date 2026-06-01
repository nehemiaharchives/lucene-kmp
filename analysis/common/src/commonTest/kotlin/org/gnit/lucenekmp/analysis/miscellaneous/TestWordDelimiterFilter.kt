package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.en.EnglishAnalyzer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test

/**
 * New WordDelimiterFilter tests... most of the tests are in ConvertedLegacyTest TODO: should
 * explicitly test things like protWords and not rely on the factory tests in Solr.
 */
@Suppress("DEPRECATION")
class TestWordDelimiterFilter : BaseTokenStreamTestCase() {
    companion object {
        private const val CATENATE_ALL = WordDelimiterFilter.CATENATE_ALL
        private const val CATENATE_NUMBERS = WordDelimiterFilter.CATENATE_NUMBERS
        private const val CATENATE_WORDS = WordDelimiterFilter.CATENATE_WORDS
        private const val GENERATE_NUMBER_PARTS = WordDelimiterFilter.GENERATE_NUMBER_PARTS
        private const val GENERATE_WORD_PARTS = WordDelimiterFilter.GENERATE_WORD_PARTS
        private const val IGNORE_KEYWORDS = WordDelimiterFilter.IGNORE_KEYWORDS
        private const val PRESERVE_ORIGINAL = WordDelimiterFilter.PRESERVE_ORIGINAL
        private const val SPLIT_ON_CASE_CHANGE = WordDelimiterFilter.SPLIT_ON_CASE_CHANGE
        private const val SPLIT_ON_NUMERICS = WordDelimiterFilter.SPLIT_ON_NUMERICS
        private const val STEM_ENGLISH_POSSESSIVE = WordDelimiterFilter.STEM_ENGLISH_POSSESSIVE
        private val DEFAULT_WORD_DELIM_TABLE = WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE
    }

    @Test
    @Throws(IOException::class)
    fun testOffsets() {
        val flags =
            GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                CATENATE_ALL or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE
        var wdf =
            WordDelimiterFilter(
                CannedTokenStream(Token("foo-bar", 5, 12)),
                DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )

        assertTokenStreamContents(
            wdf,
            arrayOf("foo", "foobar", "bar"),
            intArrayOf(5, 5, 9),
            intArrayOf(8, 12, 12)
        )

        wdf =
            WordDelimiterFilter(
                CannedTokenStream(Token("foo-bar", 5, 6)),
                DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )

        assertTokenStreamContents(
            wdf,
            arrayOf("foo", "bar", "foobar"),
            intArrayOf(5, 5, 5),
            intArrayOf(6, 6, 6)
        )
    }

    @Test
    fun testOffsetChange() {
        val flags =
            GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                CATENATE_ALL or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE
        val wdf =
            WordDelimiterFilter(
                CannedTokenStream(Token("übelkeit)", 7, 16)),
                DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )

        assertTokenStreamContents(wdf, arrayOf("übelkeit"), intArrayOf(7), intArrayOf(15))
    }

    @Test
    fun testOffsetChange2() {
        val flags =
            GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                CATENATE_ALL or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE
        val wdf =
            WordDelimiterFilter(
                CannedTokenStream(Token("(übelkeit", 7, 17)),
                DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )

        assertTokenStreamContents(wdf, arrayOf("übelkeit"), intArrayOf(8), intArrayOf(17))
    }

    @Test
    fun testOffsetChange3() {
        val flags =
            GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                CATENATE_ALL or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE
        val wdf =
            WordDelimiterFilter(
                CannedTokenStream(Token("(übelkeit", 7, 16)),
                DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )

        assertTokenStreamContents(wdf, arrayOf("übelkeit"), intArrayOf(8), intArrayOf(16))
    }

    @Test
    fun testOffsetChange4() {
        val flags =
            GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                CATENATE_ALL or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE
        val wdf =
            WordDelimiterFilter(
                CannedTokenStream(Token("(foo,bar)", 7, 16)),
                DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )

        assertTokenStreamContents(
            wdf,
            arrayOf("foo", "foobar", "bar"),
            intArrayOf(8, 8, 12),
            intArrayOf(11, 15, 15)
        )
    }

    fun doSplit(input: String, vararg output: String) {
        val flags =
            GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE
        val wdf =
            WordDelimiterFilter(
                keywordMockTokenizer(input),
                WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )

        assertTokenStreamContents(wdf, output.asList().toTypedArray())
    }

    @Test
    fun testSplits() {
        doSplit("basic-split", "basic", "split")
        doSplit("camelCase", "camel", "Case")

        // non-space marking symbol shouldn't cause split
        // this is an example in Thai
        doSplit("\u0e1a\u0e49\u0e32\u0e19", "\u0e1a\u0e49\u0e32\u0e19")
        // possessive followed by delimiter
        doSplit("test's'", "test")

        // some russian upper and lowercase
        doSplit("Роберт", "Роберт")
        // now cause a split (russian camelCase)
        doSplit("РобЕрт", "Роб", "Ерт")

        // a composed titlecase character, don't split
        doSplit("aǅungla", "aǅungla")

        // a modifier letter, don't split
        doSplit("ســـــــــــــــــلام", "ســـــــــــــــــلام")

        // enclosing mark, don't split
        doSplit("test⃝", "test⃝")

        // combining spacing mark (the virama), don't split
        doSplit("हिन्दी", "हिन्दी")

        // don't split non-ascii digits
        doSplit("١٢٣٤", "١٢٣٤")

        // don't split supplementaries into unpaired surrogates
        doSplit("𠀀𠀀", "𠀀𠀀")
    }

    fun doSplitPossessive(stemPossessive: Int, input: String, vararg output: String) {
        var flags = GENERATE_WORD_PARTS or GENERATE_NUMBER_PARTS or SPLIT_ON_CASE_CHANGE or SPLIT_ON_NUMERICS
        flags = flags or if (stemPossessive == 1) STEM_ENGLISH_POSSESSIVE else 0
        val wdf = WordDelimiterFilter(keywordMockTokenizer(input), flags, null)
        assertTokenStreamContents(wdf, output.asList().toTypedArray())
    }

    /*
     * Test option that allows disabling the special "'s" stemming, instead treating the single quote like other delimiters.
     */
    @Test
    fun testPossessives() {
        doSplitPossessive(1, "ra's", "ra")
        doSplitPossessive(0, "ra's", "ra", "s")
    }

    /*
     * Set a large position increment gap of 10 if the token is "largegap" or "/"
     */
    private class LargePosIncTokenFilter(input: TokenStream) : TokenFilter(input) {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (input.incrementToken()) {
                if (termAtt.toString() == "largegap" || termAtt.toString() == "/") {
                    posIncAtt.setPositionIncrement(10)
                }
                return true
            } else {
                return false
            }
        }
    }

    @Test
    fun testPositionIncrements() {
        val flags =
            GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                CATENATE_ALL or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE
        val protWords = CharArraySet(hashSetOf("NUTCH"), false)

        fun assertAnalyzesToNoGraphConsistency(
            analyzer: Analyzer,
            input: String,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            posIncrements: IntArray
        ) {
            assertTokenStreamContents(
                analyzer.tokenStream("dummy", input),
                output,
                startOffsets,
                endOffsets,
                null,
                posIncrements,
                null,
                input.length,
                false
            )
            checkResetException(analyzer, input)
            checkAnalysisConsistency(random(), analyzer, true, input, false)
        }

        /* analyzer that uses whitespace + wdf */
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, WordDelimiterFilter(tokenizer, flags, protWords))
                }
            }

        /* in this case, works as expected. */
        assertAnalyzesToNoGraphConsistency(
            a,
            "LUCENE / SOLR",
            arrayOf("LUCENE", "SOLR"),
            intArrayOf(0, 9),
            intArrayOf(6, 13),
            intArrayOf(1, 1)
        )

        /* only in this case, posInc of 2 ?! */
        assertAnalyzesToNoGraphConsistency(
            a,
            "LUCENE / solR",
            arrayOf("LUCENE", "sol", "solR", "R"),
            intArrayOf(0, 9, 9, 12),
            intArrayOf(6, 12, 13, 13),
            intArrayOf(1, 1, 0, 1)
        )

        assertAnalyzesToNoGraphConsistency(
            a,
            "LUCENE / NUTCH SOLR",
            arrayOf("LUCENE", "NUTCH", "SOLR"),
            intArrayOf(0, 9, 15),
            intArrayOf(6, 14, 19),
            intArrayOf(1, 1, 1)
        )

        /* analyzer that will consume tokens with large position increments */
        val a2 =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(
                        tokenizer,
                        WordDelimiterFilter(LargePosIncTokenFilter(tokenizer), flags, protWords)
                    )
                }
            }

        /* increment of "largegap" is preserved */
        assertAnalyzesToNoGraphConsistency(
            a2,
            "LUCENE largegap SOLR",
            arrayOf("LUCENE", "largegap", "SOLR"),
            intArrayOf(0, 7, 16),
            intArrayOf(6, 15, 20),
            intArrayOf(1, 10, 1)
        )

        /* the "/" had a position increment of 10, where did it go?!?!! */
        assertAnalyzesToNoGraphConsistency(
            a2,
            "LUCENE / SOLR",
            arrayOf("LUCENE", "SOLR"),
            intArrayOf(0, 9),
            intArrayOf(6, 13),
            intArrayOf(1, 11)
        )

        /* in this case, the increment of 10 from the "/" is carried over */
        assertAnalyzesToNoGraphConsistency(
            a2,
            "LUCENE / solR",
            arrayOf("LUCENE", "sol", "solR", "R"),
            intArrayOf(0, 9, 9, 12),
            intArrayOf(6, 12, 13, 13),
            intArrayOf(1, 11, 0, 1)
        )

        assertAnalyzesToNoGraphConsistency(
            a2,
            "LUCENE / NUTCH SOLR",
            arrayOf("LUCENE", "NUTCH", "SOLR"),
            intArrayOf(0, 9, 15),
            intArrayOf(6, 14, 19),
            intArrayOf(1, 11, 1)
        )

        val a3 =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val filter = StopFilter(tokenizer, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET)
                    return TokenStreamComponents(tokenizer, WordDelimiterFilter(filter, flags, protWords))
                }
            }

        assertAnalyzesToNoGraphConsistency(
            a3,
            "lucene.solr",
            arrayOf("lucene", "lucenesolr", "solr"),
            intArrayOf(0, 0, 7),
            intArrayOf(6, 11, 11),
            intArrayOf(1, 0, 1)
        )

        /* the stopword should add a gap here */
        assertAnalyzesToNoGraphConsistency(
            a3,
            "the lucene.solr",
            arrayOf("lucene", "lucenesolr", "solr"),
            intArrayOf(4, 4, 11),
            intArrayOf(10, 15, 15),
            intArrayOf(2, 0, 1)
        )

        IOUtils.close(a, a2, a3)
    }

    @Test
    fun testKeywordFilter() {
        assertAnalyzesTo(
            keywordTestAnalyzer(GENERATE_WORD_PARTS),
            "abc-def klm-nop kpop",
            arrayOf("abc", "def", "klm", "nop", "kpop")
        )
        assertAnalyzesTo(
            keywordTestAnalyzer(GENERATE_WORD_PARTS or IGNORE_KEYWORDS),
            "abc-def klm-nop kpop",
            arrayOf("abc", "def", "klm-nop", "kpop"),
            startOffsets = intArrayOf(0, 4, 8, 16),
            endOffsets = intArrayOf(3, 7, 15, 20),
            posIncrements = intArrayOf(1, 1, 1, 1)
        )
    }

    private fun keywordTestAnalyzer(flags: Int): Analyzer {
        return object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val kFilter =
                    object : KeywordMarkerFilter(tokenizer) {
                        private val term: CharTermAttribute = addAttribute(CharTermAttribute::class)

                        override fun isKeyword(): Boolean {
                            // Marks terms starting with the letter 'k' as keywords
                            return term.toString()[0] == 'k'
                        }
                    }
                return TokenStreamComponents(tokenizer, WordDelimiterFilter(kFilter, flags, null))
            }
        }
    }

    /** concat numbers + words + all */
    @Test
    fun testLotsOfConcatenating() {
        val flags =
            GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                CATENATE_WORDS or
                CATENATE_NUMBERS or
                CATENATE_ALL or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE

        /* analyzer that uses whitespace + wdf */
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, WordDelimiterFilter(tokenizer, flags, null))
                }
            }

        assertAnalyzesTo(
            a,
            "abc-def-123-456",
            arrayOf("abc", "abcdef", "abcdef123456", "def", "123", "123456", "456"),
            startOffsets = intArrayOf(0, 0, 0, 4, 8, 8, 12),
            endOffsets = intArrayOf(3, 7, 15, 7, 11, 15, 15),
            posIncrements = intArrayOf(1, 0, 0, 1, 1, 0, 1)
        )
        a.close()
    }

    /** concat numbers + words + all + preserve original */
    @Test
    fun testLotsOfConcatenating2() {
        val flags =
            PRESERVE_ORIGINAL or
                GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                CATENATE_WORDS or
                CATENATE_NUMBERS or
                CATENATE_ALL or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE

        /* analyzer that uses whitespace + wdf */
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, WordDelimiterFilter(tokenizer, flags, null))
                }
            }

        assertAnalyzesTo(
            a,
            "abc-def-123-456",
            arrayOf("abc-def-123-456", "abc", "abcdef", "abcdef123456", "def", "123", "123456", "456"),
            startOffsets = intArrayOf(0, 0, 0, 0, 4, 8, 8, 12),
            endOffsets = intArrayOf(15, 3, 7, 15, 7, 11, 15, 15),
            posIncrements = intArrayOf(1, 0, 0, 0, 1, 1, 0, 1)
        )
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    fun testRandomStrings() {
        val numIterations = atLeast(3)
        repeat(numIterations) {
            val flags = random().nextInt(512)
            val protectedWords =
                if (random().nextBoolean()) {
                    CharArraySet(hashSetOf("a", "b", "cd"), false)
                } else {
                    null
                }

            val a =
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                        return TokenStreamComponents(
                            tokenizer,
                            WordDelimiterFilter(tokenizer, flags, protectedWords)
                        )
                    }
                }
            // TODO: properly support positionLengthAttribute
            checkRandomData(random(), a, 100 * RANDOM_MULTIPLIER, 20, false, false)
            a.close()
        }
    }

    /** blast some enormous random strings through the analyzer */
    @Test
    fun testRandomHugeStrings() {
        val numIterations = atLeast(1)
        repeat(numIterations) {
            val flags = random().nextInt(512)
            val protectedWords =
                if (random().nextBoolean()) {
                    CharArraySet(hashSetOf("a", "b", "cd"), false)
                } else {
                    null
                }

            val a =
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer =
                            MockTokenizer(
                                MockTokenizer.WHITESPACE,
                                false,
                                IndexWriter.MAX_TERM_LENGTH / 2
                            )
                        return TokenStreamComponents(
                            tokenizer,
                            WordDelimiterFilter(tokenizer, flags, protectedWords)
                        )
                    }
                }
            // TODO: properly support positionLengthAttribute
            checkRandomData(random(), a, 10 * RANDOM_MULTIPLIER, 8192, false, false)
            a.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val random = random()
        for (i in 0 until 512) {
            val flags = i
            val protectedWords =
                if (random.nextBoolean()) {
                    CharArraySet(hashSetOf("a", "b", "cd"), false)
                } else {
                    null
                }

            val a =
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer: Tokenizer = KeywordTokenizer()
                        return TokenStreamComponents(
                            tokenizer,
                            WordDelimiterFilter(tokenizer, flags, protectedWords)
                        )
                    }
                }
            // depending upon options, this thing may or may not preserve the empty term
            checkAnalysisConsistency(random, a, random.nextBoolean(), "")
            a.close()
        }
    }

    @Test
    fun testOnlyNumbers() {
        val flags = GENERATE_WORD_PARTS or SPLIT_ON_CASE_CHANGE or SPLIT_ON_NUMERICS
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, WordDelimiterFilter(tokenizer, flags, null))
                }
            }

        assertAnalyzesTo(a, "7-586", emptyArray<String>(), startOffsets = intArrayOf(), endOffsets = intArrayOf(), posIncrements = intArrayOf())
    }

    @Test
    fun testNumberPunct() {
        val flags = GENERATE_WORD_PARTS or SPLIT_ON_CASE_CHANGE or SPLIT_ON_NUMERICS
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, WordDelimiterFilter(tokenizer, flags, null))
                }
            }

        assertAnalyzesTo(
            a,
            "6-",
            arrayOf("6"),
            startOffsets = intArrayOf(0),
            endOffsets = intArrayOf(1),
            posIncrements = intArrayOf(1)
        )
    }
}
