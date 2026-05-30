package org.gnit.lucenekmp.analysis.cjk

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.charfilter.MappingCharFilter
import org.gnit.lucenekmp.analysis.charfilter.NormalizeCharMap
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Most tests adopted from TestCJKTokenizer */
class TestCJKAnalyzer : BaseTokenStreamTestCase() {
    private val analyzer: Analyzer = CJKAnalyzer()

    @Test
    @Throws(IOException::class)
    fun testJa1() {
        assertAnalyzesTo(
            analyzer,
            "一二三四五六七八九十",
            arrayOf("一二", "二三", "三四", "四五", "五六", "六七", "七八", "八九", "九十"),
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8),
            intArrayOf(2, 3, 4, 5, 6, 7, 8, 9, 10),
            arrayOf("<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>"),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testJa2() {
        assertAnalyzesTo(
            analyzer,
            "一 二三四 五六七八九 十",
            arrayOf("一", "二三", "三四", "五六", "六七", "七八", "八九", "十"),
            intArrayOf(0, 2, 3, 6, 7, 8, 9, 12),
            intArrayOf(1, 4, 5, 8, 9, 10, 11, 13),
            arrayOf("<SINGLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<SINGLE>"),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testC() {
        assertAnalyzesTo(
            analyzer,
            "abc defgh ijklmn opqrstu vwxy z",
            arrayOf("abc", "defgh", "ijklmn", "opqrstu", "vwxy", "z"),
            intArrayOf(0, 4, 10, 17, 25, 30),
            intArrayOf(3, 9, 16, 24, 29, 31),
            arrayOf("<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>"),
            intArrayOf(1, 1, 1, 1, 1, 1)
        )
    }

    /** LUCENE-2207: wrong offset calculated by end() */
    @Test
    @Throws(IOException::class)
    fun testFinalOffset() {
        assertAnalyzesTo(analyzer, "あい", arrayOf("あい"), intArrayOf(0), intArrayOf(2), arrayOf("<DOUBLE>"), intArrayOf(1))
        assertAnalyzesTo(analyzer, "あい   ", arrayOf("あい"), intArrayOf(0), intArrayOf(2), arrayOf("<DOUBLE>"), intArrayOf(1))
        assertAnalyzesTo(analyzer, "test", arrayOf("test"), intArrayOf(0), intArrayOf(4), arrayOf("<ALPHANUM>"), intArrayOf(1))
        assertAnalyzesTo(analyzer, "test   ", arrayOf("test"), intArrayOf(0), intArrayOf(4), arrayOf("<ALPHANUM>"), intArrayOf(1))
        assertAnalyzesTo(
            analyzer,
            "あいtest",
            arrayOf("あい", "test"),
            intArrayOf(0, 2),
            intArrayOf(2, 6),
            arrayOf("<DOUBLE>", "<ALPHANUM>"),
            intArrayOf(1, 1)
        )
        assertAnalyzesTo(
            analyzer,
            "testあい    ",
            arrayOf("test", "あい"),
            intArrayOf(0, 4),
            intArrayOf(4, 6),
            arrayOf("<ALPHANUM>", "<DOUBLE>"),
            intArrayOf(1, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testMix() {
        assertAnalyzesTo(
            analyzer,
            "あいうえおabcかきくけこ",
            arrayOf("あい", "いう", "うえ", "えお", "abc", "かき", "きく", "くけ", "けこ"),
            intArrayOf(0, 1, 2, 3, 5, 8, 9, 10, 11),
            intArrayOf(2, 3, 4, 5, 8, 10, 11, 12, 13),
            arrayOf("<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<ALPHANUM>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>"),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testMix2() {
        assertAnalyzesTo(
            analyzer,
            "あいうえおabんcかきくけ こ",
            arrayOf("あい", "いう", "うえ", "えお", "ab", "ん", "c", "かき", "きく", "くけ", "こ"),
            intArrayOf(0, 1, 2, 3, 5, 7, 8, 9, 10, 11, 14),
            intArrayOf(2, 3, 4, 5, 7, 8, 9, 11, 12, 13, 15),
            arrayOf("<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<ALPHANUM>", "<SINGLE>", "<ALPHANUM>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<SINGLE>"),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        )
    }

    /** Non-english text (outside of CJK) is treated normally, according to unicode rules */
    @Test
    @Throws(IOException::class)
    fun testNonIdeographic() {
        assertAnalyzesTo(
            analyzer,
            "一 روبرت موير",
            arrayOf("一", "روبرت", "موير"),
            intArrayOf(0, 2, 8),
            intArrayOf(1, 7, 12),
            arrayOf("<SINGLE>", "<ALPHANUM>", "<ALPHANUM>"),
            intArrayOf(1, 1, 1)
        )
    }

    /** Same as the above, except with a nonspacing mark to show correctness. */
    @Test
    @Throws(IOException::class)
    fun testNonIdeographicNonLetter() {
        assertAnalyzesTo(
            analyzer,
            "一 رُوبرت موير",
            arrayOf("一", "رُوبرت", "موير"),
            intArrayOf(0, 2, 9),
            intArrayOf(1, 8, 13),
            arrayOf("<SINGLE>", "<ALPHANUM>", "<ALPHANUM>"),
            intArrayOf(1, 1, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testSurrogates() {
        assertAnalyzesTo(
            analyzer,
            "𩬅艱鍟䇹愯瀛",
            arrayOf("𩬅艱", "艱鍟", "鍟䇹", "䇹愯", "愯瀛"),
            intArrayOf(0, 2, 3, 4, 5),
            intArrayOf(3, 4, 5, 6, 7),
            arrayOf("<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>"),
            intArrayOf(1, 1, 1, 1, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testReusableTokenStream() {
        assertAnalyzesTo(
            analyzer,
            "あいうえおabcかきくけこ",
            arrayOf("あい", "いう", "うえ", "えお", "abc", "かき", "きく", "くけ", "けこ"),
            intArrayOf(0, 1, 2, 3, 5, 8, 9, 10, 11),
            intArrayOf(2, 3, 4, 5, 8, 10, 11, 12, 13),
            arrayOf("<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<ALPHANUM>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>"),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
        )

        assertAnalyzesTo(
            analyzer,
            "あいうえおabんcかきくけ こ",
            arrayOf("あい", "いう", "うえ", "えお", "ab", "ん", "c", "かき", "きく", "くけ", "こ"),
            intArrayOf(0, 1, 2, 3, 5, 7, 8, 9, 10, 11, 14),
            intArrayOf(2, 3, 4, 5, 7, 8, 9, 11, 12, 13, 15),
            arrayOf("<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<ALPHANUM>", "<SINGLE>", "<ALPHANUM>", "<DOUBLE>", "<DOUBLE>", "<DOUBLE>", "<SINGLE>"),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testSingleChar() {
        assertAnalyzesTo(
            analyzer,
            "一",
            arrayOf("一"),
            intArrayOf(0),
            intArrayOf(1),
            arrayOf("<SINGLE>"),
            intArrayOf(1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testTokenStream() {
        assertAnalyzesTo(
            analyzer,
            "一丁丂",
            arrayOf("一丁", "丁丂"),
            intArrayOf(0, 1),
            intArrayOf(2, 3),
            arrayOf("<DOUBLE>", "<DOUBLE>"),
            intArrayOf(1, 1)
        )
    }

    /** test that offsets are correct when mappingcharfilter is previously applied */
    @Test
    @Throws(IOException::class)
    fun testChangedOffsets() {
        val builder = NormalizeCharMap.Builder()
        builder.add("a", "一二")
        builder.add("b", "二三")
        val norm: NormalizeCharMap = builder.build()
        val analyzer: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = StandardTokenizer()
                    return TokenStreamComponents(tokenizer, CJKBigramFilter(tokenizer))
                }

                override fun initReader(fieldName: String, reader: Reader): Reader {
                    return MappingCharFilter(norm, reader)
                }
            }

        assertAnalyzesTo(analyzer, "ab", arrayOf("一二", "二二", "二三"), intArrayOf(0, 0, 1), intArrayOf(1, 1, 2))
        analyzer.close()
    }

    private class FakeStandardTokenizer(input: TokenStream) : TokenFilter(input) {
        private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (input.incrementToken()) {
                typeAtt.setType(StandardTokenizer.TOKEN_TYPES[StandardTokenizer.IDEOGRAPHIC])
                return true
            }
            return false
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSingleChar2() {
        val analyzer: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    var filter: TokenFilter = FakeStandardTokenizer(tokenizer)
                    filter = StopFilter(filter, CharArraySet.EMPTY_SET)
                    filter = CJKBigramFilter(filter)
                    return TokenStreamComponents(tokenizer, filter)
                }
            }

        assertAnalyzesTo(
            analyzer,
            "一",
            arrayOf("一"),
            intArrayOf(0),
            intArrayOf(1),
            arrayOf("<SINGLE>"),
            intArrayOf(1)
        )
        analyzer.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = CJKAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        val a: Analyzer = CJKAnalyzer()
        checkRandomData(random(), a, 10 * RANDOM_MULTIPLIER, 8192)
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, CJKBigramFilter(tokenizer))
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }
}
