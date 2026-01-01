package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.ja.JapaneseTokenizer.Mode
import org.gnit.lucenekmp.jdkport.LineNumberReader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestSearchMode : BaseTokenStreamTestCase() {
    private var analyzer: Analyzer? = null
    private var analyzerNoOriginal: Analyzer? = null

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        /*super.setUp()*/
        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(newAttributeFactory(), null, true, false, Mode.SEARCH)
                    return TokenStreamComponents(tokenizer, tokenizer)
                }
            }
        analyzerNoOriginal =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(newAttributeFactory(), null, true, true, Mode.SEARCH)
                    return TokenStreamComponents(tokenizer, tokenizer)
                }
            }
    }

    @AfterTest
    @Throws(Exception::class)
    /*override*/ fun tearDown() {
        analyzer!!.close()
        analyzerNoOriginal!!.close()
        /*super.tearDown()*/
    }

    /** Test search mode segmentation */
    @Test
    @Throws(IOException::class)
    fun testSearchSegmentation() {
        val reader = LineNumberReader(StringReader(SEGMENTATION_TESTS))
        try {
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                // Remove comments
                line = line.replace(Regex("#.*$"), "")
                // Skip empty lines or comment lines
                if (line.trim().isEmpty()) {
                    continue
                }
                if (VERBOSE) {
                    println("Line no. ${reader.lineNumber}: $line")
                }
                val fields = line.split("\t", limit = 2)
                val sourceText = fields[0]
                val expectedTokens = fields[1].trim().split(Regex("\\s+")).toMutableList()
                val expectedPosIncrs = IntArray(expectedTokens.size)
                val expectedPosLengths = IntArray(expectedTokens.size)
                for (tokIdx in expectedTokens.indices) {
                    if (expectedTokens[tokIdx].endsWith("/0")) {
                        expectedTokens[tokIdx] = expectedTokens[tokIdx].removeSuffix("/0")
                        expectedPosLengths[tokIdx] = expectedTokens.size - 1
                    } else {
                        expectedPosIncrs[tokIdx] = 1
                        expectedPosLengths[tokIdx] = 1
                    }
                }
                assertAnalyzesTo(
                    analyzer!!,
                    sourceText,
                    expectedTokens.toTypedArray(),
                    null,
                    null,
                    null,
                    expectedPosIncrs,
                    expectedPosLengths
                )
            }
        } finally {
            reader.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSearchSegmentationNoOriginal() {
        val reader = LineNumberReader(StringReader(SEGMENTATION_TESTS))
        try {
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                // Remove comments
                line = line.replace(Regex("#.*$"), "")
                // Skip empty lines or comment lines
                if (line.trim().isEmpty()) {
                    continue
                }
                if (VERBOSE) {
                    println("Line no. ${reader.lineNumber}: $line")
                }
                val fields = line.split("\t", limit = 2)
                val sourceText = fields[0]
                val tmpExpectedTokens = fields[1].trim().split(Regex("\\s+"))

                val expectedTokenList = ArrayList<String>()
                for (expectedToken in tmpExpectedTokens) {
                    if (!expectedToken.endsWith("/0")) {
                        expectedTokenList.add(expectedToken)
                    }
                }

                val expectedPosIncrs = IntArray(expectedTokenList.size)
                val expectedPosLengths = IntArray(expectedTokenList.size)
                for (tokIdx in expectedTokenList.indices) {
                    expectedPosIncrs[tokIdx] = 1
                    expectedPosLengths[tokIdx] = 1
                }
                assertAnalyzesTo(
                    analyzerNoOriginal!!,
                    sourceText,
                    expectedTokenList.toTypedArray(),
                    null,
                    null,
                    null,
                    expectedPosIncrs,
                    expectedPosLengths
                )
            }
        } finally {
            reader.close()
        }
    }

    private companion object {
        private val SEGMENTATION_TESTS: String = """
### 
### Tests for Kuromoji's search mode heuristic
### 
### In search-mode, Kuromoji uses a heuristic to do extra splitting of words
### to get a decompounding effect useful for search.  This file includes tests
### for this heuristic and demonstrates its usefulness, but also weaknesses.
### 
### This file's format is as follows:
###	  <text><tab><token1> <token2> ... <token>
### 
### This file should use UTF-8 encoding and there is one test per line.  The
### text to be segmented and its expected surface form token sequence is 
### separated by a tab ('\t').  Tokens are  separated by a half-width space.
### Whitespace lines and lines starting with a '#' are ignored.  Comments
### are not allowed on entry line.
### 
### NOTE: These tests depends on IPADIC
### 
### Revision history:
###  - 2012-01-29: Initial version
### 

##
## Organizations
##

# Kansai Internationl Airport
関西国際空港	関西 関西国際空港/0 国際 空港
# Narita Airport
成田空港	成田 成田空港/0 空港
# Haneda Airport
羽田空港	羽田 羽田空港/0 空港
# Nara Institute of Science and Technology
奈良先端科学技術大学院大学	奈良 奈良先端科学技術大学院大学/0 先端 科学 技術 大学院 大学
# Tokyo University
東京大学	東京 東京大学/0 大学
# Kyoto University
京都大学	京都 京都大学/0 大学

# NOTE: differs from non-compound mode:
# Kyoto University Baseball Club
京都大学硬式野球部	京都大 学 硬式 野球 部

##
## Katakana titles
##

# Senior Software Engineer
シニアソフトウェアエンジニア	シニア シニアソフトウェアエンジニア/0 ソフトウェア エンジニア
# Software Engineer
ソフトウェアエンジニア	ソフトウェア エンジニア
# Senior Project Manager
シニアプロジェクトマネジャー	シニア シニアプロジェクトマネジャー/0 プロジェクト マネジャー
# Project Manager
プロジェクトマネジャー	プロジェクト マネジャー
# Senior Sales Engineer
シニアセールスエンジニア	シニア シニアセールスエンジニア/0 セールス エンジニア
# System Architect
システムアーキテクト	システム システムアーキテクト/0 アーキテクト
# Senior System Architect
シニアシステムアーキテクト	シニア シニアシステムアーキテクト/0 システム アーキテクト
# System Administrator
システムアドミニストレータ	システム アドミニストレータ
システムアドミニストレーター	システム システムアドミニストレーター/0 アドミニストレーター
# Senior System Administrator
シニアシステムアドミニストレーター	シニア シニアシステムアドミニストレーター/0 システム アドミニストレーター

##
## Company names (several are fictitious)
##

# SoftBank Mobile
ソフトバンクモバイル	ソフトバンク モバイル
# Alpine Materials
アルパインマテリアルズ	アルパイン アルパインマテリアルズ/0 マテリアルズ
# Sapporo Holdings
サッポロホールディングス	サッポロ ホールディングス
# Yamada Corporation
ヤマダコーポレーション	ヤマダ ヤマダコーポレーション/0 コーポレーション
# Canon Semiconductor equipement	NOTE: Semiconductor becomes semi + conductor
キヤノンセミコンダクターエクィップメント	キヤノン キヤノンセミコンダクターエクィップメント/0 セミ コンダクター エクィップメント
# Orental Chain
オリエンタルチエン	オリエンタル オリエンタルチエン/0 チエン
# Ally Projects Japan	NOTE: Becomes one token as プロジェクツ is not in IPADIC
アーリープロジェクツジャパン	アーリープロジェクツジャパン
# Peter Pan Corporation
ピーターパンコーポレーション	ピーター ピーターパンコーポレーション/0 パン コーポレーション
# AIM Create
エイムクリエイツ	エイムクリエイツ
# Mars Engineering
マースエンジニアリング	マース マースエンジニアリング/0 エンジニアリング
# Fuji Protein Technology
フジプロテインテクノロジー	フジ フジプロテインテクノロジー/0 プロテイン テクノロジー

##
## Person names
##

# Michael Jackson
マイケルジャクソン	マイケル ジャクソン
# Steve Jobs
スティーブジョブズ	スティーブ ジョブズ
# Harry Potter	NOTE: Becomes one token (short word)
ハリーポッター	ハリーポッター
# Bill Gates	NOTE: Becomes one token (short word)
ビルゲイツ	ビルゲイツ
# Sean Connery	NOTE: Becomes one token (okay)
ショーンコネリー	ショーンコネリー

##
## Other nouns
##

# Holdings
ホールディングス	ホールディングス
# Engineering
エンジニアリング	エンジニアリング
# Software Engineering
ソフトウェアエンジニアリング	ソフトウェア エンジニアリング
# Shopping center
ショッピングセンター	ショッピング センター
# Game center (arcade)	NOTE: One token because of short word
ゲームセンター	ゲームセンター
# Christmas shopping
クリスマスショッピング	クリスマス ショッピング
# Download file
ダウンロードファイル	ダウンロード ファイル
# Technology
テクノロジー	テクノロジー
# Lillehammer Olympics
リレハンメルオリンピック	リレハンメル オリンピック

##
## Problematic terms
##

# JT Engineering	NOTE: Becomes J Tien ginia ring (substrings are in IPADIC)
ジェイティエンジニアリング	ジェイ ジェイティエンジニアリング/0 ティエン ジニア リング
# Anchovy pasta	NOTE: Become Anch yvipasta
アンチョビパスタ	アンチ アンチョビパスタ/0 ョビパスタ
# Surprise gift	NOTE: Becomes one token (surprise not in IPADIC)
サプライズギフト	サプライズギフト
""".trimIndent()
    }
}
