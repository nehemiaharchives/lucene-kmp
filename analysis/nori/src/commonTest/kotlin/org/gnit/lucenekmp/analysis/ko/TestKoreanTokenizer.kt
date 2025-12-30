package org.gnit.lucenekmp.analysis.ko

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.ko.KoreanTokenizer.DecompoundMode
import org.gnit.lucenekmp.analysis.ko.dict.UserDictionary
import org.gnit.lucenekmp.analysis.ko.tokenattributes.PartOfSpeechAttribute
import org.gnit.lucenekmp.analysis.ko.tokenattributes.ReadingAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockGraphTokenFilter
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal val TEST_KOREAN_USER_DICT = """
# Additional nouns
c++
C샤프
세종
세종시 세종 시
대한민국날씨
대한민국
날씨
21세기대한민국
세기
""".trimIndent()

class TestKoreanTokenizer : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer
    private lateinit var analyzerWithPunctuation: Analyzer
    private lateinit var analyzerUnigram: Analyzer
    private lateinit var analyzerDecompound: Analyzer
    private lateinit var analyzerDecompoundKeep: Analyzer
    private lateinit var analyzerReading: Analyzer

    companion object {
        fun readDict(): UserDictionary {
            StringReader(TEST_KOREAN_USER_DICT).use { reader ->
                return UserDictionary.open(reader) ?: throw RuntimeException("userdict content is empty")
            }
        }
    }

    @BeforeTest
    fun setUp() {
        val userDictionary = readDict()
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KoreanTokenizer(newAttributeFactory(), userDictionary, DecompoundMode.NONE, false)
                return TokenStreamComponents(tokenizer, tokenizer)
            }
        }
        analyzerWithPunctuation = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KoreanTokenizer(newAttributeFactory(), userDictionary, DecompoundMode.NONE, false, false)
                return TokenStreamComponents(tokenizer, tokenizer)
            }
        }
        analyzerUnigram = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KoreanTokenizer(newAttributeFactory(), userDictionary, DecompoundMode.NONE, true)
                return TokenStreamComponents(tokenizer, tokenizer)
            }
        }
        analyzerDecompound = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KoreanTokenizer(newAttributeFactory(), userDictionary, DecompoundMode.DISCARD, false)
                return TokenStreamComponents(tokenizer)
            }
        }
        analyzerDecompoundKeep = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KoreanTokenizer(newAttributeFactory(), userDictionary, DecompoundMode.MIXED, false)
                return TokenStreamComponents(tokenizer)
            }
        }
        analyzerReading = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KoreanTokenizer(newAttributeFactory(), userDictionary, DecompoundMode.NONE, false)
                val filter = KoreanReadingFormFilter(tokenizer)
                return TokenStreamComponents(tokenizer, filter)
            }
        }
    }

    @AfterTest
    fun tearDown() {
        IOUtils.close(analyzer, analyzerWithPunctuation, analyzerUnigram, analyzerDecompound, analyzerDecompoundKeep, analyzerReading)
    }

    @Test
    @Throws(IOException::class)
    fun testSeparateNumber() {
        assertAnalyzesTo(
            analyzer,
            "44사이즈",
            arrayOf("44", "사이즈"),
            intArrayOf(0, 2),
            intArrayOf(2, 5),
            intArrayOf(1, 1)
        )

        assertAnalyzesTo(
            analyzer,
            "９.９사이즈",
            arrayOf("９", "９", "사이즈"),
            intArrayOf(0, 2, 3),
            intArrayOf(1, 3, 6),
            intArrayOf(1, 1, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testSpaces() {
        assertAnalyzesTo(
            analyzer,
            "화학        이외의         것",
            arrayOf("화학", "이외", "의", "것"),
            intArrayOf(0, 10, 12, 22),
            intArrayOf(2, 12, 13, 23),
            intArrayOf(1, 1, 1, 1)
        )
        assertPartsOfSpeech(
            analyzer,
            "화학 이외의         것",
            arrayOf(POS.Type.MORPHEME, POS.Type.MORPHEME, POS.Type.MORPHEME, POS.Type.MORPHEME),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.JKG, POS.Tag.NNB),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.JKG, POS.Tag.NNB)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testPartOfSpeechs() {
        assertAnalyzesTo(
            analyzer,
            "화학 이외의 것",
            arrayOf("화학", "이외", "의", "것"),
            intArrayOf(0, 3, 5, 7),
            intArrayOf(2, 5, 6, 8),
            intArrayOf(1, 1, 1, 1)
        )
        assertPartsOfSpeech(
            analyzer,
            "화학 이외의 것",
            arrayOf(POS.Type.MORPHEME, POS.Type.MORPHEME, POS.Type.MORPHEME, POS.Type.MORPHEME),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.JKG, POS.Tag.NNB),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.JKG, POS.Tag.NNB)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testPartOfSpeechsWithPunc() {
        assertAnalyzesTo(
            analyzerWithPunctuation,
            "화학 이외의 것!",
            arrayOf("화학", " ", "이외", "의", " ", "것", "!"),
            intArrayOf(0, 2, 3, 5, 6, 7, 8, 9),
            intArrayOf(2, 3, 5, 6, 7, 8, 9, 11),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1)
        )
        assertPartsOfSpeech(
            analyzerWithPunctuation,
            "화학 이외의 것!",
            arrayOf(
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME
            ),
            arrayOf(POS.Tag.NNG, POS.Tag.SP, POS.Tag.NNG, POS.Tag.JKG, POS.Tag.SP, POS.Tag.NNB, POS.Tag.SF),
            arrayOf(POS.Tag.NNG, POS.Tag.SP, POS.Tag.NNG, POS.Tag.JKG, POS.Tag.SP, POS.Tag.NNB, POS.Tag.SF)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testFloatingPointNumber() {
        assertAnalyzesTo(
            analyzerWithPunctuation,
            "10.1 인치 모니터",
            arrayOf("10", ".", "1", " ", "인치", " ", "모니터"),
            intArrayOf(0, 2, 3, 4, 5, 7, 8),
            intArrayOf(2, 3, 4, 5, 7, 8, 11),
            intArrayOf(1, 1, 1, 1, 1, 1, 1)
        )

        assertAnalyzesTo(
            analyzer,
            "10.1 인치 모니터",
            arrayOf("10", "1", "인치", "모니터"),
            intArrayOf(0, 3, 5, 8),
            intArrayOf(2, 4, 7, 11),
            intArrayOf(1, 1, 1, 1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testPartOfSpeechsWithCompound() {
        assertAnalyzesTo(
            analyzer,
            "가락지나물은 한국, 중국, 일본",
            arrayOf("가락지나물", "은", "한국", "중국", "일본"),
            intArrayOf(0, 5, 7, 11, 15),
            intArrayOf(5, 6, 9, 13, 17),
            intArrayOf(1, 1, 1, 1, 1)
        )

        assertPartsOfSpeech(
            analyzer,
            "가락지나물은 한국, 중국, 일본",
            arrayOf(POS.Type.COMPOUND, POS.Type.MORPHEME, POS.Type.MORPHEME, POS.Type.MORPHEME, POS.Type.MORPHEME),
            arrayOf(POS.Tag.NNG, POS.Tag.JX, POS.Tag.NNP, POS.Tag.NNP, POS.Tag.NNP),
            arrayOf(POS.Tag.NNG, POS.Tag.JX, POS.Tag.NNP, POS.Tag.NNP, POS.Tag.NNP)
        )

        assertAnalyzesTo(
            analyzerDecompound,
            "가락지나물은 한국, 중국, 일본",
            arrayOf("가락지", "나물", "은", "한국", "중국", "일본"),
            intArrayOf(0, 3, 5, 7, 11, 15),
            intArrayOf(3, 5, 6, 9, 13, 17),
            intArrayOf(1, 1, 1, 1, 1, 1)
        )

        assertAnalyzesTo(
            analyzerDecompoundKeep,
            "가락지나물은 한국, 중국, 일본",
            arrayOf("가락지나물", "가락지", "나물", "은", "한국", "중국", "일본"),
            intArrayOf(0, 0, 3, 5, 7, 11, 15),
            intArrayOf(5, 3, 5, 6, 9, 13, 17),
            null,
            intArrayOf(1, 0, 1, 1, 1, 1, 1),
            intArrayOf(2, 1, 1, 1, 1, 1, 1)
        )

        assertPartsOfSpeech(
            analyzerDecompound,
            "가락지나물은 한국, 중국, 일본",
            arrayOf(
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME
            ),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.JX, POS.Tag.NNP, POS.Tag.NNP, POS.Tag.NNP),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.JX, POS.Tag.NNP, POS.Tag.NNP, POS.Tag.NNP)
        )

        assertPartsOfSpeech(
            analyzerDecompoundKeep,
            "가락지나물은 한국, 중국, 일본",
            arrayOf(
                POS.Type.COMPOUND,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME
            ),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.NNG, POS.Tag.JX, POS.Tag.NNP, POS.Tag.NNP, POS.Tag.NNP),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.NNG, POS.Tag.JX, POS.Tag.NNP, POS.Tag.NNP, POS.Tag.NNP)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testPartOfSpeechsWithInflects() {
        assertAnalyzesTo(analyzer, "감싸여", arrayOf("감싸여"), intArrayOf(0), intArrayOf(3), intArrayOf(1))

        assertPartsOfSpeech(
            analyzer,
            "감싸여",
            arrayOf(POS.Type.INFLECT),
            arrayOf(POS.Tag.VV),
            arrayOf(POS.Tag.EC)
        )

        assertAnalyzesTo(
            analyzerDecompound,
            "감싸여",
            arrayOf("감싸이", "어"),
            intArrayOf(0, 0),
            intArrayOf(3, 3),
            intArrayOf(1, 1)
        )

        assertAnalyzesTo(
            analyzerDecompoundKeep,
            "감싸여",
            arrayOf("감싸여", "감싸이", "어"),
            intArrayOf(0, 0, 0),
            intArrayOf(3, 3, 3),
            null,
            intArrayOf(1, 0, 1),
            intArrayOf(2, 1, 1)
        )

        assertPartsOfSpeech(
            analyzerDecompound,
            "감싸여",
            arrayOf(POS.Type.MORPHEME, POS.Type.MORPHEME),
            arrayOf(POS.Tag.VV, POS.Tag.EC),
            arrayOf(POS.Tag.VV, POS.Tag.EC)
        )

        assertPartsOfSpeech(
            analyzerDecompoundKeep,
            "감싸여",
            arrayOf(POS.Type.INFLECT, POS.Type.MORPHEME, POS.Type.MORPHEME),
            arrayOf(POS.Tag.VV, POS.Tag.VV, POS.Tag.EC),
            arrayOf(POS.Tag.EC, POS.Tag.VV, POS.Tag.EC)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testUnknownWord() {
        assertAnalyzesTo(
            analyzer,
            "2018 평창 동계올림픽대회",
            arrayOf("2018", "평창", "동계", "올림픽", "대회"),
            intArrayOf(0, 5, 8, 10, 13),
            intArrayOf(4, 7, 10, 13, 15),
            intArrayOf(1, 1, 1, 1, 1)
        )

        assertPartsOfSpeech(
            analyzer,
            "2018 평창 동계올림픽대회",
            arrayOf(
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME
            ),
            arrayOf(POS.Tag.SN, POS.Tag.NNP, POS.Tag.NNP, POS.Tag.NNP, POS.Tag.NNG),
            arrayOf(POS.Tag.SN, POS.Tag.NNP, POS.Tag.NNP, POS.Tag.NNP, POS.Tag.NNG)
        )

        assertAnalyzesTo(
            analyzerUnigram,
            "2018 평창 동계올림픽대회",
            arrayOf("2", "0", "1", "8", "평창", "동계", "올림픽", "대회"),
            intArrayOf(0, 1, 2, 3, 5, 8, 10, 13),
            intArrayOf(1, 2, 3, 4, 7, 10, 13, 15),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1)
        )

        assertPartsOfSpeech(
            analyzerUnigram,
            "2018 평창 동계올림픽대회",
            arrayOf(
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
                POS.Type.MORPHEME,
            ),
            arrayOf(
                POS.Tag.SY,
                POS.Tag.SY,
                POS.Tag.SY,
                POS.Tag.SY,
                POS.Tag.NNP,
                POS.Tag.NNP,
                POS.Tag.NNP,
                POS.Tag.NNG
            ),
            arrayOf(
                POS.Tag.SY,
                POS.Tag.SY,
                POS.Tag.SY,
                POS.Tag.SY,
                POS.Tag.NNP,
                POS.Tag.NNP,
                POS.Tag.NNP,
                POS.Tag.NNG
            )
        )
    }

    @Test
    @Throws(IOException::class)
    fun testReading() {
        assertReadings(analyzer, "喜悲哀歡", "희비애환")
        assertReadings(analyzer, "五朔居廬", "오삭거려")
        assertReadings(analyzer, "가늘라", null)
        assertAnalyzesTo(analyzerReading, "喜悲哀歡", arrayOf("희비애환"), intArrayOf(0), intArrayOf(4), intArrayOf(1))
        assertAnalyzesTo(analyzerReading, "五朔居廬", arrayOf("오삭거려"), intArrayOf(0), intArrayOf(4), intArrayOf(1))
        assertAnalyzesTo(analyzerReading, "가늘라", arrayOf("가늘라"), intArrayOf(0), intArrayOf(3), intArrayOf(1))
    }

    @Test
    @Throws(IOException::class)
    fun testUserDict() {
        assertAnalyzesTo(
            analyzer,
            "c++ 프로그래밍 언어",
            arrayOf("c++", "프로그래밍", "언어"),
            intArrayOf(0, 4, 10),
            intArrayOf(3, 9, 12),
            intArrayOf(1, 1, 1)
        )

        assertPartsOfSpeech(
            analyzer,
            "c++ 프로그래밍 언어",
            arrayOf(POS.Type.MORPHEME, POS.Type.MORPHEME, POS.Type.MORPHEME),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.NNG),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.NNG)
        )

        assertAnalyzesTo(
            analyzerDecompound,
            "정부세종청사",
            arrayOf("정부", "세종", "청사"),
            intArrayOf(0, 2, 4),
            intArrayOf(2, 4, 6),
            intArrayOf(1, 1, 1)
        )

        assertPartsOfSpeech(
            analyzerDecompound,
            "정부세종청사",
            arrayOf(POS.Type.MORPHEME, POS.Type.MORPHEME, POS.Type.MORPHEME),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.NNG),
            arrayOf(POS.Tag.NNG, POS.Tag.NNG, POS.Tag.NNG)
        )

        assertAnalyzesTo(analyzer, "대한민국날씨", arrayOf("대한민국날씨"), intArrayOf(0), intArrayOf(6), intArrayOf(1))
        assertAnalyzesTo(analyzer, "21세기대한민국", arrayOf("21세기대한민국"), intArrayOf(0), intArrayOf(8), intArrayOf(1))
    }

    @Test
    @Throws(IOException::class)
    fun testInterpunct() {
        assertAnalyzesTo(
            analyzer,
            "도로ㆍ지반ㆍ수자원ㆍ건설환경ㆍ건축ㆍ화재설비연구",
            arrayOf("도로", "지반", "수자원", "건설", "환경", "건축", "화재", "설비", "연구"),
            intArrayOf(0, 3, 6, 10, 12, 15, 18, 20, 22),
            intArrayOf(2, 5, 9, 12, 14, 17, 20, 22, 24),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), analyzer, 100 * RANDOM_MULTIPLIER)
        checkRandomData(random(), analyzerUnigram, 100 * RANDOM_MULTIPLIER)
        checkRandomData(random(), analyzerDecompound, 100 * RANDOM_MULTIPLIER)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        val random = random()
        checkRandomData(random, analyzer, RANDOM_MULTIPLIER, 4096)
        checkRandomData(random, analyzerUnigram, RANDOM_MULTIPLIER, 4096)
        checkRandomData(random, analyzerDecompound, RANDOM_MULTIPLIER, 4096)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStringsAtNight() {
        val random = random()
        checkRandomData(random, analyzer, 3 * RANDOM_MULTIPLIER, 8192)
        checkRandomData(random, analyzerUnigram, 3 * RANDOM_MULTIPLIER, 8192)
        checkRandomData(random, analyzerDecompound, 3 * RANDOM_MULTIPLIER, 8192)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStringsMockGraphAfter() {
        val random = random()
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KoreanTokenizer(newAttributeFactory(), null, DecompoundMode.MIXED, false)
                val graph = MockGraphTokenFilter(random(), tokenizer)
                return TokenStreamComponents(tokenizer, graph)
            }
        }
        checkRandomData(random, analyzer, RANDOM_MULTIPLIER, 4096)
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testCombining() {
        assertAnalyzesTo(
            analyzer,
            "Ба̀лтичко мо̑ре",
            arrayOf("Ба̀лтичко", "мо̑ре"),
            intArrayOf(0, 10),
            intArrayOf(9, 15),
            intArrayOf(1, 1)
        )
        assertPartsOfSpeech(
            analyzer,
            "Ба̀лтичко мо̑ре",
            arrayOf(POS.Type.MORPHEME, POS.Type.MORPHEME),
            arrayOf(POS.Tag.SL, POS.Tag.SL),
            arrayOf(POS.Tag.SL, POS.Tag.SL)
        )

        assertAnalyzesTo(
            analyzer,
            "ka̠k̚t͡ɕ͈a̠k̚",
            arrayOf("ka̠k̚t͡ɕ͈a̠k̚"),
            intArrayOf(0),
            intArrayOf(13),
            intArrayOf(1)
        )
        assertPartsOfSpeech(
            analyzer,
            "ka̠k̚t͡ɕ͈a̠k̚",
            arrayOf(POS.Type.MORPHEME),
            arrayOf(POS.Tag.SL),
            arrayOf(POS.Tag.SL)
        )

        assertAnalyzesTo(analyzer, "εἰμί", arrayOf("εἰμί"), intArrayOf(0), intArrayOf(4), intArrayOf(1))
        assertPartsOfSpeech(
            analyzer,
            "εἰμί",
            arrayOf(POS.Type.MORPHEME),
            arrayOf(POS.Tag.SL),
            arrayOf(POS.Tag.SL)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testDuplicate() {
        val s = "c++\nC쁠쁠\n세종\n세종\n세종시 세종 시"
        StringReader(s).use { rulesReader ->
            val dict = UserDictionary.open(rulesReader)!!
            assertTrue(dict.getRightId(3) != 0)
            assertFailsWith<IndexOutOfBoundsException> { dict.getRightId(4) }
        }

        val dupdup = "c++\nC쁠쁠\n세종\n세종\n세종시 세종 시\n세종시 세종 시"
        StringReader(dupdup).use { rulesReader ->
            val dict = UserDictionary.open(rulesReader)!!
            assertTrue(dict.getRightId(3) != 0)
            assertFailsWith<IndexOutOfBoundsException> { dict.getRightId(4) }
        }
    }

    @Throws(IOException::class)
    private fun assertReadings(analyzer: Analyzer, input: String, vararg readings: String?) {
        analyzer.tokenStream("ignored", input).use { ts ->
            val readingAtt = ts.addAttribute(ReadingAttribute::class)
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
    private fun assertPartsOfSpeech(
        analyzer: Analyzer,
        input: String,
        posTypes: Array<POS.Type>,
        leftPosTags: Array<POS.Tag>,
        rightPosTags: Array<POS.Tag>
    ) {
        analyzer.tokenStream("ignored", input).use { ts ->
            val partOfSpeechAtt = ts.addAttribute(PartOfSpeechAttribute::class)
            ts.reset()
            for (i in posTypes.indices) {
                val posType = posTypes[i]
                val leftTag = leftPosTags[i]
                val rightTag = rightPosTags[i]
                assertTrue(ts.incrementToken())
                assertEquals(posType, partOfSpeechAtt.getPOSType())
                assertEquals(leftTag, partOfSpeechAtt.getLeftPOS())
                assertEquals(rightTag, partOfSpeechAtt.getRightPOS())
            }
            assertFalse(ts.incrementToken())
            ts.end()
        }
    }
}
