package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.ja.JapaneseTokenizer.Mode
import org.gnit.lucenekmp.analysis.ja.dict.ConnectionCosts
import org.gnit.lucenekmp.analysis.ja.dict.JaMorphData
import org.gnit.lucenekmp.analysis.ja.dict.UserDictionary
import org.gnit.lucenekmp.analysis.ja.tokenattributes.*
import org.gnit.lucenekmp.analysis.morph.GraphvizFormatter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.LineNumberReader
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockGraphTokenFilter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils.close
import org.gnit.lucenekmp.util.UnicodeUtil
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class TestJapaneseTokenizer : BaseTokenStreamTestCase() {
    private var analyzer: Analyzer? = null
    private var analyzerNormal: Analyzer? = null
    private var analyzerNormalNBest: Analyzer? = null
    private var analyzerNoPunct: Analyzer? = null
    private var extendedModeAnalyzerNoPunct: Analyzer? = null
    private var analyzerNoCompound: Analyzer? = null
    private var extendedModeAnalyzerNoCompound: Analyzer? = null

    private fun makeTokenizer(
        discardPunctuation: Boolean,
        mode: Mode
    ): JapaneseTokenizer {
        return JapaneseTokenizer(
            newAttributeFactory(),
            readDict(),
            discardPunctuation,
            mode
        )
    }

    private fun makeAnalyzer(t: Tokenizer): Analyzer {
        return object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                return TokenStreamComponents(t, t)
            }
        }
    }

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        /*super.setUp()*/
        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            readDict(),
                            false,
                            false,
                            Mode.SEARCH
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        tokenizer
                    )
                }
            }
        analyzerNormal =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            readDict(),
                            false,
                            false,
                            Mode.NORMAL
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        tokenizer
                    )
                }
            }
        analyzerNormalNBest =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            readDict(),
                            false,
                            false,
                            Mode.NORMAL
                        )
                    tokenizer.setNBestCost(2000)
                    return TokenStreamComponents(
                        tokenizer,
                        tokenizer
                    )
                }
            }
        analyzerNoPunct =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            readDict(),
                            true,
                            false,
                            Mode.SEARCH
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        tokenizer
                    )
                }
            }
        extendedModeAnalyzerNoPunct =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            readDict(),
                            true,
                            false,
                            Mode.EXTENDED
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        tokenizer
                    )
                }
            }
        analyzerNoCompound =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            readDict(),
                            false,
                            true,
                            Mode.SEARCH
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        tokenizer
                    )
                }
            }
        extendedModeAnalyzerNoCompound =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            readDict(),
                            false,
                            true,
                            Mode.EXTENDED
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        tokenizer
                    )
                }
            }
    }

    @AfterTest
    @Throws(Exception::class)
    /*override */fun tearDown() {
        close(
            analyzer,
            analyzerNormal,
            analyzerNoPunct,
            extendedModeAnalyzerNoPunct,
            analyzerNoCompound,
            extendedModeAnalyzerNoCompound
        )
        /*super.tearDown()*/
    }

    @Test
    @Throws(Exception::class)
    fun testNormalMode() {
        assertAnalyzesTo(
            analyzerNormal!!,
            "シニアソフトウェアエンジニア",
            arrayOf("シニアソフトウェアエンジニア")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNormalModeNbest() {
        val t: JapaneseTokenizer =
            makeTokenizer(true, Mode.NORMAL)
        val a: Analyzer = makeAnalyzer(t)

        t.setNBestCost(2000)
        assertAnalyzesTo(
            a,
            "シニアソフトウェアエンジニア",
            arrayOf("シニア", "シニアソフトウェアエンジニア", "ソフトウェア", "エンジニア")
        )

        t.setNBestCost(5000)
        assertAnalyzesTo(
            a,
            "シニアソフトウェアエンジニア",
            arrayOf(
                "シニア",
                "シニアソフトウェアエンジニア",
                "ソフト",
                "ソフトウェア",
                "ウェア",
                "エンジニア"
            )
        )

        t.setNBestCost(0)
        assertAnalyzesTo(
            a,
            "数学部長谷川",
            arrayOf("数学", "部長", "谷川")
        )

        t.setNBestCost(3000)
        assertAnalyzesTo(
            a,
            "数学部長谷川",
            arrayOf("数学", "部", "部長", "長谷川", "谷川")
        )

        t.setNBestCost(0)
        assertAnalyzesTo(
            a,
            "経済学部長",
            arrayOf("経済", "学", "部長")
        )

        t.setNBestCost(2000)
        assertAnalyzesTo(
            a,
            "経済学部長",
            arrayOf("経済", "経済学部", "学", "部長", "長")
        )

        t.setNBestCost(0)
        assertAnalyzesTo(
            a,
            "成田空港、米原油流出",
            arrayOf("成田空港", "米", "原油", "流出")
        )

        t.setNBestCost(4000)
        assertAnalyzesTo(
            a,
            "成田空港、米原油流出",
            arrayOf("成田空港", "米", "米原", "原油", "油", "流出")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testSearchModeNbest() {
        val t: JapaneseTokenizer =
            makeTokenizer(true, Mode.SEARCH)
        val a: Analyzer = makeAnalyzer(t)

        t.setNBestCost(0)
        assertAnalyzesTo(
            a,
            "成田空港、米原油流出",
            arrayOf("成田", "空港", "米", "原油", "流出")
        )

        t.setNBestCost(4000)
        assertAnalyzesTo(
            a,
            "成田空港、米原油流出",
            arrayOf("成田", "成田空港", "空港", "米", "米原", "原油", "油", "流出")
        )
    }

    @Throws(Exception::class)
    private fun makeTokenList(
        a: Analyzer,
        `in`: String
    ): ArrayList<String> {
        val list: ArrayList<String> = ArrayList()
        val ts: TokenStream = a.tokenStream("dummy", `in`)
        val termAtt: CharTermAttribute =
            ts.getAttribute(CharTermAttribute::class)

        ts.reset()
        while (ts.incrementToken()) {
            list.add(termAtt.toString())
        }
        ts.end()
        ts.close()
        return list
    }

    @Throws(Exception::class)
    private fun checkToken(
        a: Analyzer,
        `in`: String,
        requitedToken: String
    ): Boolean {
        return makeTokenList(a, `in`).indexOf(requitedToken) != -1
    }

    @Test
    @Throws(Exception::class)
    fun testNBestCost() {
        val t: JapaneseTokenizer =
            makeTokenizer(true, Mode.NORMAL)
        val a: Analyzer = makeAnalyzer(t)

        t.setNBestCost(0)
        assertFalse(
            checkToken(a, "数学部長谷川", "学部"), message = "学部 is not a token of 数学部長谷川"
        )

        assertTrue(
            0 <= t.calcNBestCost("/数学部長谷川-学部/"), message = "cost calculated /数学部長谷川-学部/"
        )
        t.setNBestCost(t.calcNBestCost("/数学部長谷川-学部/"))
        assertTrue(
            checkToken(a, "数学部長谷川", "学部"), message = "学部 is a token of 数学部長谷川"
        )

        assertTrue(
            0 <= t.calcNBestCost("/数学部長谷川-数/成田空港-成/"), message = "cost calculated /数学部長谷川-数/成田空港-成/"
        )
        t.setNBestCost(t.calcNBestCost("/数学部長谷川-数/成田空港-成/"))
        assertTrue(
            checkToken(a, "数学部長谷川", "数"), message = "数 is a token of 数学部長谷川"
        )
        assertTrue( checkToken(a, "成田空港", "成"), message = "成 is a token of 成田空港")
    }

    @Test
    @Throws(Exception::class)
    fun testDecomposition1() {
        assertAnalyzesTo(
            analyzerNoPunct!!,
            "本来は、貧困層の女性や子供に医療保護を提供するために創設された制度である、" + "アメリカ低所得者医療援助制度が、今日では、その予算の約３分の１を老人に費やしている。",
            arrayOf(
                "本来",
                "は",
                "貧困",
                "層",
                "の",
                "女性",
                "や",
                "子供",
                "に",
                "医療",
                "保護",
                "を",
                "提供",
                "する",
                "ため",
                "に",
                "創設",
                "さ",
                "れ",
                "た",
                "制度",
                "で",
                "ある",
                "アメリカ",
                "低",
                "所得",
                "者",
                "医療",
                "援助",
                "制度",
                "が",
                "今日",
                "で",
                "は",
                "その",
                "予算",
                "の",
                "約",
                "３",
                "分の",
                "１",
                "を",
                "老人",
                "に",
                "費やし",
                "て",
                "いる"
            ),
            intArrayOf(
                0,
                2,
                4,
                6,
                7,
                8,
                10,
                11,
                13,
                14,
                16,
                18,
                19,
                21,
                23,
                25,
                26,
                28,
                29,
                30,
                31,
                33,
                34,
                37,
                41,
                42,
                44,
                45,
                47,
                49,
                51,
                53,
                55,
                56,
                58,
                60,
                62,
                63,
                64,
                65,
                67,
                68,
                69,
                71,
                72,
                75,
                76
            ),
            intArrayOf(
                2,
                3,
                6,
                7,
                8,
                10,
                11,
                13,
                14,
                16,
                18,
                19,
                21,
                23,
                25,
                26,
                28,
                29,
                30,
                31,
                33,
                34,
                36,
                41,
                42,
                44,
                45,
                47,
                49,
                51,
                52,
                55,
                56,
                57,
                60,
                62,
                63,
                64,
                65,
                67,
                68,
                69,
                71,
                72,
                75,
                76,
                78
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testDecomposition2() {
        assertAnalyzesTo(
            analyzerNoPunct!!,
            "麻薬の密売は根こそぎ絶やさなければならない",
            arrayOf(
                "麻薬",
                "の",
                "密売",
                "は",
                "根こそぎ",
                "絶やさ",
                "なけれ",
                "ば",
                "なら",
                "ない"
            ),
            intArrayOf(0, 2, 3, 5, 6, 10, 13, 16, 17, 19),
            intArrayOf(2, 3, 5, 6, 10, 13, 16, 17, 19, 21)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testDecomposition3() {
        assertAnalyzesTo(
            analyzerNoPunct!!,
            "魔女狩大将マシュー・ホプキンス。",
            arrayOf("魔女", "狩", "大将", "マシュー", "ホプキンス"),
            intArrayOf(0, 2, 3, 5, 10),
            intArrayOf(2, 3, 5, 9, 15)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testDecomposition4() {
        assertAnalyzesTo(
            analyzer!!,
            "これは本ではない",
            arrayOf("これ", "は", "本", "で", "は", "ない"),
            intArrayOf(0, 2, 3, 4, 5, 6),
            intArrayOf(2, 3, 4, 5, 6, 8)
        )
    }

    @Test
    /* Note this is really a stupid test just to see if things arent horribly slow.
   * ideally the test would actually fail instead of hanging...
   */
    @Throws(Exception::class)
    fun testDecomposition5() {
        analyzer!!.tokenStream(
            "bogus",
            "くよくよくよくよくよくよくよくよくよくよくよくよくよくよくよくよくよくよくよくよ"
        ).use { ts ->
            ts.reset()
            while (ts.incrementToken()) {
            }
            ts.end()
        }
    }

    /*
    // NOTE: intentionally fails!  Just trying to debug this
    // one input...
  public void testDecomposition6() throws Exception {
    assertAnalyzesTo(analyzer, "奈良先端科学技術大学院大学",
      new String[] { "これ", "は", "本", "で", "は", "ない" },
      new int[] { 0, 2, 3, 4, 5, 6 },
      new int[] { 2, 3, 4, 5, 6, 8 }
                     );
  }
  */
    @Test
    /** Tests that sentence offset is incorporated into the resulting offsets  */
    @Throws(Exception::class)
    fun testTwoSentences() {
        /*
    //TokenStream ts = a.tokenStream("foo", "妹の咲子です。俺と年子で、今受験生です。");
    TokenStream ts = tokenStream("foo", "&#x250cdf66<!--\"<!--#<!--;><!--#<!--#><!---->>-->;");
    ts.reset();
    CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
    while(ts.incrementToken()) {
      System.out.println("  " + termAtt.toString());
    }
    System.out.println("DONE PARSE\n\n");
    */

        assertAnalyzesTo(
            analyzerNoPunct!!,
            "魔女狩大将マシュー・ホプキンス。 魔女狩大将マシュー・ホプキンス。",
            arrayOf(
                "魔女",
                "狩",
                "大将",
                "マシュー",
                "ホプキンス",
                "魔女",
                "狩",
                "大将",
                "マシュー",
                "ホプキンス"
            ),
            intArrayOf(0, 2, 3, 5, 10, 17, 19, 20, 22, 27),
            intArrayOf(2, 3, 5, 9, 15, 19, 20, 22, 26, 32)
        )
    }

    @Test
    /** blast some random strings through the analyzer  */
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(
            random(),
            analyzer!!,
            100 * RANDOM_MULTIPLIER
        )
        checkRandomData(
            random(),
            analyzerNoPunct!!,
            100 * RANDOM_MULTIPLIER
        )
        checkRandomData(
            random(),
            analyzerNormalNBest!!,
            100 * RANDOM_MULTIPLIER
        )
    }

    @Test
    /** blast some random large strings through the analyzer  */
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        val random: Random = random()
        checkRandomData(
            random,
            analyzer!!,
            RANDOM_MULTIPLIER,
            4096
        )
        checkRandomData(
            random,
            analyzerNoPunct!!,
            RANDOM_MULTIPLIER,
            4096
        )
        checkRandomData(
            random,
            analyzerNormalNBest!!,
            RANDOM_MULTIPLIER,
            4096
        )
    }

    @Test
    @LuceneTestCase.Companion.Nightly
    @Throws(Exception::class)
    fun testRandomHugeStringsAtNight() {
        val random: Random = random()
        checkRandomData(
            random,
            analyzer!!,
            3 * RANDOM_MULTIPLIER,
            8192
        )
        checkRandomData(
            random,
            analyzerNoPunct!!,
            3 * RANDOM_MULTIPLIER,
            8192
        )
        checkRandomData(
            random,
            analyzerNormalNBest!!,
            3 * RANDOM_MULTIPLIER,
            8192
        )
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStringsMockGraphAfter() {
        // Randomly inject graph tokens after JapaneseTokenizer:
        val random: Random = random()
        val analyzer: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            readDict(),
                            false,
                            Mode.SEARCH
                        )
                    val graph: TokenStream =
                        MockGraphTokenFilter(
                            random(),
                            tokenizer
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        graph
                    )
                }
            }
        checkRandomData(
            random,
            analyzer,
            RANDOM_MULTIPLIER,
            4096
        )
        analyzer.close()
    }

    @Test
    @LuceneTestCase.Companion.Nightly
    @Throws(Exception::class)
    fun testRandomHugeStringsMockGraphAfterAtNight() {
        // Randomly inject graph tokens after JapaneseTokenizer:
        val random: Random = random()
        val analyzer: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            readDict(),
                            false,
                            JapaneseTokenizer.Mode.SEARCH
                        )
                    val graph: TokenStream =
                        MockGraphTokenFilter(
                            random(),
                            tokenizer
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        graph
                    )
                }
            }
        checkRandomData(
            random,
            analyzer,
            3 * RANDOM_MULTIPLIER,
            8192
        )
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testLargeDocReliability() {
        val numIters: Int = atLeast(1)
        for (i in 0..<numIters) {
            val s: String = TestUtil.randomUnicodeString(
                random(),
                10000
            )
            analyzer!!.tokenStream("foo", s).use { ts ->
                ts.reset()
                while (ts.incrementToken()) {
                }
                ts.end()
            }
        }
    }

    @Test
    /** simple test for supplementary characters  */
    @Throws(IOException::class)
    fun testSurrogates() {
        assertAnalyzesTo(
            analyzer!!,
            "𩬅艱鍟䇹愯瀛",
            arrayOf("𩬅", "艱", "鍟", "䇹", "愯", "瀛")
        )
    }

    @Test
    /** random test ensuring we don't ever split supplementaries  */
    @Throws(IOException::class)
    fun testSurrogates2() {
        val numIterations: Int = atLeast(500)
        for (i in 0..<numIterations) {
            if (VERBOSE) {
                println("\nTEST: iter=$i")
            }
            val s: String = TestUtil.randomUnicodeString(
                random(),
                100
            )
            analyzer!!.tokenStream("foo", s).use { ts ->
                val termAtt: CharTermAttribute =
                    ts.addAttribute(
                        CharTermAttribute::class
                    )
                ts.reset()
                while (ts.incrementToken()) {
                    assertTrue(
                        UnicodeUtil.validUTF16String(
                            termAtt
                        )
                    )
                }
                ts.end()
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testOnlyPunctuation() {
        analyzerNoPunct!!.tokenStream("foo", "。、。。").use { ts ->
            ts.reset()
            assertFalse(ts.incrementToken())
            ts.end()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testOnlyPunctuationExtended() {
        extendedModeAnalyzerNoPunct!!.tokenStream("foo", "......").use { ts ->
            ts.reset()
            assertFalse(ts.incrementToken())
            ts.end()
        }
    }

    @Test
    // note: test is kinda silly since kuromoji emits punctuation tokens.
    // but, when/if we filter these out it will be useful.
    @Throws(Exception::class)
    fun testEnd() {
        assertTokenStreamContents(
            analyzerNoPunct!!.tokenStream("foo", "これは本ではない"),
            arrayOf("これ", "は", "本", "で", "は", "ない"),
            intArrayOf(0, 2, 3, 4, 5, 6),
            intArrayOf(2, 3, 4, 5, 6, 8),
            8
        )

        assertTokenStreamContents(
            analyzerNoPunct!!.tokenStream("foo", "これは本ではない    "),
            arrayOf("これ", "は", "本", "で", "は", "ない"),
            intArrayOf(0, 2, 3, 4, 5, 6, 8),
            intArrayOf(2, 3, 4, 5, 6, 8, 9),
            12
        )
    }

    @Test
    @Throws(Exception::class)
    fun testUserDict() {
        // Not a great test because w/o userdict.txt the
        // segmentation is the same:
        assertTokenStreamContents(
            analyzer!!.tokenStream("foo", "関西国際空港に行った"),
            arrayOf("関西", "国際", "空港", "に", "行っ", "た"),
            intArrayOf(0, 2, 4, 6, 7, 9),
            intArrayOf(2, 4, 6, 7, 9, 10),
            10
        )
    }

    @Test
    @Throws(Exception::class)
    fun testUserDict2() {
        // Better test: w/o userdict the segmentation is different:
        assertTokenStreamContents(
            analyzer!!.tokenStream("foo", "朝青龍"),
            arrayOf("朝青龍"),
            intArrayOf(0),
            intArrayOf(3),
            3
        )
    }

    @Test
    @Throws(Exception::class)
    fun testUserDict3() {
        // Test entry that breaks into multiple tokens:
        assertTokenStreamContents(
            analyzer!!.tokenStream("foo", "abcd"),
            arrayOf("a", "b", "cd"),
            intArrayOf(0, 1, 2),
            intArrayOf(1, 2, 4),
            4
        )
    }

    // HMM: fails (segments as a/b/cd/efghij)... because the
    // two paths have exactly equal paths (1 KNOWN + 1
    // UNKNOWN) and we don't seem to favor longer KNOWN /
    // shorter UNKNOWN matches:
    /*
  public void testUserDict4() throws Exception {
    // Test entry that has another entry as prefix
    assertTokenStreamContents(tokenStream("foo", "abcdefghij"),
                              new String[] { "ab", "cd", "efg", "hij"  },
                              new int[] { 0, 2, 4, 7 },
                              new int[] { 2, 4, 7, 10 },
                              10
    );
  }
  */
    @Test
    @Throws(Exception::class)
    fun testSegmentation() {
        // Skip tests for Michelle Kwan -- UniDic segments Kwan as ク ワン
        //   String input = "ミシェル・クワンが優勝しました。スペースステーションに行きます。うたがわしい。";
        //   String[] surfaceForms = {
        //        "ミシェル", "・", "クワン", "が", "優勝", "し", "まし", "た", "。",
        //        "スペース", "ステーション", "に", "行き", "ます", "。",
        //        "うたがわしい", "。"
        //   };
        val input = "スペースステーションに行きます。うたがわしい。"
        val surfaceForms = arrayOf(
            "スペース",
            "ステーション",
            "に",
            "行き",
            "ます",
            "。",
            "うたがわしい",
            "。"
        )
        assertAnalyzesTo(
            analyzer!!,
            input,
            surfaceForms
        )
    }

    @Test
    @Throws(Exception::class)
    fun testLatticeToDot() {
        val gv2: GraphvizFormatter<JaMorphData> =
            GraphvizFormatter(
                ConnectionCosts.getInstance()
            )
        val analyzer: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            readDict(),
                            false,
                            Mode.SEARCH
                        )
                    tokenizer.setGraphvizFormatter(gv2)
                    return TokenStreamComponents(
                        tokenizer,
                        tokenizer
                    )
                }
            }

        val input = "スペースステーションに行きます。うたがわしい。"
        val surfaceForms = arrayOf(
            "スペース",
            "ステーション",
            "に",
            "行き",
            "ます",
            "。",
            "うたがわしい",
            "。"
        )
        assertAnalyzesTo(
            analyzer,
            input,
            surfaceForms
        )

        assertTrue(gv2.finish().indexOf("22.0") != -1)
        analyzer.close()
    }

    @Throws(IOException::class)
    private fun assertReadings(input: String, vararg readings: String) {
        analyzer!!.tokenStream("ignored", input).use { ts ->
            val readingAtt: ReadingAttribute =
                ts.addAttribute(ReadingAttribute::class)
            ts.reset()
            for (reading in readings) {
                assertTrue(ts.incrementToken())
                assertEquals(reading, readingAtt.getReading())
            }
            assertFalse(ts.incrementToken())
            ts.end()
        }
    }

    @Throws(IOException::class)
    private fun assertPronunciations(input: String, vararg pronunciations: String) {
        analyzer!!.tokenStream("ignored", input).use { ts ->
            val readingAtt: ReadingAttribute =
                ts.addAttribute(ReadingAttribute::class)
            ts.reset()
            for (pronunciation in pronunciations) {
                assertTrue(ts.incrementToken())
                assertEquals(pronunciation, readingAtt.getPronunciation())
            }
            assertFalse(ts.incrementToken())
            ts.end()
        }
    }

    @Throws(IOException::class)
    private fun assertBaseForms(input: String, vararg baseForms: String?) {
        analyzer!!.tokenStream("ignored", input).use { ts ->
            val baseFormAtt: BaseFormAttribute =
                ts.addAttribute(
                    BaseFormAttribute::class
                )
            ts.reset()
            for (baseForm in baseForms) {
                assertTrue(ts.incrementToken())
                assertEquals(baseForm, baseFormAtt.getBaseForm())
            }
            assertFalse(ts.incrementToken())
            ts.end()
        }
    }

    @Throws(IOException::class)
    private fun assertInflectionTypes(input: String, vararg inflectionTypes: String?) {
        analyzer!!.tokenStream("ignored", input).use { ts ->
            val inflectionAtt: InflectionAttribute =
                ts.addAttribute(
                    InflectionAttribute::class
                )
            ts.reset()
            for (inflectionType in inflectionTypes) {
                assertTrue(ts.incrementToken())
                assertEquals(inflectionType, inflectionAtt.getInflectionType())
            }
            assertFalse(ts.incrementToken())
            ts.end()
        }
    }

    @Throws(IOException::class)
    private fun assertInflectionForms(input: String, vararg inflectionForms: String?) {
        analyzer!!.tokenStream("ignored", input).use { ts ->
            val inflectionAtt: InflectionAttribute =
                ts.addAttribute(
                    InflectionAttribute::class
                )
            ts.reset()
            for (inflectionForm in inflectionForms) {
                assertTrue(ts.incrementToken())
                assertEquals(inflectionForm, inflectionAtt.getInflectionForm())
            }
            assertFalse(ts.incrementToken())
            ts.end()
        }
    }

    @Throws(IOException::class)
    private fun assertPartsOfSpeech(input: String, vararg partsOfSpeech: String?) {
        analyzer!!.tokenStream("ignored", input).use { ts ->
            val partOfSpeechAtt: PartOfSpeechAttribute =
                ts.addAttribute(
                    PartOfSpeechAttribute::class
                )
            ts.reset()
            for (partOfSpeech in partsOfSpeech) {
                assertTrue(ts.incrementToken())
                assertEquals(partOfSpeech, partOfSpeechAtt.getPartOfSpeech())
            }
            assertFalse(ts.incrementToken())
            ts.end()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testReadings() {
        assertReadings("寿司が食べたいです。", "スシ", "ガ", "タベ", "タイ", "デス", "。")
    }

    @Test
    @Throws(Exception::class)
    fun testReadings2() {
        assertReadings(
            "多くの学生が試験に落ちた。",
            "オオク",
            "ノ",
            "ガクセイ",
            "ガ",
            "シケン",
            "ニ",
            "オチ",
            "タ",
            "。"
        )
    }

    @Test
    @Throws(Exception::class)
    fun testPronunciations() {
        assertPronunciations("寿司が食べたいです。", "スシ", "ガ", "タベ", "タイ", "デス", "。")
    }

    @Test
    @Throws(Exception::class)
    fun testPronunciations2() {
        // pronunciation differs from reading here
        assertPronunciations(
            "多くの学生が試験に落ちた。",
            "オーク",
            "ノ",
            "ガクセイ",
            "ガ",
            "シケン",
            "ニ",
            "オチ",
            "タ",
            "。"
        )
    }

    @Test
    @Throws(Exception::class)
    fun testBasicForms() {
        assertBaseForms(
            "それはまだ実験段階にあります。",
            null,
            null,
            null,
            null,
            null,
            null,
            "ある",
            null,
            null
        )
    }

    @Test
    @Throws(Exception::class)
    fun testInflectionTypes() {
        assertInflectionTypes(
            "それはまだ実験段階にあります。",
            null,
            null,
            null,
            null,
            null,
            null,
            "五段・ラ行",
            "特殊・マス",
            null
        )
    }

    @Test
    @Throws(Exception::class)
    fun testInflectionForms() {
        assertInflectionForms(
            "それはまだ実験段階にあります。",
            null,
            null,
            null,
            null,
            null,
            null,
            "連用形",
            "基本形",
            null
        )
    }

    @Test
    @Throws(Exception::class)
    fun testPartOfSpeech() {
        assertPartsOfSpeech(
            "それはまだ実験段階にあります。",
            "名詞-代名詞-一般",
            "助詞-係助詞",
            "副詞-助詞類接続",
            "名詞-サ変接続",
            "名詞-一般",
            "助詞-格助詞-一般",
            "動詞-自立",
            "助動詞",
            "記号-句点"
        )
    }

    // TODO: the next 2 tests are no longer using the first/last word ids, maybe lookup the words and
    // fix
    // do we have a possibility to actually lookup the first and last word from dictionary
    @Test
    @Throws(Exception::class)
    fun testYabottai() {
        assertAnalyzesTo(analyzer!!, "やぼったい", arrayOf("やぼったい"))
    }

    @Test
    @Throws(Exception::class)
    fun testTsukitosha() {
        assertAnalyzesTo(analyzer!!, "突き通しゃ", arrayOf("突き通しゃ"))
    }

    @Test
    @Throws(Exception::class)
    fun testBocchan() {
        doTestBocchan(1)
    }

    @Test
    @LuceneTestCase.Companion.Nightly
    @Throws(Exception::class)
    fun testBocchanBig() {
        doTestBocchan(100)
    }

    /*
  public void testWikipedia() throws Exception {
    final FileInputStream fis = new FileInputStream("/q/lucene/jawiki-20120220-pages-articles.xml");
    final Reader r = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));

    final long startTimeNS = System.nanoTime();
    boolean done = false;
    long compoundCount = 0;
    long nonCompoundCount = 0;
    long netOffset = 0;
    while (!done) {
      final TokenStream ts = tokenStream("ignored", r);
      ts.reset();
      final PositionIncrementAttribute posIncAtt = ts.addAttribute(PositionIncrementAttribute.class);
      final OffsetAttribute offsetAtt = ts.addAttribute(OffsetAttribute.class);
      int count = 0;
      while (true) {
        if (!ts.incrementToken()) {
          done = true;
          break;
        }
        count++;
        if (posIncAtt.getPositionIncrement() == 0) {
          compoundCount++;
        } else {
          nonCompoundCount++;
          if (nonCompoundCount % 1000000 == 0) {
            System.out.println(String.format("%.2f msec [pos=%d, %d, %d]",
                                             (System.nanoTime() - startTimeNS) / (double) TimeUnit.MILLISECONDS.toNanos(1),
                                             netOffset + offsetAtt.startOffset(),
                                             nonCompoundCount,
                                             compoundCount));
          }
        }
        if (count == 100000000) {
          System.out.println("  again...");
          break;
        }
      }
      ts.end();
      netOffset += offsetAtt.endOffset();
    }
    System.out.println("compoundCount=" + compoundCount + " nonCompoundCount=" + nonCompoundCount);
    r.close();
  }
  */
    @Throws(Exception::class)
    private fun doTestBocchan(numIterations: Int) {

        val inputStream: InputStream = TODO("replace txt file access with inline string resource")

        val reader =
            LineNumberReader(
                InputStreamReader(
                    inputStream /*this.javaClass.getResourceAsStream("bocchan.utf-8")*/,
                    StandardCharsets.UTF_8
                )
            )
        val line: String? = reader.readLine()
        reader.close()

        if (VERBOSE) {
            println("Test for Bocchan without pre-splitting sentences")
        }

        /*
    if (numIterations > 1) {
      // warmup
      for (int i = 0; i < numIterations; i++) {
        final TokenStream ts = tokenStream("ignored", line);
        ts.reset();
        while(ts.incrementToken());
      }
    }
    */
        var totalStart: Long = Clock.System.now().toEpochMilliseconds()
        for (i in 0..<numIterations) {
            analyzer!!.tokenStream("ignored", line!!).use { ts ->
                ts.reset()
                while (ts.incrementToken()) {
                }
                ts.end()
            }
        }
        val sentences: Array<String> =
            line!!.split("、|。".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (VERBOSE) {
            println("Total time : " + TimeUnit.NANOSECONDS.toMillis(Clock.System.now().toEpochMilliseconds() - totalStart) + " ms")
            println("Test for Bocchan with pre-splitting sentences (" + sentences.size + " sentences)")
        }
        totalStart = Clock.System.now().toEpochMilliseconds()
        for (i in 0..<numIterations) {
            for (sentence in sentences) {
                analyzer!!.tokenStream("ignored", sentence).use { ts ->
                    ts.reset()
                    while (ts.incrementToken()) {
                    }
                    ts.end()
                }
            }
        }
        if (VERBOSE) {
            println("Total time : " + TimeUnit.NANOSECONDS.toMillis(Clock.System.now().toEpochMilliseconds() - totalStart) + " ms")
        }
    }

    @Test
    @Throws(Exception::class)
    fun testWithPunctuation() {
        assertAnalyzesTo(analyzerNoPunct!!, "羽田。空港", arrayOf("羽田", "空港"), intArrayOf(1, 1))
    }

    @Test
    @Throws(Exception::class)
    fun testCompoundOverPunctuation() {
        assertAnalyzesToPositions(analyzerNoPunct!!, "dεε϶ϢϏΎϷΞͺ羽田", arrayOf("d", "ε", "ε", "ϢϏΎϷΞͺ", "羽田"), intArrayOf(1, 1, 1, 1, 1), intArrayOf(1, 1, 1, 1, 1))
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyUserDict() {
        val emptyReader: Reader = StringReader("\n# This is an empty user dictionary\n\n")
        val emptyDict: UserDictionary? = UserDictionary.open(emptyReader)

        val analyzer: Analyzer = object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = JapaneseTokenizer(newAttributeFactory(), emptyDict, false, Mode.SEARCH)
                    return TokenStreamComponents(tokenizer, tokenizer)
                }
            }

        assertAnalyzesTo(analyzer, "これは本ではない", arrayOf("これ", "は", "本", "で", "は", "ない"), intArrayOf(0, 2, 3, 4, 5, 6), intArrayOf(2, 3, 4, 5, 6, 8))
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBigDocument() {
        val doc = "商品の購入・詳細(サイズ、画像)は商品名をクリックしてください！[L.B　CANDY　STOCK]フラワービジューベアドレス[L.B　DAILY　STOCK]ボーダーニットトップス［L.B　DAILY　STOCK］ボーダーロングニットOP［L.B　DAILY　STOCK］ロゴトートBAG［L.B　DAILY　STOCK］裏毛ロゴプリントプルオーバー【TVドラマ着用】アンゴラワッフルカーディガン【TVドラマ着用】グラフィティーバックリボンワンピース【TVドラマ着用】ボーダーハイネックトップス【TVドラマ着用】レオパードミッドカーフスカート【セットアップ対応商品】起毛ニットスカート【セットアップ対応商品】起毛ニットプルオーバー2wayサングラス33ナンバーリングニット3Dショルダーフレアードレス3周年スリッパ3周年ラグマット3周年ロックグラスキャンドルLily　Brown　2015年　福袋MIXニットプルオーバーPeckhamロゴニットアンゴラジャガードプルオーバーアンゴラタートルアンゴラチュニックアンゴラニットカーディガンアンゴラニットプルオーバーアンゴラフレアワンピースアンゴラロングカーディガンアンゴラワッフルカーディガンヴィンテージファー付コートヴィンテージボーダーニットヴィンテージレースハイネックトップスヴィンテージレースブラウスウエストシースルーボーダーワンピースオーガンジーラインフレアスカートオープンショルダーニットトップスオフショルシャーリングワンピースオフショルニットオフショルニットプルオーバーオフショルボーダーロンパースオフショルワイドコンビネゾンオルテガ柄ニットプルオーバーカシュクールオフショルワンピースカットアシンメトリードレスカットサテンプリーツフレアースカートカラースーパーハイウェストスキニーカラーブロックドレスカラーブロックニットチュニックギャザーフレアスカートキラキラストライプタイトスカートキラキラストライプドレスキルティングファーコートグラデーションベアドレスグラデーションラウンドサングラスグラフティーオフショルトップスグラフティーキュロットグリッターリボンヘアゴムクロップドブラウスケーブルハイウエストスカートコーデュロイ×スエードパネルスカートコーデュロイタイトスカートゴールドバックルベルト付スカートゴシックヒールショートブーツゴシック柄ニットワンピコンビスタジャンサイドステッチボーイズデニムパンツサスペつきショートパンツサスペンダー付プリーツロングスカートシャーリングタイトスカートジャガードタックワンピーススエードフリルフラワーパンツスエード裏毛肩空きトップススクエアショルダーBAGスクエアバックルショルダースクエアミニバッグストーンビーチサンダルストライプサスペ付きスキニーストライプバックスリットシャツスライバーシャギーコートタートル×レースタイトスカートタートルニットプルオーバータイトジャンパースカートダブルクロスチュールフレアスカートダブルストラップパンプスダブルハートリングダブルフェイスチェックストールチェーンコンビビジューネックレスチェーンコンビビジューピアスチェーンコンビビジューブレスチェーンツバ広HATチェーンビジューピアスチェックニットプルオーバーチェックネルミディアムスカートチェック柄スキニーパンツチュールコンビアシメトップスデニムフレアースカートドットオフショルフリルブラウスドットジャガードドレスドットニットプルオーバードットレーストップスニット×オーガンジースカートセットニットキャミソールワンピースニットスヌードパールコンビフープピアスハイウエストショートデニムハイウエストタイトスカートハイウエストデニムショートパンツハイウエストプリーツスカートハイウエストミッドカーフスカートハイゲージタートルニットハイゲージラインニットハイネック切り替えスウェットバタフライネックレスバタフライミニピアスバタフライリングバックタンクリブワンピースバックリボンスキニーデニムパンツバックリボン深Vワンピースビジューストラップサンダルビスチェコンビオフショルブラウスブークレジャガードニットフェイクムートンショートコートフェレットカーディガンフェレットビックタートルニットブラウジングクルーブラウスプリーツブラウスフリルニットプルオーバーフリンジニットプルオーバーフレアニットスカートブロウ型サングラスベーシックフェレットプルオーバーベルト付ガウチョパンツベルト付ショートパンツベルト付タックスカートベルト付タックパンツベルベットインヒールパンプスベロアウェッジパンプスベロアミッドカーフワンピースベロアワンピースベロア風ニットカーディガンボア付コートボーダーVネックTシャツボーダーオフショルカットソーボーダーカットソーワンピースボーダータイトカットソーボーダートップスボーダートップス×スカートセットボストンメガネマオカラーシャツニットセットミックスニットプルオーバーミッドカーフ丈ポンチスカートミリタリーギャザーショートパンツメッシュハイネックトップスメルトンPコートメルトンダッフルコートメルトンダブルコートモヘアニットカーディガンモヘアニットタートルユリ柄プリーツフレアースカートライダースデニムジャケットライナー付チェスターコートラッフルプリーツブラウスラメジャガードハイゲージニットリブニットワンピリボン×パールバレッタリボンバレッタリボンベルトハイウエストパンツリリー刺繍開襟ブラウスレースビスチェローファーサボロゴニットキャップロゴ刺繍ニットワッチロングニットガウンワッフルアンゴラプルオーバーワンショルダワーワンピース光沢ラメニットカーディガン刺繍シフォンブラウス台形ミニスカート配色ニットプルオーバー裏毛プルオーバー×オーガンジースカートセット"

        val tokenizer = JapaneseTokenizer(newAttributeFactory(), readDict(), false, Mode.NORMAL)
        tokenizer.setReader(StringReader(doc))
        tokenizer.reset()
        while (tokenizer.incrementToken()) {
        }
    }

    @Test
    @Throws(Exception::class)
    fun testPatchedSystemDict() {
        assertAnalyzesTo(analyzer!!, "令和元年", arrayOf("令和", "元年"), intArrayOf(0, 2), intArrayOf(2, 4))

        assertAnalyzesTo(analyzerNormal!!, "令和元年", arrayOf("令和", "元年"), intArrayOf(0, 2), intArrayOf(2, 4))
    }

    @Test
    @Throws(Exception::class)
    fun testNoCompoundToken() {
        assertAnalyzesTo(analyzerNormal!!, "株式会社とアカデミア", arrayOf("株式会社", "と", "アカデミア"))

        assertAnalyzesTo(analyzer!!, "株式会社とアカデミア", arrayOf("株式", "株式会社", "会社", "と", "アカデミア"))

        assertAnalyzesTo(analyzerNoCompound!!, "株式会社とアカデミア", arrayOf("株式", "会社", "と", "アカデミア"))

        assertAnalyzesTo(extendedModeAnalyzerNoPunct!!, "株式会社とアカデミア", arrayOf("株式", "株式会社", "会社", "と", "ア", "カ", "デ", "ミ", "ア"))

        assertAnalyzesTo(extendedModeAnalyzerNoCompound!!, "株式会社とアカデミア", arrayOf("株式", "会社", "と", "ア", "カ", "デ", "ミ", "ア"))

        assertAnalyzesTo(analyzer!!, "北海道日本ハムファイターズ", arrayOf("北海道", "日本", "ハムファイターズ"))

        assertAnalyzesTo(analyzerNoCompound!!, "北海道日本ハムファイターズ", arrayOf("北海道", "日本", "ハムファイターズ"))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyBacktrace() {
        var text = ""

        // since the max backtrace gap ({@link JapaneseTokenizer#MAX_BACKTRACE_GAP)
        // is set to 1024, we want the first 1023 characters to generate multiple paths
        // so that the regular backtrace is not executed.
        for (i in 0..1022) {
            text += "あ"
        }

        // and the last 2 characters to be a valid word so that they
        // will end-up together
        text += "手紙"

        val outputs: MutableList<String> = ArrayList()
        for (i in 0..510) {
            outputs.add("ああ")
        }
        outputs.add("あ")
        outputs.add("手紙")

        assertAnalyzesTo(analyzer!!, text, outputs.toTypedArray<String>())
    }

    companion object {
        fun readDict(): UserDictionary {
            try {
                val inputStream: InputStream = TODO("replace txt file access with inline string val")
                    /*TestJapaneseTokenizer::class.getResourceAsStream("userdict.txt")*/
                inputStream
                    .use { stream ->
                        InputStreamReader(stream, StandardCharsets.UTF_8)
                            .use { reader ->
                                return UserDictionary.open(reader)!!
                            }
                    }
            } catch (ioe: IOException) {
                throw RuntimeException(ioe)
            }
        }
    }
}
