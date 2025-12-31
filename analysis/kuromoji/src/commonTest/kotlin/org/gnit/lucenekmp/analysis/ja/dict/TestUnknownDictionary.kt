package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.analysis.util.CSVUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

class TestUnknownDictionary : LuceneTestCase() {

    @Test
    fun testPutCharacterCategory() {
        val unkDic = UnknownDictionaryWriter(10 * 1024 * 1024)

        expectThrows(
            Exception::class
        ) {
            unkDic.putCharacterCategory(
                0,
                "DUMMY_NAME"
            )
        }

        expectThrows(
            Exception::class
        ) {
            unkDic.putCharacterCategory(
                -1,
                "KATAKANA"
            )
        }

        unkDic.putCharacterCategory(0, "DEFAULT")
        unkDic.putCharacterCategory(1, "GREEK")
        unkDic.putCharacterCategory(2, "HIRAGANA")
        unkDic.putCharacterCategory(3, "KATAKANA")
        unkDic.putCharacterCategory(4, "KANJI")
    }

    @Test
    fun testPut() {
        val unkDic = UnknownDictionaryWriter(10 * 1024 * 1024)
        expectThrows(
            NumberFormatException::class
        ) {
            unkDic.put(
                CSVUtil.parse(
                    "KANJI,1285,11426,名詞,一般,*,*,*,*,*,*,*"
                )
            )
        }

        val entry1 = "ALPHA,1285,1285,13398,名詞,一般,*,*,*,*,*,*,*"
        val entry2 = "HIRAGANA,1285,1285,13069,名詞,一般,*,*,*,*,*,*,*"
        val entry3 = "KANJI,1285,1285,11426,名詞,一般,*,*,*,*,*,*,*"

        unkDic.putCharacterCategory(0, "ALPHA")
        unkDic.putCharacterCategory(1, "HIRAGANA")
        unkDic.putCharacterCategory(2, "KANJI")

        unkDic.put(CSVUtil.parse(entry1))
        unkDic.put(CSVUtil.parse(entry2))
        unkDic.put(CSVUtil.parse(entry3))
    }

    companion object {
        const val FILENAME: String = "unk-tokeninfo-dict.obj"
    }
}
