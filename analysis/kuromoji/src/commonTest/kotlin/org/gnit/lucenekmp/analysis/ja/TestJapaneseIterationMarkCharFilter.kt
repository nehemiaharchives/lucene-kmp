package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// See: https://issues.apache.org/jira/browse/SOLR-12028 Tests cannot remove files on Windows
// machines occasionally
class TestJapaneseIterationMarkCharFilter : BaseTokenStreamTestCase() {
    private var keywordAnalyzer: Analyzer? = null
    private var japaneseAnalyzer: Analyzer? = null

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        /*super.setUp()*/
        keywordAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.KEYWORD, false)
                    return TokenStreamComponents(tokenizer, tokenizer)
                }

                override fun initReader(fieldName: String, reader: Reader): Reader {
                    return JapaneseIterationMarkCharFilter(reader)
                }
            }
        japaneseAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            null,
                            false,
                            JapaneseTokenizer.Mode.SEARCH
                        )
                    return TokenStreamComponents(tokenizer, tokenizer)
                }

                override fun initReader(fieldName: String, reader: Reader): Reader {
                    return JapaneseIterationMarkCharFilter(reader)
                }
            }
    }

    @AfterTest
    @Throws(Exception::class)
    /*override*/ fun tearDown() {
        IOUtils.close(keywordAnalyzer, japaneseAnalyzer)
        /*super.tearDown()*/
    }

    @Test
    @Throws(IOException::class)
    fun testKanji() {
        // Test single repetition
        assertAnalyzesTo(keywordAnalyzer!!, "時々", arrayOf("時時"))
        assertAnalyzesTo(japaneseAnalyzer!!, "時々", arrayOf("時時"))

        // Test multiple repetitions
        assertAnalyzesTo(keywordAnalyzer!!, "馬鹿々々しい", arrayOf("馬鹿馬鹿しい"))
        assertAnalyzesTo(japaneseAnalyzer!!, "馬鹿々々しい", arrayOf("馬鹿馬鹿しい"))
    }

    @Test
    @Throws(IOException::class)
    fun testKatakana() {
        // Test single repetition
        assertAnalyzesTo(keywordAnalyzer!!, "ミスヾ", arrayOf("ミスズ"))
        assertAnalyzesTo(japaneseAnalyzer!!, "ミスヾ", arrayOf("ミ", "スズ")) // Side effect
    }

    @Test
    @Throws(IOException::class)
    fun testHiragana() {
        // Test single unvoiced iteration
        assertAnalyzesTo(keywordAnalyzer!!, "おゝの", arrayOf("おおの"))
        assertAnalyzesTo(japaneseAnalyzer!!, "おゝの", arrayOf("お", "おの")) // Side effect

        // Test single voiced iteration
        assertAnalyzesTo(keywordAnalyzer!!, "みすゞ", arrayOf("みすず"))
        assertAnalyzesTo(japaneseAnalyzer!!, "みすゞ", arrayOf("みすず"))

        // Test single voiced iteration
        assertAnalyzesTo(keywordAnalyzer!!, "じゞ", arrayOf("じじ"))
        assertAnalyzesTo(japaneseAnalyzer!!, "じゞ", arrayOf("じじ"))

        // Test single unvoiced iteration with voiced iteration
        assertAnalyzesTo(keywordAnalyzer!!, "じゝ", arrayOf("じし"))
        assertAnalyzesTo(japaneseAnalyzer!!, "じゝ", arrayOf("じし"))

        // Test multiple repetitions with voiced iteration
        assertAnalyzesTo(keywordAnalyzer!!, "ところゞゝゝ", arrayOf("ところどころ"))
        assertAnalyzesTo(japaneseAnalyzer!!, "ところゞゝゝ", arrayOf("ところどころ"))
    }

    @Test
    @Throws(IOException::class)
    fun testMalformed() {
        // We can't iterate c here, so emit as it is
        assertAnalyzesTo(keywordAnalyzer!!, "abcところゝゝゝゝ", arrayOf("abcところcところ"))

        // We can't iterate c (with dakuten change) here, so emit it as-is
        assertAnalyzesTo(keywordAnalyzer!!, "abcところゞゝゝゝ", arrayOf("abcところcところ"))

        // We can't iterate before beginning of stream, so emit characters as-is
        assertAnalyzesTo(keywordAnalyzer!!, "ところゞゝゝゞゝゞ", arrayOf("ところどころゞゝゞ"))

        // We can't iterate an iteration mark only, so emit as-is
        assertAnalyzesTo(keywordAnalyzer!!, "々", arrayOf("々"))
        assertAnalyzesTo(keywordAnalyzer!!, "ゞ", arrayOf("ゞ"))
        assertAnalyzesTo(keywordAnalyzer!!, "ゞゝ", arrayOf("ゞゝ"))

        // We can't iterate a full stop punctuation mark (because we use it as a flush marker)
        assertAnalyzesTo(keywordAnalyzer!!, "。ゝ", arrayOf("。ゝ"))
        assertAnalyzesTo(keywordAnalyzer!!, "。。ゝゝ", arrayOf("。。ゝゝ"))

        // We can iterate other punctuation marks
        assertAnalyzesTo(keywordAnalyzer!!, "？ゝ", arrayOf("？？"))

        // We can not get a dakuten variant of ぽ -- this is also a corner case test for inside()
        assertAnalyzesTo(keywordAnalyzer!!, "ねやぽゞつむぴ", arrayOf("ねやぽぽつむぴ"))
        assertAnalyzesTo(keywordAnalyzer!!, "ねやぽゝつむぴ", arrayOf("ねやぽぽつむぴ"))
    }

    @Test
    @Throws(IOException::class)
    fun testEmpty() {
        // Empty input stays empty
        assertAnalyzesTo(keywordAnalyzer!!, "", emptyArray())
        assertAnalyzesTo(japaneseAnalyzer!!, "", emptyArray())
    }

    @Test
    @Throws(IOException::class)
    fun testFullStop() {
        // Test full stops
        assertAnalyzesTo(keywordAnalyzer!!, "。", arrayOf("。"))
        assertAnalyzesTo(keywordAnalyzer!!, "。。", arrayOf("。。"))
        assertAnalyzesTo(keywordAnalyzer!!, "。。。", arrayOf("。。。"))
    }

    @Test
    @Throws(IOException::class)
    fun testKanjiOnly() {
        // Test kanji only repetition marks
        val filter: CharFilter =
            JapaneseIterationMarkCharFilter(
                StringReader("時々、おゝのさんと一緒にお寿司が食べたいです。abcところゞゝゝ。"),
                true, // kanji
                false // no kana
            )
        assertCharFilterEquals(
            filter,
            "時時、おゝのさんと一緒にお寿司が食べたいです。abcところゞゝゝ。"
        )
    }

    @Test
    @Throws(IOException::class)
    fun testKanaOnly() {
        // Test kana only repetition marks
        val filter: CharFilter =
            JapaneseIterationMarkCharFilter(
                StringReader("時々、おゝのさんと一緒にお寿司が食べたいです。abcところゞゝゝ。"),
                false, // no kanji
                true // kana
            )
        assertCharFilterEquals(
            filter,
            "時々、おおのさんと一緒にお寿司が食べたいです。abcところどころ。"
        )
    }

    @Test
    @Throws(IOException::class)
    fun testNone() {
        // Test no repetition marks
        val filter: CharFilter =
            JapaneseIterationMarkCharFilter(
                StringReader("時々、おゝのさんと一緒にお寿司が食べたいです。abcところゞゝゝ。"),
                false, // no kanji
                false // no kana
            )
        assertCharFilterEquals(
            filter,
            "時々、おゝのさんと一緒にお寿司が食べたいです。abcところゞゝゝ。"
        )
    }

    @Test
    @Throws(IOException::class)
    fun testCombinations() {
        assertAnalyzesTo(
            keywordAnalyzer!!,
            "時々、おゝのさんと一緒にお寿司を食べに行きます。",
            arrayOf("時時、おおのさんと一緒にお寿司を食べに行きます。")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testHiraganaCoverage() {
        // Test all hiragana iteration variants
        var source =
            "かゝがゝきゝぎゝくゝぐゝけゝげゝこゝごゝさゝざゝしゝじゝすゝずゝせゝぜゝそゝぞゝたゝだゝちゝぢゝつゝづゝてゝでゝとゝどゝはゝばゝひゝびゝふゝぶゝへゝべゝほゝぼゝ"
        var target =
            "かかがかききぎきくくぐくけけげけここごこささざさししじしすすずすせせぜせそそぞそたただたちちぢちつつづつててでてととどとははばはひひびひふふぶふへへべへほほぼほ"
        assertAnalyzesTo(keywordAnalyzer!!, source, arrayOf(target))

        // Test all hiragana iteration variants with dakuten
        source =
            "かゞがゞきゞぎゞくゞぐゞけゞげゞこゞごゞさゞざゞしゞじゞすゞずゞせゞぜゞそゞぞゞたゞだゞちゞぢゞつゞづゞてゞでゞとゞどゞはゞばゞひゞびゞふゞぶゞへゞべゞほゞぼゞ"
        target =
            "かがががきぎぎぎくぐぐぐけげげげこごごごさざざざしじじじすずずずせぜぜぜそぞぞぞただだだちぢぢぢつづづづてでででとどどどはばばばひびびびふぶぶぶへべべべほぼぼぼ"
        assertAnalyzesTo(keywordAnalyzer!!, source, arrayOf(target))
    }

    @Test
    @Throws(IOException::class)
    fun testKatakanaCoverage() {
        // Test all katakana iteration variants
        var source =
            "カヽガヽキヽギヽクヽグヽケヽゲヽコヽゴヽサヽザヽシヽジヽスヽズヽセヽゼヽソヽゾヽタヽダヽチヽヂヽツヽヅヽテヽデヽトヽドヽハヽバヽヒヽビヽフヽブヽヘヽベヽホヽボヽ"
        var target =
            "カカガカキキギキククグクケケゲケココゴコササザサシシジシススズスセセゼセソソゾソタタダタチチヂチツツヅツテテデテトトドトハハバハヒヒビヒフフブフヘヘベヘホホボホ"
        assertAnalyzesTo(keywordAnalyzer!!, source, arrayOf(target))

        // Test all katakana iteration variants with dakuten
        source =
            "カヾガヾキヾギヾクヾグヾケヾゲヾコヾゴヾサヾザヾシヾジヾスヾズヾセヾゼヾソヾゾヾタヾダヾチヾヂヾツヾヅヾテヾデヾトヾドヾハヾバヾヒヾビヾフヾブヾヘヾベヾホヾボヾ"
        target =
            "カガガガキギギギクグググケゲゲゲコゴゴゴサザザザシジジジスズズズセゼゼゼソゾゾゾタダダダチヂヂヂツヅヅヅテデデデトドドドハバババヒビビビフブブブヘベベベホボボボ"
        assertAnalyzesTo(keywordAnalyzer!!, source, arrayOf(target))
    }

    private fun assertCharFilterEquals(filter: CharFilter, expected: String) {
        val buffer = CharArray(1024)
        val sb = StringBuilder()
        try {
            while (true) {
                val read = filter.read(buffer, 0, buffer.size)
                if (read == -1) {
                    break
                }
                sb.appendRange(buffer, 0, read)
            }
        } finally {
            filter.close()
        }
        assertEquals(expected, sb.toString())
    }
}
