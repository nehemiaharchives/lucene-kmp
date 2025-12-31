package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests for [JapaneseTokenizerFactory]  */
class TestJapaneseTokenizerFactory : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun testSimple() {
        val factory = JapaneseTokenizerFactory(HashMap())
        factory.inform(StringMockResourceLoader(""))
        val ts: TokenStream =
            factory.create(newAttributeFactory())
        (ts as Tokenizer).setReader(StringReader("これは本ではない"))
        assertTokenStreamContents(
            ts,
            arrayOf("これ", "は", "本", "で", "は", "ない"),
            intArrayOf(0, 2, 3, 4, 5, 6),
            intArrayOf(2, 3, 4, 5, 6, 8)
        )
    }

    @Test
    /** Test that search mode is enabled and working by default  */
    @Throws(IOException::class)
    fun testDefaults() {
        val factory = JapaneseTokenizerFactory(HashMap())
        factory.inform(StringMockResourceLoader(""))
        val ts: TokenStream =
            factory.create(newAttributeFactory())
        (ts as Tokenizer).setReader(StringReader("シニアソフトウェアエンジニア"))
        assertTokenStreamContents(ts, arrayOf("シニア", "ソフトウェア", "エンジニア"))
    }

    @Test
    /** Test mode parameter: specifying normal mode  */
    @Throws(IOException::class)
    fun testMode() {
        val args: MutableMap<String, String> = HashMap()
        args["mode"] = "normal"
        val factory = JapaneseTokenizerFactory(args)
        factory.inform(StringMockResourceLoader(""))
        val ts: TokenStream = factory.create(newAttributeFactory())
        (ts as Tokenizer).setReader(StringReader("シニアソフトウェアエンジニア"))
        assertTokenStreamContents(ts, arrayOf("シニアソフトウェアエンジニア"))
    }

    @Test
    /** Test user dictionary  */
    @Throws(IOException::class)
    fun testUserDict() {
        val userDict =
            ("# Custom segmentation for long entries\n"
                    + "日本経済新聞,日本 経済 新聞,ニホン ケイザイ シンブン,カスタム名詞\n"
                    + "関西国際空港,関西 国際 空港,カンサイ コクサイ クウコウ,テスト名詞\n"
                    + "# Custom reading for sumo wrestler\n"
                    + "朝青龍,朝青龍,アサショウリュウ,カスタム人名\n")
        val args: MutableMap<String, String> = HashMap()
        args["userDictionary"] = "userdict.txt"
        val factory = JapaneseTokenizerFactory(args)
        factory.inform(StringMockResourceLoader(userDict))
        val ts: TokenStream = factory.create(newAttributeFactory())
        (ts as Tokenizer).setReader(StringReader("関西国際空港に行った"))
        assertTokenStreamContents(ts, arrayOf("関西", "国際", "空港", "に", "行っ", "た"))
    }

    @Test
    /** Test preserving punctuation  */
    @Throws(IOException::class)
    fun testPreservePunctuation() {
        val args: MutableMap<String, String> = HashMap()
        args["discardPunctuation"] = "false"
        val factory = JapaneseTokenizerFactory(args)
        factory.inform(StringMockResourceLoader(""))
        val ts: TokenStream = factory.create(newAttributeFactory())
        (ts as Tokenizer).setReader(StringReader("今ノルウェーにいますが、来週の頭日本に戻ります。楽しみにしています！お寿司が食べたいな。。。"))
        assertTokenStreamContents(
            ts,
            arrayOf(
                "今",
                "ノルウェー",
                "に",
                "い",
                "ます",
                "が",
                "、",
                "来週",
                "の",
                "頭",
                "日本",
                "に",
                "戻り",
                "ます",
                "。",
                "楽しみ",
                "に",
                "し",
                "て",
                "い",
                "ます",
                "！",
                "お",
                "寿司",
                "が",
                "食べ",
                "たい",
                "な",
                "。",
                "。",
                "。"
            )
        )
    }

    @Test
    /** Test discarding compound (original) token  */
    @Throws(IOException::class)
    fun testPreserveCompoundToken() {
        val args: MutableMap<String, String> = HashMap()
        args["discardCompoundToken"] = "false"
        val factory = JapaneseTokenizerFactory(args)
        factory.inform(StringMockResourceLoader(""))
        val ts: TokenStream = factory.create(newAttributeFactory())
        (ts as Tokenizer).setReader(StringReader("シニアソフトウェアエンジニア"))
        assertTokenStreamContents(ts, arrayOf("シニア", "シニアソフトウェアエンジニア", "ソフトウェア", "エンジニア"))
    }

    @Test
    /** Test that bogus arguments result in exception  */
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected: IllegalArgumentException =
            expectThrows(
                IllegalArgumentException::class
            ) {
                JapaneseTokenizerFactory(mutableMapOf("bogusArg" to "bogusValue"))
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }

    @Throws(IOException::class)
    private fun makeTokenStream(
        args: HashMap<String, String>,
        `in`: String
    ): TokenStream {
        val factory = JapaneseTokenizerFactory(args)
        factory.inform(StringMockResourceLoader(""))
        val t: Tokenizer = factory.create(newAttributeFactory())
        t.setReader(StringReader(`in`))
        return t
    }

    @Test
    /** Test nbestCost parameter  */
    @Throws(IOException::class)
    fun testNbestCost() {
        val args = HashMap<String, String>()
        args["nBestCost"] = "2000"
        assertTokenStreamContents(makeTokenStream(args,"鳩山積み"), arrayOf("鳩", "鳩山", "山積み", "積み"))
    }

    @Test
    /** Test nbestExamples parameter  */
    @Throws(IOException::class)
    fun testNbestExample() {
        val args = HashMap<String, String>()
        args["nBestExamples"] = "/鳩山積み-鳩山/鳩山積み-鳩/"
        assertTokenStreamContents(makeTokenStream(args,"鳩山積み"), arrayOf("鳩", "鳩山", "山積み", "積み"))
    }
}
