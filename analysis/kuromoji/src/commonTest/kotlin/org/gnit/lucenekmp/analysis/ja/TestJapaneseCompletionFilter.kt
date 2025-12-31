package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.cjk.CJKWidthCharFilter
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestJapaneseCompletionFilter : BaseTokenStreamTestCase() {
    private var indexAnalyzer: Analyzer? = null
    private var queryAnalyzer: Analyzer? = null

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        /*super.setUp()*/
        indexAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            null,
                            true,
                            JapaneseTokenizer.Mode.NORMAL
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        JapaneseCompletionFilter(
                            tokenizer,
                            JapaneseCompletionFilter.Mode.INDEX
                        )
                    )
                }

                override fun initReader(
                    fieldName: String,
                    reader: Reader
                ): Reader {
                    return CJKWidthCharFilter(reader)
                }

                override fun initReaderForNormalization(
                    fieldName: String?,
                    reader: Reader
                ): Reader {
                    return CJKWidthCharFilter(reader)
                }
            }
        queryAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            null,
                            true,
                            JapaneseTokenizer.Mode.NORMAL
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        JapaneseCompletionFilter(
                            tokenizer,
                            JapaneseCompletionFilter.Mode.QUERY
                        )
                    )
                }

                override fun initReader(
                    fieldName: String,
                    reader: Reader
                ): Reader {
                    return CJKWidthCharFilter(reader)
                }

                override fun initReaderForNormalization(
                    fieldName: String?,
                    reader: Reader
                ): Reader {
                    return CJKWidthCharFilter(reader)
                }
            }
    }

    @AfterTest
    @Throws(Exception::class)
    /*override*/ fun tearDown() {
        IOUtils.close(indexAnalyzer)
        IOUtils.close(queryAnalyzer)
        /*super.tearDown()*/
    }

    @Test
    @Throws(IOException::class)
    fun testCompletionIndex() {
        assertAnalyzesTo(
            indexAnalyzer!!,
            "東京",
            arrayOf("東京", "toukyou"),
            intArrayOf(0, 0),
            intArrayOf(2, 2),
            intArrayOf(1, 0)
        )

        assertAnalyzesTo(
            indexAnalyzer!!,
            "東京都",
            arrayOf("東京", "toukyou", "都", "to"),
            intArrayOf(0, 0, 2, 2),
            intArrayOf(2, 2, 3, 3),
            intArrayOf(1, 0, 1, 0)
        )

        assertAnalyzesTo(
            indexAnalyzer!!,
            "ドラえもん",
            arrayOf("ドラえもん", "doraemon", "doraemonn"),
            intArrayOf(0, 0, 0),
            intArrayOf(5, 5, 5),
            intArrayOf(1, 0, 0)
        )

        assertAnalyzesTo(
            indexAnalyzer!!,
            "ソースコード",
            arrayOf("ソース", "soーsu", "コード", "koーdo"),
            intArrayOf(0, 0, 3, 3),
            intArrayOf(3, 3, 6, 6),
            intArrayOf(1, 0, 1, 0)
        )

        assertAnalyzesTo(
            indexAnalyzer!!,
            "反社会的勢力",
            arrayOf(
                "反",
                "han",
                "hann",
                "社会",
                "syakai",
                "shakai",
                "的",
                "teki",
                "勢力",
                "seiryoku"
            ),
            intArrayOf(0, 0, 0, 1, 1, 1, 3, 3, 4, 4),
            intArrayOf(1, 1, 1, 3, 3, 3, 4, 4, 6, 6),
            intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 1, 0)
        )

        assertAnalyzesTo(
            indexAnalyzer!!, "々", arrayOf("々"), intArrayOf(0), intArrayOf(1), intArrayOf(1)
        )

        assertAnalyzesTo(
            indexAnalyzer!!,
            "是々",
            arrayOf("是", "ze", "々"),
            intArrayOf(0, 0, 1),
            intArrayOf(1, 1, 2),
            intArrayOf(1, 0, 1)
        )

        assertAnalyzesTo(
            indexAnalyzer!!,
            "是々の",
            arrayOf("是", "ze", "々", "の", "no"),
            intArrayOf(0, 0, 1, 2, 2),
            intArrayOf(1, 1, 2, 3, 3),
            intArrayOf(1, 0, 1, 1, 0)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testCompletionQuery() {
        assertAnalyzesTo(
            queryAnalyzer!!,
            "東京",
            arrayOf("東京", "toukyou"),
            intArrayOf(0, 0),
            intArrayOf(2, 2),
            intArrayOf(1, 0)
        )

        assertAnalyzesTo(
            queryAnalyzer!!,
            "東京都",
            arrayOf("東京", "toukyou", "都", "to"),
            intArrayOf(0, 0, 2, 2),
            intArrayOf(2, 2, 3, 3),
            intArrayOf(1, 0, 1, 0)
        )

        assertAnalyzesTo(
            queryAnalyzer!!,
            "ドラえもん",
            arrayOf("ドラえもん", "doraemon", "doraemonn"),
            intArrayOf(0, 0, 0),
            intArrayOf(5, 5, 5),
            intArrayOf(1, 0, 0)
        )

        assertAnalyzesTo(
            queryAnalyzer!!,
            "ソースコード",
            arrayOf("ソースコード", "soーsukoーdo"),
            intArrayOf(0, 0),
            intArrayOf(6, 6),
            intArrayOf(1, 0)
        )

        assertAnalyzesTo(
            queryAnalyzer!!,
            "反社会的勢力",
            arrayOf(
                "反",
                "han",
                "hann",
                "社会",
                "syakai",
                "shakai",
                "的",
                "teki",
                "勢力",
                "seiryoku"
            ),
            intArrayOf(0, 0, 0, 1, 1, 1, 3, 3, 4, 4),
            intArrayOf(1, 1, 1, 3, 3, 3, 4, 4, 6, 6),
            intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 1, 0)
        )

        assertAnalyzesTo(
            queryAnalyzer!!, "々", arrayOf("々"), intArrayOf(0), intArrayOf(1), intArrayOf(1)
        )

        assertAnalyzesTo(
            queryAnalyzer!!,
            "是々",
            arrayOf("是", "ze", "々"),
            intArrayOf(0, 0, 1),
            intArrayOf(1, 1, 2),
            intArrayOf(1, 0, 1)
        )

        assertAnalyzesTo(
            indexAnalyzer!!,
            "是々の",
            arrayOf("是", "ze", "々", "の", "no"),
            intArrayOf(0, 0, 1, 2, 2),
            intArrayOf(1, 1, 2, 3, 3),
            intArrayOf(1, 0, 1, 1, 0)
        )

        assertAnalyzesTo(
            queryAnalyzer!!,
            "東京ｔ",
            arrayOf("東京t", "toukyout"),
            intArrayOf(0, 0),
            intArrayOf(3, 3),
            intArrayOf(1, 0)
        )

        assertAnalyzesTo(
            queryAnalyzer!!,
            "サッｋ",
            arrayOf("サッk", "sakk"),
            intArrayOf(0, 0),
            intArrayOf(3, 3),
            intArrayOf(1, 0)
        )

        assertAnalyzesTo(
            queryAnalyzer!!,
            "反ｓｙ",
            arrayOf("反sy", "hansy", "hannsy"),
            intArrayOf(0, 0, 0),
            intArrayOf(3, 3, 3),
            intArrayOf(1, 0, 0)
        )

        assertAnalyzesTo(
            queryAnalyzer!!,
            "さーきゅｒ",
            arrayOf("さーきゅr", "saーkyur"),
            intArrayOf(0, 0),
            intArrayOf(5, 5),
            intArrayOf(1, 0)
        )

        assertAnalyzesTo(
            queryAnalyzer!!,
            "是々ｈ",
            arrayOf("是", "ze", "々h"),
            intArrayOf(0, 0, 1),
            intArrayOf(1, 1, 3),
            intArrayOf(1, 0, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testEnglish() {
        assertAnalyzesTo(
            indexAnalyzer!!,
            "this atest",
            arrayOf("this", "atest")
        )
        assertAnalyzesTo(
            queryAnalyzer!!,
            "this atest",
            arrayOf("this", "atest")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testRandomStrings() {
        checkRandomData(
            random(),
            indexAnalyzer!!,
            atLeast(200)
        )
        checkRandomData(
            random(),
            queryAnalyzer!!,
            atLeast(200)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        KeywordTokenizer()
                    return TokenStreamComponents(
                        tokenizer,
                        JapaneseCompletionFilter(tokenizer)
                    )
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }
}
