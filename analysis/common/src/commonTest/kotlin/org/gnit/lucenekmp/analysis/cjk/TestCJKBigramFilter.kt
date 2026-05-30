package org.gnit.lucenekmp.analysis.cjk

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestCJKBigramFilter : BaseTokenStreamTestCase() {
    private val analyzer: Analyzer =
        object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val t: Tokenizer = StandardTokenizer()
                return TokenStreamComponents(t, CJKBigramFilter(t))
            }
        }

    private val unibiAnalyzer: Analyzer =
        object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val t: Tokenizer = StandardTokenizer()
                return TokenStreamComponents(t, CJKBigramFilter(t, 0xff, true))
            }
        }

    @Test
    @Throws(Exception::class)
    fun testHuge() {
        assertAnalyzesTo(
            analyzer,
            "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた",
            arrayOf(
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた", "た多",
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた", "た多",
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた", "た多",
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた", "た多",
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた", "た多",
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた", "た多",
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた", "た多",
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた", "た多",
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた", "た多",
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた", "た多",
                "多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testHanOnly() {
        val a: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = StandardTokenizer()
                    return TokenStreamComponents(t, CJKBigramFilter(t, CJKBigramFilter.HAN))
                }
            }
        assertAnalyzesTo(
            a,
            "多くの学生が試験に落ちた。",
            arrayOf("多", "く", "の", "学生", "が", "試験", "に", "落", "ち", "た"),
            intArrayOf(0, 1, 2, 3, 5, 6, 8, 9, 10, 11),
            intArrayOf(1, 2, 3, 5, 6, 8, 9, 10, 11, 12),
            arrayOf(
                "<SINGLE>",
                "<HIRAGANA>",
                "<HIRAGANA>",
                "<DOUBLE>",
                "<HIRAGANA>",
                "<DOUBLE>",
                "<HIRAGANA>",
                "<SINGLE>",
                "<HIRAGANA>",
                "<HIRAGANA>"
            ),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        )
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAllScripts() {
        val a: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = StandardTokenizer()
                    return TokenStreamComponents(t, CJKBigramFilter(t, 0xff, false))
                }
            }
        assertAnalyzesTo(
            a,
            "多くの学生が試験に落ちた。",
            arrayOf("多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた")
        )
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUnigramsAndBigramsAllScripts() {
        assertAnalyzesTo(
            unibiAnalyzer,
            "多くの学生が試験に落ちた。",
            arrayOf(
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た"
            ),
            intArrayOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11),
            intArrayOf(1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12),
            arrayOf(
                "<SINGLE>", "<DOUBLE>", "<SINGLE>", "<DOUBLE>", "<SINGLE>", "<DOUBLE>", "<SINGLE>", "<DOUBLE>",
                "<SINGLE>", "<DOUBLE>", "<SINGLE>", "<DOUBLE>", "<SINGLE>", "<DOUBLE>", "<SINGLE>", "<DOUBLE>",
                "<SINGLE>", "<DOUBLE>", "<SINGLE>", "<DOUBLE>", "<SINGLE>", "<DOUBLE>", "<SINGLE>"
            ),
            intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1),
            intArrayOf(1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testUnigramsAndBigramsHanOnly() {
        val a: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val t: Tokenizer = StandardTokenizer()
                    return TokenStreamComponents(t, CJKBigramFilter(t, CJKBigramFilter.HAN, true))
                }
            }
        assertAnalyzesTo(
            a,
            "多くの学生が試験に落ちた。",
            arrayOf("多", "く", "の", "学", "学生", "生", "が", "試", "試験", "験", "に", "落", "ち", "た"),
            intArrayOf(0, 1, 2, 3, 3, 4, 5, 6, 6, 7, 8, 9, 10, 11),
            intArrayOf(1, 2, 3, 4, 5, 5, 6, 7, 8, 8, 9, 10, 11, 12),
            arrayOf(
                "<SINGLE>", "<HIRAGANA>", "<HIRAGANA>", "<SINGLE>", "<DOUBLE>",
                "<SINGLE>", "<HIRAGANA>", "<SINGLE>", "<DOUBLE>", "<SINGLE>",
                "<HIRAGANA>", "<SINGLE>", "<HIRAGANA>", "<HIRAGANA>"
            ),
            intArrayOf(1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1),
            intArrayOf(1, 1, 1, 1, 2, 1, 1, 1, 2, 1, 1, 1, 1, 1)
        )
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUnigramsAndBigramsHuge() {
        assertAnalyzesTo(
            unibiAnalyzer,
            "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた" +
                "多くの学生が試験に落ちた",
            arrayOf(
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た", "た多",
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た", "た多",
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た", "た多",
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た", "た多",
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た", "た多",
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た", "た多",
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た", "た多",
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た", "た多",
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た", "た多",
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た", "た多",
                "多", "多く", "く", "くの", "の", "の学", "学", "学生", "生", "生が", "が", "が試", "試", "試験", "験", "験に",
                "に", "に落", "落", "落ち", "ち", "ちた", "た"
            )
        )
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomUnibiStrings() {
        checkRandomData(random(), unibiAnalyzer, 200 * RANDOM_MULTIPLIER)
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomUnibiHugeStrings() {
        val random = random()
        checkRandomData(random, unibiAnalyzer, 10 * RANDOM_MULTIPLIER, 8192)
    }
}
