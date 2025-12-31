package org.gnit.lucenekmp.analysis.ja.dict

import okio.IOException
import org.gnit.lucenekmp.analysis.ja.TestJapaneseTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class TestUserDictionary : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testLookup() {
        val dictionary: UserDictionary =
            TestJapaneseTokenizer.readDict()
        var s = "関西国際空港に行った"
        val dictionaryEntryResult: Array<IntArray> =
            dictionary.lookup(s.toCharArray(), 0, s.length)
        // Length should be three 関西, 国際, 空港
        assertEquals(3, dictionaryEntryResult.size.toLong())

        // Test positions
        assertEquals(0, dictionaryEntryResult[0][1].toLong()) // index of 関西
        assertEquals(2, dictionaryEntryResult[1][1].toLong()) // index of 国際
        assertEquals(4, dictionaryEntryResult[2][1].toLong()) // index of 空港

        // Test lengths
        assertEquals(2, dictionaryEntryResult[0][2].toLong()) // length of 関西
        assertEquals(2, dictionaryEntryResult[1][2].toLong()) // length of 国際
        assertEquals(2, dictionaryEntryResult[2][2].toLong()) // length of 空港

        s = "関西国際空港と関西国際空港に行った"
        val dictionaryEntryResult2: Array<IntArray> =
            dictionary.lookup(s.toCharArray(), 0, s.length)
        // Length should be six
        assertEquals(6, dictionaryEntryResult2.size.toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testReadings() {
        val dictionary: UserDictionary =
            TestJapaneseTokenizer.readDict()
        var result: Array<IntArray> = dictionary.lookup("日本経済新聞".toCharArray(), 0, 6)
        assertEquals(3, result.size.toLong())
        val wordIdNihon = result[0][0] // wordId of 日本 in 日本経済新聞
        assertEquals(
            "ニホン",
            dictionary.getMorphAttributes().getReading(wordIdNihon, "日本".toCharArray(), 0, 2)
        )

        result = dictionary.lookup("朝青龍".toCharArray(), 0, 3)
        assertEquals(1, result.size.toLong())
        val wordIdAsashoryu = result[0][0] // wordId for 朝青龍
        assertEquals(
            "アサショウリュウ",
            dictionary.getMorphAttributes()
                .getReading(wordIdAsashoryu, "朝青龍".toCharArray(), 0, 3)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testPartOfSpeech() {
        val dictionary: UserDictionary =
            TestJapaneseTokenizer.readDict()
        val result: Array<IntArray> = dictionary.lookup("日本経済新聞".toCharArray(), 0, 6)
        assertEquals(3, result.size.toLong())
        val wordIdKeizai = result[1][0] // wordId of 経済 in 日本経済新聞
        assertEquals(
            "カスタム名詞",
            dictionary.getMorphAttributes().getPartOfSpeech(wordIdKeizai)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testRead() {
        val dictionary: UserDictionary =
            TestJapaneseTokenizer.readDict()
        assertNotNull(dictionary)
    }

    @Test
    @Throws(IOException::class)
    fun testReadInvalid1() {
        // the concatenated segment must be the same as the surface form
        val invalidEntry = "日経新聞,日本 経済 新聞,ニホン ケイザイ シンブン,カスタム名詞"
        val e: RuntimeException =
            expectThrows(
                RuntimeException::class,
                "RuntimeException should be thrown when passed an invalid dictionary entry."
            ) {
                UserDictionary.open(
                    StringReader(invalidEntry)
                )
            }
        assertTrue(e.message!!.contains("does not match the surface form"))
    }

    @Test
    @Throws(IOException::class)
    fun testReadInvalid2() {
        // the concatenated segment must be the same as the surface form
        val invalidEntry = "日本経済新聞,日経 新聞,ニッケイ シンブン,カスタム名詞"
        val e: RuntimeException =
            expectThrows(
                RuntimeException::class,
                "RuntimeException should be thrown when passed an invalid dictionary entry."
            ) {
                UserDictionary.open(
                    StringReader(invalidEntry)
                )
            }
        assertTrue(e.message!!.contains("does not match the surface form"))
    }

    @Test
    @Throws(IOException::class)
    fun testSharp() {
        val inputs = arrayOf("テスト#", "テスト#テスト")
        val dictionary: UserDictionary =
            TestJapaneseTokenizer.readDict()

        for (input in inputs) {
            println(input)
            val result: Array<IntArray> =
                dictionary.lookup(input.toCharArray(), 0, input.length)
            assertEquals(
                "カスタム名刺",
                dictionary.getMorphAttributes().getPartOfSpeech(result[0][0])
            )
        }
    }
}
