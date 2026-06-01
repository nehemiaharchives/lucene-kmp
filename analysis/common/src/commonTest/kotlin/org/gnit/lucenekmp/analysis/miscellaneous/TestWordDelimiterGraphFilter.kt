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
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * New WordDelimiterGraphFilter tests... most of the tests are in ConvertedLegacyTest TODO: should
 * explicitly test things like protWords and not rely on the factory tests in Solr.
 */
class TestWordDelimiterGraphFilter : BaseTokenStreamTestCase() {
    companion object {
        private const val CATENATE_ALL = WordDelimiterGraphFilter.CATENATE_ALL
        private const val CATENATE_NUMBERS = WordDelimiterGraphFilter.CATENATE_NUMBERS
        private const val CATENATE_WORDS = WordDelimiterGraphFilter.CATENATE_WORDS
        private const val GENERATE_NUMBER_PARTS = WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS
        private const val GENERATE_WORD_PARTS = WordDelimiterGraphFilter.GENERATE_WORD_PARTS
        private const val IGNORE_KEYWORDS = WordDelimiterGraphFilter.IGNORE_KEYWORDS
        private const val PRESERVE_ORIGINAL = WordDelimiterGraphFilter.PRESERVE_ORIGINAL
        private const val SPLIT_ON_CASE_CHANGE = WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE
        private const val SPLIT_ON_NUMERICS = WordDelimiterGraphFilter.SPLIT_ON_NUMERICS
        private const val STEM_ENGLISH_POSSESSIVE = WordDelimiterGraphFilter.STEM_ENGLISH_POSSESSIVE
        private val DEFAULT_WORD_DELIM_TABLE = WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE

        private const val NUMBER = 0
        private const val LETTER = 1
        private const val DELIM = 2

        private fun has(flags: Int, flag: Int): Boolean {
            return (flags and flag) != 0
        }

        private fun toType(ch: Char): Int {
            return when {
                Character.isDigit(ch.code) -> NUMBER
                Character.isLetter(ch.code) -> LETTER
                else -> DELIM
            }
        }
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
            WordDelimiterGraphFilter(
                CannedTokenStream(Token("foo-bar", 5, 12)),
                true,
                DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )

        assertTokenStreamContents(
            wdf,
            arrayOf("foobar", "foo", "bar"),
            intArrayOf(5, 5, 9),
            intArrayOf(12, 8, 12)
        )

        // with illegal offsets:
        wdf =
            WordDelimiterGraphFilter(
                CannedTokenStream(Token("foo-bar", 5, 6)),
                true,
                DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )
        assertTokenStreamContents(
            wdf,
            arrayOf("foobar", "foo", "bar"),
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
            WordDelimiterGraphFilter(
                CannedTokenStream(Token("übelkeit)", 7, 16)),
                true,
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
            WordDelimiterGraphFilter(
                CannedTokenStream(Token("(übelkeit", 7, 17)),
                true,
                DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )
        // illegal offsets:
        assertTokenStreamContents(wdf, arrayOf("übelkeit"), intArrayOf(7), intArrayOf(17))
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
            WordDelimiterGraphFilter(
                CannedTokenStream(Token("(übelkeit", 7, 16)),
                true,
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
            WordDelimiterGraphFilter(
                CannedTokenStream(Token("(foo,bar)", 7, 16)),
                true,
                DEFAULT_WORD_DELIM_TABLE,
                flags,
                null
            )

        assertTokenStreamContents(
            wdf,
            arrayOf("foobar", "foo", "bar"),
            intArrayOf(8, 8, 12),
            intArrayOf(15, 11, 15)
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
            WordDelimiterGraphFilter(
                keywordMockTokenizer(input),
                false,
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
        doSplit("\u0e1a\u0e49\u0e32\u0e19", "\u0e1a\u0e49\u0e32\u0e19")
        doSplit("test's'", "test")
        doSplit("Роберт", "Роберт")
        doSplit("РобЕрт", "Роб", "Ерт")
        doSplit("aǅungla", "aǅungla")
        doSplit("ســـــــــــــــــلام", "ســـــــــــــــــلام")
        doSplit("test⃝", "test⃝")
        doSplit("हिन्दी", "हिन्दी")
        doSplit("١٢٣٤", "١٢٣٤")
        doSplit("𠀀𠀀", "𠀀𠀀")
    }

    fun doSplitPossessive(stemPossessive: Int, input: String, vararg output: String) {
        var flags = GENERATE_WORD_PARTS or GENERATE_NUMBER_PARTS or SPLIT_ON_CASE_CHANGE or SPLIT_ON_NUMERICS
        flags = flags or if (stemPossessive == 1) STEM_ENGLISH_POSSESSIVE else 0
        val wdf = WordDelimiterGraphFilter(keywordMockTokenizer(input), flags, null)
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

    @Test
    fun testTokenType() {
        val flags =
            GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                CATENATE_ALL or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE
        val token = Token("foo-bar", 5, 12)
        token.setType("mytype")
        val wdf = WordDelimiterGraphFilter(CannedTokenStream(token), flags, null)

        assertTokenStreamContents(
            wdf,
            arrayOf("foobar", "foo", "bar"),
            arrayOf("mytype", "mytype", "mytype")
        )
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
        val a4 =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val flags =
                        SPLIT_ON_NUMERICS or
                            GENERATE_WORD_PARTS or
                            PRESERVE_ORIGINAL or
                            GENERATE_NUMBER_PARTS or
                            SPLIT_ON_CASE_CHANGE
                    return TokenStreamComponents(
                        tokenizer,
                        WordDelimiterGraphFilter(tokenizer, flags, CharArraySet.EMPTY_SET)
                    )
                }
            }
        assertAnalyzesTo(
            a4,
            "SAL_S8371 - SAL",
            arrayOf("SAL_S8371", "SAL", "S", "8371", "-", "SAL"),
            posIncrements = intArrayOf(1, 0, 1, 1, 1, 1)
        )

        val flags =
            GENERATE_WORD_PARTS or
                GENERATE_NUMBER_PARTS or
                CATENATE_ALL or
                SPLIT_ON_CASE_CHANGE or
                SPLIT_ON_NUMERICS or
                STEM_ENGLISH_POSSESSIVE
        val protWords = CharArraySet(hashSetOf("NUTCH"), false)

        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(
                        tokenizer,
                        WordDelimiterGraphFilter(tokenizer, true, DEFAULT_WORD_DELIM_TABLE, flags, protWords)
                    )
                }
            }

        assertAnalyzesTo(
            a,
            "LUCENE / SOLR",
            arrayOf("LUCENE", "SOLR"),
            startOffsets = intArrayOf(0, 9),
            endOffsets = intArrayOf(6, 13),
            posIncrements = intArrayOf(1, 2)
        )
        assertAnalyzesTo(
            a,
            "LUCENE / solR",
            arrayOf("LUCENE", "solR", "sol", "R"),
            startOffsets = intArrayOf(0, 9, 9, 12),
            endOffsets = intArrayOf(6, 13, 12, 13),
            posIncrements = intArrayOf(1, 2, 0, 1)
        )
        assertAnalyzesTo(
            a,
            "LUCENE / NUTCH SOLR",
            arrayOf("LUCENE", "NUTCH", "SOLR"),
            startOffsets = intArrayOf(0, 9, 15),
            endOffsets = intArrayOf(6, 14, 19),
            posIncrements = intArrayOf(1, 2, 1)
        )

        val a2 =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(
                        tokenizer,
                        WordDelimiterGraphFilter(
                            LargePosIncTokenFilter(tokenizer),
                            true,
                            DEFAULT_WORD_DELIM_TABLE,
                            flags,
                            protWords
                        )
                    )
                }
            }

        assertAnalyzesTo(
            a2,
            "LUCENE largegap SOLR",
            arrayOf("LUCENE", "largegap", "SOLR"),
            startOffsets = intArrayOf(0, 7, 16),
            endOffsets = intArrayOf(6, 15, 20),
            posIncrements = intArrayOf(1, 10, 1)
        )
        assertAnalyzesTo(
            a2,
            "LUCENE / SOLR",
            arrayOf("LUCENE", "SOLR"),
            startOffsets = intArrayOf(0, 9),
            endOffsets = intArrayOf(6, 13),
            posIncrements = intArrayOf(1, 11)
        )
        assertAnalyzesTo(
            a2,
            "LUCENE / solR",
            arrayOf("LUCENE", "solR", "sol", "R"),
            startOffsets = intArrayOf(0, 9, 9, 12),
            endOffsets = intArrayOf(6, 13, 12, 13),
            posIncrements = intArrayOf(1, 11, 0, 1)
        )
        assertAnalyzesTo(
            a2,
            "LUCENE / NUTCH SOLR",
            arrayOf("LUCENE", "NUTCH", "SOLR"),
            startOffsets = intArrayOf(0, 9, 15),
            endOffsets = intArrayOf(6, 14, 19),
            posIncrements = intArrayOf(1, 11, 1)
        )

        val a3 =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val filter = StopFilter(tokenizer, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET)
                    return TokenStreamComponents(
                        tokenizer,
                        WordDelimiterGraphFilter(filter, true, DEFAULT_WORD_DELIM_TABLE, flags, protWords)
                    )
                }
            }

        assertAnalyzesTo(
            a3,
            "lucene.solr",
            arrayOf("lucenesolr", "lucene", "solr"),
            startOffsets = intArrayOf(0, 0, 7),
            endOffsets = intArrayOf(11, 6, 11),
            posIncrements = intArrayOf(1, 0, 1)
        )
        assertAnalyzesTo(
            a3,
            "the lucene.solr",
            arrayOf("lucenesolr", "lucene", "solr"),
            startOffsets = intArrayOf(4, 4, 11),
            endOffsets = intArrayOf(15, 10, 15),
            posIncrements = intArrayOf(2, 0, 1)
        )

        IOUtils.close(a, a2, a3, a4)
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
            startOffsets = intArrayOf(0, 0, 8, 16),
            endOffsets = intArrayOf(7, 7, 15, 20),
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
                            return term.toString()[0] == 'k'
                        }
                    }
                return TokenStreamComponents(tokenizer, WordDelimiterGraphFilter(kFilter, flags, null))
            }
        }
    }

    @Test
    fun testOriginalTokenEmittedFirst() {
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

        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(
                        tokenizer,
                        WordDelimiterGraphFilter(tokenizer, true, DEFAULT_WORD_DELIM_TABLE, flags, null)
                    )
                }
            }

        assertAnalyzesTo(
            a,
            "abc-def abcDEF abc123",
            arrayOf(
                "abc-def", "abcdef", "abc", "def", "abcDEF", "abcDEF", "abc", "DEF", "abc123", "abc123",
                "abc", "123"
            )
        )
        a.close()
    }

    // https://issues.apache.org/jira/browse/LUCENE-9006
    @Test
    fun testCatenateAllEmittedBeforeParts() {
        val flags = PRESERVE_ORIGINAL or GENERATE_WORD_PARTS or CATENATE_ALL
        val useCharFilter = true
        val graphOffsetsAreCorrect = false

        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(
                        tokenizer,
                        WordDelimiterGraphFilter(tokenizer, true, DEFAULT_WORD_DELIM_TABLE, flags, null)
                    )
                }
            }

        assertTokenStreamContents(
            a.tokenStream("dummy", "8-other"),
            arrayOf("8-other", "8other", "other"),
            intArrayOf(0, 0, 2),
            intArrayOf(7, 7, 7),
            intArrayOf(1, 0, 0)
        )
        checkAnalysisConsistency(random(), a, useCharFilter, "8-other", graphOffsetsAreCorrect)
        verify("8-other", flags)

        assertTokenStreamContents(
            a.tokenStream("dummy", "other-9"),
            arrayOf("other-9", "other9", "other"),
            intArrayOf(0, 0, 0),
            intArrayOf(7, 7, 5),
            intArrayOf(1, 0, 0)
        )
        checkAnalysisConsistency(random(), a, useCharFilter, "other-9", graphOffsetsAreCorrect)
        verify("9-other", flags)

        a.close()
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

        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(
                        tokenizer,
                        WordDelimiterGraphFilter(tokenizer, true, DEFAULT_WORD_DELIM_TABLE, flags, null)
                    )
                }
            }

        assertAnalyzesTo(
            a,
            "abc-def-123-456",
            arrayOf("abcdef123456", "abcdef", "abc", "def", "123456", "123", "456"),
            startOffsets = intArrayOf(0, 0, 0, 4, 8, 8, 12),
            endOffsets = intArrayOf(15, 7, 3, 7, 15, 11, 15),
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

        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, WordDelimiterGraphFilter(tokenizer, flags, null))
                }
            }

        assertAnalyzesTo(
            a,
            "abc-def-123-456",
            arrayOf("abc-def-123-456", "abcdef123456", "abcdef", "abc", "def", "123456", "123", "456"),
            startOffsets = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            endOffsets = intArrayOf(15, 15, 15, 15, 15, 15, 15, 15),
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
                if (random().nextBoolean()) CharArraySet(hashSetOf("a", "b", "cd"), false) else null

            val a =
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                        return TokenStreamComponents(
                            tokenizer,
                            WordDelimiterGraphFilter(tokenizer, flags, protectedWords)
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
                if (random().nextBoolean()) CharArraySet(hashSetOf("a", "b", "cd"), false) else null

            val a =
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                        val wdgf: TokenStream = WordDelimiterGraphFilter(tokenizer, flags, protectedWords)
                        return TokenStreamComponents(tokenizer, wdgf)
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
                if (random.nextBoolean()) CharArraySet(hashSetOf("a", "b", "cd"), false) else null

            val a =
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer: Tokenizer = KeywordTokenizer()
                        return TokenStreamComponents(
                            tokenizer,
                            WordDelimiterGraphFilter(tokenizer, flags, protectedWords)
                        )
                    }
                }
            checkAnalysisConsistency(random, a, random.nextBoolean(), "")
            a.close()
        }
    }

    private fun getAnalyzer(flags: Int): Analyzer {
        return getAnalyzer(flags, null)
    }

    private fun getAnalyzer(flags: Int, protectedWords: CharArraySet?): Analyzer {
        return object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(
                    tokenizer,
                    WordDelimiterGraphFilter(tokenizer, flags, protectedWords)
                )
            }
        }
    }

    private class WordPart(text: String, startOffset: Int, endOffset: Int) {
        val part: String = text.substring(startOffset, endOffset)
        val startOffset: Int = startOffset
        val endOffset: Int = endOffset
        val type: Int = toType(part[0])

        override fun toString(): String {
            return "WordPart($part $startOffset-$endOffset)"
        }
    }

    /**
     * Does (hopefully) the same thing as WordDelimiterGraphFilter, according to the flags, but more
     * slowly, returning all string paths combinations.
     */
    private fun slowWDF(text: String, flags: Int): MutableSet<String> {
        val wordParts = mutableListOf<WordPart>()
        var lastCH = -1
        var wordPartStart = 0
        var inToken = false

        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (toType(ch) == DELIM) {
                if (inToken) {
                    wordParts.add(WordPart(text, wordPartStart, i))
                    inToken = false
                }

                if (
                    has(flags, STEM_ENGLISH_POSSESSIVE) &&
                        ch == '\'' &&
                        i > 0 &&
                        i < text.length - 1 &&
                        (text[i + 1] == 's' || text[i + 1] == 'S') &&
                        toType(text[i - 1]) == LETTER &&
                        (i + 2 == text.length || toType(text[i + 2]) == DELIM)
                ) {
                    i += 2
                    if (i >= text.length) {
                        break
                    }
                }
            } else if (!inToken) {
                inToken = true
                wordPartStart = i
            } else {
                var newToken = false
                if (Character.isLetter(lastCH)) {
                    if (Character.isLetter(ch.code)) {
                        if (
                            has(flags, SPLIT_ON_CASE_CHANGE) &&
                                Character.isLowerCase(lastCH) &&
                                !Character.isLowerCase(ch.code)
                        ) {
                            newToken = true
                        }
                    } else if (has(flags, SPLIT_ON_NUMERICS) && Character.isDigit(ch.code)) {
                        newToken = true
                    }
                } else {
                    if (Character.isLetter(ch.code) && has(flags, SPLIT_ON_NUMERICS)) {
                        newToken = true
                    }
                }
                if (newToken) {
                    wordParts.add(WordPart(text, wordPartStart, i))
                    wordPartStart = i
                }
            }
            lastCH = ch.code
            i++
        }

        if (inToken) {
            wordParts.add(WordPart(text, wordPartStart, text.length))
        }

        val paths = mutableSetOf<String>()
        if (wordParts.isNotEmpty()) {
            enumerate(flags, 0, text, wordParts, paths, StringBuilder())
        }

        if (has(flags, PRESERVE_ORIGINAL)) {
            paths.add(text)
        }

        if (has(flags, CATENATE_ALL) && wordParts.isNotEmpty()) {
            val b = StringBuilder()
            for (wordPart in wordParts) {
                b.append(wordPart.part)
            }
            paths.add(b.toString())
        }

        return paths
    }

    private fun add(path: StringBuilder, part: String) {
        if (path.isNotEmpty()) {
            path.append(' ')
        }
        path.append(part)
    }

    private fun add(path: StringBuilder, wordParts: List<WordPart>, from: Int, to: Int) {
        if (path.isNotEmpty()) {
            path.append(' ')
        }
        for (i in from until to) {
            path.append(wordParts[i].part)
        }
    }

    private fun addWithSpaces(path: StringBuilder, wordParts: List<WordPart>, from: Int, to: Int) {
        for (i in from until to) {
            add(path, wordParts[i].part)
        }
    }

    /** Finds the end (exclusive) of the series of part with the same type */
    private fun endOfRun(wordParts: List<WordPart>, start: Int): Int {
        var upto = start + 1
        while (upto < wordParts.size && wordParts[upto].type == wordParts[start].type) {
            upto++
        }
        return upto
    }

    /** Recursively enumerates all paths through the word parts */
    private fun enumerate(
        flags: Int,
        upto: Int,
        text: String,
        wordParts: List<WordPart>,
        paths: MutableSet<String>,
        path: StringBuilder
    ) {
        if (upto == wordParts.size) {
            if (path.isNotEmpty()) {
                paths.add(path.toString())
            }
        } else {
            val savLength = path.length
            val end = endOfRun(wordParts, upto)

            if (wordParts[upto].type == NUMBER) {
                if (has(flags, GENERATE_NUMBER_PARTS) || wordParts.size == 1) {
                    addWithSpaces(path, wordParts, upto, end)
                    if (has(flags, CATENATE_NUMBERS)) {
                        enumerate(flags, end, text, wordParts, paths, path)
                        path.setLength(savLength)
                        add(path, wordParts, upto, end)
                    }
                } else if (has(flags, CATENATE_NUMBERS)) {
                    add(path, wordParts, upto, end)
                }
                enumerate(flags, end, text, wordParts, paths, path)
                path.setLength(savLength)
            } else {
                if (has(flags, GENERATE_WORD_PARTS) || wordParts.size == 1) {
                    addWithSpaces(path, wordParts, upto, end)
                    if (has(flags, CATENATE_WORDS)) {
                        enumerate(flags, end, text, wordParts, paths, path)
                        path.setLength(savLength)
                        add(path, wordParts, upto, end)
                    }
                } else if (has(flags, CATENATE_WORDS)) {
                    add(path, wordParts, upto, end)
                }
                enumerate(flags, end, text, wordParts, paths, path)
                path.setLength(savLength)
            }
        }
    }

    @Test
    fun testBasicGraphSplits() {
        assertGraphStrings(getAnalyzer(0), "PowerShotPlus", "PowerShotPlus")
        assertGraphStrings(getAnalyzer(GENERATE_WORD_PARTS), "PowerShotPlus", "PowerShotPlus")
        assertGraphStrings(
            getAnalyzer(GENERATE_WORD_PARTS or SPLIT_ON_CASE_CHANGE),
            "PowerShotPlus",
            "Power Shot Plus"
        )
        assertGraphStrings(
            getAnalyzer(GENERATE_WORD_PARTS or SPLIT_ON_CASE_CHANGE or PRESERVE_ORIGINAL),
            "PowerShotPlus",
            "PowerShotPlus",
            "Power Shot Plus"
        )

        assertGraphStrings(getAnalyzer(GENERATE_WORD_PARTS), "Power-Shot-Plus", "Power Shot Plus")
        assertGraphStrings(
            getAnalyzer(GENERATE_WORD_PARTS or SPLIT_ON_CASE_CHANGE),
            "Power-Shot-Plus",
            "Power Shot Plus"
        )
        assertGraphStrings(
            getAnalyzer(GENERATE_WORD_PARTS or SPLIT_ON_CASE_CHANGE or PRESERVE_ORIGINAL),
            "Power-Shot-Plus",
            "Power-Shot-Plus",
            "Power Shot Plus"
        )

        assertGraphStrings(
            getAnalyzer(GENERATE_WORD_PARTS or SPLIT_ON_CASE_CHANGE),
            "PowerShot1000Plus",
            "Power Shot1000Plus"
        )
        assertGraphStrings(
            getAnalyzer(GENERATE_WORD_PARTS or SPLIT_ON_CASE_CHANGE or CATENATE_WORDS),
            "PowerShotPlus",
            "Power Shot Plus",
            "PowerShotPlus"
        )
        assertGraphStrings(
            getAnalyzer(GENERATE_WORD_PARTS or SPLIT_ON_CASE_CHANGE or CATENATE_WORDS),
            "PowerShot1000Plus",
            "Power Shot1000Plus",
            "PowerShot1000Plus"
        )
        assertGraphStrings(
            getAnalyzer(
                GENERATE_WORD_PARTS or
                    GENERATE_NUMBER_PARTS or
                    SPLIT_ON_CASE_CHANGE or
                    CATENATE_WORDS or
                    CATENATE_NUMBERS
            ),
            "Power-Shot-1000-17-Plus",
            "Power Shot 1000 17 Plus",
            "Power Shot 100017 Plus",
            "PowerShot 1000 17 Plus",
            "PowerShot 100017 Plus"
        )
        assertGraphStrings(
            getAnalyzer(
                GENERATE_WORD_PARTS or
                    GENERATE_NUMBER_PARTS or
                    SPLIT_ON_CASE_CHANGE or
                    CATENATE_WORDS or
                    CATENATE_NUMBERS or
                    PRESERVE_ORIGINAL
            ),
            "Power-Shot-1000-17-Plus",
            "Power-Shot-1000-17-Plus",
            "Power Shot 1000 17 Plus",
            "Power Shot 100017 Plus",
            "PowerShot 1000 17 Plus",
            "PowerShot 100017 Plus"
        )
    }

    private fun randomWDFText(): String {
        val b = StringBuilder()
        val length = TestUtil.nextInt(random(), 1, 50)
        for (i in 0 until length) {
            val surpriseMe = random().nextInt(37)
            var lower = -1
            var upper = -1
            if (surpriseMe < 10) {
                lower = 'a'.code
                upper = 'z'.code
            } else if (surpriseMe < 20) {
                lower = 'A'.code
                upper = 'Z'.code
            } else if (surpriseMe < 30) {
                lower = '0'.code
                upper = '9'.code
            } else if (surpriseMe < 35) {
                lower = '-'.code
                upper = '-'.code
            } else {
                b.append("'s")
            }

            if (lower != -1) {
                b.append(TestUtil.nextInt(random(), lower, upper).toChar())
            }
        }

        return b.toString()
    }

    @Test
    fun testInvalidFlag() {
        expectThrows(IllegalArgumentException::class) {
            WordDelimiterGraphFilter(CannedTokenStream(), 1 shl 31, null)
        }
    }

    @Test
    fun testRandomPaths() {
        val iters = atLeast(10)
        for (iter in 0 until iters) {
            val text = randomWDFText()
            if (VERBOSE) {
                println("\nTEST: text=$text len=${text.length}")
            }

            var flags = 0
            if (random().nextBoolean()) flags = flags or GENERATE_WORD_PARTS
            if (random().nextBoolean()) flags = flags or GENERATE_NUMBER_PARTS
            if (random().nextBoolean()) flags = flags or CATENATE_WORDS
            if (random().nextBoolean()) flags = flags or CATENATE_NUMBERS
            if (random().nextBoolean()) flags = flags or CATENATE_ALL
            if (random().nextBoolean()) flags = flags or PRESERVE_ORIGINAL
            if (random().nextBoolean()) flags = flags or SPLIT_ON_CASE_CHANGE
            if (random().nextBoolean()) flags = flags or SPLIT_ON_NUMERICS
            if (random().nextBoolean()) flags = flags or STEM_ENGLISH_POSSESSIVE

            verify(text, flags)
        }
    }

    /** Runs normal and slow WDGF and compares results */
    private fun verify(text: String, flags: Int) {
        val expected = slowWDF(text, flags)
        if (VERBOSE) {
            for (path in expected) {
                println("  $path")
            }
        }

        val actual = getGraphStrings(getAnalyzer(flags), text)
        if (actual != expected) {
            val b = StringBuilder()
            b.append("\n\nFAIL: text=").append(text)
            b.append(" flags=").append(WordDelimiterGraphFilter.flagsToString(flags)).append('\n')
            b.append("  expected paths:\n")
            for (s in expected) {
                b.append("    ").append(s)
                if (!actual.contains(s)) {
                    b.append(" [missing!]")
                }
                b.append('\n')
            }

            b.append("  actual paths:\n")
            for (s in actual) {
                b.append("    ").append(s)
                if (!expected.contains(s)) {
                    b.append(" [unexpected!]")
                }
                b.append('\n')
            }

            fail(b.toString())
        }

        val useCharFilter = true
        checkAnalysisConsistency(random(), getAnalyzer(flags), useCharFilter, text)
    }

    @Test
    fun testOnlyNumbers() {
        assertGraphStrings(
            getAnalyzer(GENERATE_WORD_PARTS or SPLIT_ON_CASE_CHANGE or SPLIT_ON_NUMERICS),
            "7-586"
        )
    }

    @Test
    fun testNoCatenate() {
        assertGraphStrings(
            getAnalyzer(
                GENERATE_WORD_PARTS or GENERATE_NUMBER_PARTS or SPLIT_ON_CASE_CHANGE or SPLIT_ON_NUMERICS
            ),
            "a-b-c-9-d",
            "a b c 9 d"
        )
    }

    @Test
    fun testCuriousCase1() {
        verify(
            "u-0L-4836-ip4Gw--13--q7--L07E1",
            CATENATE_WORDS or CATENATE_ALL or SPLIT_ON_CASE_CHANGE or SPLIT_ON_NUMERICS or STEM_ENGLISH_POSSESSIVE
        )
    }

    @Test
    fun testCuriousCase2() {
        verify("u-l-p", CATENATE_ALL)
    }

    @Test
    fun testOriginalPosLength() {
        verify("Foo-Bar-Baz", CATENATE_WORDS or SPLIT_ON_CASE_CHANGE or PRESERVE_ORIGINAL)
    }

    @Test
    fun testCuriousCase3() {
        verify(
            "cQzk4-GL0izl0mKM-J8--4m-'s",
            GENERATE_NUMBER_PARTS or CATENATE_NUMBERS or SPLIT_ON_CASE_CHANGE or SPLIT_ON_NUMERICS
        )
    }

    @Test
    fun testEmptyString() {
        val wdf =
            WordDelimiterGraphFilter(
                CannedTokenStream(Token("", 0, 0)),
                GENERATE_WORD_PARTS or CATENATE_ALL or PRESERVE_ORIGINAL,
                null
            )
        wdf.reset()
        assertTrue(wdf.incrementToken())
        assertFalse(wdf.incrementToken())
        wdf.end()
        wdf.close()
    }

    @Test
    fun testProtectedWords() {
        val tokens =
            CannedTokenStream(
                Token("foo17-bar", 0, 9),
                Token("foo-bar", 0, 7)
            )

        val protectedWords = CharArraySet(hashSetOf("foo17-BAR"), true)
        val wdf =
            WordDelimiterGraphFilter(
                tokens,
                GENERATE_WORD_PARTS or PRESERVE_ORIGINAL or CATENATE_ALL,
                protectedWords
            )
        assertGraphStrings(wdf, "foo17-bar foo bar", "foo17-bar foo-bar", "foo17-bar foobar")
    }
}
