package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Tests [CapitalizationFilter] */
class TestCapitalizationFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testCapitalization() {
        val keep = CharArraySet(mutableListOf<Any>("and", "the", "it", "BIG"), false)

        assertCapitalizesTo(
            "kiTTEN",
            arrayOf("Kitten"),
            true,
            keep,
            true,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        assertCapitalizesTo(
            "and",
            arrayOf("And"),
            true,
            keep,
            true,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        assertCapitalizesTo(
            "AnD",
            arrayOf("And"),
            true,
            keep,
            true,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        // first is not forced, but it's not a keep word, either
        assertCapitalizesTo(
            "AnD",
            arrayOf("And"),
            true,
            keep,
            false,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        assertCapitalizesTo(
            "big",
            arrayOf("Big"),
            true,
            keep,
            true,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        assertCapitalizesTo(
            "BIG",
            arrayOf("BIG"),
            true,
            keep,
            true,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        assertCapitalizesToKeyword(
            "Hello thEre my Name is Ryan",
            "Hello there my name is ryan",
            true,
            keep,
            true,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        // now each token
        assertCapitalizesTo(
            "Hello thEre my Name is Ryan",
            arrayOf("Hello", "There", "My", "Name", "Is", "Ryan"),
            false,
            keep,
            true,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        // now only the long words
        assertCapitalizesTo(
            "Hello thEre my Name is Ryan",
            arrayOf("Hello", "There", "my", "Name", "is", "Ryan"),
            false,
            keep,
            true,
            null,
            3,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        // without prefix
        assertCapitalizesTo(
            "McKinley",
            arrayOf("Mckinley"),
            true,
            keep,
            true,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        // Now try some prefixes
        val okPrefix = mutableListOf<CharArray>()
        okPrefix.add("McK".toCharArray())

        assertCapitalizesTo(
            "McKinley",
            arrayOf("McKinley"),
            true,
            keep,
            true,
            okPrefix,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        // now try some stuff with numbers
        assertCapitalizesTo(
            "1st 2nd third",
            arrayOf("1st", "2nd", "Third"),
            false,
            keep,
            false,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )

        assertCapitalizesToKeyword(
            "the The the",
            "The The the",
            false,
            keep,
            true,
            null,
            0,
            CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
            CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
        )
    }

    @Test
    @Throws(Exception::class)
    fun testRandomString() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                return TokenStreamComponents(tokenizer, CapitalizationFilter(tokenizer))
            }
        }

        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, CapitalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }

    /** checking the validity of constructor arguments */
    @Test
    @Throws(Exception::class)
    fun testIllegalArguments() {
        assertFailsWith<IllegalArgumentException> {
            CapitalizationFilter(
                whitespaceMockTokenizer("accept only valid arguments"),
                true,
                null,
                true,
                null,
                -1,
                CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
                CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIllegalArguments1() {
        assertFailsWith<IllegalArgumentException> {
            CapitalizationFilter(
                whitespaceMockTokenizer("accept only valid arguments"),
                true,
                null,
                true,
                null,
                0,
                -10,
                CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIllegalArguments2() {
        assertFailsWith<IllegalArgumentException> {
            CapitalizationFilter(
                whitespaceMockTokenizer("accept only valid arguments"),
                true,
                null,
                true,
                null,
                0,
                CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
                -50
            )
        }
    }

    companion object {
        @Throws(IOException::class)
        fun assertCapitalizesTo(
            tokenizer: Tokenizer,
            expected: Array<String>,
            onlyFirstWord: Boolean,
            keep: CharArraySet?,
            forceFirstLetter: Boolean,
            okPrefix: Collection<CharArray>?,
            minWordLength: Int,
            maxWordCount: Int,
            maxTokenLength: Int
        ) {
            val filter = CapitalizationFilter(
                tokenizer,
                onlyFirstWord,
                keep,
                forceFirstLetter,
                okPrefix,
                minWordLength,
                maxWordCount,
                maxTokenLength
            )
            assertTokenStreamContents(filter, expected)
        }

        @Throws(IOException::class)
        fun assertCapitalizesTo(
            input: String,
            expected: Array<String>,
            onlyFirstWord: Boolean,
            keep: CharArraySet?,
            forceFirstLetter: Boolean,
            okPrefix: Collection<CharArray>?,
            minWordLength: Int,
            maxWordCount: Int,
            maxTokenLength: Int
        ) {
            val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
            tokenizer.setReader(StringReader(input))
            assertCapitalizesTo(
                tokenizer,
                expected,
                onlyFirstWord,
                keep,
                forceFirstLetter,
                okPrefix,
                minWordLength,
                maxWordCount,
                maxTokenLength
            )
        }

        @Throws(IOException::class)
        fun assertCapitalizesToKeyword(
            input: String,
            expected: String,
            onlyFirstWord: Boolean,
            keep: CharArraySet?,
            forceFirstLetter: Boolean,
            okPrefix: Collection<CharArray>?,
            minWordLength: Int,
            maxWordCount: Int,
            maxTokenLength: Int
        ) {
            val tokenizer = MockTokenizer(MockTokenizer.KEYWORD, false)
            tokenizer.setReader(StringReader(input))
            assertCapitalizesTo(
                tokenizer,
                arrayOf(expected),
                onlyFirstWord,
                keep,
                forceFirstLetter,
                okPrefix,
                minWordLength,
                maxWordCount,
                maxTokenLength
            )
        }
    }
}
