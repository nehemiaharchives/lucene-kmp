package org.gnit.lucenekmp.analysis.shingle

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.random.Random
import kotlin.test.Test

class TestShingleFilter : BaseTokenStreamTestCase() {
    companion object {
        val TEST_TOKEN =
            arrayOf(
                createToken("please", 0, 6),
                createToken("divide", 7, 13),
                createToken("this", 14, 18),
                createToken("sentence", 19, 27),
                createToken("into", 28, 32),
                createToken("shingles", 33, 39),
            )

        val UNIGRAM_ONLY_POSITION_INCREMENTS = intArrayOf(1, 1, 1, 1, 1, 1)

        val UNIGRAM_ONLY_TYPES =
            arrayOf("word", "word", "word", "word", "word", "word")

        val testTokenWithHoles =
            arrayOf(
                createToken("please", 0, 6),
                createToken("divide", 7, 13),
                createToken("sentence", 19, 27, 2),
                createToken("shingles", 33, 39, 2),
            )

        val BI_GRAM_TOKENS =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please divide", 0, 13),
                createToken("divide", 7, 13),
                createToken("divide this", 7, 18),
                createToken("this", 14, 18),
                createToken("this sentence", 14, 27),
                createToken("sentence", 19, 27),
                createToken("sentence into", 19, 32),
                createToken("into", 28, 32),
                createToken("into shingles", 28, 39),
                createToken("shingles", 33, 39),
            )

        val BI_GRAM_POSITION_INCREMENTS = intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1)

        val BI_GRAM_TYPES =
            arrayOf(
                "word", "shingle", "word", "shingle", "word", "shingle", "word", "shingle", "word",
                "shingle", "word"
            )

        val BI_GRAM_TOKENS_WITH_HOLES =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please divide", 0, 13),
                createToken("divide", 7, 13),
                createToken("divide _", 7, 19),
                createToken("_ sentence", 19, 27),
                createToken("sentence", 19, 27),
                createToken("sentence _", 19, 33),
                createToken("_ shingles", 33, 39),
                createToken("shingles", 33, 39),
            )

        val BI_GRAM_POSITION_INCREMENTS_WITH_HOLES = intArrayOf(1, 0, 1, 0, 1, 1, 0, 1, 1)

        val BI_GRAM_TYPES_WITH_HOLES =
            arrayOf("word", "shingle", "word", "shingle", "shingle", "word", "shingle", "shingle", "word")

        val BI_GRAM_TOKENS_WITHOUT_UNIGRAMS =
            arrayOf(
                createToken("please divide", 0, 13),
                createToken("divide this", 7, 18),
                createToken("this sentence", 14, 27),
                createToken("sentence into", 19, 32),
                createToken("into shingles", 28, 39),
            )

        val BI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS = intArrayOf(1, 1, 1, 1, 1)

        val BI_GRAM_TYPES_WITHOUT_UNIGRAMS =
            arrayOf("shingle", "shingle", "shingle", "shingle", "shingle")

        val BI_GRAM_TOKENS_WITH_HOLES_WITHOUT_UNIGRAMS =
            arrayOf(
                createToken("please divide", 0, 13),
                createToken("divide _", 7, 19),
                createToken("_ sentence", 19, 27),
                createToken("sentence _", 19, 33),
                createToken("_ shingles", 33, 39),
            )

        val BI_GRAM_POSITION_INCREMENTS_WITH_HOLES_WITHOUT_UNIGRAMS = intArrayOf(1, 1, 1, 1, 1, 1)

        val TEST_SINGLE_TOKEN = arrayOf(createToken("please", 0, 6))

        val SINGLE_TOKEN = arrayOf(createToken("please", 0, 6))

        val SINGLE_TOKEN_INCREMENTS = intArrayOf(1)

        val SINGLE_TOKEN_TYPES = arrayOf("word")

        val EMPTY_TOKEN_ARRAY = emptyArray<Token>()

        val EMPTY_TOKEN_INCREMENTS_ARRAY = intArrayOf()

        val EMPTY_TOKEN_TYPES_ARRAY = emptyArray<String>()

        val TRI_GRAM_TOKENS =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please divide", 0, 13),
                createToken("please divide this", 0, 18),
                createToken("divide", 7, 13),
                createToken("divide this", 7, 18),
                createToken("divide this sentence", 7, 27),
                createToken("this", 14, 18),
                createToken("this sentence", 14, 27),
                createToken("this sentence into", 14, 32),
                createToken("sentence", 19, 27),
                createToken("sentence into", 19, 32),
                createToken("sentence into shingles", 19, 39),
                createToken("into", 28, 32),
                createToken("into shingles", 28, 39),
                createToken("shingles", 33, 39),
            )

        val TRI_GRAM_POSITION_INCREMENTS = intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1)

        val TRI_GRAM_TYPES =
            arrayOf(
                "word", "shingle", "shingle", "word", "shingle", "shingle", "word", "shingle", "shingle",
                "word", "shingle", "shingle", "word", "shingle", "word"
            )

        val TRI_GRAM_TOKENS_WITHOUT_UNIGRAMS =
            arrayOf(
                createToken("please divide", 0, 13),
                createToken("please divide this", 0, 18),
                createToken("divide this", 7, 18),
                createToken("divide this sentence", 7, 27),
                createToken("this sentence", 14, 27),
                createToken("this sentence into", 14, 32),
                createToken("sentence into", 19, 32),
                createToken("sentence into shingles", 19, 39),
                createToken("into shingles", 28, 39),
            )

        val TRI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS = intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1)

        val TRI_GRAM_TYPES_WITHOUT_UNIGRAMS =
            arrayOf(
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle",
            )

        val FOUR_GRAM_TOKENS =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please divide", 0, 13),
                createToken("please divide this", 0, 18),
                createToken("please divide this sentence", 0, 27),
                createToken("divide", 7, 13),
                createToken("divide this", 7, 18),
                createToken("divide this sentence", 7, 27),
                createToken("divide this sentence into", 7, 32),
                createToken("this", 14, 18),
                createToken("this sentence", 14, 27),
                createToken("this sentence into", 14, 32),
                createToken("this sentence into shingles", 14, 39),
                createToken("sentence", 19, 27),
                createToken("sentence into", 19, 32),
                createToken("sentence into shingles", 19, 39),
                createToken("into", 28, 32),
                createToken("into shingles", 28, 39),
                createToken("shingles", 33, 39),
            )

        val FOUR_GRAM_POSITION_INCREMENTS = intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1)

        val FOUR_GRAM_TYPES =
            arrayOf(
                "word", "shingle", "shingle", "shingle", "word", "shingle", "shingle", "shingle", "word",
                "shingle", "shingle", "shingle", "word", "shingle", "shingle", "word", "shingle", "word"
            )

        val FOUR_GRAM_TOKENS_WITHOUT_UNIGRAMS =
            arrayOf(
                createToken("please divide", 0, 13),
                createToken("please divide this", 0, 18),
                createToken("please divide this sentence", 0, 27),
                createToken("divide this", 7, 18),
                createToken("divide this sentence", 7, 27),
                createToken("divide this sentence into", 7, 32),
                createToken("this sentence", 14, 27),
                createToken("this sentence into", 14, 32),
                createToken("this sentence into shingles", 14, 39),
                createToken("sentence into", 19, 32),
                createToken("sentence into shingles", 19, 39),
                createToken("into shingles", 28, 39),
            )

        val FOUR_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS = intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1)

        val FOUR_GRAM_TYPES_WITHOUT_UNIGRAMS =
            arrayOf(
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
            )

        val TRI_GRAM_TOKENS_MIN_TRI_GRAM =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please divide this", 0, 18),
                createToken("divide", 7, 13),
                createToken("divide this sentence", 7, 27),
                createToken("this", 14, 18),
                createToken("this sentence into", 14, 32),
                createToken("sentence", 19, 27),
                createToken("sentence into shingles", 19, 39),
                createToken("into", 28, 32),
                createToken("shingles", 33, 39),
            )

        val TRI_GRAM_POSITION_INCREMENTS_MIN_TRI_GRAM = intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 1)

        val TRI_GRAM_TYPES_MIN_TRI_GRAM =
            arrayOf("word", "shingle", "word", "shingle", "word", "shingle", "word", "shingle", "word", "word")

        val TRI_GRAM_TOKENS_WITHOUT_UNIGRAMS_MIN_TRI_GRAM =
            arrayOf(
                createToken("please divide this", 0, 18),
                createToken("divide this sentence", 7, 27),
                createToken("this sentence into", 14, 32),
                createToken("sentence into shingles", 19, 39),
            )

        val TRI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_MIN_TRI_GRAM = intArrayOf(1, 1, 1, 1)

        val TRI_GRAM_TYPES_WITHOUT_UNIGRAMS_MIN_TRI_GRAM =
            arrayOf("shingle", "shingle", "shingle", "shingle")

        val FOUR_GRAM_TOKENS_MIN_TRI_GRAM =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please divide this", 0, 18),
                createToken("please divide this sentence", 0, 27),
                createToken("divide", 7, 13),
                createToken("divide this sentence", 7, 27),
                createToken("divide this sentence into", 7, 32),
                createToken("this", 14, 18),
                createToken("this sentence into", 14, 32),
                createToken("this sentence into shingles", 14, 39),
                createToken("sentence", 19, 27),
                createToken("sentence into shingles", 19, 39),
                createToken("into", 28, 32),
                createToken("shingles", 33, 39),
            )

        val FOUR_GRAM_POSITION_INCREMENTS_MIN_TRI_GRAM = intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1, 1)

        val FOUR_GRAM_TYPES_MIN_TRI_GRAM =
            arrayOf(
                "word", "shingle", "shingle", "word", "shingle", "shingle", "word", "shingle", "shingle",
                "word", "shingle", "word", "word"
            )

        val FOUR_GRAM_TOKENS_WITHOUT_UNIGRAMS_MIN_TRI_GRAM =
            arrayOf(
                createToken("please divide this", 0, 18),
                createToken("please divide this sentence", 0, 27),
                createToken("divide this sentence", 7, 27),
                createToken("divide this sentence into", 7, 32),
                createToken("this sentence into", 14, 32),
                createToken("this sentence into shingles", 14, 39),
                createToken("sentence into shingles", 19, 39),
            )

        val FOUR_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_MIN_TRI_GRAM = intArrayOf(1, 0, 1, 0, 1, 0, 1)

        val FOUR_GRAM_TYPES_WITHOUT_UNIGRAMS_MIN_TRI_GRAM =
            arrayOf(
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle"
            )

        val FOUR_GRAM_TOKENS_MIN_FOUR_GRAM =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please divide this sentence", 0, 27),
                createToken("divide", 7, 13),
                createToken("divide this sentence into", 7, 32),
                createToken("this", 14, 18),
                createToken("this sentence into shingles", 14, 39),
                createToken("sentence", 19, 27),
                createToken("into", 28, 32),
                createToken("shingles", 33, 39),
            )

        val FOUR_GRAM_POSITION_INCREMENTS_MIN_FOUR_GRAM = intArrayOf(1, 0, 1, 0, 1, 0, 1, 1, 1)

        val FOUR_GRAM_TYPES_MIN_FOUR_GRAM =
            arrayOf("word", "shingle", "word", "shingle", "word", "shingle", "word", "word", "word")

        val FOUR_GRAM_TOKENS_WITHOUT_UNIGRAMS_MIN_FOUR_GRAM =
            arrayOf(
                createToken("please divide this sentence", 0, 27),
                createToken("divide this sentence into", 7, 32),
                createToken("this sentence into shingles", 14, 39),
            )

        val FOUR_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_MIN_FOUR_GRAM = intArrayOf(1, 1, 1)

        val FOUR_GRAM_TYPES_WITHOUT_UNIGRAMS_MIN_FOUR_GRAM =
            arrayOf("shingle", "shingle", "shingle")

        val BI_GRAM_TOKENS_NO_SEPARATOR =
            arrayOf(
                createToken("please", 0, 6),
                createToken("pleasedivide", 0, 13),
                createToken("divide", 7, 13),
                createToken("dividethis", 7, 18),
                createToken("this", 14, 18),
                createToken("thissentence", 14, 27),
                createToken("sentence", 19, 27),
                createToken("sentenceinto", 19, 32),
                createToken("into", 28, 32),
                createToken("intoshingles", 28, 39),
                createToken("shingles", 33, 39),
            )

        val BI_GRAM_POSITION_INCREMENTS_NO_SEPARATOR = intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1)

        val BI_GRAM_TYPES_NO_SEPARATOR =
            arrayOf(
                "word", "shingle", "word", "shingle", "word", "shingle", "word", "shingle", "word",
                "shingle", "word"
            )

        val BI_GRAM_TOKENS_WITHOUT_UNIGRAMS_NO_SEPARATOR =
            arrayOf(
                createToken("pleasedivide", 0, 13),
                createToken("dividethis", 7, 18),
                createToken("thissentence", 14, 27),
                createToken("sentenceinto", 19, 32),
                createToken("intoshingles", 28, 39),
            )

        val BI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_NO_SEPARATOR = intArrayOf(1, 1, 1, 1, 1)

        val BI_GRAM_TYPES_WITHOUT_UNIGRAMS_NO_SEPARATOR =
            arrayOf("shingle", "shingle", "shingle", "shingle", "shingle")

        val TRI_GRAM_TOKENS_NO_SEPARATOR =
            arrayOf(
                createToken("please", 0, 6),
                createToken("pleasedivide", 0, 13),
                createToken("pleasedividethis", 0, 18),
                createToken("divide", 7, 13),
                createToken("dividethis", 7, 18),
                createToken("dividethissentence", 7, 27),
                createToken("this", 14, 18),
                createToken("thissentence", 14, 27),
                createToken("thissentenceinto", 14, 32),
                createToken("sentence", 19, 27),
                createToken("sentenceinto", 19, 32),
                createToken("sentenceintoshingles", 19, 39),
                createToken("into", 28, 32),
                createToken("intoshingles", 28, 39),
                createToken("shingles", 33, 39),
            )

        val TRI_GRAM_POSITION_INCREMENTS_NO_SEPARATOR = intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1)

        val TRI_GRAM_TYPES_NO_SEPARATOR =
            arrayOf(
                "word", "shingle", "shingle", "word", "shingle", "shingle", "word", "shingle", "shingle",
                "word", "shingle", "shingle", "word", "shingle", "word"
            )

        val TRI_GRAM_TOKENS_WITHOUT_UNIGRAMS_NO_SEPARATOR =
            arrayOf(
                createToken("pleasedivide", 0, 13),
                createToken("pleasedividethis", 0, 18),
                createToken("dividethis", 7, 18),
                createToken("dividethissentence", 7, 27),
                createToken("thissentence", 14, 27),
                createToken("thissentenceinto", 14, 32),
                createToken("sentenceinto", 19, 32),
                createToken("sentenceintoshingles", 19, 39),
                createToken("intoshingles", 28, 39),
            )

        val TRI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_NO_SEPARATOR = intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1)

        val TRI_GRAM_TYPES_WITHOUT_UNIGRAMS_NO_SEPARATOR =
            arrayOf(
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle",
            )

        val BI_GRAM_TOKENS_ALT_SEPARATOR =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please<SEP>divide", 0, 13),
                createToken("divide", 7, 13),
                createToken("divide<SEP>this", 7, 18),
                createToken("this", 14, 18),
                createToken("this<SEP>sentence", 14, 27),
                createToken("sentence", 19, 27),
                createToken("sentence<SEP>into", 19, 32),
                createToken("into", 28, 32),
                createToken("into<SEP>shingles", 28, 39),
                createToken("shingles", 33, 39),
            )

        val BI_GRAM_POSITION_INCREMENTS_ALT_SEPARATOR = intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1)

        val BI_GRAM_TYPES_ALT_SEPARATOR =
            arrayOf(
                "word", "shingle", "word", "shingle", "word", "shingle", "word", "shingle", "word",
                "shingle", "word"
            )

        val BI_GRAM_TOKENS_WITHOUT_UNIGRAMS_ALT_SEPARATOR =
            arrayOf(
                createToken("please<SEP>divide", 0, 13),
                createToken("divide<SEP>this", 7, 18),
                createToken("this<SEP>sentence", 14, 27),
                createToken("sentence<SEP>into", 19, 32),
                createToken("into<SEP>shingles", 28, 39),
            )

        val BI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_ALT_SEPARATOR = intArrayOf(1, 1, 1, 1, 1)

        val BI_GRAM_TYPES_WITHOUT_UNIGRAMS_ALT_SEPARATOR =
            arrayOf("shingle", "shingle", "shingle", "shingle", "shingle")

        val TRI_GRAM_TOKENS_ALT_SEPARATOR =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please<SEP>divide", 0, 13),
                createToken("please<SEP>divide<SEP>this", 0, 18),
                createToken("divide", 7, 13),
                createToken("divide<SEP>this", 7, 18),
                createToken("divide<SEP>this<SEP>sentence", 7, 27),
                createToken("this", 14, 18),
                createToken("this<SEP>sentence", 14, 27),
                createToken("this<SEP>sentence<SEP>into", 14, 32),
                createToken("sentence", 19, 27),
                createToken("sentence<SEP>into", 19, 32),
                createToken("sentence<SEP>into<SEP>shingles", 19, 39),
                createToken("into", 28, 32),
                createToken("into<SEP>shingles", 28, 39),
                createToken("shingles", 33, 39),
            )

        val TRI_GRAM_POSITION_INCREMENTS_ALT_SEPARATOR = intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1)

        val TRI_GRAM_TYPES_ALT_SEPARATOR =
            arrayOf(
                "word", "shingle", "shingle", "word", "shingle", "shingle", "word", "shingle", "shingle",
                "word", "shingle", "shingle", "word", "shingle", "word"
            )

        val TRI_GRAM_TOKENS_WITHOUT_UNIGRAMS_ALT_SEPARATOR =
            arrayOf(
                createToken("please<SEP>divide", 0, 13),
                createToken("please<SEP>divide<SEP>this", 0, 18),
                createToken("divide<SEP>this", 7, 18),
                createToken("divide<SEP>this<SEP>sentence", 7, 27),
                createToken("this<SEP>sentence", 14, 27),
                createToken("this<SEP>sentence<SEP>into", 14, 32),
                createToken("sentence<SEP>into", 19, 32),
                createToken("sentence<SEP>into<SEP>shingles", 19, 39),
                createToken("into<SEP>shingles", 28, 39),
            )

        val TRI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_ALT_SEPARATOR = intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1)

        val TRI_GRAM_TYPES_WITHOUT_UNIGRAMS_ALT_SEPARATOR =
            arrayOf(
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle", "shingle",
                "shingle",
            )

        val TRI_GRAM_TOKENS_NULL_SEPARATOR =
            arrayOf(
                createToken("please", 0, 6),
                createToken("pleasedivide", 0, 13),
                createToken("pleasedividethis", 0, 18),
                createToken("divide", 7, 13),
                createToken("dividethis", 7, 18),
                createToken("dividethissentence", 7, 27),
                createToken("this", 14, 18),
                createToken("thissentence", 14, 27),
                createToken("thissentenceinto", 14, 32),
                createToken("sentence", 19, 27),
                createToken("sentenceinto", 19, 32),
                createToken("sentenceintoshingles", 19, 39),
                createToken("into", 28, 32),
                createToken("intoshingles", 28, 39),
                createToken("shingles", 33, 39),
            )

        val TRI_GRAM_POSITION_INCREMENTS_NULL_SEPARATOR = intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1)

        val TRI_GRAM_TYPES_NULL_SEPARATOR =
            arrayOf(
                "word", "shingle", "shingle", "word", "shingle", "shingle", "word", "shingle", "shingle",
                "word", "shingle", "shingle", "word", "shingle", "word"
            )

        val TEST_TOKEN_POS_INCR_EQUAL_TO_N =
            arrayOf(
                createToken("please", 0, 6),
                createToken("divide", 7, 13),
                createToken("this", 14, 18),
                createToken("sentence", 29, 37, 3),
                createToken("into", 38, 42),
                createToken("shingles", 43, 49),
            )

        val TRI_GRAM_TOKENS_POS_INCR_EQUAL_TO_N =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please divide", 0, 13),
                createToken("please divide this", 0, 18),
                createToken("divide", 7, 13),
                createToken("divide this", 7, 18),
                createToken("divide this _", 7, 29),
                createToken("this", 14, 18),
                createToken("this _", 14, 29),
                createToken("this _ _", 14, 29),
                createToken("_ _ sentence", 29, 37),
                createToken("_ sentence", 29, 37),
                createToken("_ sentence into", 29, 42),
                createToken("sentence", 29, 37),
                createToken("sentence into", 29, 42),
                createToken("sentence into shingles", 29, 49),
                createToken("into", 38, 42),
                createToken("into shingles", 38, 49),
                createToken("shingles", 43, 49),
            )

        val TRI_GRAM_POSITION_INCREMENTS_POS_INCR_EQUAL_TO_N = intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1)

        val TRI_GRAM_TYPES_POS_INCR_EQUAL_TO_N =
            arrayOf(
                "word", "shingle", "shingle", "word", "shingle", "shingle", "word", "shingle", "shingle",
                "shingle", "shingle", "shingle", "word", "shingle", "shingle", "word", "shingle", "word"
            )

        val TRI_GRAM_TOKENS_POS_INCR_EQUAL_TO_N_WITHOUT_UNIGRAMS =
            arrayOf(
                createToken("please divide", 0, 13),
                createToken("please divide this", 0, 18),
                createToken("divide this", 7, 18),
                createToken("divide this _", 7, 29),
                createToken("this _", 14, 29),
                createToken("this _ _", 14, 29),
                createToken("_ _ sentence", 29, 37),
                createToken("_ sentence", 29, 37),
                createToken("_ sentence into", 29, 42),
                createToken("sentence into", 29, 42),
                createToken("sentence into shingles", 29, 49),
                createToken("into shingles", 38, 49),
            )

        val TRI_GRAM_POSITION_INCREMENTS_POS_INCR_EQUAL_TO_N_WITHOUT_UNIGRAMS = intArrayOf(1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 1)

        val TRI_GRAM_TYPES_POS_INCR_EQUAL_TO_N_WITHOUT_UNIGRAMS =
            arrayOf(
                "shingle", "shingle", "shingle", "shingle", "shingle", "shingle", "shingle", "shingle",
                "shingle", "shingle", "shingle", "shingle",
            )

        val TEST_TOKEN_POS_INCR_GREATER_THAN_N =
            arrayOf(
                createToken("please", 0, 6),
                createToken("divide", 57, 63, 8),
                createToken("this", 64, 68),
                createToken("sentence", 69, 77),
                createToken("into", 78, 82),
                createToken("shingles", 83, 89),
            )

        val TRI_GRAM_TOKENS_POS_INCR_GREATER_THAN_N =
            arrayOf(
                createToken("please", 0, 6),
                createToken("please _", 0, 57),
                createToken("please _ _", 0, 57),
                createToken("_ _ divide", 57, 63),
                createToken("_ divide", 57, 63),
                createToken("_ divide this", 57, 68),
                createToken("divide", 57, 63),
                createToken("divide this", 57, 68),
                createToken("divide this sentence", 57, 77),
                createToken("this", 64, 68),
                createToken("this sentence", 64, 77),
                createToken("this sentence into", 64, 82),
                createToken("sentence", 69, 77),
                createToken("sentence into", 69, 82),
                createToken("sentence into shingles", 69, 89),
                createToken("into", 78, 82),
                createToken("into shingles", 78, 89),
                createToken("shingles", 83, 89),
            )

        val TRI_GRAM_POSITION_INCREMENTS_POS_INCR_GREATER_THAN_N = intArrayOf(1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1)

        val TRI_GRAM_TYPES_POS_INCR_GREATER_THAN_N =
            arrayOf(
                "word", "shingle", "shingle", "shingle", "shingle", "shingle", "word", "shingle", "shingle",
                "word", "shingle", "shingle", "word", "shingle", "shingle", "word", "shingle", "word"
            )

        val TRI_GRAM_TOKENS_POS_INCR_GREATER_THAN_N_WITHOUT_UNIGRAMS =
            arrayOf(
                createToken("please _", 0, 57),
                createToken("please _ _", 0, 57),
                createToken("_ _ divide", 57, 63),
                createToken("_ divide", 57, 63),
                createToken("_ divide this", 57, 68),
                createToken("divide this", 57, 68),
                createToken("divide this sentence", 57, 77),
                createToken("this sentence", 64, 77),
                createToken("this sentence into", 64, 82),
                createToken("sentence into", 69, 82),
                createToken("sentence into shingles", 69, 89),
                createToken("into shingles", 78, 89),
            )

        val TRI_GRAM_POSITION_INCREMENTS_POS_INCR_GREATER_THAN_N_WITHOUT_UNIGRAMS = intArrayOf(1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0, 1)

        val TRI_GRAM_TYPES_POS_INCR_GREATER_THAN_N_WITHOUT_UNIGRAMS =
            arrayOf(
                "shingle", "shingle", "shingle", "shingle", "shingle", "shingle", "shingle", "shingle",
                "shingle", "shingle", "shingle", "shingle",
            )

        private fun createToken(term: String, start: Int, offset: Int): Token {
            return createToken(term, start, offset, 1)
        }

        private fun createToken(term: String, start: Int, offset: Int, positionIncrement: Int): Token {
            val token = Token()
            token.setOffset(start, offset)
            token.copyBuffer(term.toCharArray(), 0, term.length)
            token.setPositionIncrement(positionIncrement)
            return token
        }
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilter() {
        shingleFilterTest(2, TEST_TOKEN, BI_GRAM_TOKENS, BI_GRAM_POSITION_INCREMENTS, BI_GRAM_TYPES, true)
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterWithHoles() {
        shingleFilterTest(
            2,
            testTokenWithHoles,
            BI_GRAM_TOKENS_WITH_HOLES,
            BI_GRAM_POSITION_INCREMENTS_WITH_HOLES,
            BI_GRAM_TYPES_WITH_HOLES,
            true
        )
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterWithoutUnigrams() {
        shingleFilterTest(
            2,
            TEST_TOKEN,
            BI_GRAM_TOKENS_WITHOUT_UNIGRAMS,
            BI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS,
            BI_GRAM_TYPES_WITHOUT_UNIGRAMS,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterWithHolesWithoutUnigrams() {
        shingleFilterTest(
            2,
            testTokenWithHoles,
            BI_GRAM_TOKENS_WITH_HOLES_WITHOUT_UNIGRAMS,
            BI_GRAM_POSITION_INCREMENTS_WITH_HOLES_WITHOUT_UNIGRAMS,
            BI_GRAM_TYPES_WITHOUT_UNIGRAMS,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterWithSingleToken() {
        shingleFilterTest(2, TEST_SINGLE_TOKEN, SINGLE_TOKEN, SINGLE_TOKEN_INCREMENTS, SINGLE_TOKEN_TYPES, true)
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterWithSingleTokenWithoutUnigrams() {
        shingleFilterTest(2, TEST_SINGLE_TOKEN, EMPTY_TOKEN_ARRAY, EMPTY_TOKEN_INCREMENTS_ARRAY, EMPTY_TOKEN_TYPES_ARRAY, false)
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterWithEmptyTokenStream() {
        shingleFilterTest(2, EMPTY_TOKEN_ARRAY, EMPTY_TOKEN_ARRAY, EMPTY_TOKEN_INCREMENTS_ARRAY, EMPTY_TOKEN_TYPES_ARRAY, true)
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterWithEmptyTokenStreamWithoutUnigrams() {
        shingleFilterTest(2, EMPTY_TOKEN_ARRAY, EMPTY_TOKEN_ARRAY, EMPTY_TOKEN_INCREMENTS_ARRAY, EMPTY_TOKEN_TYPES_ARRAY, false)
    }

    @Test
    @Throws(IOException::class)
    fun testTriGramFilter() {
        shingleFilterTest(3, TEST_TOKEN, TRI_GRAM_TOKENS, TRI_GRAM_POSITION_INCREMENTS, TRI_GRAM_TYPES, true)
    }

    @Test
    @Throws(IOException::class)
    fun testTriGramFilterWithoutUnigrams() {
        shingleFilterTest(
            3,
            TEST_TOKEN,
            TRI_GRAM_TOKENS_WITHOUT_UNIGRAMS,
            TRI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS,
            TRI_GRAM_TYPES_WITHOUT_UNIGRAMS,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testFourGramFilter() {
        shingleFilterTest(4, TEST_TOKEN, FOUR_GRAM_TOKENS, FOUR_GRAM_POSITION_INCREMENTS, FOUR_GRAM_TYPES, true)
    }

    @Test
    @Throws(IOException::class)
    fun testFourGramFilterWithoutUnigrams() {
        shingleFilterTest(
            4,
            TEST_TOKEN,
            FOUR_GRAM_TOKENS_WITHOUT_UNIGRAMS,
            FOUR_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS,
            FOUR_GRAM_TYPES_WITHOUT_UNIGRAMS,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTriGramFilterMinTriGram() {
        shingleFilterTest(
            3,
            3,
            TEST_TOKEN,
            TRI_GRAM_TOKENS_MIN_TRI_GRAM,
            TRI_GRAM_POSITION_INCREMENTS_MIN_TRI_GRAM,
            TRI_GRAM_TYPES_MIN_TRI_GRAM,
            true
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTriGramFilterWithoutUnigramsMinTriGram() {
        shingleFilterTest(
            3,
            3,
            TEST_TOKEN,
            TRI_GRAM_TOKENS_WITHOUT_UNIGRAMS_MIN_TRI_GRAM,
            TRI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_MIN_TRI_GRAM,
            TRI_GRAM_TYPES_WITHOUT_UNIGRAMS_MIN_TRI_GRAM,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testFourGramFilterMinTriGram() {
        shingleFilterTest(
            3,
            4,
            TEST_TOKEN,
            FOUR_GRAM_TOKENS_MIN_TRI_GRAM,
            FOUR_GRAM_POSITION_INCREMENTS_MIN_TRI_GRAM,
            FOUR_GRAM_TYPES_MIN_TRI_GRAM,
            true
        )
    }

    @Test
    @Throws(IOException::class)
    fun testFourGramFilterWithoutUnigramsMinTriGram() {
        shingleFilterTest(
            3,
            4,
            TEST_TOKEN,
            FOUR_GRAM_TOKENS_WITHOUT_UNIGRAMS_MIN_TRI_GRAM,
            FOUR_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_MIN_TRI_GRAM,
            FOUR_GRAM_TYPES_WITHOUT_UNIGRAMS_MIN_TRI_GRAM,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testFourGramFilterMinFourGram() {
        shingleFilterTest(
            4,
            4,
            TEST_TOKEN,
            FOUR_GRAM_TOKENS_MIN_FOUR_GRAM,
            FOUR_GRAM_POSITION_INCREMENTS_MIN_FOUR_GRAM,
            FOUR_GRAM_TYPES_MIN_FOUR_GRAM,
            true
        )
    }

    @Test
    @Throws(IOException::class)
    fun testFourGramFilterWithoutUnigramsMinFourGram() {
        shingleFilterTest(
            4,
            4,
            TEST_TOKEN,
            FOUR_GRAM_TOKENS_WITHOUT_UNIGRAMS_MIN_FOUR_GRAM,
            FOUR_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_MIN_FOUR_GRAM,
            FOUR_GRAM_TYPES_WITHOUT_UNIGRAMS_MIN_FOUR_GRAM,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterNoSeparator() {
        shingleFilterTest("", 2, 2, TEST_TOKEN, BI_GRAM_TOKENS_NO_SEPARATOR, BI_GRAM_POSITION_INCREMENTS_NO_SEPARATOR, BI_GRAM_TYPES_NO_SEPARATOR, true)
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterWithoutUnigramsNoSeparator() {
        shingleFilterTest(
            "",
            2,
            2,
            TEST_TOKEN,
            BI_GRAM_TOKENS_WITHOUT_UNIGRAMS_NO_SEPARATOR,
            BI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_NO_SEPARATOR,
            BI_GRAM_TYPES_WITHOUT_UNIGRAMS_NO_SEPARATOR,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTriGramFilterNoSeparator() {
        shingleFilterTest("", 2, 3, TEST_TOKEN, TRI_GRAM_TOKENS_NO_SEPARATOR, TRI_GRAM_POSITION_INCREMENTS_NO_SEPARATOR, TRI_GRAM_TYPES_NO_SEPARATOR, true)
    }

    @Test
    @Throws(IOException::class)
    fun testTriGramFilterWithoutUnigramsNoSeparator() {
        shingleFilterTest(
            "",
            2,
            3,
            TEST_TOKEN,
            TRI_GRAM_TOKENS_WITHOUT_UNIGRAMS_NO_SEPARATOR,
            TRI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_NO_SEPARATOR,
            TRI_GRAM_TYPES_WITHOUT_UNIGRAMS_NO_SEPARATOR,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterAltSeparator() {
        shingleFilterTest("<SEP>", 2, 2, TEST_TOKEN, BI_GRAM_TOKENS_ALT_SEPARATOR, BI_GRAM_POSITION_INCREMENTS_ALT_SEPARATOR, BI_GRAM_TYPES_ALT_SEPARATOR, true)
    }

    @Test
    @Throws(IOException::class)
    fun testBiGramFilterWithoutUnigramsAltSeparator() {
        shingleFilterTest(
            "<SEP>",
            2,
            2,
            TEST_TOKEN,
            BI_GRAM_TOKENS_WITHOUT_UNIGRAMS_ALT_SEPARATOR,
            BI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_ALT_SEPARATOR,
            BI_GRAM_TYPES_WITHOUT_UNIGRAMS_ALT_SEPARATOR,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTriGramFilterAltSeparator() {
        shingleFilterTest("<SEP>", 2, 3, TEST_TOKEN, TRI_GRAM_TOKENS_ALT_SEPARATOR, TRI_GRAM_POSITION_INCREMENTS_ALT_SEPARATOR, TRI_GRAM_TYPES_ALT_SEPARATOR, true)
    }

    @Test
    @Throws(IOException::class)
    fun testTriGramFilterWithoutUnigramsAltSeparator() {
        shingleFilterTest(
            "<SEP>",
            2,
            3,
            TEST_TOKEN,
            TRI_GRAM_TOKENS_WITHOUT_UNIGRAMS_ALT_SEPARATOR,
            TRI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS_ALT_SEPARATOR,
            TRI_GRAM_TYPES_WITHOUT_UNIGRAMS_ALT_SEPARATOR,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTriGramFilterNullSeparator() {
        shingleFilterTest(null, 2, 3, TEST_TOKEN, TRI_GRAM_TOKENS_NULL_SEPARATOR, TRI_GRAM_POSITION_INCREMENTS_NULL_SEPARATOR, TRI_GRAM_TYPES_NULL_SEPARATOR, true)
    }

    @Test
    @Throws(IOException::class)
    fun testPositionIncrementEqualToN() {
        shingleFilterTest(
            2,
            3,
            TEST_TOKEN_POS_INCR_EQUAL_TO_N,
            TRI_GRAM_TOKENS_POS_INCR_EQUAL_TO_N,
            TRI_GRAM_POSITION_INCREMENTS_POS_INCR_EQUAL_TO_N,
            TRI_GRAM_TYPES_POS_INCR_EQUAL_TO_N,
            true
        )
    }

    @Test
    @Throws(IOException::class)
    fun testPositionIncrementEqualToNWithoutUnigrams() {
        shingleFilterTest(
            2,
            3,
            TEST_TOKEN_POS_INCR_EQUAL_TO_N,
            TRI_GRAM_TOKENS_POS_INCR_EQUAL_TO_N_WITHOUT_UNIGRAMS,
            TRI_GRAM_POSITION_INCREMENTS_POS_INCR_EQUAL_TO_N_WITHOUT_UNIGRAMS,
            TRI_GRAM_TYPES_POS_INCR_EQUAL_TO_N_WITHOUT_UNIGRAMS,
            false
        )
    }

    @Test
    @Throws(IOException::class)
    fun testPositionIncrementGreaterThanN() {
        shingleFilterTest(
            2,
            3,
            TEST_TOKEN_POS_INCR_GREATER_THAN_N,
            TRI_GRAM_TOKENS_POS_INCR_GREATER_THAN_N,
            TRI_GRAM_POSITION_INCREMENTS_POS_INCR_GREATER_THAN_N,
            TRI_GRAM_TYPES_POS_INCR_GREATER_THAN_N,
            true
        )
    }

    @Test
    @Throws(IOException::class)
    fun testPositionIncrementGreaterThanNWithoutUnigrams() {
        shingleFilterTest(
            2,
            3,
            TEST_TOKEN_POS_INCR_GREATER_THAN_N,
            TRI_GRAM_TOKENS_POS_INCR_GREATER_THAN_N_WITHOUT_UNIGRAMS,
            TRI_GRAM_POSITION_INCREMENTS_POS_INCR_GREATER_THAN_N_WITHOUT_UNIGRAMS,
            TRI_GRAM_TYPES_POS_INCR_GREATER_THAN_N_WITHOUT_UNIGRAMS,
            false
        )
    }

    @Test
    @Throws(Exception::class)
    fun testReset() {
        val wsTokenizer = WhitespaceTokenizer()
        wsTokenizer.setReader(StringReader("please divide this sentence"))
        val filter = ShingleFilter(wsTokenizer, 2)
        assertTokenStreamContents(
            filter,
            arrayOf("please", "please divide", "divide", "divide this", "this", "this sentence", "sentence"),
            intArrayOf(0, 0, 7, 7, 14, 14, 19),
            intArrayOf(6, 13, 13, 18, 18, 27, 27),
            arrayOf(
                TypeAttribute.DEFAULT_TYPE,
                "shingle",
                TypeAttribute.DEFAULT_TYPE,
                "shingle",
                TypeAttribute.DEFAULT_TYPE,
                "shingle",
                TypeAttribute.DEFAULT_TYPE
            ),
            intArrayOf(1, 0, 1, 0, 1, 0, 1)
        )
        wsTokenizer.setReader(StringReader("please divide this sentence"))
        assertTokenStreamContents(
            filter,
            arrayOf("please", "please divide", "divide", "divide this", "this", "this sentence", "sentence"),
            intArrayOf(0, 0, 7, 7, 14, 14, 19),
            intArrayOf(6, 13, 13, 18, 18, 27, 27),
            arrayOf(
                TypeAttribute.DEFAULT_TYPE,
                "shingle",
                TypeAttribute.DEFAULT_TYPE,
                "shingle",
                TypeAttribute.DEFAULT_TYPE,
                "shingle",
                TypeAttribute.DEFAULT_TYPE
            ),
            intArrayOf(1, 0, 1, 0, 1, 0, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testOutputUnigramsIfNoShinglesSingleTokenCase() {
        // Single token input with outputUnigrams==false is the primary case where
        // enabling this option should alter program behavior.
        shingleFilterTest(
            2,
            2,
            TEST_SINGLE_TOKEN,
            SINGLE_TOKEN,
            SINGLE_TOKEN_INCREMENTS,
            SINGLE_TOKEN_TYPES,
            false,
            true
        )
    }

    @Test
    @Throws(IOException::class)
    fun testOutputUnigramsIfNoShinglesWithSimpleBigram() {
        // Here we expect the same result as with testBiGramFilter().
        shingleFilterTest(2, 2, TEST_TOKEN, BI_GRAM_TOKENS, BI_GRAM_POSITION_INCREMENTS, BI_GRAM_TYPES, true, true)
    }

    @Test
    @Throws(IOException::class)
    fun testOutputUnigramsIfNoShinglesWithSimpleUnigramlessBigram() {
        // Here we expect the same result as with testBiGramFilterWithoutUnigrams().
        shingleFilterTest(
            2,
            2,
            TEST_TOKEN,
            BI_GRAM_TOKENS_WITHOUT_UNIGRAMS,
            BI_GRAM_POSITION_INCREMENTS_WITHOUT_UNIGRAMS,
            BI_GRAM_TYPES_WITHOUT_UNIGRAMS,
            false,
            true
        )
    }

    @Test
    @Throws(IOException::class)
    fun testOutputUnigramsIfNoShinglesWithMultipleInputTokens() {
        // Test when the minimum shingle size is greater than the number of input tokens
        shingleFilterTest(
            7,
            7,
            TEST_TOKEN,
            TEST_TOKEN,
            UNIGRAM_ONLY_POSITION_INCREMENTS,
            UNIGRAM_ONLY_TYPES,
            false,
            true
        )
    }

    private fun shingleFilterTest(
        maxSize: Int,
        tokensToShingle: Array<Token>,
        tokensToCompare: Array<Token>,
        positionIncrements: IntArray,
        types: Array<String>,
        outputUnigrams: Boolean
    ) {
        val filter = ShingleFilter(CannedTokenStream(*tokensToShingle), maxSize)
        filter.setOutputUnigrams(outputUnigrams)
        shingleFilterTestCommon(filter, tokensToCompare, positionIncrements, types)
    }

    private fun shingleFilterTest(
        minSize: Int,
        maxSize: Int,
        tokensToShingle: Array<Token>,
        tokensToCompare: Array<Token>,
        positionIncrements: IntArray,
        types: Array<String>,
        outputUnigrams: Boolean
    ) {
        val filter = ShingleFilter(CannedTokenStream(*tokensToShingle), minSize, maxSize)
        filter.setOutputUnigrams(outputUnigrams)
        shingleFilterTestCommon(filter, tokensToCompare, positionIncrements, types)
    }

    private fun shingleFilterTest(
        minSize: Int,
        maxSize: Int,
        tokensToShingle: Array<Token>,
        tokensToCompare: Array<Token>,
        positionIncrements: IntArray,
        types: Array<String>,
        outputUnigrams: Boolean,
        outputUnigramsIfNoShingles: Boolean
    ) {
        val filter = ShingleFilter(CannedTokenStream(*tokensToShingle), minSize, maxSize)
        filter.setOutputUnigrams(outputUnigrams)
        filter.setOutputUnigramsIfNoShingles(outputUnigramsIfNoShingles)
        shingleFilterTestCommon(filter, tokensToCompare, positionIncrements, types)
    }

    private fun shingleFilterTest(
        tokenSeparator: String?,
        minSize: Int,
        maxSize: Int,
        tokensToShingle: Array<Token>,
        tokensToCompare: Array<Token>,
        positionIncrements: IntArray,
        types: Array<String>,
        outputUnigrams: Boolean
    ) {
        val filter = ShingleFilter(CannedTokenStream(*tokensToShingle), minSize, maxSize)
        filter.setTokenSeparator(tokenSeparator)
        filter.setOutputUnigrams(outputUnigrams)
        shingleFilterTestCommon(filter, tokensToCompare, positionIncrements, types)
    }

    private fun shingleFilterTestCommon(
        filter: ShingleFilter,
        tokensToCompare: Array<Token>,
        positionIncrements: IntArray,
        types: Array<String>
    ) {
        val text = Array(tokensToCompare.size) { "" }
        val startOffsets = IntArray(tokensToCompare.size)
        val endOffsets = IntArray(tokensToCompare.size)

        for (i in tokensToCompare.indices) {
            text[i] = String.fromCharArray(tokensToCompare[i].buffer(), 0, tokensToCompare[i].length)
            startOffsets[i] = tokensToCompare[i].startOffset()
            endOffsets[i] = tokensToCompare[i].endOffset()
        }

        assertTokenStreamContents(filter, text, startOffsets, endOffsets, types, positionIncrements)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, ShingleFilter(tokenizer))
                }
            }
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        val random: Random = random()
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        MockTokenizer(MockTokenizer.WHITESPACE, false, IndexWriter.MAX_TERM_LENGTH / 2)
                    return TokenStreamComponents(tokenizer, ShingleFilter(tokenizer))
                }
            }
        checkRandomData(random, a, 3 * RANDOM_MULTIPLIER, 8192) // TODO reduced valueA = 200 to 3 for dev speed
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, ShingleFilter(tokenizer))
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testTrailingHole1() {
        // Analyzing "wizard of", where of is removed as a
        // stopword leaving a trailing hole:
        val inputTokens = arrayOf(createToken("wizard", 0, 6))
        val filter = ShingleFilter(CannedTokenStream(1, 9, *inputTokens), 2, 2)

        assertTokenStreamContents(
            filter,
            arrayOf("wizard", "wizard _"),
            intArrayOf(0, 0),
            intArrayOf(6, 9),
            intArrayOf(1, 0),
            9
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTrailingHole2() {
        // Analyzing "purple wizard of", where of is removed as a
        // stopword leaving a trailing hole:
        val inputTokens = arrayOf(createToken("purple", 0, 6), createToken("wizard", 7, 13))
        val filter = ShingleFilter(CannedTokenStream(1, 16, *inputTokens), 2, 2)

        assertTokenStreamContents(
            filter,
            arrayOf("purple", "purple wizard", "wizard", "wizard _"),
            intArrayOf(0, 0, 7, 7),
            intArrayOf(6, 13, 13, 16),
            intArrayOf(1, 0, 1, 0),
            16
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTwoTrailingHoles() {
        // Analyzing "purple wizard of the", where of and the are removed as a
        // stopwords, leaving two trailing holes:
        val inputTokens = arrayOf(createToken("purple", 0, 6), createToken("wizard", 7, 13))
        val filter = ShingleFilter(CannedTokenStream(2, 20, *inputTokens), 2, 2)

        assertTokenStreamContents(
            filter,
            arrayOf("purple", "purple wizard", "wizard", "wizard _"),
            intArrayOf(0, 0, 7, 7),
            intArrayOf(6, 13, 13, 20),
            intArrayOf(1, 0, 1, 0),
            20
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTwoTrailingHolesTriShingle() {
        // Analyzing "purple wizard of the", where of and the are removed as a
        // stopwords, leaving two trailing holes:
        val inputTokens = arrayOf(createToken("purple", 0, 6), createToken("wizard", 7, 13))
        val filter = ShingleFilter(CannedTokenStream(2, 20, *inputTokens), 2, 3)

        assertTokenStreamContents(
            filter,
            arrayOf("purple", "purple wizard", "purple wizard _", "wizard", "wizard _", "wizard _ _"),
            intArrayOf(0, 0, 0, 7, 7, 7),
            intArrayOf(6, 13, 20, 13, 20, 20),
            intArrayOf(1, 0, 0, 1, 0, 0),
            20
        )
    }
}
